/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en13384

import scala.annotation.targetName

import algebra.instances.all.given

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en13384.typedefs.*

import afpma.firecalc.i18n.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}


object std:
    
    case class Inputs(
        pipes: Pipes_EN13384,
        nationalAcceptedData: NationalAcceptedData,
        fuelType: FuelType = FuelType.WoodLog30pHumidity,
        localConditions: LocalConditions,
        flueGasCondition: FlueGasCondition,
    )

    /**
      * Caractérisation de l'appareil de combustion utilisé
      *
      * @param flue_gas_mass_flow débit massique des fumées
      * @param flue_gas_temperature température des fumées
      * @param flue_gas_draft_min_pdiff_max tirage minimal nécessaire ou la pression différentielle maximale de l'appareil à combustion, pour les conduits de fumée fonctionnant sous pression négative
      * @param flue_gas_draft_max_pdiff_min tirage minimal nécessaire ou la pression différentielle maximale de l'appareil à combustion, pour les conduits de fumée fonctionnant sous pression négative
      */
    case class HeatingAppliance(
        reference         : LocalizedString                          ,
        type_of_appliance : TypeOfAppliance                          ,
        efficiency        : HeatingAppliance.Efficiency              ,
        fluegas           : HeatingAppliance.FlueGas                 ,
        powers            : HeatingAppliance.Powers                  ,
        temperatures      : HeatingAppliance.Temperatures            ,
        massFlows         : HeatingAppliance.MassFlows               = HeatingAppliance.MassFlows.undefined,
        pressures         : HeatingAppliance.Pressures               ,
        volumeFlows       : Option[HeatingAppliance.VolumeFlows]     = None
    )

    object HeatingAppliance:

        type CtxOp4_EFPoM[X] = 
            HeatingAppliance.Efficiency ?=>
            HeatingAppliance.FlueGas ?=>
            HeatingAppliance.Powers ?=>
            HeatingAppliance.MassFlows ?=> X

        type CtxOp5_EFPoTM[X] = 
            HeatingAppliance.Efficiency ?=>
            HeatingAppliance.FlueGas ?=>
            HeatingAppliance.Powers ?=>
            HeatingAppliance.Temperatures ?=>
            HeatingAppliance.MassFlows ?=> X
        
        type CtxOp6_EFPoTMPr[X] = 
            HeatingAppliance.Efficiency ?=>
            HeatingAppliance.FlueGas ?=>
            HeatingAppliance.Powers ?=>
            HeatingAppliance.Temperatures ?=>
            HeatingAppliance.MassFlows ?=>
            HeatingAppliance.Pressures ?=> X

        def summon(using ha: HeatingAppliance): HeatingAppliance = ha

        // given HeatingAppliance -> given HeatingAppliance.*
        given ha2ha_efficiency: (ha: HeatingAppliance)    => HeatingAppliance.Efficiency     = ha.efficiency
        given ha2ha_fluegas: (ha: HeatingAppliance)       => HeatingAppliance.FlueGas        = ha.fluegas
        given ha2ha_powers: (ha: HeatingAppliance)        => HeatingAppliance.Powers         = ha.powers
        given ha2ha_temperatures: (ha: HeatingAppliance)  => HeatingAppliance.Temperatures   = ha.temperatures
        given ha2ha_massFlows: (ha: HeatingAppliance)     => HeatingAppliance.MassFlows      = ha.massFlows
        given ha2ha_pressures: (ha: HeatingAppliance)     => HeatingAppliance.Pressures      = ha.pressures
        
        case class Efficiency(
            perc_nominal: Percentage,
            perc_lowest: Option[Percentage]
        )
        object Efficiency:
            def summon(using ev: HeatingAppliance.Efficiency): Efficiency = ev

        case class FlueGas(
            co2_dry_perc_nominal: Percentage            ,
            co2_dry_perc_reduced : Option[Percentage]    ,
            h2o_perc_nominal    : Option[Percentage]    = None,
            h2o_perc_reduced     : Option[Percentage]    = None,
        )

        object FlueGas:
            def summon(using ev: HeatingAppliance.FlueGas): FlueGas = ev
        case class Temperatures(
            flue_gas_temp_nominal: TCelsius,
            flue_gas_temp_reduced: Option[TCelsius]
        )
        object Temperatures:
            def summon(using ev: HeatingAppliance.Temperatures): Temperatures = ev
        case class Powers(
            heat_output_nominal: Power,
            heat_output_reduced: Option[Power],
        )
        object Powers:
            def summon(using ev: HeatingAppliance.Powers): Powers = ev

        case class MassFlows(
            flue_gas_mass_flow_nominal: Option[MassFlow],
            flue_gas_mass_flow_reduced: Option[MassFlow],
            combustion_air_mass_flow_nominal: Option[MassFlow],
            combustion_air_mass_flow_reduced: Option[MassFlow],
        )
        object MassFlows:
            val undefined = MassFlows(None, None, None, None)
            def summon(using ev: HeatingAppliance.MassFlows): MassFlows = ev

        case class Pressures(
            underPressure: UnderPressure,
            flue_gas_draft_min: Option[Pressure],
            flue_gas_draft_max: Option[Pressure],
            flue_gas_pdiff_min: Option[Pressure],
            flue_gas_pdiff_max: Option[Pressure],
        )
        object Pressures:
            def summon(using ev: HeatingAppliance.Pressures): Pressures = ev

        case class VolumeFlows(
            flue_gas_volume_flow_nominal: VolumeFlow,
            flue_gas_volume_flow_reduced: Option[VolumeFlow],
            combustion_air_volume_flow_nominal: VolumeFlow,
            combustion_air_volume_flow_reduced: Option[VolumeFlow],
        )

    /**
     *
     * @param humidity humidité du bois (% de la masse de bois sec)
     * @param composition composition du bois
     */
    case class Wood(
        humidity: Percentage, // % de la masse de bois sec
        composition: Wood.Composition
    )
    
     /**
     * Composition élémentaire du bois
     * 
     * @param xC proportion massique en C (sur masse sèche)
     * @param xH proportion massique en H (sur masse sèche)
     * @param xO proportion massique en O (sur masse sèche)
     * @param xN proportion massique en N (sur masse sèche)
     * @param xOther proportion massique des autrees éléments (cendres, etc...)
     */
    object Wood:
        case class Composition(
            xC: Percentage,
            xH: Percentage,
            xO: Percentage,
            xN: Percentage,
            xOther: Percentage,
        )

        /**
          * Bois selon ONORM_B_8303
          *
          * @param humidity % humidité sur bois sec
          * @return
          */
        def from_ONORM_B_8303(humidity: Percentage): Wood = 
            Wood(
                humidity, 
                Composition(
                    xC      = 49.72            .percent,
                    xH      = 5.31             .percent,
                    xN      = 0.22             .percent,
                    xO      = 44.34            .percent,
                    xOther  = (0.01 + 0.36)    .percent,
                )
            )

        given Conversion[Wood, afpma.firecalc.engine.wood_combustion.Wood] = 
            w =>
                afpma.firecalc.engine.wood_combustion.Wood(
                    atomic_composition = Map(
                        "C"      -> w.composition.xC,
                        "H"      -> w.composition.xH,
                        "O"      -> w.composition.xO,
                        "N"      -> w.composition.xN,
                        "Others" -> w.composition.xOther,
                    ),
                    humidity = w.humidity,
                )

    end Wood

    case class NationalAcceptedData(
        val T_uo_override: T_uo_temperature_override,
        val T_L_override: T_L_override
    )

    object NationalAcceptedData:
        val noOverride = NationalAcceptedData(
            T_uo_override       = T_uo_temperature_override.none,
            T_L_override        = T_L_override.none
        )
        def overrideWith(tuo_temperatures: AmbiantAirTemperatureSet) = NationalAcceptedData(
            T_uo_override       = T_uo_temperature_override.from(tuo_temperatures),
            T_L_override        = T_L_override.none
        )
        def overrideWith(tl: T_L_override) = NationalAcceptedData(
            T_uo_override       = T_uo_temperature_override.none,
            T_L_override        = tl
        )
        def overrideWith(tuo_temperatures: AmbiantAirTemperatureSet, tl: T_L_override) = NationalAcceptedData(
            T_uo_override       = T_uo_temperature_override.from(tuo_temperatures),
            T_L_override        = tl
        )

    /**
      * See Annex A of EN13384-1:2015+A1:2019
      */
    object ThermalResistance:

        import afpma.firecalc.engine.models.gtypedefs.{ThermalConductivity, D_h}

        /**
          * Returns thermal resistance of a single layer 
          *
          * @param dhi hydraulic diameter of interior layer
          * @param e thickness of layer
          * @param λ thermal conductivity of layer
          * @param form form of layer
          * @return thermal resistance of layer
          */
        @targetName("forSingleLayer_usingThickness")
        @deprecated def forSingleLayer(
            dhi: D_h,
            e: QtyD[Meter],
            λ: ThermalConductivity,
            form: CoefficientOfForm,
            
        ): SquareMeterKelvinPerWatt = 
            val dho: D_h = dhi.toUnit[Meter] + 2.0 * e
            forSingleLayer(dhi, dho, λ, form)

        /**
         * Returns thermal resistance of a single layer 
         *
         * @param dhi hydraulic diameter of interior layer
         * @param e thickness of layer
         * @param λ thermal conductivity of layer
         * @param form form of layer
         * @return thermal resistance of layer
         */
        @targetName("forSingleLayer_usingHydraulicDiameters")
        @deprecated def forSingleLayer(
            dhi: D_h,
            dho: D_h,
            lambda: ThermalConductivity,
            form: CoefficientOfForm,
        ): SquareMeterKelvinPerWatt = 
            SquareMeterKelvinPerWatt(
                form.unwrap * dhi.to_m.value / (2.0 * lambda.toUnit[Watt / (Meter * Kelvin)].value) * math.log((dho / dhi).value)
            )

        // TODO: formula should be delegated to some specific implementation, not hard-coded here.
        // @deprecated def forLayers(layers: Layers): SquareMeterKelvinPerWatt = 
        //     val y = layers.form.y
        //     import layers.dh
        //     val rs = 
        //         for 
        //             x <- layers.xs
        //             r = (
        //                     dh.toUnit[Meter].value / (2.0 * (x.λ.toUnit[Watt / (Meter * Kelvin)]).value) 
        //                     * 
        //                     math.log((x.dho / x.dhi).value)
        //                 )
        //         yield
        //             r
        //     SquareMeterKelvinPerWatt(y * rs.sum)

        // case class Layers(
        //     dh: D_h,
        //     xs: List[Layer],
        //     form: CoefficientOfForm
        // )

        // object Layers:
        //     def make(dh: D_h, layers: List[(Length, ThermalConductivity)], form: CoefficientOfForm): Layers = 
        //         var dhi: Length = dh
        //         val xs = 
        //             for 
        //                 (thickness, lambda) <- layers
        //             yield
        //                 val dho: Length = dhi + 2.0 * thickness
        //                 val layer = Layer(dhi = dhi, dho = dho, λ = lambda)
        //                 dhi = dho
        //                 layer
        //         Layers(dh, xs, form)

        //     extension (layers: Layers)
        //         def Λinverse: SquareMeterKelvinPerWatt = 
        //             ThermalResistance.forLayers(layers)

        // case class Layer(
        //     dhi: D_h,
        //     dho: D_h,
        //     λ: ThermalConductivity,
        // )

        // object Layer:
        //     import afpma.firecalc.dto.all.*

        //     def mkFromThicknessAndLambda(dhi: D_h, e: Length, λ: ThermalConductivity): Layer = 
        //         Layer(
        //             dhi = dhi, 
        //             dho = dhi + 2.0 * e,
        //             λ   = λ
        //         )

        export CoefficientOfForm.unwrap

        opaque type CoefficientOfForm = Double
        object CoefficientOfForm:
            def wrap(d: Double): CoefficientOfForm = d
            extension (c: CoefficientOfForm)
                def unwrap: Double = c

    end ThermalResistance

    case class ReferenceTemperatures(
        // ambiant_air_temperatures: TuTemperatures,
        flueGasCondition: FlueGasCondition,
        tuo: T_uo_temperature,
        tl: T_L_override,
    )

    
end std
