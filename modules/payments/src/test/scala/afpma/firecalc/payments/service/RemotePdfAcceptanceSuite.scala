/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.others.sunits.SUnits.given

import afpma.firecalc.dto.FireCalcYAMLMigrations

import afpma.firecalc.engine.models.OpPoint
import afpma.firecalc.engine.api.WireConversions
import afpma.firecalc.engine.models.FireboxPipeT
import afpma.firecalc.engine.models.PipeResult

import afpma.firecalc.payments.GenerateExampleProjectFixture
import afpma.firecalc.payments.Main
import afpma.firecalc.payments.exceptions.ReportFileAccessException
import afpma.firecalc.payments.service.impl.CalculationReportServiceImpl
import afpma.firecalc.payments.shared.api.FileDescriptionWithContent

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.validated.*

import scala.concurrent.ExecutionContext

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Locale
import io.taig.babel.Locales

import EcolabeledRemoteFixtures.*
import utest.*

/**
 * Acceptance tests for the Phase 2 IP gate (ticket 12, checklist item 4): the
 * payments backend's Austrian browser→PDF workflow.
 *
 * Proves behaviorally, through the shared [[FireboxCalcClient]] seam:
 *
 *   (a) an Austrian (Ecolabeled) project's PDF is produced by a FRESH
 *       server-side calculation request on every call — the captured wire
 *       request carries the full project DTO and there is no payments-side
 *       response cache;
 *   (b) an exhausted backend policy (transport / protocol / decode failure,
 *       or a raised client error) fails EXPLICITLY with a typed
 *       [[CalculationReportError.CalculationError]] — never a browser cache,
 *       a payments cache, or a locally computed Austrian PDF.
 *
 * The golden success response is a SYNTHETIC wire response built entirely
 * from public contract and engine types: hand-built `PipeResult` values
 * (`PipeResult.fromValues`, the same continuation-state shape
 * `BlackBoxAsResultSuite` uses) are encoded to wire form with
 * `WireConversions.toWireResult` and keyed by `OpPoint.all` — mirroring the
 * server's operating-point-keyed `AsResult` emitter without performing any
 * Austrian (or any other) calculation in-process. The public strict loader
 * builds the Ecolabeled project through its generic remote-only carrier, so
 * the service under test exercises its real parse→load→request→render path
 * with no proprietary symbol on the classpath.
 */
object RemotePdfAcceptanceSuite extends TestSuite {

    private given ExecutionContext = ExecutionContext.global
    private given Locale           = Locales.fr

    private def base64(s: String): String =
        java.util.Base64.getEncoder.encodeToString(s.getBytes("UTF-8"))

    // The shared Ecolabeled fixture (DTO project, encoded YAML, decoded
    // round-trip, degenerate variants, assert helper) lives in
    // [[EcolabeledRemoteFixtures]]; see also RemotePdfNoSizingInputSuite.

    /**
     * Records every calculation request and answers with a scripted effect.
     * The call count and the captured wire requests ARE the contract here.
     */
    final class FakeCalcClient(respond: FireboxCalcRequest => IO[Either[CalculationFailure, FireboxCalcResponse]])
        extends FireboxCalcClient[IO] {
        val requests = scala.collection.mutable.Buffer[FireboxCalcRequest]()
        var calls    = 0
        def getKinds = IO.pure(Right(DiscoveryResponse(ApiVersion.v1, Nil)))
        def calculate(req: FireboxCalcRequest) = IO { calls += 1; requests += req } *> respond(req)
        def close = IO.unit
    }

