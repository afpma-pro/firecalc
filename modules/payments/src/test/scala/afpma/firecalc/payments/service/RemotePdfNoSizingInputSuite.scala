/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.all.*

import afpma.firecalc.payments.service.impl.CalculationReportServiceImpl

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import scala.concurrent.ExecutionContext

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Locales
import utest.*
import EcolabeledRemoteFixtures.*

/**
 * US18 (re-scoped wire-only): an Austrian project stored with no sizing input
 * (`sizing_method: MaxLoad` and `maximum_load: null`) must fail the report
 * with a NAMED calculation error, before the shared [[FireboxCalcClient]]
 * seam — never as a MissingMB-verdict round-trip that later crashes PDF
 * generation forcing the lazy `m_B`.
 *
 * Shares its Ecolabeled fixture with `RemotePdfAcceptanceSuite` via
 * [[EcolabeledRemoteFixtures]]; here the stub REFUSES every call, so the
 * guard is pinned by an exact zero-call assertion.
 */
object RemotePdfNoSizingInputSuite extends TestSuite {

    private given ExecutionContext = ExecutionContext.global

    /** The exact named-failure string the guard owes the user. */
    private val NoSizingInputMessage =
        "Project has no sizing input: set a maximum load (m_B) or a nominal heat output (P_n) to generate a report"

    /**
     * Records every calculation call and refuses to answer it: the guard must
     * short-circuit the pipeline before the seam, so ANY call is itself a
     * contract failure (and the refusal would surface as a wrong error type).
     */
    final class RefusingCalcClient extends FireboxCalcClient[IO] {
        var calls                              = 0
        def getKinds                           = IO.pure(Right(DiscoveryResponse(ApiVersion.v1, Nil)))
        def calculate(req: FireboxCalcRequest) =
            IO(calls += 1) *> IO.pure(
                Left(CalculationFailure.TransportError(new java.net.ConnectException("guard must prevent this call")))
            )
        def close = IO.unit
    }

    val tests = Tests {

        test("A no-sizing-input project fails with the named error before any client call") {
            // Sanity: the stored YAML really selects MaxLoad and nulls the
            // load — a broken fixture fails here, not at suite init.
            val decoded = FireCalcYAMLMigrations.decodeAndMigrateTry(degenerateYaml).get
            assert(decoded.stove_params.sizing_method == StoveParams.SizingMethod.MaxLoad)
            assert(decoded.stove_params.maximum_load.isEmpty                             )

            val fake    = new RefusingCalcClient
            val service = new CalculationReportServiceImpl(fake, Locales.fr)

            val result = service.generateRemotePdf(degenerateYaml, asDraft = true).unsafeRunSync()

            assertCalculationErrorMentions(result, NoSizingInputMessage)
            // The guard short-circuits the pipeline: the remote seam is never invoked.
            assert                        (fake.calls == 0             )
        }

        test("The NominalHeatOutput twin fails with the named error before any client call") {
            // Sanity: the stored YAML really selects NominalHeatOutput and
            // keeps the nominal heat output null — a broken fixture fails
            // here, not at suite init.
            val decoded = FireCalcYAMLMigrations.decodeAndMigrateTry(nominalHeatDegenerateYaml).get
            assert(decoded.stove_params.sizing_method == StoveParams.SizingMethod.NominalHeatOutput)
            assert(decoded.stove_params.nominal_heat_output.isEmpty                                )

            val fake    = new RefusingCalcClient
            val service = new CalculationReportServiceImpl(fake, Locales.fr)

            val result = service.generateRemotePdf(nominalHeatDegenerateYaml, asDraft = true).unsafeRunSync()

            assertCalculationErrorMentions(result, NoSizingInputMessage)
            // The guard short-circuits the pipeline: the remote seam is never invoked.
            assert                        (fake.calls == 0             )
        }
    }
}
