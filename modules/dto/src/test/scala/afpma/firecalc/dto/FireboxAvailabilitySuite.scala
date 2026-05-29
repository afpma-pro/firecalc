/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.domain.FireboxAvailability
import afpma.firecalc.units.coulombutils.*

import coulomb.syntax.withUnit

import afpma.firecalc.dto.all.Firebox
import afpma.firecalc.dto.common.OutsideAirLocationInHeater
import afpma.firecalc.domain.PipeShape
import afpma.firecalc.dto.v4.EmissionValues_DTO
import afpma.firecalc.dto.v4.EmissionsAndEfficiencyValues_DTO
import afpma.firecalc.dto.common.HeatOutputReduced
import afpma.firecalc.dto.v4.PolluantName
import afpma.firecalc.dto.v4.TestEmissionValue_DTO
import afpma.firecalc.dto.v4.TypeOfAppliance

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FireboxAvailabilitySuite extends AnyFreeSpec with Matchers:

    import FireboxAvailabilityExtensions.allows

    private val emis: EmissionsAndEfficiencyValues_DTO = EmissionsAndEfficiencyValues_DTO(
        firebox_name                = "test",
        accredited_or_notified_body = "test",
        test_reports                = Nil,
        emissions_values            = EmissionValues_DTO(
            co   = TestEmissionValue_DTO(PolluantName.CO, None, "", 0.0.percent),
            dust = TestEmissionValue_DTO(PolluantName.Dust, None, "", 0.0.percent),
            ogc  = TestEmissionValue_DTO(PolluantName.OGC, None, "", 0.0.percent),
            nox  = TestEmissionValue_DTO(PolluantName.NOx, None, "", 0.0.percent)
        )
    )

    private val traditional: Firebox.Traditional = Firebox.Traditional(
        heat_output_reduced                   = HeatOutputReduced.NotDefined,
        firebox_depth                         = 1.0.cm,
        firebox_width                         = 1.0.cm,
        firebox_height                        = 1.0.cm,
        height_of_lowest_opening              = 1.0.cm,
        pressure_loss_coefficient_from_door   = 1.0.unitless,
        total_air_intake_surface_area_on_door = 1.0.cm2,
        glass_width                           = 1.0.cm,
        glass_height                          = 1.0.cm
    )

    private val ecolabeled: Firebox.Ecolabeled = Firebox.Ecolabeled(
        heat_output_reduced                     = HeatOutputReduced.NotDefined,
        version                                 = Left("Version 1"),
        air_intake_shape                        = None,
        firebox_depth                           = 1.0.cm,
        firebox_width                           = 1.0.cm,
        firebox_height                          = 1.0.cm,
        height_of_first_row_of_air_injectors    = 1.0.cm,
        door_opening_width                      = 1.0.cm,
        glass_width                             = 1.0.cm,
        glass_height                            = 1.0.cm,
        ash_pit_height                          = 1.0.cm,
        air_manifold_height                     = 1.0.cm,
        firebox_floor_thickness                 = 1.0.cm,
        firebox_inner_wall_thickness            = 1.0.cm,
        firebox_outer_wall_thickness            = 1.0.cm,
        air_column_thickness                    = 1.0.cm,
        width_between_two_air_columns_sides     = 1.0.cm,
        width_between_two_air_columns_rear      = 1.0.cm,
        reinforcement_bars_offset_in_corners_R1 = 1.0.cm,
        reinforcement_bars_offset_in_corners_R2 = 1.0.cm,
        reinforcement_bars_offset_in_corners_R3 = 1.0.cm,
        injector_height                         = 1.0.cm
    )

    private val afpmaPrse: Firebox.AFPMA_PRSE = Firebox.AFPMA_PRSE(
        heat_output_reduced                   = HeatOutputReduced.NotDefined,
        outside_air_location_in_heater        = OutsideAirLocationInHeater.FromBottom,
        outside_air_conduit_shape             = PipeShape.Circle(10.0.cm),
        firebox_depth                         = 1.0.cm,
        firebox_width                         = 1.0.cm,
        firebox_height                        = 1.0.cm,
        height_of_first_row_of_air_injectors  = 1.0.cm,
        glass_width                           = 1.0.cm,
        glass_height                          = 1.0.cm,
        ash_pit_height                        = 1.0.cm,
        floor_thickness                       = 1.0.cm,
        combustion_air_manifold_height        = 1.0.cm,
        outside_air_inlet_lip                 = 1.0.cm,
        height_of_air_feed_to_columns         = 1.0.cm,
        number_of_air_columns_feeding_firebox = 1,
        number_of_air_columns_feeding_door    = 1
    )

    private val singleTested: Firebox.SingleTested = Firebox.SingleTested(
        reference                              = "test",
        type_of_appliance                      = TypeOfAppliance.WoodLogs,
        test_standard                          = Firebox.TestStandard.EN_15250,
        firebox_depth                          = 1.0.cm,
        firebox_width                          = 1.0.cm,
        firebox_height                         = 1.0.cm,
        ash_pit_height                         = 1.0.cm,
        is_glass_surface_ratio_below_one_fifth = false,
        glass_area                             = 1.0.cm2,
        mean_firebox_temperature               = None,
        t_burnout                              = 20.0.degreesCelsius,
        efficiency_nominal                     = 0.8.percent,
        efficiency_reduced                     = None,
        heat_output_reduced                    = HeatOutputReduced.FromTypeTest(1.0.kW),
        minimum_fuel_mass                      = None,
        maximum_fuel_mass                      = 1.0.kg,
        air_fuel_ratio_nominal                 = 1.0.unitless,
        air_fuel_ratio_lowest                  = None,
        co2_dry_nominal                        = 0.1.percent,
        co2_dry_lowest                         = None,
        pellets_load_burn_duration             = None,
        emissions_values                       = emis,
        image                                  = None
    )

    private val door15a: Firebox.Door15aFirebox_Catalog = Firebox.Door15aFirebox_Catalog(
        reference                   = "test",
        firebox_depth               = 1.0.cm,
        firebox_width               = 1.0.cm,
        firebox_height              = 1.0.cm,
        load_size_nominal           = None,
        sb                          = 1.0.withUnit[Centimeter],
        sb_min                      = None,
        sb_max                      = None,
        mb_min                      = None,
        mb_max                      = None,
        pressure_loss_table_raw     = "",
        expectedAirIntakePipeShapes = List(PipeShape.Circle(10.0.cm)),
        actualAirIntakePipeShape    = PipeShape.Circle(10.0.cm),
        co2_dry_nominal             = 0.1.percent,
        co2_dry_lowest              = None,
        emissions_values            = emis,
        glass_area                  = 1.0.cm2,
        height_of_lowest_opening    = 1.0.cm,
        heat_output_reduced         = HeatOutputReduced.NotDefined,
        image                       = None
    )

    "FireboxAvailability.allows" - {

        val mixed = FireboxAvailability(
            traditional    = true,
            ecolabeled     = false,
            afpmaPrse      = true,
            singleTested   = false,
            door15aCatalog = true
        )

        "maps Traditional to traditional field" in {
            mixed.allows(traditional) shouldBe true
        }

        "maps Ecolabeled to ecolabeled field" in {
            mixed.allows(ecolabeled) shouldBe false
        }

        "maps AFPMA_PRSE to afpmaPrse field" in {
            mixed.allows(afpmaPrse) shouldBe true
        }

        "maps SingleTested to singleTested field" in {
            mixed.allows(singleTested) shouldBe false
        }

        "maps Door15aFirebox_Catalog to door15aCatalog field" in {
            mixed.allows(door15a) shouldBe true
        }

        "AllEnabled allows all subtypes" in {
            FireboxAvailability.AllEnabled.allows(traditional) shouldBe true
            FireboxAvailability.AllEnabled.allows(ecolabeled) shouldBe true
            FireboxAvailability.AllEnabled.allows(afpmaPrse) shouldBe true
            FireboxAvailability.AllEnabled.allows(singleTested) shouldBe true
            FireboxAvailability.AllEnabled.allows(door15a) shouldBe true
        }
    }

end FireboxAvailabilitySuite
