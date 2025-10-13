/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en13384



import cats.*
import cats.syntax.all.*

import afpma.firecalc.engine.OTypedQtyD
import afpma.firecalc.engine.OTypedTempD
import afpma.firecalc.engine.models.LoadQty
import afpma.firecalc.engine.models.TermDef
import afpma.firecalc.engine.models.TermDefDetails

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.policy.standard.given
import coulomb.units.si.*

object typedefs:

    enum UnderPressure:
        case Negative, Positive

    /**
      * TemperatureRequirements for EN 13384
      *
      * @param tob temperature at the chimney outlet, in °C
      * @param tig temperature limit is the condensing temperature under dry (or wet) operating conditions (according to 5.3)
      * @param tiob inner wall temperature at the chimney outlet at temperature equilibrium
      * @param tsp condensing temperature (see 5.7.6)
      */
    case class TemperatureRequirements_EN13384 private (
        atLoadQty: LoadQty,
        tob: TCelsius,
        tig: TCelsius,
        tiob: TCelsius,
        tsp: Option[TCelsius],
    )

    object TemperatureRequirements_EN13384:

        /**
          * Constructor method for TemperatureRequirements_EN13384 when operating under *dry* conditions
          *
          * @param tob temperature at the chimney outlet, in °C
          * @param tig temperature limit of the flue gas
          * @param tiob inner wall temperature at the chimney outlet at temperature equilibrium
          * @param tsp condensing temperature (see 5.7.6)
          * @return
          */
        def forDryOperatingConditions(tob: TCelsius, tig: TCelsius, tiob: TCelsius, tsp: TCelsius)(using lq: LoadQty, flueGasCond: FlueGasCondition.Dry_NonCondensing.type) =
            TemperatureRequirements_EN13384(
                atLoadQty   = lq,
                tob         = tob,
                tig         = tig,
                tiob        = tiob,
                tsp         = tsp.some,
            )

        /**
         * Constructor method for TemperatureRequirements_EN13384 when operating under *wet* conditions
         *
         * @param tob temperature at the chimney outlet, in °C
         * @param tig temperature limit of the flue gas
         * @param tiob inner wall temperature at the chimney outlet at temperature equilibrium
         * @param tsp condensing temperature (see 5.7.6)
         * @return
         */
        def forWetOperatingConditions(tob: TCelsius, tig: TCelsius, tiob: TCelsius, tsp: TCelsius)(using lq: LoadQty, flueGasCond: FlueGasCondition.Wet_Condensing.type) =
            TemperatureRequirements_EN13384(
                atLoadQty   = lq,
                tob         = tob,
                tig         = tig,
                tiob        = tiob,
                tsp         = tsp.some,
            )

    sealed trait PressureRequirements_13384:
        val atLoadQty: LoadQty
    object PressureRequirements_13384:
        // "5.2.1", Conduits de fumées fonctionnant sous pression négative
        case class UnderNegPress(
            atLoadQty: LoadQty,
            // min draught
            P_B_min_draught: Pressure,
            P_FV_min_draught: Pressure,
            P_H_min_draught: Pressure,
            P_HV_min_draught: Pressure,
            P_R_min_draught: Pressure,
            // max draught
            P_B_max_draught: Pressure,
            P_FV_max_draught: Pressure,
            P_H_max_draught: Pressure,
            P_HV_max_draught: Pressure,
            P_R_max_draught: Pressure,
            // others / results
            P_L: Pressure,
            P_W: Pressure,
            P_Wmax: Pressure,
            P_Z: Pressure,
            P_Zmax: Pressure,
            P_Ze: Pressure,
            P_Zemax: Pressure,
        ) extends PressureRequirements_13384

        // "5.2.2", Conduits de fumées fonctionnant sous pression positive
        case class UnderPosPress(
            atLoadQty: LoadQty,
            // min draught
            P_B_min_draught: Pressure,
            P_FV_min_draught: Pressure,
            P_H_min_draught: Pressure,
            P_HV_min_draught: Pressure,
            P_R_min_draught: Pressure,
            // max draught
            P_B_max_draught: Pressure,
            P_FV_max_draught: Pressure,
            P_H_max_draught: Pressure,
            P_HV_max_draught: Pressure,
            P_R_max_draught: Pressure,
            // others / results
            P_L: Pressure,
            P_WO: Pressure,
            P_WOmin: Pressure,
            P_ZO: Pressure,
            P_ZOmin: Pressure,
            P_ZOe: Pressure,
            P_ZOemin: Pressure,
            P_Zexcess: Option[Pressure] = None,  // not implemented
            P_ZVexcess: Option[Pressure] = None, // not implemented
        ) extends PressureRequirements_13384

    // "5.5.3", "Température des fumées"

    /** flue gas temperature of the appliance (in K) */
    type T_W = FlueGasTemperature.Type
    object FlueGasTemperature extends OTypedTempD[Kelvin]:
        def termDef = TermDef("T_W")
        def termDefDetails = TermDefDetails(
            "flue gas temperature of the appliance (in K)",
            "flue gas temperature of the appliance (in K)"
        )

    // "5.5.3.1",
    // "Température des fumées à la puissance utile nominale (T_WN)"

    /** flue gas temperature of the appliance at nominal heat output (in K) */
    type T_WN = FlueGasTemperatureAtNominalHeatOutput.Type
    object FlueGasTemperatureAtNominalHeatOutput extends OTypedTempD[Kelvin]:
        def termDef = TermDef("T_WN")
        def termDefDetails = TermDefDetails(
            "flue gas temperature of the appliance at nominal heat output (in K)",
            "flue gas temperature of the appliance at nominal heat output (in K)"
        )

    // "5.5.3.2",
    // "Température des fumées à la puissance utile la plus faible possible (T_Wmin)"

    /** flue gas temperature of the appliance at the lowest possible heat output (in K) */
    type T_Wmin = FlueGasTemperatureAtLowestPossibleHeatOutput.Type
    object FlueGasTemperatureAtLowestPossibleHeatOutput
        extends OTypedTempD[Kelvin]:
        def termDef = TermDef("T_Wmin")
        def termDefDetails = TermDefDetails(
            "flue gas temperature of the appliance at the lowest possible heat output (in K)",
            "flue gas temperature of the appliance at the lowest possible heat output (in K)"
        )

    // "5.7.1.2", "Température de l'air extérieur (T_L)"

    /** exterior air temperature (in K) */
    type T_L = ExteriorAirTemperature.Type
    object ExteriorAirTemperature extends OTypedTempD[Kelvin]:
        def termDef = TermDef("T_L")
        def termDefDetails = TermDefDetails(
            "exterior air temperature (in K)",
            "exterior air temperature (in K)",
        )


    opaque type T_L_override <: Option[Map[DraftCondition, T_L]] = Option[Map[DraftCondition, T_L]]
    object T_L_override:
        val none: T_L_override = None
        def forTKelvin(whenDraftMin: TKelvin, whenDraftMax: TKelvin): T_L_override = Map[DraftCondition, T_L](
            DraftCondition.DraftMinOrPositivePressureMax -> whenDraftMin,
            DraftCondition.DraftMaxOrPositivePressureMin -> whenDraftMax
        ).some
        def forTKelvin(whenDraftMinOrDraftMax: TKelvin): T_L_override = Map[DraftCondition, T_L](
            DraftCondition.DraftMinOrPositivePressureMax -> whenDraftMinOrDraftMax,
            DraftCondition.DraftMaxOrPositivePressureMin -> whenDraftMinOrDraftMax
        ).some
        def forTCelsius(whenDraftMin: TCelsius, whenDraftMax: TCelsius): T_L_override =
            forTKelvin(whenDraftMin = whenDraftMin, whenDraftMax = whenDraftMax)
        def forTCelsius(whenDraftMinOrDraftMax: TCelsius): T_L_override =
            forTKelvin(whenDraftMinOrDraftMax = whenDraftMinOrDraftMax)
        extension (tlo: T_L_override)
            def opaqueGetOrElse(alt: T_L_override): T_L_override = tlo match
                case Some(tlo) => tlo.some
                case None => alt

    // "5.7.1.3", "Température du l'air ambiant (T_u)"

    /** ambiant air temperature (in K) */
    type T_u = AmbiantAirTemperature.Type
    object AmbiantAirTemperature extends OTypedTempD[Kelvin]:
        def termDef = TermDef("T_u")
        def termDefDetails = TermDefDetails(
            "ambiant air temperature (in K)",
            "ambiant air temperature (in K)"
        )

    // "5.7.2", "Pression de l'air extérieur (p_L)"

    /** exterior air pressure (in Pa) */
    type p_L = ExteriorAirPressure.Type
    object ExteriorAirPressure extends OTypedQtyD[Pascal]:
        def termDef = TermDef("p_L")
        def termDefDetails = TermDefDetails(
            "exterior air pressure (in Pa)",
            "exterior air pressure (in Pa)"
        )

    // "5.8", "Détermination des températures"

    // "5.8.2", "Calcul du coefficient de refroidissement (K)"

    /** coefficient of cooling */
    type K = CoefficientOfCooling.Type
    object CoefficientOfCooling extends OTypedQtyD[1]:
        def termDef = TermDef("K")
        def termDefDetails = TermDefDetails(
            "coefficient of cooling",
            "coefficient of cooling"
        )

    /** coefficient of cooling for the connecting flue pipe */
    type K_V = CoefficientOfCoolingForConnectingFluePipe.Type
    object CoefficientOfCoolingForConnectingFluePipe
        extends OTypedQtyD[1]:
        def termDef = TermDef("K_V")
        def termDefDetails = TermDefDetails(
            "coefficient of cooling for the connecting flue pipe",
            "coefficient of cooling for the connecting flue pipe"
        )

    // "5.10.4", "Wind Velocity Pressure"
    type P_L = WindVelocityPressure.Type
    object WindVelocityPressure extends OTypedQtyD[Pascal]:
        override def valueFormat: String = "%.0f"
        def termDef = TermDef("P_L")
        def termDefDetails = TermDefDetails(
            I18N.en13384.terms.P_L,
            I18N.en13384.terms.P_L,
        )

    // "7.8", "Détermination des températures"

    /** mean temperature of the combustion air (in K) */
    type T_mB = CombustionAirMeanTemperature.Type
    object CombustionAirMeanTemperature extends OTypedTempD[Kelvin]:
        def termDef = TermDef("T_mB")
        def termDefDetails = TermDefDetails(
            "mean temperature of the combustion air (in K)",
            "mean temperature of the combustion air, averaged over the length of the air intake duct (in K)"
        )

    // "Annexe B", "Tableau B.6"

    // /** exterior air temperature under dry conditions (in K) */
    // type T_uo_dry_override = AmbiantAirTemperatureNational_Dry.Type
    // object AmbiantAirTemperatureNational_Dry extends OTypedOptTempD[Kelvin]:
    //     extension (T_uo_dry_override: T_uo_dry_override)
    //         def opaqueGetOrElse(default: T_uo_dry_override): T_uo_dry_override = 
    //             val ou = T_uo_dry_override.unwrap
    //             val od = default.unwrap
    //             (ou, od) match
    //                 case (su @ Some(_), _)    => su
    //                 case (None, so @ Some(_)) => so
    //                 case (None, None)         => None

    //     def termDef = TermDef("T_uo_dry_override")
    //     def termDefDetails = TermDefDetails(
    //         "ambiant air temperature under dry conditions (in K), based on national accepted data",
    //         "ambiant air temperature under dry conditions (in K), based on national accepted data",
    //     )

    // /** exterior air temperature under wet conditions (in K) */
    // type T_uo_wet_override = AmbiantAirTemperatureNational_Wet.Type
    // object AmbiantAirTemperatureNational_Wet extends OTypedOptTempD[Kelvin]:
    //     extension (T_uo_wet_override: T_uo_wet_override)
    //         def opaqueGetOrElse(default: T_uo_wet_override): T_uo_wet_override = 
    //             val ou = T_uo_wet_override.unwrap
    //             val od = default.unwrap
    //             (ou, od) match
    //                 case (su @ Some(_), _)    => su
    //                 case (None, so @ Some(_)) => so
    //                 case (None, None)         => None

    //     def termDef = TermDef("T_uo_wet_override")
    //     def termDefDetails = TermDefDetails(
    //         "ambiant air temperature under wet conditions (in K), based on national accepted data",
    //         "ambiant air temperature under wet conditions (in K), based on national accepted data",
    //     )

    
    case class TuTemperatures(
        boilerRoom: AmbiantAirTemperatureSet,
        heatedArea: AmbiantAirTemperatureSet,
        unheatedInside: AmbiantAirTemperatureSet,
        outsideOrExterior: AmbiantAirTemperatureSet,
        xtras: List[CustomArea],
    )

    object TuTemperatures:

        case class Defaults(
            boilerRoom: AmbiantAirTemperatureSet,
            heatedArea: AmbiantAirTemperatureSet,
            unheatedInside: AmbiantAirTemperatureSet,
            outsideOrExterior: AmbiantAirTemperatureSet
        )

    /** override type for air temperature at chimney outlet (in K) */
    opaque type T_uo_temperature_override = Option[AmbiantAirTemperatureSet]
    object T_uo_temperature_override:
        val none: T_uo_temperature_override = None
        def from(tuTemp: AmbiantAirTemperatureSet): T_uo_temperature_override = Some(tuTemp)
        
        extension (x: T_uo_temperature_override)
            def unwrap: Option[AmbiantAirTemperatureSet] = x
            def unwrap_T_uo: T_uo_temperature = T_uo_temperature.fromOverride(x.unwrap)
            def opaqueGetOrElse(default: T_uo_temperature): T_uo_temperature = 
                val d = default.unwrap
                (x, d) match
                    case (x @ Some(_), _)   => x
                    case (None, d @ Some(_)) => d
                    case (None, None)        => None

    /** type for air temperature at chimney outlet (in K) */
    opaque type T_uo_temperature = Option[AmbiantAirTemperatureSet]
    object T_uo_temperature:
        def from(tuTemp: AmbiantAirTemperatureSet): T_uo_temperature = Some(tuTemp)
        def fromOverride(tuoTempOverride: T_uo_temperature_override): T_uo_temperature = tuoTempOverride
        extension (x: T_uo_temperature)
            def unwrap: Option[AmbiantAirTemperatureSet] = x
        

    type UnheatedHeightInsideAndOutside =
        UnheatedHeightInsideAndOutside.Type

    object UnheatedHeightInsideAndOutside extends OTypedQtyD[Meter]:
        def termDef = TermDef("unheated-height-inside-and-outside")
        def termDefDetails = TermDefDetails(
            "hauteur des zones non chauffées intérieures et extérieures (voir EN13384-1:2015+A1:2019 / 5.7.1.3)",
            "hauteur des zones non chauffées intérieures et extérieures (in meters)",
        )
        def toLength(tuo_override: Type): QtyD[Meter] = tuo_override

    enum AirSpace:
        case DeadAirSpace
        case VentilatedAirSpace_SameDirectionAsFlueGas

    object AirSpace:
        given Show[AirSpace] = Show.show {
            case DeadAirSpace => 
                "Sans Lame d'Air Ventilee"
            case VentilatedAirSpace_SameDirectionAsFlueGas =>
                "Avec Lame d'Air Ventilee Dans le Sens de Ciruclation des Fumees"
        }

    /**
     * Pression positive = tirage assisté par ventilateur
     * Pression négative = tirage naturel
     *
     * Tirage Min = PressionNegativeMin = tirage naturel min
     * Tirage Max = PressionNegativeMax = tirage naturel max
     */
    enum DraftCondition:
        case DraftMinOrPositivePressureMax
        case DraftMaxOrPositivePressureMin
    object DraftCondition:
        
        // alias
        val draftMin = DraftMinOrPositivePressureMax
        val draftMax = DraftMaxOrPositivePressureMin
        
        // alias
        val posPressMax = DraftMinOrPositivePressureMax
        val posPressMin = DraftMaxOrPositivePressureMin

        object givens:
            given given_draftMin: DraftCondition = draftMin
            given given_draftMax: DraftCondition = draftMax

        type DraftMinOrPositivePressureMax =
            DraftMinOrPositivePressureMax.type
        type DraftMaxOrPositivePressureMin =
            DraftMaxOrPositivePressureMin.type

        def summon(using inst: DraftCondition): DraftCondition = inst

        given ShowUsingLocale[DraftCondition] = showUsingLocale:
            case DraftMinOrPositivePressureMax => I18N.draft_min
            case DraftMaxOrPositivePressureMin => I18N.draft_max

    enum FlueGasCondition:
        /** Dry flue gases : non condensing gases. This is the case for classic wood burning appliance (wood log or pellets stoves) */
        case Dry_NonCondensing

        /** Wet flue gases : consensing gases permitted (like in boiler) */
        case Wet_Condensing

    object FlueGasCondition:
        type Dry_NonCondensing = Dry_NonCondensing.type
        type Wet_Condensing = Wet_Condensing.type
        given ShowUsingLocale[FlueGasCondition] = showUsingLocale:
            case FlueGasCondition.Wet_Condensing     => I18N.en13384.flue_gas_conditions_wet
            case FlueGasCondition.Dry_NonCondensing  => I18N.en13384.flue_gas_conditions_dry


    // Annexe B / Tableau B.1"

    enum FuelType:
        case WoodLog30pHumidity
        case Pellets

    object FuelType:
        given Show[FuelType] = cats.derived.semiauto.show

    