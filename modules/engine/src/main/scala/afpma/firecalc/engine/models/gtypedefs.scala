/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import cats.*

import algebra.instances.all.given

import afpma.firecalc.engine.*

import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

// class for injecting docstate ?
object gtypedefs:

    enum KindOfWood:
        // feuillus
        case HardWood
        // résineux
        case SoftWood

    // Air-Fuel ratio

    /** air fuel ratio */
    type λ = AirFuelRatio.Type
    object AirFuelRatio extends OTypedQtyD[1]:
        def termDef = TermDef(
            "λ",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "air-fuel ratio",
            "ratio between the amount of air supplied to the combustion and the theoretically required amount of air",
        )

    // PipeSectionLength

    /** pipe section length (in m) */
    type L = PipeSectionLength.Type
    object PipeSectionLength extends OTypedQtyD[Meter]:
        def termDef = TermDef(
            "L",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "pipe section length (in m)",
            "pipe section length (in m)",
        )

    // Roughness Height

    /** roughness height (in m) */
    type k_f = RoughnessHeight.Type
    object RoughnessHeight extends OTypedQtyD[Meter]:
        def termDef = TermDef(
            "k_f",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "roughness height (in m)",
            "roughness height (in m)",
        )
    given Conversion[Roughness, k_f] = r => RoughnessHeight.fromHidden(r.unwrap)

    // Friction coefficient

    /** friction coefficient */
    type λ_f = FrictionCoefficient.Type
    object FrictionCoefficient extends OTypedQtyD[1]:
        def termDef = TermDef(
            "λ_f",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "friction coefficient",
            "friction coefficient",
        )

    // Hydraulic Diameter

    /** hydraulic diameter (in m) */
    type D_h = HydraulicDiameter.Type
    object HydraulicDiameter extends OTypedQtyDPretty[Meter, Centimeter]:
        def toPrettyUnit = _.toUnit[Centimeter]
        def termDef = TermDef(
            "D_h",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "hydraulic diameter (in m)",
            "hydraulic diameter (in m)",
        )



    type z_geodetical_height = GeodeticalHeight.Type
    object GeodeticalHeight extends afpma.firecalc.engine.OTypedQtyD[Meter]:
        def termDef = TermDef(
            "z",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "geodetical height (in m)",
            "geodetical height (in m)",
        )

    type G = AccelerationOfGravity.Type
    object AccelerationOfGravity extends afpma.firecalc.engine.OTypedQtyD[Meter / (Second ^ 2)]:
        def termDef = TermDef(
            "g",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "accemeration of gravity (in m/s²)",
            "accemeration of gravity (in m/s²)",
        )
    val G: G = 9.81.withUnit[Meter / (Second ^ 2)]

    /** coefficient of flow resistance */
    type ζ = CoefficientOfFlowResistance.Type
    object CoefficientOfFlowResistance extends afpma.firecalc.engine.OTypedQtyD[1]:
        override def valueFormat: String = "%.2f"
        val zero: ζ = 0.0.unitless
        def termDef = TermDef(
            "ζ",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "coefficient of flow resistance",
            "coefficient of flow resistance",
        )
        extension (zetas: Iterable[ζ])
            def sumO: ζ = zetas.map(_.value).sum.ea

    // Static Friction

    /** static friction (in Pa) */
    type p_R = StaticFriction.Type
    object StaticFriction extends afpma.firecalc.engine.OTypedQtyD[Pascal]:
        def termDef = TermDef(
            "p_R",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "static friction (in Pa)",
            "static friction (in Pa)",
        )
        given p_R_monoidSum: Monoid[p_R] =
            Monoid.instance(0.0.pascals, _ + _)

    // Dynamic Pressure

    /** dynamic pressure (in Pa) */
    type p_d = DynamicPressure.Type
    object DynamicPressure extends OTypedQtyD[Pascal]:
        def termDef = TermDef(
            "p_d",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "dynamic pressure (in Pa)",
            "dynamic pressure (in Pa)",
        )

    // Resistance due to direction change

    /** resistance due to direction change (in Pa) */
    type p_u = ResistanceDueToDirectionChange.Type
    object ResistanceDueToDirectionChange extends OTypedQtyD[Pascal]:
        def termDef = TermDef(
            "p_u",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "resistance due to direction change (in Pa)",
            "resistance due to direction change (in Pa)",
        )
        given p_u_monoidSum: Monoid[p_u] =
            Monoid.instance(0.0.pascals, _ + _)

    // Temperature Correction Factor

    type f_t = TemperatureCorrectionFactor.Type
    object TemperatureCorrectionFactor extends OTypedQtyD[1]:
        def termDef = TermDef(
            "f_t",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "temperature correction factor",
            "temperature correction factor",
        )

    // Altitude Correction

    type f_s = AltitudeCorrectionFactor.Type
    object AltitudeCorrectionFactor extends OTypedQtyD[1]:
        def termDef = TermDef(
            "f_s",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "altitude correction factor",
            "altitude correction factor",
        )

    // Flue gas density

    /** flue gas density (in kg/m3) */
    type ρ_G = FlueGasDensity.Type
    object FlueGasDensity extends OTypedQtyD[Kilogram / (Meter ^ 3)]:
        def termDef = TermDef(
            "ρ_G",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "flue gas density (in kg/m3)",
            "flue gas density (in kg/m3)",
        )

    // Combustion air density

    type ρ_L = CombustionAirDensity.Type
    object CombustionAirDensity extends OTypedQtyD[Kilogram / (Meter ^ 3)]:
        def termDef = TermDef(
            "ρ_L",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "combustion air density (in kg/m3)",
            "combustion air density (in kg/m3)",
        )

    // Standing pressure

    /** standing pressure (in Pa) */
    type p_h = StandingPressure.Type
    object StandingPressure extends OTypedQtyD[Pascal]:
        def termDef = TermDef(
            "p_h",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "standing pressure (in Pa)",
            "standing pressure (in Pa)",
        )
        given p_h_monoidSum: Monoid[p_h] =
            Monoid.instance(0.0.pascals, _ + _)

    // Effective Height

    /** effective height (in m) */
    type H = EffectiveHeight.Type
    object EffectiveHeight extends OTypedQtyD[Meter]:
        def termDef = TermDef(
            "H",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "effective height (in m)",
            "vertical difference betwenn the flue gas exit and the flue gas entrance (in m)",
        )

    // Flue Gas flow rate

    type V_G = FlueGasFlowRate.Type
    object FlueGasFlowRate extends OTypedQtyD[(Meter ^ 3) / Second]:
        def termDef = TermDef(
            "V_G",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "flue gas flow rate (in m3/s)",
            "flue gas flow rate (in m3/s)",
        )

    // Combustion air flow rate

    type V_L = CombustionAirFlowRate.Type
    object CombustionAirFlowRate extends OTypedQtyD[(Meter ^ 3) / Second]:
        def termDef = TermDef(
            "V_L",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "combustion air flow rate",
            "combustion air flow rate",
        )

    // Flow Velocity

    /** flow velocity (in m/s) */
    type v = FlowVelocity.Type
    object FlowVelocity extends OTypedQtyD[Meter / Second]:
        def termDef = TermDef(
            "v",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "flow velocity (in m/s)",
            "flow velocity (in m/s)",
        )

    // Thermal Conductivity

    /** thermal conductivity (in W / (m.K)) */
    type ThermalConductivity = ThermalConductivity.Type
    object ThermalConductivity extends OTypedQtyD[Watt / (Meter * Kelvin)]:
        def termDef = TermDef(
            "λ",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "thermal conductivity (in W / (m.K))",
            "thermal conductivity (in W / (m.K))",
        )

    // Chimney temperatures

    type t_chimney_entrance = FlueGasTemperatureChimneyEntrance.Type
    object FlueGasTemperatureChimneyEntrance extends OTypedTempD[Celsius]:
        def termDef = TermDef(
            "t_chimney_entrance",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "flue gas temperature at the chimney entrance (in °C)",
            "flue gas temperature at the chimney entrance (in °C)",
        )

    type t_chimney_mean = FlueGasMeanTemperatureChimney.Type
    object FlueGasMeanTemperatureChimney extends OTypedTempD[Celsius]:
        def termDef = TermDef(
            "t_chimney_mean",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "mean flue gas temperature of the chimney (in °C)",
            "mean flue gas temperature of the chimney (in °C)",
        )

    type t_chimney_out = FlueGasOutputTemperatureChimney.Type
    object FlueGasOutputTemperatureChimney extends OTypedTempD[Celsius]:
        def termDef = TermDef(
            "t_chimney_out",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "flue gas temperature of the outlet of the chimney (in °C)",
            "flue gas temperature of the outlet of the chimney (in °C)",
        )

    type t_chimney_wall_top = FlueGasTemperatureChimneyWallTop.Type
    object FlueGasTemperatureChimneyWallTop extends OTypedTempD[Celsius]:
        def termDef = TermDef(
            "t_chimney_wall_top",
            "global definitions"
        )
        def termDefDetails = TermDefDetails(
            "temperature of the chimney wall at the top of the chimney (in °C)",
            "temperature of the chimney wall at the top of the chimney (in °C)",
        )

end gtypedefs
