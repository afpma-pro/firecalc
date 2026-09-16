/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_testkit

import cats.data.OptionT
import cats.effect.kernel.{Async, Clock, Ref}
import cats.syntax.all.*

import coulomb.*

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.others.sunit.SUnit
import afpma.firecalc.units.others.sunits.SUnits.given

import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given
import io.circe.{CursorOp, DecodingFailure, Json}
import io.circe.syntax.*
import org.http4s.{Header, HttpApp, HttpRoutes, MediaType, Method, Request, Response, Status}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIStringSyntax

/**
 * A minimal, Austrian-free REFERENCE implementation of the normative Firebox
 * Calculation API (`GET /v1/kinds`, `POST /v1/calculate`), used as the target
 * of the public black-box contract tests (ticket 17).
 *
 * It implements the contract directly from the public wire types
 * (`firebox_calc_api`) and the OpenAPI document — no engine registry, no
 * `FireboxCalcCore`, no Strict/Austrian symbol, no proprietary fixture. Its
 * calculation responses are canned wire payloads whose quantity unit strings
 * are taken from the public `SUnit` vocabulary, exactly the strings a
 * conforming server emits and the generic consumer validates. It is NOT a
 * numerical oracle: numerical equivalence belongs to the private repository
 * (`LocalRemoteEquivalenceSuite`).
 *
 * Behaviors mirrored from the normative contract:
 *   - discovery advertises only the kinds this deployment serves, with
 *     `Cache-Control: public, max-age=3600`;
 *   - the §7 error taxonomy (`unknown_kind`, `unsupported_api_version`,
 *     `unsupported_locale`, `kind_not_served`, `malformed_request`,
 *     `payload_too_large`, `rate_limited`) with its status codes and JSON
 *     envelopes. Request-decode failures mirror the real server
 *     (`FireboxCalcRoutes`): an unknown/absent `kind` is 400 `unknown_kind`
 *     (naming the valid wire values); any other decode failure is 400
 *     `malformed_request` carrying real field-path details
 *     (`decoding_failure:<field.path>`);
 *   - per-endpoint token-bucket rate limiting with `X-RateLimit-*` headers,
 *     Unix-epoch reset semantics, and `Retry-After` on 429;
 *   - a complete operating-point-keyed `AsResult` grid (round-3 D3) so generic
 *     consumers can exercise the full continuation state over HTTP.
 */
