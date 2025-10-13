/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import cats.data.NonEmptyList
import cats.data.ValidatedNel

import algebra.instances.all.given

import afpma.firecalc.engine.OTypedQtyD
import afpma.firecalc.engine.OTypedQtyDPretty
import afpma.firecalc.engine.OTypedTempD
import afpma.firecalc.engine.models.CheckableConstraint
import afpma.firecalc.engine.models.TermDef
import afpma.firecalc.engine.models.TermDefDetails
import afpma.firecalc.engine.models.gtypedefs
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.i18n.I
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import io.taig.babel.Locale
import magnolia1.Transl
import cats.data.ValidatedNel
import afpma.firecalc.engine.models.TermConstraintError

object typedefs:

    // Section "1"

    type GlassArea = GlassArea.Type
    object GlassArea extends OTypedQtyDPretty[(Meter ^ 2), (Centimeter ^ 2)]:
        def toPrettyUnit = _.toUnit[(Centimeter ^ 2)]
        override def valueFormat: String = "%.0f"
        def termDef = TermDef("glass_area")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.GlassArea.name,
            I18N.en15544.terms.GlassArea.descr,
        )

    // Section "3.1"

    // Section "3.2"

    type L_N = CalculatedFluePipeLength.Type
    object CalculatedFluePipeLength extends OTypedQtyD[Meter]:
        def termDef = TermDef("L_N")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.L_N.name,
            I18N.en15544.terms.L_N.descr,
        )

    // Section "3.5"

    type A_BR = FireboxBase.Type
    object FireboxBase extends OTypedQtyDPretty[(Meter ^ 2), (Centimeter ^ 2)]:
        def toPrettyUnit = _.toUnit[(Centimeter ^ 2)]
        def termDef = TermDef("A_BR")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.A_BR.name,
            I18N.en15544.terms.A_BR.descr,
        )

    // Section "3.6"

    type H_BR = FireboxHeight.Type
    object FireboxHeight extends OTypedQtyDPretty[Meter, Centimeter]:
        def toPrettyUnit = _.toUnit[Centimeter]
        def termDef = TermDef("H_BR")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.H_BR.name,
            I18N.en15544.terms.H_BR.descr,
        )

    // Section "3.7"

    type O_BR = FireboxSurface.Type
    object FireboxSurface extends OTypedQtyDPretty[(Meter ^ 2), (Centimeter ^ 2)]:
        def toPrettyUnit = _.toUnit[(Centimeter ^ 2)]
        def termDef = TermDef("O_BR")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.O_BR.name,
            I18N.en15544.terms.O_BR.descr,
        )

    // Section "3.9"

    type m_BU = BurningRate.Type
    object BurningRate extends OTypedQtyD[Kilogram / Hour]:
        def termDef = TermDef("m_BU")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.m_BU.name,
            I18N.en15544.terms.m_BU.descr,
        )

    // Section "3.10"

    type U_BR = FireboxAdmeasurement.Type
    object FireboxAdmeasurement extends OTypedQtyDPretty[Meter, Centimeter]:
        def toPrettyUnit = _.toUnit[Centimeter]
        def termDef = TermDef("U_BR")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.U_BR.name,
            I18N.en15544.terms.U_BR.descr,
        )

    // Section "3.11"

    type A_GS = GasGrooveProfile.Type
    object GasGrooveProfile extends OTypedQtyDPretty[(Meter ^ 2), (Centimeter ^ 2)]:
        def toPrettyUnit = _.toUnit[(Centimeter ^ 2)]
        def termDef = TermDef("A_GS")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.A_GS.name,
            I18N.en15544.terms.A_GS.descr,
        )

    // Section "3.12"

    /**
     * flue pipe length
     *
     * length of the connecting line of all geometric centres of the flue pipe profiles from the firebox exit to the connector pipe entrance.
     */
    type L_Z = FluePipeLength.Type
    object FluePipeLength extends afpma.firecalc.engine.OTypedQtyD[Meter]:
        def termDef = TermDef("L_Z")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.L_Z.name,
            I18N.en15544.terms.L_Z.descr,
        )

    // Section "3.16"

    type m_B = MaximumLoad.Type
    object MaximumLoad extends OTypedQtyD[Kilogram]:
        def termDef = TermDef("m_B")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.m_B.name,
            I18N.en15544.terms.m_B.descr,
        )
        def summon(using ev: m_B): m_B = ev

    // Section "3.17"

    type m_B_min = MinimumLoad.Type
    object MinimumLoad extends OTypedQtyD[Kilogram]:
        def termDef = TermDef("m_B_min")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.m_B_min.name,
            I18N.en15544.terms.m_B_min.descr,
        )

    // Section "3.18"

    type P_n = NominalHeatOutput.Type
    object NominalHeatOutput extends OTypedQtyD[Kilo * Watt]:
        def termDef = TermDef("P_n")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.P_n.name,
            I18N.en15544.terms.P_n.descr,
        )

    // Section "3.20"

    type t_n = StoragePeriod.Type
    object StoragePeriod extends OTypedQtyD[Hour]:
        def termDef = TermDef("t_n")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms.t_n.name,
            I18N.en15544.terms.t_n.descr,
        )
    // export StoragePeriod.given

    // Section "3.XX - cf 4.2.1"

    type n_min = RequiredMinimumEfficiency.Type
    object RequiredMinimumEfficiency extends OTypedQtyD[Percent]:
        def termDef = TermDef("n_min")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.n_min.name,
            I18N.en15544.terms_xtra.n_min.descr,
        )

    // Section "3.XX - cf 4.3.1.1 - General"

    type height_of_lowest_opening = HeightOfLowestOpening.Type
    object HeightOfLowestOpening extends OTypedQtyDPretty[Meter, Centimeter]:
        def toPrettyUnit = _.toUnit[Centimeter]
        def termDef = TermDef("height_of_the_lowest_opening")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.height_of_the_lowest_opening.name,
            I18N.en15544.terms_xtra.height_of_the_lowest_opening.descr,
        )

    // Section "3.XX - cf 4.3.3 - Minimum flue pipe length "

    type Table_1_Factor_a = Table_1_Factor_a.Type
    object Table_1_Factor_a extends OTypedQtyD[1]:
        def termDef = TermDef("Table_1_Factor_a")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.Table_1_Factor_a.name,
            I18N.en15544.terms_xtra.Table_1_Factor_a.descr,
        )

    type Table_1_Factor_b = Table_1_Factor_b.Type
    object Table_1_Factor_b extends OTypedQtyD[1]:
        def termDef = TermDef("Table_1_Factor_b")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.Table_1_Factor_b.name,
            I18N.en15544.terms_xtra.Table_1_Factor_b.descr,
        )

    type Table_1_Factor_a_or_b = Either[Table_1_Factor_a, Table_1_Factor_b]

    // Section "3.XX - cf 4.6.2.1 - Combustion air flow rate"

    // Section "3.XX - cf 4.6.2.2 - Temperature Correction"

    // Section "3.XX - cf 4.6.2.3 - Altitude Correction"

    // Section "3.XX - cf 4.6.2.3 - Temperature"

    type t_ext = TemperatureExt.Type
    object TemperatureExt extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_ext")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_ext.name,
            I18N.en15544.terms_xtra.t_ext.descr,
        )

    // Section "3.XX - cf 4.6.3 - Flue gas flow rate"

    // Section "3.XX - cf 4.6.4 - Flue gas mass flow rate"

    type m_G = FlueGasMassFlowRate.Type
    object FlueGasMassFlowRate extends OTypedQtyDPretty[Kilogram / Second, Gram / Second]:
        def toPrettyUnit = _.toUnit[Gram / Second]
        override def valueFormat: String = "%.1f"
        def termDef = TermDef("m_G")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.m_G.name,
            I18N.en15544.terms_xtra.m_G.descr,
        )

    // not present in the norm (but implicit)
    
    type m_L = CombustionAirMassFlowRate.Type
    object CombustionAirMassFlowRate extends OTypedQtyDPretty[Kilogram / Second, Gram / Second]:
        def toPrettyUnit = _.toUnit[Gram / Second]
        override def valueFormat: String = "%.1f"
        def termDef = TermDef("m_L")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.m_L.name,
            I18N.en15544.terms_xtra.m_L.descr,
        )

    // Section "3.XX - cf 4.7.1 - Combustion air density"

    // Section "3.XX - cf 4.7.2 - Flue gas density"

    // Section "3.XX - cf 4.8.1 - Mean outside air temperature and combustion air temperature"

    type t_outside_air_mean = OutsideAirMeanTemperature.Type
    object OutsideAirMeanTemperature extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_outside_air_mean")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_outside_air_mean.name,
            I18N.en15544.terms_xtra.t_outside_air_mean.descr,
        )

    type t_combustion_air = CombustionAirTemperature.Type
    object CombustionAirTemperature extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_combustion_air")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_combustion_air.name,
            I18N.en15544.terms_xtra.t_combustion_air.descr,
        )

    // Section "4.8.2", "Mean firebox temperature"

    type t_BR = FireboxMeanTemperature.Type
    object FireboxMeanTemperature extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_BR")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_BR.name,
            I18N.en15544.terms_xtra.t_BR.descr,
        )

    // Section "4.8.3", "Flue gas temperature in the flue pipe"

    type t_burnout = FireboxMeanTemperatureAtBurnout.Type
    object FireboxMeanTemperatureAtBurnout
        extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_burnout")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_burnout.name,
            I18N.en15544.terms_xtra.t_burnout.descr,
        )

    type t_fluepipe = FluePipeTemperature.Type
    object FluePipeTemperature extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_fluepipe")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_fluepipe.name,
            I18N.en15544.terms_xtra.t_fluepipe.descr,
        )

    type t_connector_pipe = ConnectorPipeTemperature.Type
    object ConnectorPipeTemperature extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_connector_pipe")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_connector_pipe.name,
            I18N.en15544.terms_xtra.t_connector_pipe.descr,
        )

    type t_connector_pipe_mean = ConnectorPipeMeanTemperature.Type
    object ConnectorPipeMeanTemperature extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_connector_pipe_mean")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_connector_pipe_mean.name,
            I18N.en15544.terms_xtra.t_connector_pipe_mean.descr,
        )

    type c_P = SpecificHeatCapacityOfTheFlueGas.Type
    object SpecificHeatCapacityOfTheFlueGas
        extends OTypedQtyD[Joule / (Kilogram * Kelvin)]:
        def termDef = TermDef("c_P")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.c_P.name,
            I18N.en15544.terms_xtra.c_P.descr,
        )
    val c_P = SpecificHeatCapacityOfTheFlueGas

    // Section "4.8.5",
    // "Flue gas temperature at chimney entrance mean flue gas " +
    // "temperature of the chimney and temperature of the chimney wall " +
    // "at the top of the chimney"

    // defined in gtypedefs because we need some for EN13384 also
    // import gtypedefs.t_chimney_entrance
    // import gtypedefs.t_chimney_mean
    // import gtypedefs.t_chimney_out
    // import gtypedefs.t_chimney_wall_top

    // Section "4.9", "Calculation of flow mechanics"

    // Section "4.9.1", "General"

    // TODO: check case when air intake is carried out via a heating door frame

    // Section "4.9.2", "Calculation of the standing pressure"

    // Section "4.9.3", "Calculation of the flow velocity"

    // Section "4.9.4.1", "Static fricition (p_R)"

    // Section "4.9.4.2", "Dynamic Pressure (p_d)"

    // Section "4.9.4.3", "Friction coefficient (λ_f)"

    // Section "4.9.4.4", "Hydraulic Diameter (D_h)"

    // Section "4.9.5"
    // "Calculation of the resistance due to direction change (p_u)"

    // Section "4.10.1", "Pressure Condition"

    case class PressureRequirement(
        sum_pr_pu: Pressure,
        sum_ph: Pressure,
        sum_ph_min_expected: Pressure,
        sum_ph_max_expected: Pressure,
    ) {
        val min = sum_ph_min_expected
        val current = sum_ph
        val max = sum_ph_max_expected

        val `current-min` = current - min
        val `current-max` = current - max
        
        val isInValidRange: Boolean = 
            if (min <= current && current <= max) true else false

        val isTooMuchDraft = if (current > max) true else false
        val isTooMuchResistance = if (min > current) true else false
    }

    // Section "4.10.3", "Efficiency of the combustion (η)"

    type σ_CO2 = CarbonDioxideConcentration.Type
    object CarbonDioxideConcentration extends OTypedQtyD[Percent]:
        def termDef = TermDef("σ_CO2")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.σ_CO2.name,
            I18N.en15544.terms_xtra.σ_CO2.descr,
        )

    type σ_H2O = WaterContentOfTheWood.Type
    object WaterContentOfTheWood extends OTypedQtyD[Percent]:
        def termDef = TermDef("σ_H2O")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.σ_H2O.name,
            I18N.en15544.terms_xtra.σ_H2O.descr,
        )

    type η = EfficiencyOfTheCombustion.Type
    object EfficiencyOfTheCombustion extends OTypedQtyD[Percent]:
        override def valueFormat: String = "%.1f"
        def termDef = TermDef("η")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.η.name,
            I18N.en15544.terms_xtra.η.descr,
        )

    type t_F = InletTemperatureIntoConnectorPipe.Type
    object InletTemperatureIntoConnectorPipe extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_F")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_F.name,
            I18N.en15544.terms_xtra.t_F.descr,
        )

    // Section "4.10.4", "Flue gas triple of variates"

    type t_flue_gas = FlueGasTemperature.Type
    object FlueGasTemperature extends OTypedTempD[Celsius]:
        def termDef = TermDef("t_flue_gas")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.t_flue_gas.name,
            I18N.en15544.terms_xtra.t_flue_gas.descr,
        )

    type RequiredDeliveryPressure = RequiredDeliveryPressure.Type
    object RequiredDeliveryPressure extends OTypedQtyD[Pascal]:
        override def valueFormat: String = "%.1f"
        def termDef = TermDef("RequiredDeliveryPressure")
        def termDefDetails = TermDefDetails(
            I18N.en15544.terms_xtra.RequiredDeliveryPressure.name,
            I18N.en15544.terms_xtra.RequiredDeliveryPressure.descr,
        )

    case class FlueGasTripleOfVariates(
        rdp: RequiredDeliveryPressure,
        t_flue_gas: t_flue_gas,
        flue_gas_rate: m_G
    )

    case class EstimatedOutputTemperatures(
        t_firebox: TCelsius,
        t_firebox_outlet: TCelsius,
        t_stove_out: TCelsius,
        t_chimney_out: TCelsius,
        t_chimney_wall_top_out: TCelsius,
    )

    case class CitedConstraints(
        t_n                         : CheckableConstraint[t_n],
        m_B                         : CheckableConstraint[m_B],
        m_B_min                     : CheckableConstraint[m_B_min],
        glass_area                  : CheckableConstraint[GlassArea],
        fireboxDimensions_Base  : CheckableConstraint[std.Firebox_15544.Dimensions.Base],
        h_br                        : CheckableConstraint[H_BR],
        λ                           : CheckableConstraint[λ],
        η                           : CheckableConstraint[η]
    )

    object CitedConstraints:
        extension (cc: CitedConstraints)
            
            def asList: List[CheckableConstraint[?]] = 
                Tuple.fromProductTyped(cc).toList

            def checkAndReturnVNelError: ValidatedNel[TermConstraintError[?], Unit] =
                import cats.implicits.catsSyntaxValidatedId
                val tnOL: Option[List[TermConstraintError[?]]] = 
                    cc.t_n.vresultOption                         .flatMap(_.showInvalidConstraintErrors)
                val outputs = List(
                    cc.t_n.vresultOption                         .flatMap(_.showInvalidConstraintErrors),
                    cc.m_B.vresultOption                         .flatMap(_.showInvalidConstraintErrors),
                    cc.m_B_min.vresultOption                     .flatMap(_.showInvalidConstraintErrors),
                    cc.glass_area.vresultOption                  .flatMap(_.showInvalidConstraintErrors),
                    cc.fireboxDimensions_Base.vresultOption      .flatMap(_.showInvalidConstraintErrors),
                    cc.h_br.vresultOption                        .flatMap(_.showInvalidConstraintErrors),
                    cc.λ.vresultOption                           .flatMap(_.showInvalidConstraintErrors),
                    cc.η.vresultOption                           .flatMap(_.showInvalidConstraintErrors),
                ).flatten.map(_.toList).flatten
                if (outputs.size > 0) NonEmptyList.fromListUnsafe(outputs).invalid else ().validNel
            
            def checkAndReturnVNelString: Locale ?=> VNelString[Unit] = 
                import cats.implicits.catsSyntaxValidatedId
                val outputs = List(
                    cc.t_n.vresultOption                         .flatMap(_.showIfInvalid),
                    cc.m_B.vresultOption                         .flatMap(_.showIfInvalid),
                    cc.m_B_min.vresultOption                     .flatMap(_.showIfInvalid),
                    cc.glass_area.vresultOption                  .flatMap(_.showIfInvalid),
                    cc.fireboxDimensions_Base.vresultOption      .flatMap(_.showIfInvalid),
                    cc.h_br.vresultOption                        .flatMap(_.showIfInvalid),
                    cc.λ.vresultOption                           .flatMap(_.showIfInvalid),
                    cc.η.vresultOption                           .flatMap(_.showIfInvalid),
                ).flatten
                if (outputs.size > 0) NonEmptyList.fromListUnsafe(outputs).invalid else ().validNel
                

end typedefs