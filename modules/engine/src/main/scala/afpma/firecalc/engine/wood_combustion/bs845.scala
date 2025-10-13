/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.wood_combustion.bs845
import afpma.firecalc.engine.wood_combustion.*

import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

// According to Bristish Standard BS845:Part1:1987
// See: https://www.ametek-land.com/-/media/ameteklandinstruments/documentation/products/portablegasanalysers/lancom4/ametek_land_technical_bulletin_03_calculations_normalisations_conversions.pdf?la=en&revision=040ac48e-caf0-493b-80ea-303ffde43245
//
trait BS845_Alg:

    // def perfect_combustion_efficiency_given_O2_wet(
    //     wood: Wood,
    //     t_flue_gas: TCelsius, 
    //     t_ambiant_air: TCelsius,
    //     o2_wet_perc: Percentage,
    // ): Percentage

    // def perfect_combustion_efficiency_given_CO2_wet(
    //     wood: Wood,
    //     t_flue_gas: TCelsius, 
    //     t_ambiant_air: TCelsius,
    //     co2_wet_perc: Percentage, // TODO: unsure if wet or dry here...
    // ): Percentage

    def perfect_combustion_efficiency_given_CO2_dry(
        wood: Wood,
        t_flue_gas: TCelsius, 
        t_ambiant_air: TCelsius,
        co2_dry_perc: Percentage, // TODO: unsure if wet or dry here...
    ): Percentage

    // extension (w: Wood)
        // def CO2_max_wet: QtyD[Percent]
        // def CO2_max_dry: QtyD[Percent]
        // def co2_wet_from_o2_wet(o2_wet: Percentage): Percentage
        // def co2_dry_from_o2_dry(o2_dry: Percentage): Percentage
        // def o2_wet_from_co2_wet(co2_wet: Percentage): Percentage
        // def o2_dry_from_co2_dry(co2_dry: Percentage): Percentage
        // def v0_a0_ratio: Double
        // def excessAirForO2(o2_perc_vol: Percentage): Double
        // def lambdaForO2(o2_perc_vol: Percentage): Double
        // def lambdaForCO2(co2_perc_wet: Percentage): Double

trait BS845_Impl extends BS845_Alg:

    // def perfect_combustion_efficiency_given_O2_wet(
    //     wood: Wood,
    //     t_flue_gas: TCelsius, 
    //     t_ambiant_air: TCelsius,
    //     o2_wet_perc: Percentage,
    // ): Percentage = 
    //     val co2_wet_perc = wood.co2_wet_from_o2_wet(o2_wet_perc)
    //     perfect_combustion_efficiency_given_CO2_wet(wood, t_flue_gas, t_ambiant_air, co2_wet_perc)

    def perfect_combustion_efficiency_given_CO2_dry(
        wood: Wood,
        t_flue_gas: TCelsius, 
        t_ambiant_air: TCelsius,
        co2_dry_perc: Percentage, // TODO: unsure if wet or dry here...
    ): Percentage = 
        val tf = t_flue_gas.toUnit[Celsius].value
        val ta = t_ambiant_air.toUnit[Celsius].value

        val wc = wood.water_content.value

        val co2 = co2_dry_perc.value
        val c = wood.atomic_composition("C").value
        val h = wood.atomic_composition("H").value
        val qgr = wood.lower_calorific_value_dry.toUnit[Kilo * Joule / Kilogram] // LCV dry

        // use British Standard BS845:Part:1987
        // See https://www.ametek-land.com/-/media/ameteklandinstruments/documentation/products/portablegasanalysers/lancom4/ametek_land_technical_bulletin_03_calculations_normalisations_conversions.pdf?la=en&revision=040ac48e-caf0-493b-80ea-303ffde43245

        val k1 = 253 * c / qgr.value
        val L1 = k1 * (tf - ta) / co2

        val k2 = 2.1 * (wc + 9 * h) / qgr.value
        val L2 = k2 * ( 1185.0 + tf - 2 * ta )

        val co = 0.0    // we assume perfect combustion for simplicity
        val hxcy = 0.0  // we assume perfect combustion for simplicity
        val k3 = 1.25 * c - 55
        val L3 = k3 * (co + hxcy) / (co2 + co + hxcy)

        val efficiency = 100 - L1 - L2 - L3
        efficiency.percent

    // extension (w: Wood)

        // def CO2_max_wet = 
        //     val c = w.atomic_composition.get("C").get
        //     val h = w.atomic_composition.get("H").get
        //     c / ( c + h / 2.0 + (79.1 / 20.9) * (c + h / 4.0) )

        // def CO2_max_dry = 
        //     val c = w.atomic_composition.get("C").get
        //     val h = w.atomic_composition.get("H").get
        //     c / ( c + (79.1 / 20.9) * (c + h / 4.0) )

        // def co2_wet_from_o2_wet(o2_wet: Percentage): Percentage = 
        //     w.CO2_max_wet * (20.9 - o2_wet.value) / 20.9

        // def co2_dry_from_o2_dry(o2_dry: Percentage): Percentage = 
        //     w.CO2_max_dry * (20.9 - o2_dry.value) / 20.9

        // def o2_wet_from_co2_wet(co2_wet: Percentage): Percentage =
        //     20.9.percent - (20.9 * co2_wet / w.CO2_max_wet)

        // def o2_dry_from_co2_dry(co2_dry: Percentage): Percentage =
        //     20.9.percent - (20.9 * co2_dry / w.CO2_max_dry)

        // According to https://www.ametek-land.com/-/media/ameteklandinstruments/documentation/products/portablegasanalysers/lancom4/ametek_land_technical_bulletin_03_calculations_normalisations_conversions.pdf?la=en&revision=040ac48e-caf0-493b-80ea-303ffde43245
        // Section 4.1
        // def v0_a0_ratio = 
        //     val c = w.atomic_composition.get("C").get.toUnit[1].value
        //     val h = w.atomic_composition.get("H").get.toUnit[1].value
        //     ( c + (3.73 * (c + h / 4)) )
        //     /
        //     ( (1 + 3.73) * (c + h / 4) )

        // def excessAirForO2(o2_perc_vol: Percentage): Double = 
        //     (o2_perc_vol / (20.9.percent - o2_perc_vol) * v0_a0_ratio).value

        // def lambdaForO2(o2_perc_vol: Percentage): Double =
        //     excessAirForO2(o2_perc_vol) + 1

        // def lambdaForCO2(co2_perc_wet: Percentage): Double = 
        //     require(co2_perc_wet > 0.1.percent, s"unexpected co2 percentage = ${co2_perc_wet.show} (should be at least 0.1 for calculating 'lambda')")
        //     val l = (w.CO2_max_wet / co2_perc_wet).value
        //     l

        