object ReferenceConformingServer:

    /** Locales this reference deployment serves (mirrors the i18n layer). */
    val supportedLocales: List[String] = List("en", "fr")

    /** The only API version in the normative contract. */
    val supportedApiVersions: List[String] = List(afpma.firecalc.firebox_calc_api.ApiVersion.v1)

    /**
     * The kinds this reference deployment serves — deliberately a SUBSET of
     * `WireFireboxKind`: discovery is deployment discovery, not a catalog of
     * every wire enum value (SPEC_00 Further Notes).
     */
    val servedKinds: List[WireFireboxKind] =
        List(WireFireboxKind.Traditional, WireFireboxKind.Ecolabeled)

    /** Maximum request body size in bytes (1 MiB) — 413 above it. */
    val maxBodySize: Long = 1024L * 1024L

    /**
     * Token-bucket capacities per composite key. `kindsBurst` keeps the
     * production value so the 429 behavior stays testable within a suite;
     * `calculateBurst` is generous so contract tests never trip it. Both
     * buckets run the same middleware code path.
     */
    val calculateBurst: Int = 100
    val kindsBurst    : Int = 5

    private enum Decision:
        case Allow(headers: List[Header.Raw])
        case Deny(headers: List[Header.Raw], waitSeconds: Long)

    /** The normative routes wrapped in per-endpoint rate limiting, as an app. */
    def app[F[_]: Async]: F[HttpApp[F]] =
        routes[F].map(_.orNotFound)

    /**
     * The normative routes, wrapped in per-endpoint rate limiting.
     * Effectful (`F[HttpRoutes[F]]`) because the token buckets allocate state.
     */
    def routes[F[_]: Async]: F[HttpRoutes[F]] =
        Ref.of[F, Map[String, (Double, Long)]](Map.empty).map { buckets =>
            HttpRoutes[F] { req =>
                val (capacity, refillPerSecond) = endpointLimit(req)
                val key = s"${capacity}|${compositeKey(req)}"
                OptionT(
                    Clock[F].realTime.map(_.toSeconds).flatMap { now =>
                        buckets
                            .modify { m =>
                                val (tokens, lastRefill) = m.getOrElse(key, (capacity.toDouble, now))
                                val refilled =
                                    math.min(tokens + (now - lastRefill) * refillPerSecond, capacity.toDouble)
                                if refilled >= 1.0 then
                                    val remaining = refilled - 1.0
                                    val reset     =
                                        now + ((capacity - remaining) / refillPerSecond).ceil.toLong
                                    (
                                        m.updated(key, (remaining, now)),
                                        Decision.Allow(rateHeaders(capacity, remaining.toInt, reset))
                                    )
                                else
                                    val waitSecs =
                                        math.max(1L, ((1.0 - refilled) / refillPerSecond).ceil.toLong)
                                    (
                                        m.updated    (key, (refilled, now)                              ),
                                        Decision.Deny(rateHeaders(capacity, 0, now + waitSecs), waitSecs)
                                    )
                            }
                            .flatMap {
                                case Decision.Allow(headers)          =>
                                    dispatch(req).map(resp => Some(resp.putHeaders(headers.map(h => h: Header.ToRaw)*)))
                                case Decision.Deny(headers, waitSecs) =>
                                    val allHeaders: Seq[Header.ToRaw] =
                                        Header.Raw(ci"Retry-After", waitSecs.toString) +:
                                            headers.map(h => h: Header.ToRaw)
                                    Async[F].pure(
                                        Some(
                                            jsonResponse(
                                                Status.TooManyRequests,
                                                ErrorResponseEnvelope  (
                                                    error   = "rate_limited",
                                                    message = s"Rate limit exceeded. Retry after $waitSecs seconds."
                                                ).asJson,
                                                extraHeaders = allHeaders
                                            )
                                        )
                                    )
                            }
                    }
                )
            }
        }

    // ── Dispatch ───────────────────────────────────────────────────────────

    private def dispatch[F[_]: Async](req: Request[F]): F[Response[F]] =
        (req.method, req.pathInfo.renderString) match
            case (Method.GET, "/v1/kinds"     ) =>
                Async[F].pure(
                    jsonResponse(
                        Status.Ok,
                        DiscoveryResponse(
                            apiVersion = afpma.firecalc.firebox_calc_api.ApiVersion.v1,
                            kinds      = servedKinds
                        ).asJson,
                        extraHeaders = Seq(Header.Raw(ci"Cache-Control", "public, max-age=3600"))
                    )
                )
            case (Method.POST, "/v1/calculate") =>
                calculate(req)
            case _ =>
                Async[F].pure(Response[F](Status.NotFound))

    private def calculate[F[_]: Async](req: Request[F]): F[Response[F]] =
        for
            body <- req.bodyText.compile.string
            oversize = req.contentLength.exists(_ > maxBodySize) || body.length > maxBodySize
            response <-
                if oversize then
                    Async[F].pure(
                        errorEnvelope(
                            Status.PayloadTooLarge,
                            "payload_too_large",
                            "Request body exceeds the maximum allowed size"
                        )
                    )
                else
                    decodeRequest(body) match
                        case Left(error)    => Async[F].pure(decodeErrorToResponse[F](error))
                        case Right(request) => Async[F].pure(handle[F](request)             )
        yield response

    private enum DecodeError:
        case UnknownKind(df: DecodingFailure)
        case KindNotServed(kind: WireFireboxKind)
        case Malformed(err: io.circe.Error)

    /**
     * Two-phase decode: parse JSON, decode `kind` first, check if served
     * (404), then decode full request. Ensures a known-but-unserved kind
     * yields 404 even when the firebox subtype doesn't match (which would
     * otherwise cause a decode failure → 400).
     */
    private def decodeRequest(body: String): Either[DecodeError, FireboxCalcRequest] =
        io.circe.parser.parse(body) match
            case Left(pf)    =>
                // JSON syntax error.
                Left(DecodeError.Malformed(pf))
            case Right(json) =>
                json.hcursor.downField("kind").as[WireFireboxKind] match
                    // Kind field is unknown or absent → unknown_kind.
                    case Left(df: DecodingFailure)                  =>
                        Left(DecodeError.UnknownKind(df))
                    // Kind is known. Check if served before full decode.
                    case Right(kind) if !servedKinds.contains(kind) =>
                        Left(DecodeError.KindNotServed(kind))
                    // Kind is served — decode the full request.
                    case Right(_)                                   =>
                        json.as[FireboxCalcRequest] match
                            case Left(err) => Left(DecodeError.Malformed(err))
                            case Right(r)  => Right(r)

    private def decodeErrorToResponse[F[_]](error: DecodeError): Response[F] =
        error match
            case DecodeError.UnknownKind(df)     => unknownKindResponse[F](df)
            case DecodeError.KindNotServed(kind) =>
                errorEnvelope[F](
                    Status.NotFound,
                    "kind_not_served",
                    s"Firebox kind '${kind.wire}' is not served by this deployment (registry: reference)"
                )
            case DecodeError.Malformed(err)      => decodeFailureResponse[F](err)

    private def handle[F[_]](req: FireboxCalcRequest): Response[F] =
        if !supportedApiVersions.contains(req.apiVersion) then
            errorEnvelope(
                Status.BadRequest,
                "unsupported_api_version",
                s"API version '${req.apiVersion}' is not supported. Supported: ${supportedApiVersions.mkString(", ")}"
            )
        else if !supportedLocales.contains(req.locale.take(2)) then
            errorEnvelope                 (
                Status.BadRequest,
                "unsupported_locale",
                s"Locale '${req.locale}' is not supported. Supported: ${supportedLocales.mkString(", ")}"
            )
        else
            jsonResponse                  (
                Status.Ok,
                FireboxCalcResponse               (
                    apiVersion                = req.apiVersion,
                    kind                      = req.kind,
                    locale                    = req.locale,
                    fireboxContribution       = WireValidated.Valid(
                        WireFireboxContribution.AsResult(cannedGrid)
                    ),
                    combustionAirContribution = WireValidated.Valid(WireFireboxContribution.Without),
                    constraints               = cannedConstraints(req.params.m_B),
                    reportData                = cannedReport(req.kind)
                ).asJson
            )

    /**
     * Map a request-body decode failure to its §7 response, mirroring
     * `FireboxCalcRoutes`: a circe `DecodingFailure` at the `kind` field is
     * 400 `unknown_kind`; any other `DecodingFailure` is 400
     * `malformed_request` with field-path details; a JSON syntax error
     * (`ParsingFailure`, which carries no field cursor) is a details-less 400
     * `malformed_request`.
     */
    private def decodeFailureResponse[F[_]](err: io.circe.Error): Response[F] =
        err match
            case df: DecodingFailure =>
                findKindDecodeFailure(df) match
                    case Some(kindDf) => unknownKindResponse(kindDf)
                    case None         => malformedRequestResponse(df)
            case _ =>
                jsonResponse(
                    Status.BadRequest,
                    FireboxCalcError  (
                        error   = "malformed_request",
                        message = "Request body failed validation",
                        details = None
                    ).asJson
                )

    /**
     * Detect a decode failure at the `kind` field (an unknown or absent kind
     * value), mirroring `FireboxCalcRoutes.findKindDecodeFailure`. The derived
     * `FireboxCalcRequest` decoder reports a bad `kind` with a
     * `CursorOp.DownField("kind")` (or `Field`) in its history; matching the
     * cursor ops — never the failure message — keeps the detection structural.
     */
    /** Extract field names from a circe cursor history. */
    private def fieldNamesFromHistory(history: List[CursorOp]): List[String] =
        history.collect {
            case CursorOp.DownField(name) => name
            case CursorOp.Field(name)     => name
        }

    private def findKindDecodeFailure(df: DecodingFailure): Option[DecodingFailure] =
        if fieldNamesFromHistory(df.history).contains("kind") then Some(df) else None

    /**
     * 400 `unknown_kind` for an unknown/absent `kind` value — mirrors
     * `FireboxCalcRoutes.unknownKindResponse`, naming the valid wire values.
     */
    private def unknownKindResponse[F[_]](df: DecodingFailure): Response[F] =
        errorEnvelope(
            Status.BadRequest,
            "unknown_kind",
            s"Unknown firebox kind (${df.message}). Supported: " +
                WireFireboxKind.values.map(_.wire).mkString(", ")
        )

    /**
     * 400 `malformed_request` with per-field details — mirrors
     * `FireboxCalcRoutes.extractDecodeDetails`. The field path is reconstructed
     * from the circe cursor history, so a failure nested under
     * `firebox.firebox_depth` is reported as `decoding_failure:firebox.firebox_depth`.
     */
    private def malformedRequestResponse[F[_]](df: DecodingFailure): Response[F] =
        val fieldPath = fieldNamesFromHistory(df.history).mkString(".")
        jsonResponse(
            Status.BadRequest,
            FireboxCalcError  (
                error   = "malformed_request",
                message = s"Request body failed validation at ${if fieldPath.nonEmpty then fieldPath else "root"}",
                details = Some(
                    List(
                        WireErrorItem   (
                            code    = s"decoding_failure:${if fieldPath.nonEmpty then fieldPath else "root"}",
                            message = df.message
                        )
                    )
                )
            ).asJson
        )

    /** Build a canonical error response envelope. */
    private def errorEnvelope[F[_]](status: Status, error: String, message: String): Response[F] =
        jsonResponse(status, ErrorResponseEnvelope(error, message).asJson)

    private def jsonResponse[F[_]](
        status      : Status,
        json        : Json,
        extraHeaders: Seq[Header.ToRaw] = Seq.empty
    ): Response[F] =
        Response[F](status)
            .withEntity(json)
            .withContentType(`Content-Type`(MediaType.application.json))
            .putHeaders(extraHeaders*)

    // ── Rate limiting ──────────────────────────────────────────────────────

    private def endpointLimit[F[_]](req: Request[F]): (Int, Double) =
        if req.pathInfo.renderString == "/v1/kinds" then (kindsBurst, 10.0 / 60.0)
        else                                             (calculateBurst, 1.0    )

    private def compositeKey[F[_]](req: Request[F]): String =
        val ip     = req.remote.map(_.toString).getOrElse("unknown")
        val appKey =
            req.headers.headers.find(_.name == ci"X-Firecalc-App-Key").map(_.value).getOrElse("")
        s"$ip|$appKey"

    private def rateHeaders(capacity: Int, remaining: Int, resetEpochSeconds: Long): List[Header.Raw] =
        List(
            Header.Raw(ci"X-RateLimit-Limit", capacity.toString         ),
            Header.Raw(ci"X-RateLimit-Remaining", remaining.toString    ),
            Header.Raw(ci"X-RateLimit-Reset", resetEpochSeconds.toString)
        )

    // ── Canned wire payloads ───────────────────────────────────────────────
    //
    // Unit strings come from the public SUnit vocabulary — the exact strings a
    // conforming server emits and the generic consumer's `WireConversions`
    // validates. No engine type is constructed here.

    private def qty(value: String, unit: String): WireQty = WireQty(value, unit)

    private val meter   : String = SUnit[Meter].showUnitFull
    private val pascal  : String = SUnit[Pascal].showUnitFull
    private val celsius : String = SUnit[Celsius].showUnitFull
    private val density : String = SUnit[Kilogram / (Meter ^ 3)].showUnitFull
    private val velocity: String = SUnit[Meter / Second].showUnitFull

    private def cannedResult: WirePipeResult =
        WirePipeResult               (
            typ                = WirePipeType.Firebox,
            lengthSum          = qty("1.0", meter),
            heightSum          = qty("1.0", meter),
            zeta               = None,
            pu                 = WireValidated.Valid(qty("12.0", pascal)),
            pd                 = None,
            pRs                = qty("10.0", pascal),
            pRg                = qty("2.0", pascal),
            ph                 = qty("12.0", pascal),
            gasTempStart       = qty("20.0", celsius),
            gasTempMiddle      = qty("120.0", celsius),
            gasTempEnd         = qty("220.0", celsius),
            densityMiddle      = Some(qty("0.9", density)),
            densityMean        = Some(qty("0.95", density)),
            vStart             = Some(qty("1.5", velocity)),
            vEnd               = Some(qty("2.5", velocity)),
            lastDensityMean    = Some(qty("0.95", density)),
            lastDensityMiddle  = Some(qty("0.9", density)),
            lastVelocityMean   = Some(qty("2.0", velocity)),
            lastVelocityMiddle = Some(qty("2.0", velocity))
        )

    /**
     * A two-point grid covering both draft conditions at nominal load — the
     * complete operating-point state (round-3 D3) for a firebox without a
     * reduced output. Keys are unique, as the wire decoder requires.
     */
    private def cannedGrid: List[WireOpPointResult] =
        val result = cannedResult
        List(
            WireOpPointResult(
                WireDraftCondition.DraftMinOrPositivePressureMax,
                WireLoadQty.Nominal,
                result
            ),
            WireOpPointResult(
                WireDraftCondition.DraftMaxOrPositivePressureMin,
                WireLoadQty.Nominal,
                result
            )
        )

    private def cannedConstraints(mB: Option[WireQty]): List[ConstraintVerdict] =
        List(
            ConstraintVerdict      (
                term       = ConstraintTerm.MB,
                label      = "m_B",
                descriptor = ConstraintDescriptor.Opaque,
                outcome    = mB match
                    case Some(_) => ConstraintOutcome.Satisfied
                    case None    => ConstraintOutcome.NotEvaluated(NotEvaluatedReason.MissingMB)
            )
        )

    private def cannedReport(kind: WireFireboxKind): FireboxReportData =
        FireboxReportData    (
            fireboxDescription     = WireTable(
                header     = Some("Firebox description"),
                colHeaders = Some(List("Property", "Value")),
                rows       = List(List("type", kind.wire))
            ),
            emissionsAndEfficiency = WireTable(
                header     = Some("Emissions and efficiency"),
                colHeaders = Some(List("Parameter", "Value")),
                rows       = Nil
            ),
            combustionAirPipeRows  = Nil,
            fireboxPipeRows        = Nil,
            ecolabeledOutputs      = None
        )

end ReferenceConformingServer