    /**
     * One synthetic operating-point result: a hand-built `PipeResult` whose
     * members are a fixed, valid continuation state (the contract `AsResult`
     * owes), encoded to wire form with the SAME emitter the server uses
     * (`WireConversions.toWireResult`). No calculation happens here — the
     * values are test data standing in for the private backend's solve.
     */
    private def syntheticWireResult(typ: afpma.firecalc.engine.models.PipeType): WirePipeResult =
        WireConversions.toWireResult(
            PipeResult.fromValues                  (
                pt                   = typ,
                lengthSum            = 1.meters,
                heightSum            = 1.meters,
                pd                   = None,
                ζ                    = None,
                pu                   = 0.pascals.validNel,
                pRs                  = 0.pascals,
                pRg                  = 0.pascals,
                ph                   = 2.pascals,
                gas_temp_start       = 250.degreesCelsius,
                gas_temp_middle      = 240.degreesCelsius,
                gas_temp_end         = 230.degreesCelsius,
                density_middle       = Some(0.8.kg_per_m3),
                density_mean         = Some(0.85.kg_per_m3),
                v_start              = Some(1.5.m_per_s),
                v_end                = Some(1.4.m_per_s),
                last_density_mean    = Some(0.9.kg_per_m3),
                last_density_middle  = Some(0.95.kg_per_m3),
                last_velocity_mean   = Some(1.6.m_per_s),
                last_velocity_middle = Some(1.7.m_per_s)
            )
        )

    /**
     * Golden success response: an operating-point-keyed `AsResult` grid for
     * the firebox gas path covering `OpPoint.all` (the full grid the standard
     * evaluates), and `Without` on the combustion-air path. The wire shape is
     * exactly what the private backend emits; only the numbers are synthetic.
     */
    private def goldenResponse(req: FireboxCalcRequest): FireboxCalcResponse = {
        val wireFireboxGrid = OpPoint.all.map { op =>
            WireOpPointResult(
                draftCondition = WireConversions.toWireDraftCondition(op.draftCondition),
                loadQty        = WireConversions.toWireLoadQty(op.loadQty),
                result         = syntheticWireResult(FireboxPipeT)
            )
        }

        FireboxCalcResponse               (
            apiVersion                = req.apiVersion,
            kind                      = req.kind,
            locale                    = req.locale,
            fireboxContribution       = WireValidated.Valid(WireFireboxContribution.AsResult(wireFireboxGrid)),
            combustionAirContribution = WireValidated.Valid(WireFireboxContribution.Without),
            constraints               = Nil,
            reportData                = FireboxReportData(
                fireboxDescription     = WireTable(None, None, Nil),
                emissionsAndEfficiency = WireTable(None, None, Nil),
                combustionAirPipeRows  = Nil,
                fireboxPipeRows        = Nil,
                ecolabeledOutputs      = None
            )
        )
    }

    private def goldenFake: FakeCalcClient =
        new FakeCalcClient(req => IO.pure(Right(goldenResponse(req))))

    private def transportFailureFake: FakeCalcClient =
        new FakeCalcClient(_ =>
            IO.pure(Left(CalculationFailure.TransportError(new java.net.ConnectException("refused"))))
        )

    private def serviceWith(fake: FakeCalcClient): CalculationReportServiceImpl =
        new CalculationReportServiceImpl(fake, Locales.fr)

