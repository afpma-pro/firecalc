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

import afpma.firecalc.i18n.*

import afpma.firecalc.engine.alg.en13384.ComputeAt
import afpma.firecalc.engine.alg.en15544.*
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.impl.en15544.strict.FireboxEngine_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.λ
import afpma.firecalc.engine.ops.generic.UpstreamState
import afpma.firecalc.engine.standard.*

import coulomb.*
import coulomb.syntax.*

import afpma.firecalc.domain.PolluantName
import afpma.firecalc.domain.TestReport
import afpma.firecalc.domain.TypeOfAppliance
import afpma.firecalc.firebox_calc_api.FireboxReportData
import afpma.firecalc.firebox_calc_api.WireTable
import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.validated.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class BlackBoxAsResultSuite extends AnyFreeSpec with Matchers:

    given Locale = Locales.en

    // ── Synthetic firebox for registry-dispatch test ──────────────────────

    private object BlackBoxFirebox extends Firebox_15544:
        override def firebox_type: Locale ?=> String = "test-blackbox"

        override def reference: LocalizedString =
            LocalizedString(_ => "BB-001")

        override def type_of_appliance: TypeOfAppliance =
            TypeOfAppliance.WoodLogs

        override def dimensions: Dimensions =
            Dimensions  (
                base   = Dimensions.Base.Squared(50.cm, 50.cm),
                height = 60.cm
            )

        override def glass_area: GlassArea =
            500.cm2

        override def height_of_lowest_opening: Length =
            10.cm

        override def emissions_values: EmissionsAndEfficiencyValues =
            EmissionsAndEfficiencyValues                      (
                firebox_name                       = "BB-001",
                accredited_or_notified_body        = "Test Body",
                test_reports                       = List(TestReport("test", "2026-01-01")),
                min_efficiency_firebox_nominal     = None,
                min_efficiency_full_stove_nominal  = None.validNel,
                min_efficiency_firebox_reduced     = None,
                min_efficiency_full_stove_reduced  = None.validNel,
                min_seasonal_efficiency_full_stove = None.validNel,
                emissions_values                   = EmissionValues(
                    co   = TestEmissionValue.defineAt13pO2(PolluantName.CO, None, ""),
                    dust = TestEmissionValue.defineAt13pO2(PolluantName.Dust, None, ""),
                    ogc  = TestEmissionValue.defineAt13pO2(PolluantName.OGC, None, ""),
                    nox  = TestEmissionValue.defineAt13pO2(PolluantName.NOx, None, "")
                )
            )

        override def min_load: MinLoad =
            MinLoad.NotDefined

        override def nominal_load: Option[Mass] = None
        override def max_load    : Option[Mass] = None

        override def pn_reduced: HeatOutputReduced =
            HeatOutputReduced.NotDefined

        override def co2_dry_nominal: σ_CO2         = 7.05.percent
        override def co2_dry_lowest : Option[σ_CO2] = None

    // ── Grid helpers ──────────────────────────────────────────────────────

    /**
     * One grid entry: a hand-built firebox-path `PipeResult` whose
     * `gas_temp_end` is the per-point discriminator. Every other member is a
     * fixed, valid continuation state (the contract `AsResult` owes).
     */
    private def stubGridEntry(op: OpPoint, gasTempEnd: Double): (OpPoint, PipeResult) =
        op -> PipeResult.fromValues(
            pt                   = FireboxPipeT,
            lengthSum            = 1.meters,
            heightSum            = 1.meters,
            pd                   = None,
            ζ                    = None,
            pu                   = 0.pascals.validNel,
            pRs                  = 0.pascals,
            pRg                  = 0.pascals,
            ph                   = 0.pascals,
            gas_temp_start       = 400.degreesCelsius,
            gas_temp_middle      = 375.degreesCelsius,
            gas_temp_end         = gasTempEnd.degreesCelsius,
            density_middle       = Some(1.0.kg_per_m3),
            density_mean         = Some(1.1.kg_per_m3),
            v_start              = Some(3.m_per_s),
            v_end                = Some(2.m_per_s),
            last_density_mean    = Some(1.2.kg_per_m3),
            last_density_middle  = Some(1.3.kg_per_m3),
            last_velocity_mean   = Some(4.m_per_s),
            last_velocity_middle = Some(5.m_per_s)
        )

    /** The full 4-point grid, each entry carrying `gasTempEnd`. */
    private def fullGrid(gasTempEnd: Double): NonEmptyList[(OpPoint, PipeResult)] =
        NonEmptyList.fromListUnsafe(OpPoint.all.map(op => stubGridEntry(op, gasTempEnd)))

    // ── Whole-stove app injection (mirrors LocalRemoteEquivalenceSuite) ───

    private def stubStoveParams: StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = 26.0.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    private def buildEngineState(firebox: afpma.firecalc.dto.v5.Firebox_V4): FireCalcYAML_V7 =
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

    /** A Traditional DTO (no reduced load → the standard evaluates 2 points). */
    private val traditionalFixture: afpma.firecalc.dto.v5.Firebox_V4.Traditional =
        afpma.firecalc.dto.v5.Firebox_V4.Traditional                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            firebox_depth                         = 50.cm,
            firebox_width                         = 50.cm,
            firebox_height                        = 60.cm,
            height_of_lowest_opening              = 10.cm,
            pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
            total_air_intake_surface_area_on_door = 0.025.withUnit[(Meter ^ 2)],
            glass_width                           = 30.cm,
            glass_height                          = 20.cm
        )

    /**
     * Build the whole-stove strict application whose firebox engine is a
     * remote-backed stub dictating `FireboxContribution.AsResult(grid)` on the
     * firebox gas path and `Without` on the combustion-air path.
     */
    private def appWithGrid(grid: NonEmptyList[(OpPoint, PipeResult)]): EN15544_Strict_Application =
        val remoteData = RemoteFireboxData[
            FireboxPipe_Module_15544.FullDescr,
            CombustionAirPipe_Module_15544.FullDescr
        ]      (
            fireboxContribution       = Validated.valid(FireboxContribution.AsResult(grid)),
            combustionAirContribution = Validated.valid(FireboxContribution.Without),
            constraints               = Nil,
            reportData                = FireboxReportData(
                fireboxDescription     = WireTable(header = None, colHeaders = None, rows = Nil),
                emissionsAndEfficiency = WireTable(header = None, colHeaders = None, rows = Nil),
                combustionAirPipeRows  = Nil,
                fireboxPipeRows        = Nil,
                ecolabeledOutputs      = None
            )
        )
        val yaml       = buildEngineState(traditionalFixture)
        val loader     = FireCalcYAML_Loader(yaml)
        val appV       = loader.make_en15544_Strict_Application(remoteData = Some(remoteData))
        appV.isValid shouldBe true
        appV.toOption.get

    // ── Test 1: AsResult round-trips continuation state ───────────────────

    "FireboxContribution.AsResult" - {

        "round-trips continuation state through UpstreamState.fromPipeResult" in {
            // Build a PipeResult with distinctive, non-default continuation values.
            // Mean and middle carry DIFFERENT values so we can prove they are not
            // transposed by the seam.
            val handBuilt = PipeResult.fromValues(
                pt                   = FireboxPipeT,
                lengthSum            = 1.meters,
                heightSum            = 1.meters,
                pd                   = None,
                ζ                    = None,
                pu                   = 0.pascals.validNel,
                pRs                  = 0.pascals,
                pRg                  = 0.pascals,
                ph                   = 0.pascals,
                gas_temp_start       = 400.degreesCelsius,
                gas_temp_middle      = 375.degreesCelsius,
                gas_temp_end         = 347.degreesCelsius,
                density_middle       = Some(1.0.kg_per_m3),
                density_mean         = Some(1.1.kg_per_m3),
                v_start              = Some(3.m_per_s),
                v_end                = Some(2.m_per_s),
                last_density_mean    = Some(1.2.kg_per_m3),
                last_density_middle  = Some(1.3.kg_per_m3),
                last_velocity_mean   = Some(4.m_per_s),
                last_velocity_middle = Some(5.m_per_s)
            )

            // Mean strategy picks _mean fields.
            val stateMean =
                UpstreamState.fromPipeResult(handBuilt, ComputeAt.Mean)
            stateMean.temp_start shouldBe 347.degreesCelsius
            stateMean.last_pipe_density shouldBe Some(1.2.kg_per_m3)
            stateMean.last_pipe_velocity shouldBe Some(4.m_per_s)

            // Middle strategy picks _middle fields.
            val stateMiddle =
                UpstreamState.fromPipeResult(handBuilt, ComputeAt.Middle)
            stateMiddle.temp_start shouldBe 347.degreesCelsius
            stateMiddle.last_pipe_density shouldBe Some(1.3.kg_per_m3)
            stateMiddle.last_pipe_velocity shouldBe Some(5.m_per_s)
        }

        // ── Test 2: useless PipeResult does NOT satisfy the contract ───────

        "PipeResult.useless leaves continuation fields None so it breaks the contract" in {
            // PipeResult.useless(pt, pu, gas_temp) — the 3-arg overload — leaves
            // all four continuation fields as None.  This is precisely why the
            // AsResult scaladoc directs implementors to PipeResult.fromValues
            // and NOT useless: a downstream flue pipe seeded from this result
            // would throw MecaFlu_Error.MissingUpstreamSeedValues.
            val useless =
                PipeResult.useless(FireboxPipeT, 0.pascals, 347.degreesCelsius)

            useless.last_density_mean shouldBe None
            useless.last_density_middle shouldBe None
            useless.last_velocity_mean shouldBe None
            useless.last_velocity_middle shouldBe None

            // UpstreamState derived from a useless result has empty density/velocity.
            val state =
                UpstreamState.fromPipeResult(useless, ComputeAt.Mean)
            state.last_pipe_density shouldBe None
            state.last_pipe_velocity shouldBe None
        }

        // ── Test 3: black-box engine dispatches through registry ───────────

        "black-box engine dispatches through a one-entry registry and yields its AsResult grid" in {
            // The hand-built grid the engine will return: every operating point
            // carries the same continuation values.
            val grid = fullGrid(347.0)

            // Minimal engine: kind = BlackBox, fireboxContribution = AsResult(grid),
            // combustionAirContribution = Without. The per-term constraint
            // algebra is a no-op, supplied through `constraintsAlg` — the
            // bounded `FoldsConstraints` member `FireboxEngineAny.from` demands.
            given blackBoxEngine: (
                FireboxEngine_15544_Strict[BlackBoxFirebox.type] & FoldsConstraints[BlackBoxFirebox.type]
            ) =
                new FireboxEngine_15544_Strict[BlackBoxFirebox.type] with FoldsConstraints[BlackBoxFirebox.type]:
                    override def kind: FireboxKind =
                        FireboxKind.BlackBox("test-blackbox")

                    override def fireboxContribution(
                        firebox: BlackBoxFirebox.type
                    ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[FireboxPipe_FullDescr]] =
                        FireboxContribution.AsResult(grid).validNel

                    override def combustionAirContribution(
                        firebox: BlackBoxFirebox.type
                    ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
                        FireboxContribution.Without.validNel

                    override def constraintsAlg: FireboxConstraints[BlackBoxFirebox.type] =
                        new FireboxConstraints[BlackBoxFirebox.type]:
                            override def kind: FireboxKind =
                                FireboxKind.BlackBox("test-blackbox")

                            override def m_B_constraints(
                                firebox: BlackBoxFirebox.type,
                                ctx    : ConstraintContext
                            ): Seq[Option[TermConstraint[m_B]]] = Seq.empty

                            override def m_B_min_constraints(
                                firebox: BlackBoxFirebox.type,
                                ctx    : ConstraintContext
                            ): Seq[Option[TermConstraint[m_B_min]]] = Seq.empty

                            override def glassArea_constraints(
                                firebox: BlackBoxFirebox.type,
                                ctx    : ConstraintContext
                            ): Seq[Option[TermConstraint[GlassArea]]] = Seq.empty

                            override def fireboxDimensions_Base_constraints(
                                firebox: BlackBoxFirebox.type,
                                ctx    : ConstraintContext
                            ): Seq[Option[TermConstraint[Dimensions.Base]]] = Seq.empty

                            override def h_br_constraints(
                                firebox: BlackBoxFirebox.type,
                                ctx    : ConstraintContext
                            ): Seq[Option[TermConstraint[H_BR]]] = Seq.empty

                            override def lambda_constraints(
                                firebox: BlackBoxFirebox.type,
                                ctx    : ConstraintContext
                            ): Seq[Option[TermConstraint[λ]]] = Seq.empty

                            override def eta_constraints(
                                firebox: BlackBoxFirebox.type,
                                ctx    : ConstraintContext
                            ): Seq[Option[TermConstraint[η]]] = Seq.empty

                            override def height_of_lowest_opening_constraints(
                                firebox: BlackBoxFirebox.type,
                                ctx    : ConstraintContext
                            ): Seq[Option[TermConstraint[height_of_lowest_opening]]] = Seq.empty

                            override def firebox_glass_surface_ratio_constraint(
                                firebox: BlackBoxFirebox.type
                            ): Option[TermConstraint[Unit]] = None

                            override def firebox_custom_constraints(
                                firebox: BlackBoxFirebox.type,
                                ctx    : FireboxConstraintContext
                            ): List[FireboxError] = Nil

                            override def internal_firebox_custom_constraints(
                                firebox: BlackBoxFirebox.type
                            ): List[FireboxError] = Nil

            // Build a one-entry test registry via FireboxEngineAny.from.
            // Deliberately do NOT call checkTotality() — BlackBox is excluded
            // from FireboxKind.closed so the totality check would fail.
            // `FireboxEngineAny.from[BlackBoxFirebox.type]` summons a
            // compiler-synthesised `TypeTest[Firebox_15544, BlackBoxFirebox.type]`
            // (singleton `eq` test) — no hand-written evidence needed.

            val registry =
                new FireboxEngineRegistry[
                    FireboxEngine_15544_Strict.FD,
                    FireboxEngine_15544_Strict.CD
                ]:
                    val name = "test-registry"
                    val all  = List(
                        FireboxEngineAny.from[BlackBoxFirebox.type]
                    )

            // Dispatch: registry resolves the engine for our synthetic firebox.
            val result = registry.engineFor(BlackBoxFirebox)
            result.isRight shouldBe true

            val engineFor = result.toOption.get
            engineFor.engine.kind shouldBe FireboxKind.BlackBox("test-blackbox")

            // The engine's fireboxContribution must be the AsResult grid we
            // supplied: one entry per operating point, each carrying the same
            // continuation values.
            val contribution =
                engineFor.engine.fireboxContribution(engineFor.firebox)
            contribution.isValid shouldBe true

            contribution.toOption.get match
                case FireboxContribution.AsResult(g) =>
                    val byPoint = g.toList.toMap
                    byPoint.keySet shouldBe OpPoint.all.toSet
                    OpPoint.all.foreach: op =>
                        val pr = byPoint(op)
                        pr.gas_temp_end shouldBe 347.degreesCelsius
                        pr.last_density_mean shouldBe Some(1.2.kg_per_m3)
                        pr.last_density_middle shouldBe Some(1.3.kg_per_m3)
                        pr.last_velocity_mean shouldBe Some(4.m_per_s)
                        pr.last_velocity_middle shouldBe Some(5.m_per_s)
                case other                           =>
                    fail(s"expected AsResult but got $other")
        }

        // ── Test 4: the discriminating test — per-point consumption ────────

        "the whole-stove solve picks the grid entry matching each operating point" in {
            // Perturb one member per point: gas_temp_end differs across the
            // grid in canonical order. This is the test the old single-result
            // shape could never express — a stale reuse would surface the SAME
            // temperature at every operating point.
            val perturbed =
                NonEmptyList.fromListUnsafe(
                    OpPoint.all.zipWithIndex.map((op, idx) => stubGridEntry(op, 347.0 + idx))
                )

            val app = appWithGrid(perturbed)

            // The CasType Traditional firebox declares a reduced load, so the
            // standard evaluates all four points (both draft conditions × both
            // loads). The discriminating property: each point gets its OWN
            // entry, not a stale reuse of one result.
            val evaluated = OpPoint15544.axesFor(app.firebox)
            evaluated.map(_.draftCondition).toSet shouldBe Set(
                DraftCondition.DraftMinOrPositivePressureMax,
                DraftCondition.DraftMaxOrPositivePressureMin
            )

            // Index the perturbed grid by point so we can assert per-point.
            val perturbedByPoint = perturbed.toList.toMap

            val observed = evaluated.map: op =>
                val pipeResult = app.atOperatingPointFor(op).firebox_PipeResult
                pipeResult.isValid shouldBe true
                pipeResult.toOption.get.gas_temp_end shouldBe perturbedByPoint(op).gas_temp_end
                pipeResult.toOption.get.gas_temp_end

            // Non-vacuous: the grid entries are genuinely distinct and the
            // solve surfaced distinct values — a stale reuse would collapse
            // them to one.
            observed.distinct.size shouldBe evaluated.size
        }

        // ── Test 5: a covered-point miss fails loudly, naming the point ────

        "a grid missing a covered operating point fails loudly with the point named" in {
            // Deliberately incomplete: only draft-min/nominal is present —
            // built WITHOUT the sanctioned `OpPoint15544.axesFor` derivation. The
            // solve at draft-max/nominal must fail with an UnexpectedDevError
            // NAMING the operating point; it must never fall back to the
            // solver or to another point's entry.
            val incomplete =
                NonEmptyList.one(
                    stubGridEntry(OpPoint(DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Nominal), 347.0)
                )

            val app = appWithGrid(incomplete)

            // The covered point present in the grid still resolves to its entry.
            val covered = app.atDraftMin_LoadNominal.firebox_PipeResult
            covered.isValid shouldBe true
            covered.toOption.get.gas_temp_end shouldBe 347.degreesCelsius

            // The uncovered point fails loudly.
            val missed    = app.atDraftMax_LoadNominal.firebox_PipeResult
            missed.isInvalid shouldBe true
            val devErrors = missed.swap.toOption.get.toList.collect { case UnexpectedDevError(msg) => msg }
            devErrors should not be empty
            devErrors.exists(_.contains("DraftMaxOrPositivePressureMin")) shouldBe true
        }
    }
