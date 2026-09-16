/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

import afpma.firecalc.dto.instances.V5Instances.given

import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*

object Codecs:

    // =========================================================================
    //  Semiauto-derived codecs (case classes / enum with fields)
    // =========================================================================

    given Encoder[WireQty] = deriveEncoder
    given Decoder[WireQty] = deriveDecoder

    given Encoder[WirePipeShape] = deriveEncoder
    given Decoder[WirePipeShape] = deriveDecoder

    given Encoder[Standard15544Params] = deriveEncoder
    given Decoder[Standard15544Params] = deriveDecoder

    given Encoder[WireErrorItem] = deriveEncoder
    given Decoder[WireErrorItem] = deriveDecoder

    given Encoder[WireNamedPipeEl] = deriveEncoder
    given Decoder[WireNamedPipeEl] = deriveDecoder

    given Encoder[WirePipeFullDescr] = deriveEncoder
    given Decoder[WirePipeFullDescr] = deriveDecoder

    given Encoder[WirePipeResult] = deriveEncoder
    given Decoder[WirePipeResult] = deriveDecoder

    given Encoder[WireEcolabeledOutputs] = deriveEncoder
    given Decoder[WireEcolabeledOutputs] = deriveDecoder

    given Encoder[WireTable] = deriveEncoder
    given Decoder[WireTable] = deriveDecoder

    given Encoder[FireboxReportData] = deriveEncoder
    given Decoder[FireboxReportData] = deriveDecoder

    given Encoder[DiscoveryResponse] = deriveEncoder
    given Decoder[DiscoveryResponse] = deriveDecoder

    given Encoder[FireboxCalcError] = deriveEncoder
    given Decoder[FireboxCalcError] = deriveDecoder

    // =========================================================================
    //  String enums — wire values live on the enum cases (`wire`), never here
    // =========================================================================

    /**
     * Derives the `Encoder`/`Decoder` pair for a flat string enum whose cases
     * each carry their wire value. `values` is the enum's generated companion
     * array, so the mapping is exhaustive by construction and a new case can
     * never be forgotten here.
     *
     * `typeName` only ever appears in the failure message, mirroring the former
     * hand-written decoders (`Unknown <typeName>: <bad value>`). The decoder is
     * `Decoder.decodeString.emap`, which reports failures with the *calling*
     * cursor's history — so a bad value nested under a field still yields a
     * `DecodingFailure` carrying `CursorOp.DownField(...)`, which
     * `FireboxCalcRoutes.findKindDecodeFailure` relies on to pick
     * `unknown_kind` over `malformed_request`.
     */
    private def wireCodec[A](typeName: String, values: Array[A])(
        wireOf: A => String
    ): (Encoder[A], Decoder[A]) =
        val byWire: Map[String, A] = values.view.map(a => wireOf(a) -> a).toMap
        (
            Encoder.encodeString.contramap[A](wireOf                                              ),
            Decoder.decodeString.emap        (s => byWire.get(s).toRight(s"Unknown $typeName: $s"))
        )

    private val (encodeWireFireboxKind, decodeWireFireboxKind) =
        wireCodec("WireFireboxKind", WireFireboxKind.values)(_.wire)

    given Encoder[WireFireboxKind] = encodeWireFireboxKind
    given Decoder[WireFireboxKind] = decodeWireFireboxKind

    private val (encodeWireStandardVariant, decodeWireStandardVariant) =
        wireCodec("WireStandardVariant", WireStandardVariant.values)(_.wire)

    given Encoder[WireStandardVariant] = encodeWireStandardVariant
    given Decoder[WireStandardVariant] = decodeWireStandardVariant

    given Encoder[WireOperatingEnvelope] = deriveEncoder
    given Decoder[WireOperatingEnvelope] = deriveDecoder

    given Encoder[WireTemperatureOverrides] = deriveEncoder
    given Decoder[WireTemperatureOverrides] = deriveDecoder

    // FireboxCalcRequest — custom codec: firebox field uses flat encoding
    // (kind discriminator + subtype properties, no wrapper key)
    given Encoder[FireboxCalcRequest] = Encoder.instance { req =>
        val fireboxFields                 = req.kind.constructorName match
            case None     =>
                // Unencodable kind (e.g. CustomForLab) — return empty firebox
                // rather than throwing; the encoder contract returns Json.
                Nil
            case Some(cn) =>
                req.firebox.asJson.asObject match
                    case Some(obj) =>
                        obj.toList.find(_._1 == cn) match
                            case Some((_, inner)) => inner.asObject.getOrElse(JsonObject.empty).toList
                            case None             =>
                                // Wrapper key mismatch (kind/firebox type mismatch in test
                                // data). Extract fields from whatever wrapper key is present
                                // so the decoder can still find the correct subtype.
                                Console.err.println(
                                    s"WARNING: FireboxCalcRequest encoder: wrapper key " +
                                        s"'$cn' not found in V5Instances-encoded JSON for " +
                                        s"kind ${req.kind.wire}. Extracting from available key."
                                )
                                obj.toList.headOption match
                                    case Some((_, inner)) => inner.asObject.getOrElse(JsonObject.empty).toList
                                    case None             => Nil
                    case None      => Nil
        Json.obj(
            ("apiVersion", req.apiVersion.asJson                ),
            ("kind", req.kind.asJson                            ),
            ("firebox", Json.obj(fireboxFields*)                ),
            ("params", req.params.asJson                        ),
            ("airIntakePipeShape", req.airIntakePipeShape.asJson),
            ("locale", req.locale.asJson                        ),
            ("standard", req.standard.asJson                    ),
            ("operatingEnvelope", req.operatingEnvelope.asJson  )
        )
    }

    given Decoder[FireboxCalcRequest] = Decoder.instance { c =>
        for
            apiVersion      <- c.downField("apiVersion").as[ApiVersion]
            kind            <- c.downField("kind").as[WireFireboxKind]
            constructorName <- kind.constructorName match
                case Some(name) => Right(name)
                case None       =>
                    Left(
                        DecodingFailure(
                            s"kind ${kind.wire} has no Firebox_V4 constructor",
                            c.downField("firebox").history
                        )
                    )
            fireboxRaw      <- c.downField("firebox").as[Json]
            fireboxWrapped = Json.obj(constructorName -> fireboxRaw)
            firebox            <-
                // Strict decode: wrap flat firebox JSON with the constructor name
                // derived from `kind`. No fallback — the kind must match the
                // firebox subtype, or the request is rejected (400 malformed_request).
                // Catch exceptions from the underlying codec (e.g., bad units) so
                // they surface as DecodingFailure rather than crashing the route.
                try Decoder[afpma.firecalc.dto.v5.Firebox_V4].decodeJson(fireboxWrapped)
                catch
                    case ex: Exception =>
                        Left(DecodingFailure(s"firebox decode error: ${ex.getMessage}", c.history))
            params             <- c.downField("params").as[Standard15544Params]
            airIntakePipeShape <- c.downField("airIntakePipeShape").as[Option[WirePipeShape]]
            locale             <- c.downField("locale").as[WireLocale]
            standard           <- c.downField("standard").as[WireStandardVariant]
            operatingEnvelope  <- c.downField("operatingEnvelope").as[Option[WireOperatingEnvelope]]
        yield FireboxCalcRequest(
            apiVersion,
            kind,
            firebox,
            params,
            airIntakePipeShape,
            locale,
            standard,
            operatingEnvelope
        )
    }

    // FireboxCalcResponse
    given Encoder[FireboxCalcResponse] = deriveEncoder
    given Decoder[FireboxCalcResponse] = deriveDecoder

    given Encoder[WireOpPointResult] = deriveEncoder
    given Decoder[WireOpPointResult] = deriveDecoder

    // ConstraintVerdict
    given Encoder[ConstraintVerdict] = deriveEncoder
    given Decoder[ConstraintVerdict] = deriveDecoder

    // =========================================================================
    //  Either[WireQty, Double] — Left = object {value, unit}, Right = number
    // =========================================================================

    given Encoder[Either[WireQty, Double]] = Encoder.instance {
        case Left(q)  => summon[Encoder[WireQty]].apply(q)
        case Right(d) => Json.fromDoubleOrString(d)
    }
    given Decoder[Either[WireQty, Double]] =
        Decoder.decodeDouble.map(Right(_)) or Decoder[WireQty].map(Left(_))

    private val (encodeWirePipeType, decodeWirePipeType) =
        wireCodec("WirePipeType", WirePipeType.values)(_.wire)

    given Encoder[WirePipeType] = encodeWirePipeType
    given Decoder[WirePipeType] = decodeWirePipeType

    private val (encodeConstraintTerm, decodeConstraintTerm) =
        wireCodec("ConstraintTerm", ConstraintTerm.values)(_.wire)

    given Encoder[ConstraintTerm] = encodeConstraintTerm
    given Decoder[ConstraintTerm] = decodeConstraintTerm

    private val (encodeNotEvaluatedReason, decodeNotEvaluatedReason) =
        wireCodec("NotEvaluatedReason", NotEvaluatedReason.values)(_.wire)

    given Encoder[NotEvaluatedReason] = encodeNotEvaluatedReason
    given Decoder[NotEvaluatedReason] = decodeNotEvaluatedReason

    private val (encodeWireDraftCondition, decodeWireDraftCondition) =
        wireCodec("WireDraftCondition", WireDraftCondition.values)(_.wire)

    given Encoder[WireDraftCondition] = encodeWireDraftCondition
    given Decoder[WireDraftCondition] = decodeWireDraftCondition

    private val (encodeWireLoadQty, decodeWireLoadQty) =
        wireCodec("WireLoadQty", WireLoadQty.values)(_.wire)

    given Encoder[WireLoadQty] = encodeWireLoadQty
    given Decoder[WireLoadQty] = decodeWireLoadQty

    // =========================================================================
    //  Discriminated oneOf codecs — WirePipeElDescr (discriminator: "kind")
    // =========================================================================

    private def addKind(kind: String)(json: Json): Json =
        json.asObject match
            case Some(obj) => obj.add("kind", Json.fromString(kind)).toJson
            case None      => json

    private given Encoder[WirePipeElDescr.StraightSection]        = deriveEncoder
    private given Encoder[WirePipeElDescr.AngleVifDe0A180]        = deriveEncoder
    private given Encoder[WirePipeElDescr.CircularArc60]          = deriveEncoder
    private given Encoder[WirePipeElDescr.SplitMerge90]           = deriveEncoder
    private given Encoder[WirePipeElDescr.SectionGeometryChange]  = deriveEncoder
    private given Encoder[WirePipeElDescr.SingularFlowResistance] = deriveEncoder
    private given Encoder[WirePipeElDescr.PressureDiff]           = deriveEncoder

    private given Decoder[WirePipeElDescr.StraightSection]        = deriveDecoder
    private given Decoder[WirePipeElDescr.AngleVifDe0A180]        = deriveDecoder
    private given Decoder[WirePipeElDescr.CircularArc60]          = deriveDecoder
    private given Decoder[WirePipeElDescr.SplitMerge90]           = deriveDecoder
    private given Decoder[WirePipeElDescr.SectionGeometryChange]  = deriveDecoder
    private given Decoder[WirePipeElDescr.SingularFlowResistance] = deriveDecoder
    private given Decoder[WirePipeElDescr.PressureDiff]           = deriveDecoder

    given Encoder[WirePipeElDescr] = Encoder.instance {
        case el: WirePipeElDescr.StraightSection        => addKind("straightSection")(el.asJson)
        case el: WirePipeElDescr.AngleVifDe0A180        => addKind("angleVifDe0A180")(el.asJson)
        case el: WirePipeElDescr.CircularArc60          => addKind("circularArc60")(el.asJson)
        case el: WirePipeElDescr.SplitMerge90           => addKind("splitMerge90")(el.asJson)
        case el: WirePipeElDescr.SectionGeometryChange  => addKind("sectionGeometryChange")(el.asJson)
        case el: WirePipeElDescr.SingularFlowResistance => addKind("singularFlowResistance")(el.asJson)
        case el: WirePipeElDescr.PressureDiff           => addKind("pressureDiff")(el.asJson)
    }

    given Decoder[WirePipeElDescr] = Decoder.instance { c =>
        c.downField("kind").as[String].flatMap {
            case "straightSection"        => c.as[WirePipeElDescr.StraightSection]
            case "angleVifDe0A180"        => c.as[WirePipeElDescr.AngleVifDe0A180]
            case "circularArc60"          => c.as[WirePipeElDescr.CircularArc60]
            case "splitMerge90"           => c.as[WirePipeElDescr.SplitMerge90]
            case "sectionGeometryChange"  => c.as[WirePipeElDescr.SectionGeometryChange]
            case "singularFlowResistance" => c.as[WirePipeElDescr.SingularFlowResistance]
            case "pressureDiff"           => c.as[WirePipeElDescr.PressureDiff]
            case other                    => Left(DecodingFailure(s"Unknown WirePipeElDescr kind: $other", c.history))
        }
    }

    // =========================================================================
    //  Discriminated oneOf codecs — WireValidated (discriminator: "status")
    // =========================================================================

    given [A: Encoder: Decoder]: Encoder[WireValidated[A]] = Encoder.instance {
        case WireValidated.Valid(value)    =>
            Json.obj(
                "status" -> Json.fromString("valid"),
                "value"  -> summon[Encoder[A]].apply(value)
            )
        case WireValidated.Invalid(errors) =>
            Json.obj(
                "status" -> Json.fromString("invalid"),
                "errors" -> errors.asJson
            )
    }

    given [A: Encoder: Decoder]: Decoder[WireValidated[A]] = Decoder.instance { c =>
        c.downField("status").as[String].flatMap {
            case "valid"   =>
                c.downField("value").as[A].map(value => WireValidated.Valid(value))
            case "invalid" =>
                c.downField("errors").as[List[WireErrorItem]].flatMap { errors =>
                    if errors.isEmpty then
                        Left  (DecodingFailure("WireValidated.Invalid requires at least one error", c.history))
                    else Right(WireValidated.Invalid(errors)                                                  )
                }
            case other     =>
                Left(DecodingFailure(s"Unknown WireValidated status: $other", c.history))
        }
    }

    // =========================================================================
    //  Discriminated oneOf codecs — WireFireboxContribution (discriminator: "kind")
    // =========================================================================

    given Encoder[WireFireboxContribution] = Encoder.instance {
        case WireFireboxContribution.Without           =>
            Json.obj("kind" -> Json.fromString("without"))
        case WireFireboxContribution.AsDescr(descr)    =>
            Json.obj (
                "kind"  -> Json.fromString("asDescr"),
                "descr" -> descr.asJson
            )
        case WireFireboxContribution.AsResult(results) =>
            Json.obj   (
                "kind"    -> Json.fromString("asResult"),
                "results" -> results.asJson
            )
    }

    given Decoder[WireFireboxContribution] = Decoder.instance { c =>
        c.downField("kind").as[String].flatMap {
            case "without"  =>
                Right(WireFireboxContribution.Without)
            case "asDescr"  =>
                c.downField("descr").as[WirePipeFullDescr].map(WireFireboxContribution.AsDescr(_))
            case "asResult" =>
                c.downField("results").as[List[WireOpPointResult]].flatMap { results =>
                    if results.isEmpty then
                        Left(
                            DecodingFailure("WireFireboxContribution.AsResult requires at least one result", c.history)
                        )
                    else
                        val keys = results.map(r => (r.draftCondition, r.loadQty))
                        if keys.distinct.sizeIs != keys.size then
                            Left  (
                                DecodingFailure(
                                    "WireFireboxContribution.AsResult has duplicate (draftCondition, loadQty) keys",
                                    c.history
                                )
                            )
                        else Right(WireFireboxContribution.AsResult(results))
                }
            case other      =>
                Left(DecodingFailure(s"Unknown WireFireboxContribution kind: $other", c.history))
        }
    }

    // =========================================================================
    //  Discriminated oneOf codecs — ConstraintDescriptor (discriminator: "kind")
    // =========================================================================

    given Encoder[ConstraintDescriptor] = Encoder.instance {
        case ConstraintDescriptor.Min(min)             =>
            Json.obj(
                "kind" -> Json.fromString("min"),
                "min"  -> min.asJson
            )
        case ConstraintDescriptor.Max(max)             =>
            Json.obj(
                "kind" -> Json.fromString("max"),
                "max"  -> max.asJson
            )
        case ConstraintDescriptor.Range(min, max)      =>
            Json.obj(
                "kind" -> Json.fromString("range"),
                "min"  -> min.map(_.asJson).getOrElse(Json.Null),
                "max"  -> max.map(_.asJson).getOrElse(Json.Null)
            )
        case ConstraintDescriptor.ShapeOneOf(expected) =>
            Json.obj    (
                "kind"     -> Json.fromString("shapeOneOf"),
                "expected" -> expected.asJson
            )
        case ConstraintDescriptor.Opaque               =>
            Json.obj("kind" -> Json.fromString("opaque"))
    }

    given Decoder[ConstraintDescriptor] = Decoder.instance { c =>
        c.downField("kind").as[String].flatMap {
            case "min"        =>
                c.downField("min").as[Either[WireQty, Double]].map(ConstraintDescriptor.Min(_))
            case "max"        =>
                c.downField("max").as[Either[WireQty, Double]].map(ConstraintDescriptor.Max(_))
            case "range"      =>
                for
                    min <- c.downField("min").as[Option[Either[WireQty, Double]]]
                    max <- c.downField("max").as[Option[Either[WireQty, Double]]]
                yield ConstraintDescriptor.Range(min, max)
            case "shapeOneOf" =>
                c.downField("expected").as[List[WirePipeShape]].map(ConstraintDescriptor.ShapeOneOf(_))
            case "opaque"     =>
                Right(ConstraintDescriptor.Opaque)
            case other        =>
                Left(DecodingFailure(s"Unknown ConstraintDescriptor kind: $other", c.history))
        }
    }

    // =========================================================================
    //  Discriminated oneOf codecs — ConstraintOutcome (discriminator: "status")
    // =========================================================================

    given Encoder[ConstraintOutcome] = Encoder.instance {
        case ConstraintOutcome.Satisfied                         =>
            Json.obj("status" -> Json.fromString("satisfied"))
        case ConstraintOutcome.Violated(code, message, blocking) =>
            Json.obj  (
                "status"   -> Json.fromString("violated"),
                "code"     -> code.asJson,
                "message"  -> message.asJson,
                "blocking" -> blocking.asJson
            )
        case ConstraintOutcome.NotEvaluated(reason)              =>
            Json.obj(
                "status" -> Json.fromString("notEvaluated"),
                "reason" -> reason.asJson
            )
        case ConstraintOutcome.NotApplicable                     =>
            Json.obj("status" -> Json.fromString("notApplicable"))
    }

    given Decoder[ConstraintOutcome] = Decoder.instance { c =>
        c.downField("status").as[String].flatMap {
            case "satisfied"     =>
                Right(ConstraintOutcome.Satisfied)
            case "violated"      =>
                for
                    code     <- c.downField("code").as[String]
                    message  <- c.downField("message").as[String]
                    blocking <- c.downField("blocking").as[Boolean]
                yield ConstraintOutcome.Violated(code, message, blocking)
            case "notEvaluated"  =>
                c.downField("reason").as[NotEvaluatedReason].map(ConstraintOutcome.NotEvaluated(_))
            case "notApplicable" =>
                Right(ConstraintOutcome.NotApplicable)
            case other           =>
                Left(DecodingFailure(s"Unknown ConstraintOutcome status: $other", c.history))
        }
    }

end Codecs
