/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import afpma.firecalc.dto.v4.Firebox_V3
import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.LocalizedString

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en15544.std.Firebox_15544

import cats.syntax.validated.*

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import afpma.firecalc.engine.models.en15544.typedefs.GlassArea

object single_tested:

    /** Maps DTO [[Firebox_V3.TestStandard]] to engine [[Firebox_15544.TestStandard]]. */
    given Transformer[Firebox_V3.TestStandard, Firebox_15544.TestStandard] =
        _.transformInto[Firebox_15544.TestStandard]

    /**
     * Builds [[EmissionsAndEfficiencyValues]] from the flat DTO fields.
     * Efficiency placeholder fields are set to None.validNel — overwritten by the engine
     * during calculation.
     */
    private def buildEmissionsValues(dto: Firebox_V3.SingleTested): EmissionsAndEfficiencyValues =
        EmissionsAndEfficiencyValues(
            firebox_name                       = dto.emissions_firebox_name,
            accredited_or_notified_body        = dto.emissions_accredited_body,
            test_reports                       = Nil,
            min_efficiency_firebox_nominal     = None,
            min_efficiency_full_stove_nominal  = None.validNel,
            min_efficiency_firebox_reduced     = None,
            min_efficiency_full_stove_reduced  = None.validNel,
            min_seasonal_efficiency_full_stove = None.validNel,
            emissions_values                   = EmissionValues(
                co   = TestEmissionValue.defineAt13pO2(
                    PolluantName.CO,
                    Some(dto.emissions_co),
                    test_method = ""
                ),
                dust = TestEmissionValue.defineAt13pO2(
                    PolluantName.Dust,
                    Some(dto.emissions_dust),
                    test_method = ""
                ),
                ogc  = TestEmissionValue.defineAt13pO2(
                    PolluantName.OGC,
                    Some(dto.emissions_ogc),
                    test_method = ""
                ),
                nox  = TestEmissionValue.defineAt13pO2(
                    PolluantName.NOx,
                    Some(dto.emissions_nox),
                    test_method = ""
                )
            )
        )

    /** Primary transformer: [[Firebox_V3.SingleTested]] → [[Firebox_15544.SingleTested]]. */
    given transformer_SingleTested: Transformer[Firebox_V3.SingleTested, Firebox_15544.SingleTested] =
        import afpma.firecalc.engine.models.en15544.typedefs.σ_CO2
        Transformer
            .define[Firebox_V3.SingleTested, Firebox_15544.SingleTested]
            .enableDefaultValues
            .withFieldComputed(_.reference, dto => LocalizedString(_ => dto.reference))
            .withFieldComputed(_.glass_area, _.glass_area: GlassArea)
            .withFieldConst(_.type_of_appliance, TypeOfAppliance.WoodLogs)
            .withFieldRenamed(_.test_standard, _.test_standard)
            .withFieldRenamed(_.heat_output_reduced, _.pn_reduced)
            .withFieldRenamed(_.minimum_fuel_mass, _.minimumFuelMass)
            .withFieldRenamed(_.maximum_fuel_mass, _.maximumFuelMass)
            .withFieldRenamed(_.air_fuel_ratio_nominal, _.airFuelRatio_nominal)
            .withFieldRenamed(_.air_fuel_ratio_lowest, _.airFuelRatio_lowest)
            .withFieldComputed(_.co2_dry_nominal, _.co2_dry_nominal: σ_CO2)
            .withFieldComputed(_.co2_dry_lowest, _.co2_dry_lowest.map(x => x: σ_CO2))
            .withFieldComputed(_.emissions_values, buildEmissionsValues)
            .withFieldRenamed(_.mean_firebox_temperature, _.meanFireboxTemperature)
            .withFieldRenamed(_.t_burnout, _.tBurnout)
            .buildTransformer

end single_tested