    val tests = Tests {
        test("Austrian PDF generation causes a fresh server-side calculation request") {
            val fake    = goldenFake
            val service = serviceWith(fake)

            val result = service.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()

            result match {
                case Right(file) =>
                    assert(file.exists()    )
                    assert(file.length() > 0)
                case Left(err)   =>
                    throw new java.lang.AssertionError(s"expected a generated PDF, got $err")
            }

            // Exactly one fresh calculation request reached the shared client seam.
            assert(fake.calls == 1)

            // The captured wire request carries the full Austrian project.
            val req = fake.requests.head
            assert(req.apiVersion == ApiVersion.v1                      )
            assert(req.kind == WireFireboxKind.Ecolabeled               )
            assert(req.standard == WireStandardVariant.Strict           )
            assert(req.locale == decodedEcolabeled.locale.language.value)
            assert(req.operatingEnvelope.isDefined                      )
            assert(
                req.operatingEnvelope.get == WireOperatingEnvelope            (
                    altitude             = WireConversions.qtyDToWireQty[Meter](decodedEcolabeled.local_conditions.altitude),
                    temperatureOverrides = None
                )
            )
            assert(req.firebox == decodedEcolabeled.firebox             )
            // The sizing params must ride the wire request: absent params
            // silently degrade the server to NotEvaluated(MissingMB).
            // JVM WireQty rendering is Double.toString ("26.0", "78.0").
            assert(
                req.params == Standard15544Params  (
                    m_B   = Some(WireQty("26.0", "kilogram")),
                    n_min = Some(WireQty("78.0", "percent"))
                )
            )
        }

        test("A second PDF request issues a second fresh calculation (no payments-side response cache)") {
            val fake    = goldenFake
            val service = serviceWith(fake)

            val first  = service.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()
            val second = service.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()

            assert(first.isRight  )
            assert(second.isRight )
            // The call count IS the contract: no payments-side cache may absorb
            // the second request — each PDF request recalculates server-side.
            assert(fake.calls == 2)
        }

        test("Exhausted backend policy — transport failure fails explicitly") {
            val fake    = transportFailureFake
            val service = serviceWith(fake)

            val result = service.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()

            // Typed failure, no File value — the pipeline never reaches PDF
            // rendering, so no PDF is produced.
            assertCalculationErrorMentions(result, "Transport error")
            assert                        (fake.calls == 1          )
        }

        test("Exhausted backend policy — protocol failure fails explicitly") {
            val fake    = new FakeCalcClient(_ =>
                IO.pure(Left(CalculationFailure.ProtocolError(503, "service_unavailable", "backend down", None)))
            )
            val service = serviceWith(fake)

            val result = service.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()

            assertCalculationErrorMentions(result, "Protocol error: HTTP 503")
        }

        test("Pre-check 422 — the report reason names the failed checks from the error details") {
            // Ticket 03: the reason a report reader sees must name the server's
            // verdict items, on top of the existing protocol-error shape (US12,
            // distinguishable per US13).
            val items   = List(
                WireErrorItem("firebox.length", "total pipe length exceeds the maximum"),
                WireErrorItem("firebox.height", "firebox height below the minimum"     )
            )
            val fake    = new FakeCalcClient(_ =>
                IO.pure(
                    Left(CalculationFailure.ProtocolError(422, "calculation_failed", "geometry rejected", Some(items)))
                )
            )
            val service = serviceWith(fake)

            val result = service.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()

            val reason = calculationErrorReason(result)
            // The whole reason is the existing protocol-error shape extended with
            // the item messages — base text intact (US13), every item named (US12).
            assert(
                reason ==
                    "Remote calculation failed: Protocol error: HTTP 422 (calculation_failed) - geometry rejected: " +
                    items.map(_.message).mkString(", ")
            )
        }

        test("Non-pre-check protocol errors keep the bare reason shape") {
            // US16 pin: a details-less 422 and a details-carrying 400 both fail
            // the canonical predicate pairing, so neither may grow a suffix.
            val items     = List(WireErrorItem("malformed.field", "unknown field"))
            val bare422   = new FakeCalcClient(_ =>
                IO.pure(Left(CalculationFailure.ProtocolError(422, "calculation_failed", "geometry rejected", None)))
            )
            val detail400 = new FakeCalcClient(_ =>
                IO.pure(Left(CalculationFailure.ProtocolError(400, "malformed_request", "bad request", Some(items))))
            )

            val result422 = serviceWith(bare422).generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()
            val result400 = serviceWith(detail400).generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()

            assert(
                calculationErrorReason(result422) ==
                    "Remote calculation failed: Protocol error: HTTP 422 (calculation_failed) - geometry rejected"
            )
            assert(
                calculationErrorReason(result400) ==
                    "Remote calculation failed: Protocol error: HTTP 400 (malformed_request) - bad request"
            )
        }

        test("Exhausted backend policy — decode failure fails explicitly") {
            val fake    = new FakeCalcClient(_ =>
                IO.pure(Left(CalculationFailure.DecodeError(new RuntimeException("bad json"), "{")))
            )
            val service = serviceWith(fake)

            val result = service.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()

            assertCalculationErrorMentions(result, "Decode error")
        }

        test("No local fallback — a raised client error surfaces as failure, never a locally computed PDF") {
            // The client raises instead of returning a typed failure: the
            // service's callCalculation .attempt path must convert it to the
            // typed CalculationError. An exception escape or a locally
            // computed PDF would both fail this test.
            val fake    = new FakeCalcClient(_ => IO.raiseError(new java.net.ConnectException("refused")))
            val service = serviceWith(fake)

            val result = service.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()

            assertCalculationErrorMentions(result, "Remote calculation failed")
            assert                        (fake.calls == 1                    )
        }

        test("Non-Austrian kinds never touch the calculation client") {
            val fake            = goldenFake
            val service         = serviceWith(fake)
            val traditionalYaml =
                FireCalcYAMLMigrations.encodeToYamlTry(GenerateExampleProjectFixture.exampleFireCalcYaml).get

            val result = service.generateRemotePdf(traditionalYaml, asDraft = true).unsafeRunSync()

            assertCalculationErrorMentions(result, "not Austrian")
            // The remote seam is never invoked for a non-Austrian kind.
            assert                        (fake.calls == 0       )
        }

        test(
            "generatePdfReportBlocking routes Austrian YAML through the remote service and fails explicitly without one"
        ) {

            test("a configured golden service produces a PDF via exactly one remote calculation") {
                val fake     = goldenFake
                val service  = serviceWith(fake)
                val fileDesc = FileDescriptionWithContent("p.fcalc", "application/yaml", base64(ecolabeledYaml))

                val file = Main
                    .generatePdfReportBlocking[IO](fileDesc, Locales.fr, asDraft = true, Some(service))
                    .unsafeRunSync()

                assert(file.exists()    )
                assert(file.length() > 0)
                assert(fake.calls == 1  )
            }

            test("without a remote service the Austrian YAML fails explicitly") {
                val fileDesc  = FileDescriptionWithContent("p.fcalc", "application/yaml", base64(ecolabeledYaml))
                val attempted = Main
                    .generatePdfReportBlocking[IO](fileDesc, Locales.fr, asDraft = true, None)
                    .attempt
                    .unsafeRunSync()

                attempted match {
                    case Left(err: ReportFileAccessException) =>
                        assert(err.getMessage.contains("remote calculation service is not configured"))
                    case other                                =>
                        throw new java.lang.AssertionError(
                            s"expected ReportFileAccessException for unconfigured remote service, got $other"
                        )
                }
            }

            test("a typed remote failure propagates as ReportFileAccessException — never an in-process fallback") {
                // A failing backend (here a transport failure; a malformed
                // 200-shaped response fails through the same typed branch)
                // must surface through the route as a ReportFileAccessException
                // tagged "remote_calculation:" — the route has NO fallback to
                // the in-process factory for Austrian YAML.
                val fake      = transportFailureFake
                val service   = serviceWith(fake)
                val fileDesc  = FileDescriptionWithContent("p.fcalc", "application/yaml", base64(ecolabeledYaml))
                val attempted = Main
                    .generatePdfReportBlocking[IO](fileDesc, Locales.fr, asDraft = true, Some(service))
                    .attempt
                    .unsafeRunSync()

                attempted match {
                    case Left(err: ReportFileAccessException) =>
                        assert(err.getMessage.contains("remote_calculation:"))
                    case other                                =>
                        throw new java.lang.AssertionError(
                            s"expected ReportFileAccessException propagating the typed remote failure, got $other"
                        )
                }
            }
        }

        test("A browser-side cache can never satisfy the payments PDF request") {
            // SPEC_00 decisions 6/14: payments carries no cache parameter and
            // no fallback branch — the injected FireboxCalcClient is the
            // service's ONLY calculation data source. Prove it behaviorally:
            // a request that succeeds against one service instance leaves
            // nothing behind for a fresh instance whose client fails.
            val okService = serviceWith(goldenFake)
            val first     = okService.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()
            assert(first.isRight)

            val failingFake  = transportFailureFake
            val freshService = serviceWith(failingFake)
            val second       = freshService.generateRemotePdf(ecolabeledYaml, asDraft = true).unsafeRunSync()

            assertCalculationErrorMentions(second, "Transport error")
            // The fresh service really did hit the seam again — nothing was
            // retained from the earlier success.
            assert                        (failingFake.calls == 1   )
        }
    }
}
