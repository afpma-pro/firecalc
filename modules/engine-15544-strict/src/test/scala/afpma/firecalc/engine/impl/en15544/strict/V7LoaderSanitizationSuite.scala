/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.dto.v7.AirIntakePosition
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.domain.AirDistributionBox
import afpma.firecalc.domain.FireboxCoordinateSystem

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
            t_burnout                              = Some(700.0.degreesCelsius),
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

    private def flueClean(): Seq[FlowOnlyPipeDescr_15544] = Seq(
        SetFlowOnlyPipeProp_15544.SetRoughness           (3.mm                 ),
        SetFlowOnlyPipeProp_15544.SetInnerShape(PipeShape.Rectangle(11.cm, 12.cm)),
        AddFlowOnlyPipeElement_15544.AddSectionHorizontal("sortie foyer", 30.cm)
    )

    private def airIntakeDescrRear(): Seq[FlowOnlyPipeDescr_13384] = Seq(
        SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(0.2.meters)),
        AddFlowOnlyPipeElement_13384.AddSectionHorizontal("h1", 2.0.meters)
    )

    "FireCalcYAML_Loader" - {

        "sanitizes V7 input" in {
            val mixed = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_pipes               = FramedAirIntakePipes.fromLegacy(Seq.empty),
                firebox                        = mkSingleTested,
                post_firebox_pipes             = FramedPostFireboxPipes(
                    PipeInitialDirection(
                        AzimuthDirection.Left,
                        InclinationDirection.Horizontal
                    ),
                    PostFireboxStartPosition.Auto,
                    slots = Seq(
                        PostFireboxPipeDescrSlot.FlueSlot(flueClean()),
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

        "empty slot vector is handled safely" in {
            val empty = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_pipes               = FramedAirIntakePipes.fromLegacy(Seq.empty),
                firebox                        = mkSingleTested,
                post_firebox_pipes             = FramedPostFireboxPipes(
                    PipeInitialDirection.default,
                    PostFireboxStartPosition.Auto,
                    slots = Seq.empty
                )
            )

            val loader = FireCalcYAML_Loader(empty)
            loader.slotBuildResults should have size 0
        }

        "resolves Auto position through full loader path" in {
            // Full integration: YAML with Auto position → loader → PipePositionComputer → algebra
            // Firebox 33x33x52, direction Left/Horizontal, inner shape Rectangle(11x12)
            // Expected: top-aligned on left face
            //   z = fireboxHeight - innerHeight/2 = 0.52 - 0.06 = 0.46
            //   x = -halfWidth = -0.165, y = 0
            val yaml = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_pipes               = FramedAirIntakePipes.fromLegacy(Seq.empty),
                firebox                        = mkSingleTested,
                post_firebox_pipes             = FramedPostFireboxPipes(
                    PipeInitialDirection(
                        AzimuthDirection.Left,
                        InclinationDirection.Horizontal
                    ),
                    PostFireboxStartPosition.Auto,
                    slots = Seq(
                        PostFireboxPipeDescrSlot.FlueSlot(flueClean()),
                        PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                        PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                    )
                )
            )

            val loader = FireCalcYAML_Loader(yaml)
            val pos    = loader.stoveProjectDescr_EN15544_Strict.en15544_incrInputs.postFirebox.resolvedPosition

            pos shouldBe defined
            val resolved = pos.get
            // x = -halfWidth = -0.33/2 = -0.165
            (math.abs(resolved.x.value - (FireboxCoordinateSystem.FireboxBaseCenterX - 0.165)) < 1e-6) shouldBe true
            // y = 0 (Left is pure -X)
            (math.abs(resolved.y.value - FireboxCoordinateSystem.FireboxBaseCenterY) < 1e-6          ) shouldBe true
            // z = fireboxHeight - innerHeight/2 = 0.52 - 0.12/2 = 0.46 (relative to FireboxBaseCenterZ)
            (math.abs(resolved.z.value - (FireboxCoordinateSystem.FireboxBaseCenterZ + 0.46)) < 1e-6 ) shouldBe true
        }

        // ── airIntakeInitialPosition ─────────────────────────────────────

        "airIntakeInitialPosition returns None for empty descriptors" in {
            val yaml = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_pipes               = FramedAirIntakePipes.fromLegacy(Seq.empty),
                firebox                        = mkSingleTested,
                post_firebox_pipes             = FramedPostFireboxPipes(
                    PipeInitialDirection.default,
                    PostFireboxStartPosition.Auto,
                    slots = Seq.empty
                )
            )

            val loader = FireCalcYAML_Loader(yaml)
            loader.stoveProjectDescr_EN15544_Strict.en15544_incrInputs.airIntake.resolvedPosition shouldBe None
        }

        "airIntakeInitialPosition resolves InitialAuto via reverse computation" in {
            // Single 2m horizontal section going Rear
            // After replay from Origin: final = (0, 2, 0), direction = Rear
            // Target connection (Rear): ray from center in -Rear=Front hits front face
            //   x=0, y=-depth/2, z=adBoxZBottom+0.1
            // Offset = target - rawFinal = (0, -0.165-2, -0.20+0.1) = (0, -2.165, -0.10)
            val yaml = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_pipes               = FramedAirIntakePipes(
                    initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal),
                    position   = AirIntakePosition.InitialAuto,
                    descr      = airIntakeDescrRear()
                ),
                firebox                        = mkSingleTested,
                post_firebox_pipes             = FramedPostFireboxPipes(
                    PipeInitialDirection.default,
                    PostFireboxStartPosition.Auto,
                    slots = Seq.empty
                )
            )

            val loader = FireCalcYAML_Loader(yaml)
            val pos    = loader.stoveProjectDescr_EN15544_Strict.en15544_incrInputs.airIntake.resolvedPosition
            pos shouldBe defined

            val resolved = pos.get
            // Air intake resolved position is an OFFSET (target - rawFinal), not an absolute position.
            // Both replay start and connection point use FireboxBaseCenterX/Y, so X offset cancels to 0.
            // x = 0 (Rear is pure +Y)
            (math.abs(resolved.x.value - 0.0) < 1e-6           ) shouldBe true
            // y = -depth/2 - 2.0 = -0.165 - 2.0 = -2.165
            (math.abs(resolved.y.value - (-0.165 - 2.0)) < 1e-6) shouldBe true
            // z = connectionZ - rawFinalZ = (AirDistributionBox.CenterZ + 0.1) - FireboxBaseCenterZ = -0.1 - 100 = -100.1
            (math.abs(
                resolved.z.value - (AirDistributionBox.CenterZ + 0.1 - FireboxCoordinateSystem.FireboxBaseCenterZ)
            ) < 1e-6                                           ) shouldBe true
        }

        "airIntakeInitialPosition resolves FinalAuto same as InitialAuto" in {
            val yaml = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_pipes               = FramedAirIntakePipes(
                    initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal),
                    position   = AirIntakePosition.FinalAuto,
                    descr      = airIntakeDescrRear()
                ),
                firebox                        = mkSingleTested,
                post_firebox_pipes             = FramedPostFireboxPipes(
                    PipeInitialDirection.default,
                    PostFireboxStartPosition.Auto,
                    slots = Seq.empty
                )
            )

            val loader = FireCalcYAML_Loader(yaml)
            val pos    = loader.stoveProjectDescr_EN15544_Strict.en15544_incrInputs.airIntake.resolvedPosition
            pos shouldBe defined

            // Should match InitialAuto result (offset, not absolute)
            (math.abs(pos.get.x.value - 0.0) < 1e-6           ) shouldBe true
            (math.abs(pos.get.y.value - (-0.165 - 2.0)) < 1e-6) shouldBe true
            (math.abs(
                pos.get.z.value - (AirDistributionBox.CenterZ + 0.1 - FireboxCoordinateSystem.FireboxBaseCenterZ)
            ) < 1e-6                                          ) shouldBe true
        }

        "airIntakeInitialPosition returns Manual position as-is" in {
            val manualPos = Position3D(1.5.meters, -3.0.meters, -0.05.meters)
            val yaml      = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_pipes               = FramedAirIntakePipes(
                    initialDir = PipeInitialDirection.default,
                    position   = AirIntakePosition.InitialManual(manualPos),
                    descr      = airIntakeDescrRear()
                ),
                firebox                        = mkSingleTested,
                post_firebox_pipes             = FramedPostFireboxPipes(
                    PipeInitialDirection.default,
                    PostFireboxStartPosition.Auto,
                    slots = Seq.empty
                )
            )

            val loader = FireCalcYAML_Loader(yaml)
            val pos    = loader.stoveProjectDescr_EN15544_Strict.en15544_incrInputs.airIntake.resolvedPosition
            pos shouldBe Some(manualPos)
        }

        "airIntakeInitialPosition returns FinalManual position as-is" in {
            val manualPos = Position3D(-0.5.meters, 1.0.meters, 0.1.meters)
            val yaml      = FireCalcYAML_V7(
                locale                         = Locale(Languages.Fr, None),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr("test", "", Country.France),
                local_conditions               = LocalConditions(0.meters, false, LocalConditions.ChimneyTermination.Classic),
                stove_params                   = mkStoveParams,
                air_intake_pipes               = FramedAirIntakePipes(
                    initialDir = PipeInitialDirection.default,
                    position   = AirIntakePosition.FinalManual(manualPos),
                    descr      = airIntakeDescrRear()
                ),
                firebox                        = mkSingleTested,
                post_firebox_pipes             = FramedPostFireboxPipes(
                    PipeInitialDirection.default,
                    PostFireboxStartPosition.Auto,
                    slots = Seq.empty
                )
            )

            val loader = FireCalcYAML_Loader(yaml)
            val pos    = loader.stoveProjectDescr_EN15544_Strict.en15544_incrInputs.airIntake.resolvedPosition
            pos shouldBe Some(manualPos)
        }
    }
end V7LoaderSanitizationSuite
