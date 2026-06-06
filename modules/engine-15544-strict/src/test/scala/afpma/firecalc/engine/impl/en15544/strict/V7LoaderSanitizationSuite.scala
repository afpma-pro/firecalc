/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot

import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.models.geometry.Vec3

import io.taig.babel.Languages
import io.taig.babel.Locale

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Verifies that `FireCalcYAML_Loader` defensively sanitizes mixed V7 input
 * (wrapper fields + stale legacy descriptor elements in slots).
 */
class V7LoaderSanitizationSuite extends AnyFreeSpec with Matchers:

    private def mkSingleTested: Firebox.SingleTested =
        Firebox.SingleTested                         (
            test_standard                          = Firebox.TestStandard.EN_15250,
            reference                              = "REF-SANITIZE-001",
            type_of_appliance                      = TypeOfAppliance.WoodLogs,
            firebox_depth                          = 33.0.cm,
            firebox_width                          = 33.0.cm,
            firebox_height                         = 52.0.cm,
            ash_pit_height                         = 5.0.cm,
            glass_area                             = 600.0.cm2,
            efficiency_nominal                     = 80.0.percent,
            efficiency_reduced                     = Some(65.0.percent),
            heat_output_reduced                    = HeatOutputReduced.FromTypeTest(8.0.kW),
            minimum_fuel_mass                      = Some(3.0.kg),
            maximum_fuel_mass                      = 15.0.kg,
            air_fuel_ratio_nominal                 = 3.0.unitless,
            air_fuel_ratio_lowest                  = Some(2.5.unitless),
            co2_dry_nominal                        = 12.0.percent,
            co2_dry_lowest                         = Some(9.0.percent),
            pellets_load_burn_duration             = None,
            mean_firebox_temperature               = Some(350.0.degreesCelsius),
            t_burnout                              = 700.0.degreesCelsius,
            is_glass_surface_ratio_below_one_fifth = true,
            emissions_values                       = EmissionsAndEfficiencyValues_DTO(
                firebox_name                = "Firebox Sanitize Model",
                accredited_or_notified_body = "Lab ABC",
                test_reports                = Nil,
                emissions_values            = EmissionValues_DTO(
                    co   = TestEmissionValue_DTO(PolluantName.CO, Some(1200.0.mg_per_Nm3), "", 13.0.percent),
                    dust = TestEmissionValue_DTO(PolluantName.Dust, Some(40.0.mg_per_Nm3), "", 13.0.percent),
                    ogc  = TestEmissionValue_DTO(PolluantName.OGC, Some(120.0.mg_per_Nm3), "", 13.0.percent),
                    nox  = TestEmissionValue_DTO(PolluantName.NOx, Some(80.0.mg_per_Nm3), "", 13.0.percent)
                )
            )
        )

    private def mkStoveParams: StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = 15.0.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    private def flueWithDeprecated(): Seq[FlowOnlyPipeDescr_15544_V3] = Seq(
        SetFlowOnlyPipeProp_15544.SetInitialDirection    (AzimuthDirection.Rear, InclinationDirection.Horizontal),
        SetFlowOnlyPipeProp_15544.SetInitialPosition     (10.cm, 20.cm, 30.cm                                   ),
        SetFlowOnlyPipeProp_15544.SetRoughness           (3.mm                                                  ),
        SetFlowOnlyPipeProp_15544.SetInnerShape(PipeShape.Rectangle(11.cm, 12.cm)),
        AddFlowOnlyPipeElement_15544.AddSectionHorizontal("sortie foyer", 30.cm                                 )
    )

    "FireCalcYAML_Loader" - {

        "sanitizes directly-constructed mixed V7 input" in {
            val mixed = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_descr               = Seq.empty,
                firebox                        = mkSingleTested,
                post_firebox_pipes             = PostFireboxPipes(
                    initialDirection = PostFireboxInitialDirection(
                        AzimuthDirection.Left,
                        InclinationDirection.Horizontal
                    ),
                    initialPosition  = PostFireboxInitialPosition(0.cm, 0.cm, 0.cm),
                    slots            = Seq(
                        PostFireboxPipeDescrSlot.FlueSlot(flueWithDeprecated()),
                        PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                        PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                    )
                )
            )

            val loader = FireCalcYAML_Loader(mixed)

            loader.slotBuildResults should not be empty

            // The application should be buildable (wrapper direction is ground truth)
            val result = loader.make_en15544_Strict_Application
            result.isValid shouldBe true
        }

        "wrapper direction wins when stale descriptor direction conflicts" in {
            val wrapperLeft = PostFireboxInitialDirection(
                AzimuthDirection.Left,
                InclinationDirection.Horizontal
            )
            val conflicting = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_descr               = Seq.empty,
                firebox                        = mkSingleTested,
                post_firebox_pipes             = PostFireboxPipes(
                    initialDirection = wrapperLeft,
                    initialPosition  = PostFireboxInitialPosition(0.cm, 0.cm, 0.cm),
                    slots            = Seq(
                        PostFireboxPipeDescrSlot.FlueSlot(
                            Seq(
                                SetFlowOnlyPipeProp_15544.SetInitialDirection       (
                                    AzimuthDirection.Rear,
                                    InclinationDirection.Horizontal
                                ),
                                SetFlowOnlyPipeProp_15544.SetRoughness              (3.mm),
                                SetFlowOnlyPipeProp_15544.SetInnerShape(PipeShape.Rectangle(11.cm, 12.cm)),
                                AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                                    "turn-to-rear",
                                    90.degrees,
                                    Some(AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal))
                                )
                            )
                        ),
                        PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                        PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                    )
                )
            )

            val loader = FireCalcYAML_Loader(conflicting)
            loader.slotBuildResults.head.finalFrame.map(_.direction) shouldBe Some(Vec3.Left)
        }

        "empty slot vector is handled safely" in {
            val empty = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_descr               = Seq.empty,
                firebox                        = mkSingleTested,
                post_firebox_pipes             = PostFireboxPipes(
                    initialDirection = PostFireboxInitialDirection.default,
                    initialPosition  = PostFireboxInitialPosition(0.cm, 0.cm, 0.cm),
                    slots            = Seq.empty
                )
            )

            val loader = FireCalcYAML_Loader(empty)
            loader.slotBuildResults should have size 0
        }
    }
end V7LoaderSanitizationSuite
