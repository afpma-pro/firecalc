/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

object I18nData_HeatingAppliance:

    case class HeatingAppliance(
        efficiency_nominal: String,
        efficiency_reduced: String,
        fluegas           : HeatingAppliance.FlueGas,
        powers            : HeatingAppliance.Powers,
        temperatures      : HeatingAppliance.Temperatures,
        massFlows         : HeatingAppliance.MassFlows,
        pressures         : HeatingAppliance.Pressures,
        volumeFlows       : HeatingAppliance.VolumeFlows
    )

    object HeatingAppliance:
        case class FlueGas(
            co2_dry_perc_nominal: String,
            co2_dry_perc_reduced: String,
            h2o_perc_nominal    : String,
            h2o_perc_reduced    : String
        )
        case class Powers(
            heat_output_nominal: String,
            heat_output_reduced: String
        )
        case class Temperatures(
            flue_gas_temp_nominal: String,
            flue_gas_temp_reduced: String
        )
        case class MassFlows(
            flue_gas_mass_flow_nominal      : String,
            flue_gas_mass_flow_reduced      : String,
            combustion_air_mass_flow_nominal: String,
            combustion_air_mass_flow_reduced: String
        )
        case class Pressures(
            underPressure         : String,
            underPressure_negative: String,
            underPressure_positive: String,
            flue_gas_draft_min    : String,
            flue_gas_draft_max    : String,
            flue_gas_pdiff_min    : String,
            flue_gas_pdiff_max    : String
        )

        case class VolumeFlows(
            flue_gas_volume_flow_nominal      : String,
            flue_gas_volume_flow_reduced      : String,
            combustion_air_volume_flow_nominal: String,
            combustion_air_volume_flow_reduced: String
        )
