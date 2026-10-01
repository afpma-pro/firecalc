/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.OutsideAirLocationInHeater

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.ShowUsingLocale

import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.biblio.kov.firebox_emissions.Standing_Standard_Burning_Firebox
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * The remote-only carriers refuse a locally resolved Austrian kind (ADR-0010).
 *
 * `Ecolabeled` and `Door15aCatalog` have no in-process calculator in the public
 * artifact; a deployment that resolves them to `Local` used to produce a silent
 * degenerate result — no Austrian outputs, no violation. Each carrier's
 * `constraints` now reports one blocking refusal verdict, which `FoldedVerdicts`
 * folds into `FireboxErrorCustom`, so the application fails validation at the
 * seam its consumers already read.
 *
 * Taken at the application build and validation seam
 * (`RemoteConstraintReportingSuite` is the prior art for the same vocabulary on
 * the remote side): the refusal fires locally, the non-Austrian kinds are
 * untouched, and an Austrian application carrying remote data reports only the
 * wire-sourced verdicts. The Austrian stub family and the shipped EN/FR refusal
 * wording are shared with `CarrierRefusalReportFactorySuite` through
 * [[RefusalStubs_15544]].
 */
class CarrierRefusalSuite extends AnyFreeSpec with Matchers:

    // ── Suite-local fixtures (the Austrian family lives in RefusalStubs_15544) ──

    private def stubTraditionalDTO: Firebox.Traditional =
        Firebox.Traditional                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            firebox_depth                         = 0.44.meters,
            firebox_width                         = 0.42.meters,
            firebox_height                        = 0.78.meters,
            height_of_lowest_opening              = 0.05.meters,
            pressure_loss_coefficient_from_door   = 0.3.unitless,
            total_air_intake_surface_area_on_door = 150.0.cm2,
            glass_width                           = 0.15.meters,
            glass_height                          = 0.20.meters
        )

    /** Canonical PRSE geometry, declaring the 200 mm circular intake the scaffold draws. */
    private def stubAfpmaPrseDTO: Firebox.AFPMA_PRSE =
        Firebox.AFPMA_PRSE                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            outside_air_location_in_heater        = OutsideAirLocationInHeater.FromBottom,
            outside_air_conduit_shape             = PipeShape.Circle(20.0.cm),
            firebox_depth                         = 0.5.meters,
            firebox_width                         = 0.5.meters,
            firebox_height                        = 0.5.meters,
            height_of_first_row_of_air_injectors  = 0.1.meters,
            glass_width                           = 0.2.meters,
            glass_height                          = 0.2.meters,
            ash_pit_height                        = 0.1.meters,
            floor_thickness                       = 0.05.meters,
            combustion_air_manifold_height        = 0.1.meters,
            outside_air_inlet_lip                 = 0.05.meters,
            height_of_air_feed_to_columns         = 0.1.meters,
            number_of_air_columns_feeding_firebox = 4,
            number_of_air_columns_feeding_door    = 2
        )

    private def stubSingleTestedDTO: Firebox.SingleTested =
        Firebox.SingleTested                             (
            reference                              = "test",
            type_of_appliance                      = TypeOfAppliance.WoodLogs,
            test_standard                          = Firebox.TestStandard.EN_13229,
            firebox_depth                          = 0.44.meters,
            firebox_width                          = 0.42.meters,
            firebox_height                         = 0.6.meters,
            ash_pit_height                         = 0.05.meters,
            is_glass_surface_ratio_below_one_fifth = true,
            glass_area                             = 0.25.squareMeters,
            mean_firebox_temperature               = Some(700.degreesCelsius),
            t_burnout                              = Some(550.degreesCelsius),
            efficiency_nominal                     = 80.percent,
            efficiency_reduced                     = None,
            heat_output_reduced                    = HeatOutputReduced.NotDefined,
            minimum_fuel_mass                      = Some(10.kg),
            maximum_fuel_mass                      = 18.5.kg,
            air_fuel_ratio_nominal                 = 2.5.unitless,
            air_fuel_ratio_lowest                  = None,
            co2_dry_nominal                        = 8.percent,
            co2_dry_lowest                         = None,
            pellets_load_burn_duration             = Some(75.minutes),
            emissions_values                       = RefusalStubs_15544.stubEmis
        )

    /**
     * `CustomForLab` has no DTO subtype, so it cannot be built through the YAML
     * loader; its guard is taken at the engine interface the fold consumes.
     */
    private def customForLabModel: Firebox_15544.Traditional.CustomForLab =
        Firebox_15544.Traditional.CustomForLab               (
            reference                = LocalizedString(_ => "carrier-refusal"),
            type_of_appliance        = TypeOfAppliance.WoodLogs,
            emissions_values         = Standing_Standard_Burning_Firebox,
            pn_reduced               = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            dimensions               = Firebox_15544.Dimensions(
                base   = Firebox_15544.Dimensions.Base.Squared(width = 39.0.cm, depth = 55.0.cm),
                height = 58.3.cm
            ),
            glass_area               = 200.0.cm2,
            height_of_lowest_opening = 5.0.cm
        )

    /**
     * The application as a locally resolved deployment builds it. The build
     * itself stays valid — the refusal is a verdict of the constraint fold, not
     * an input error — which is what keeps the remote prefetch alive (ADR-0010).
     */
    private def localApp(firebox: Firebox, maxLoad: Double = 26.0): EN15544_Strict_Application =
        val appV =
            FireCalcYAML_Loader(
                RefusalStubs_15544.austrianEngineState(firebox, maxLoad)
            ).make_en15544_Strict_Application
        appV.isValid shouldBe true
        appV.toOption.get

    private val noPipeContext =
        FireboxConstraintContext(mB = 0.0.kg, flow_rate = None, airIntakePipeShape = None)

    private def remoteWrapped(
        firebox : Firebox,
        verdicts: List[ConstraintVerdict]
    ): EN15544_Strict_Application =
        EN15544_Strict_Application.withRemoteData(localApp(firebox), RefusalStubs_15544.remoteDataWith(verdicts))

    /** The refusal verdicts among `verdicts`; empty means the carrier did not refuse. */
    private def refusals(verdicts: List[ConstraintVerdict]): List[ConstraintVerdict] =
        verdicts.filter:
            case ConstraintVerdict(_, "no_calculator_configured", _, _) => true
            case _                                                      => false

    /**
     * The kind's registry entry folded verdicts — the same instance the
     * carrier givens expose, reached through the registry dispatch the
     * application build itself uses.
     */
    private def carrierConstraints(kind: FireboxKind, carrier: Firebox_15544)(using
        Locale
    ): List[
        ConstraintVerdict
    ] =
        val entry   = FireboxEngineRegistry_Strict.all.find(_.kind == kind).get
        val firebox = entry.narrow(carrier).get
        entry.instance.constraints(firebox, ctx = None, fctx = noPipeContext)

    // ── Locally resolved Austrian kinds: one blocking refusal each ──────
    //
    // The two kinds run the same four assertions over
    // (dto, carrier, EN name, FR name) — a table, the pattern
    // `CarrierRefusalReportFactorySuite` already uses for its seam.

    private val austrianRows = List(
        (
            "Ecolabeled",
            FireboxKind.Ecolabeled,
            RefusalStubs_15544.stubEcolabeledDTO,
            RemoteOnlyFireboxes_Strict.EcolabeledCarrier(RefusalStubs_15544.stubEcolabeledDTO),
            "Austrian Eco firebox",
            "Foyer Ecolabellisé"
        ),
        (
            "Door15aCatalog",
            FireboxKind.Door15aCatalog,
            RefusalStubs_15544.stubDoor15aDTO,
            RemoteOnlyFireboxes_Strict.Door15aCatalogCarrier(RefusalStubs_15544.stubDoor15aDTO),
            "15a door",
            "Foyer 15a"
        )
    )

    austrianRows.foreach: (name, kind, dto, carrier, enName, frName) =>
        s"Locally resolved $name" - {

            "the carrier reports one blocking refusal verdict naming the kind" in {
                given Locale = Locale(Languages.En)
                val verdicts = carrierConstraints(kind, carrier)

                verdicts should have size 1
                verdicts.head.term shouldBe ConstraintTerm.Custom
                verdicts.head.descriptor shouldBe ConstraintDescriptor.Opaque
                verdicts.head.outcome shouldBe ConstraintOutcome.Violated    (
                    code     = "no_calculator_configured",
                    message  = RefusalStubs_15544.refusalEn(enName),
                    blocking = true
                )
            }

            "the application fails the firebox-specific validation with the refusal" in {
                given Locale = Locale(Languages.En)
                val app      = localApp(dto)

                app.primary.validateFireboxSpecificConstraints.swap.toOption.get.toList
                    .map(ShowUsingLocale[MCalc_Error].show) shouldBe List(RefusalStubs_15544.refusalEn(enName))
                app.validateResultsExceptEmissionsValues(TestScaffold_15544.project.country).isValid shouldBe false
            }

            "the refusal is not reported as EN 15544 cited constraint physics" in {
                localApp(dto).primary.validateCitedConstraints.isValid shouldBe true
            }

            "the refusal is shipped in French as well" in {
                given Locale = Locale(Languages.Fr)
                val verdicts = carrierConstraints(kind, carrier)

                verdicts should have size 1
                verdicts.head.outcome shouldBe ConstraintOutcome.Violated    (
                    code     = "no_calculator_configured",
                    message  = RefusalStubs_15544.refusalFr(frName),
                    blocking = true
                )
            }
        }

    // ── The refusal must not fire where a calculator does exist ────────

    "Non-Austrian kinds report no refusal" - {

        // The three loader-built kinds are built exactly as the Austrian
        // applications above are, and the assertion is taken at
        // `validateFireboxSpecificConstraints` — the validator the refusal lands in
        // (a `ConstraintTerm.Custom` verdict is firebox-specific). The whole-project
        // checkpoint also runs the EN 15544 pipe physics (injector velocities,
        // pressure requirement), which these minimal stubs do not claim to satisfy:
        // full-checkpoint validity for the non-Austrian kinds stays pinned by
        // `SingleTestedValidationSuite` and
        // `TraditionalFireboxConstraintDispatchSuite`, which this change leaves
        // byte-unchanged.
        "traditional" in {
            val app = localApp(stubTraditionalDTO, maxLoad = 18.5)
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "afpma-prse" in {
            val app = localApp(stubAfpmaPrseDTO, maxLoad = 18.5)
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "single-tested" in {
            val app = localApp(stubSingleTestedDTO, maxLoad = 18.5)
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "custom-for-lab" in {
            // No DTO subtype, hence no loader path for this kind: the guard is
            // taken at the engine interface the constraint fold consumes.
            given Locale = Locale(Languages.En)
            val entry    = FireboxEngineRegistry_Strict.all.find(_.kind == FireboxKind.CustomForLab).get
            val firebox  = entry.narrow(customForLabModel)

            firebox.isDefined shouldBe true
            refusals(entry.instance.constraints(firebox.get, ctx = None, fctx = noPipeContext)) shouldBe empty
        }
    }

    "Austrian kinds carrying remote data" - {

        "the Ecolabeled application stays valid with satisfied wire verdicts" in {
            val app = remoteWrapped(
                RefusalStubs_15544.stubEcolabeledDTO,
                List(RefusalStubs_15544.satisfied(ConstraintTerm.NominalLoadInRange))
            )
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "the Ecolabeled application reports only the wire-sourced verdicts" in {
            given Locale = Locale(Languages.En)
            val app      = remoteWrapped(
                RefusalStubs_15544.stubEcolabeledDTO,
                List(RefusalStubs_15544.violated(ConstraintTerm.NominalLoadInRange, "nominal load out of range"))
            )

            app.primary.validateFireboxSpecificConstraints.swap.toOption.get.toList
                .map(ShowUsingLocale[MCalc_Error].show) shouldBe List("nominal load out of range")
        }

        "the Door15aCatalog application stays valid with satisfied wire verdicts" in {
            val app = remoteWrapped(
                RefusalStubs_15544.stubDoor15aDTO,
                List(RefusalStubs_15544.satisfied(ConstraintTerm.NominalLoadInRange))
            )
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "the Door15aCatalog application reports only the wire-sourced verdicts" in {
            given Locale = Locale(Languages.En)
            val app      = remoteWrapped(
                RefusalStubs_15544.stubDoor15aDTO,
                List(RefusalStubs_15544.violated(ConstraintTerm.NominalLoadInRange, "nominal load out of range"))
            )

            app.primary.validateFireboxSpecificConstraints.swap.toOption.get.toList
                .map(ShowUsingLocale[MCalc_Error].show) shouldBe List("nominal load out of range")
        }
    }

end CarrierRefusalSuite
