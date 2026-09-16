/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import afpma.firecalc.i18n.ShowUsingLocale

import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.RemoteFireboxData
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error

import cats.data.Validated

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Regression: a remotely computed firebox must report its constraints honestly.
 *
 * `RemoteBackedFireboxEngine` carries no per-term [[FireboxConstraints]]
 * algebra — it is not transmitted over the wire. Two earlier designs were both
 * wrong:
 *
 *   - returning `Seq.empty` / `Nil` / `None` from each per-term method, which
 *     made `citedConstraints` look like a firebox with no constraints and made
 *     `validateFireboxSpecificConstraints` return `().validNel` — a silent
 *     "satisfied" for checks that were never evaluated;
 *   - raising `sys.error` from each per-term method, which made
 *     `citedConstraints`, `validateCitedConstraints` and
 *     `validateFireboxSpecificConstraints` throw an uncaught `RuntimeException`
 *     — crashing the firebox panel (`FireboxPanel.all_cons_signal`) and the
 *     Typst report (`TypstReportFactory_15544.en15544_citedConstraints`), since
 *     nothing on those paths catches it.
 *
 * The fix types the absence: the registry entry is built with
 * `FireboxEngineAny.foldedOnly`, so `FireboxEngineFor.appliedConstraints` is
 * `None`, `CitedConstraints.perTermDetailAvailable` is `false`, and both
 * validators read the engine's folded verdicts instead.
 */
class RemoteConstraintReportingSuite extends AnyFreeSpec with Matchers:

    private def stubEcolabeledDTO: Firebox.Ecolabeled =
        Firebox.Ecolabeled                    (
            heat_output_reduced                     = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            version                                 = Left("Version 1"),
            air_intake_shape                        = None,
            firebox_depth                           = 0.54.meters,
            firebox_width                           = 0.54.meters,
            firebox_height                          = 0.813.meters,
            height_of_first_row_of_air_injectors    = 0.10.meters,
            door_opening_width                      = 0.52.meters,
            glass_width                             = 0.50.meters,
            glass_height                            = 0.40.meters,
            ash_pit_height                          = 0.08.meters,
            air_manifold_height                     = 0.11.meters,
            firebox_floor_thickness                 = 0.08.meters,
            firebox_inner_wall_thickness            = 0.06.meters,
            firebox_outer_wall_thickness            = 0.06.meters,
            air_column_thickness                    = 0.035.meters,
            width_between_two_air_columns_sides     = 0.045.meters,
            width_between_two_air_columns_rear      = 0.045.meters,
            reinforcement_bars_offset_in_corners_R1 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R2 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R3 = 0.045.meters,
            injector_height                         = 0.008.meters
        )

    private def stubStoveParams: StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = 26.0.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    private def buildEngineState(firebox: Firebox): FireCalcYAML_V7 =
        FireCalcYAML_V7                        (
            locale                         = Locale(Languages.Fr),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = ProjectDescr(
                reference = TestScaffold_15544.project.reference,
                date      = TestScaffold_15544.project.date,
                country   = TestScaffold_15544.project.country
            ),
            local_conditions               = TestScaffold_15544.localConditions,
            stove_params                   = stubStoveParams,
            air_intake_pipes               = FramedAirIntakePipes.fromLegacy(TestScaffold_15544.conduit_air_descr),
            firebox                        = firebox,
            post_firebox_pipes             = FramedPostFireboxPipes(
                PipeInitialDirection.default,
                PostFireboxStartPosition.Auto,
                slots = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot     (TestScaffold_15544.accumulateur_descr        ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(TestScaffold_15544.conduit_raccordement_descr),
                    PostFireboxPipeDescrSlot.ChimneySlot  (TestScaffold_15544.conduit_fumees_descr      )
                )
            )
        )

    private def stubReportData: FireboxReportData =
        FireboxReportData    (
            fireboxDescription     = WireTable(header = None, colHeaders = None, rows = Nil),
            emissionsAndEfficiency = WireTable(header = None, colHeaders = None, rows = Nil),
            combustionAirPipeRows  = Nil,
            fireboxPipeRows        = Nil,
            ecolabeledOutputs      = None
        )

    private def remoteDataWith(verdicts: List[ConstraintVerdict]): RemoteFireboxData[
        FireboxPipe_Module_15544.FullDescr,
        CombustionAirPipe_Module_15544.FullDescr
    ] =
        RemoteFireboxData      (
            fireboxContribution       = Validated.valid(FireboxContribution.Without),
            combustionAirContribution = Validated.valid(FireboxContribution.Without),
            constraints               = verdicts,
            reportData                = stubReportData
        )

    /** An Ecolabeled application whose Ecolabeled entry is the remote engine. */
    private def remoteApp(verdicts: List[ConstraintVerdict]): EN15544_Strict_Application =
        val loader = FireCalcYAML_Loader(buildEngineState(stubEcolabeledDTO))
        val appV   = loader.make_en15544_Strict_Application
        appV.isValid shouldBe true
        EN15544_Strict_Application.withRemoteData(appV.toOption.get, remoteDataWith(verdicts))

    private def violated(term: ConstraintTerm, message: String, blocking: Boolean): ConstraintVerdict =
        ConstraintVerdict(
            term,
            term.toString,
            ConstraintDescriptor.Opaque,
            ConstraintOutcome.Violated("TestCode", message, blocking)
        )

    private def satisfied(term: ConstraintTerm): ConstraintVerdict =
        ConstraintVerdict(term, term.toString, ConstraintDescriptor.Opaque, ConstraintOutcome.Satisfied)

    "Remotely computed firebox: per-term constraint reporting" - {

        // The regression itself: every one of these three reads used to raise
        // `sys.error` from RemoteBackedFireboxEngine's per-term stub.
        "citedConstraints does not throw" in {
            val app = remoteApp(Nil)
            noException should be thrownBy app.citedConstraints
        }

        "validateCitedConstraints does not throw" in {
            val app = remoteApp(Nil)
            noException should be thrownBy app.primary.validateCitedConstraints
        }

        "validateFireboxSpecificConstraints does not throw" in {
            val app = remoteApp(Nil)
            noException should be thrownBy app.primary.validateFireboxSpecificConstraints
        }

        // Honest reporting: absence is flagged, not disguised as emptiness.
        "citedConstraints flags per-term detail as unavailable" in {
            val app = remoteApp(Nil)
            app.citedConstraints.perTermDetailAvailable shouldBe false
        }

        // The counterpart — "a locally computed firebox still reports per-term
        // detail as available" — lives in the private repository's
        // `LocalAustrianConstraintReportingSuite`: the public module links no
        // local Austrian per-term algebra (tickets 11, 16), so a locally built
        // Austrian kind reports per-term detail as unavailable by design.

        // Nothing is fabricated as satisfied: a violated remote verdict still fails.
        "a blocking remote violation of a cited term fails validateCitedConstraints" in {
            val app    = remoteApp(List(violated(ConstraintTerm.MB, "m_B out of range", blocking = true)))
            val result = app.primary.validateCitedConstraints
            result.isValid shouldBe false
            result.swap.toOption.get.toList.size shouldBe 1
        }

        "a blocking remote violation of a firebox-specific term fails that validation" in {
            val app = remoteApp(
                List(violated(ConstraintTerm.NominalLoadInRange, "nominal load out of range", blocking = true))
            )
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe false
        }

        // Each violation is routed to exactly one validator, matching the local split.
        "a cited-term violation does not leak into the firebox-specific validation" in {
            val app = remoteApp(List(violated(ConstraintTerm.MB, "m_B out of range", blocking = true)))
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "a firebox-specific violation does not leak into the cited validation" in {
            val app = remoteApp(
                List(violated(ConstraintTerm.NominalLoadInRange, "nominal load out of range", blocking = true))
            )
            app.primary.validateCitedConstraints.isValid shouldBe true
        }

        // Satisfied and non-blocking verdicts are not turned into failures.
        "satisfied remote verdicts leave both validations valid" in {
            val app = remoteApp(List(satisfied(ConstraintTerm.MB), satisfied(ConstraintTerm.NominalLoadInRange)))
            app.primary.validateCitedConstraints.isValid shouldBe true
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "a non-blocking remote violation is a warning, not a validation failure" in {
            val app = remoteApp(List(violated(ConstraintTerm.MB, "borderline", blocking = false)))
            app.primary.validateCitedConstraints.isValid shouldBe true
        }

        // The remote verdicts stay reachable — they are what the UI/report show.
        "the engine's folded verdicts remain available through `constraints`" in {
            given Locale = Locale(Languages.En)
            val verdicts = List(satisfied(ConstraintTerm.MB), satisfied(ConstraintTerm.GlassArea))
            val app      = remoteApp(verdicts)
            val fc       = app.activeRegistry.engineFor(app.inputs.design.firebox).toOption.get
            fc.appliedConstraints shouldBe None
            fc.engine.constraints (
                fc.firebox,
                ctx  = None,
                fctx = afpma.firecalc.engine.alg.en15544.FireboxConstraintContext(
                    mB                 = 0.0.kg,
                    flow_rate          = None,
                    airIntakePipeShape = None
                )
            ) shouldBe verdicts
        }

        // The whole-project validation gate (reports + UI) must not throw either.
        "validateResultsExceptEmissionsValues does not throw" in {
            val app = remoteApp(Nil)
            noException should be thrownBy
                app.validateResultsExceptEmissionsValues(TestScaffold_15544.project.country)
        }

        // A cited violation carries the remote message through to the reader, so
        // the panel/report show what the service actually said.
        "a remote cited violation carries the service's message" in {
            given Locale = Locale(Languages.En)
            val app      = remoteApp(List(violated(ConstraintTerm.MB, "m_B out of range", blocking = true)))
            val errs     = app.primary.validateCitedConstraints.swap.toOption.get.toList
            errs.map(ShowUsingLocale[MCalc_Error].show) shouldBe List("m_B out of range")
        }
    }
