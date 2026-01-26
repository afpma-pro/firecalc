/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import cats.syntax.all.*

import afpma.firecalc.engine.impl.en13384.FlowOnlyIncrementalPipeDefModule_13384
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application.ComputeAt
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas
import afpma.firecalc.engine.impl.en13384.EN13384_WithFlowOnlyAirIntake_Application
import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Application
import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Application
import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Application.LabConditions
import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Formulas
import afpma.firecalc.engine.impl.en15544.mce.EN15544_MCE_Application
import afpma.firecalc.engine.impl.en15544.mce.EN15544_MCE_Formulas
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_Alg
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.std.Wood
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544
import afpma.firecalc.engine.models.en15544.std
import afpma.firecalc.engine.models.en15544.std.Design
import afpma.firecalc.engine.models.en15544.std.Inputs_15544_Alg
import afpma.firecalc.engine.models.en15544.std.Inputs_15544_Strict
import afpma.firecalc.engine.models.en15544.std.Inputs_15544_MCE

import afpma.firecalc.engine.models.gtypedefs.KindOfWood
import afpma.firecalc.engine.utils.*
import afpma.firecalc.engine.wood_combustion.*
import afpma.firecalc.engine.wood_combustion.bs845.BS845_Impl

import afpma.firecalc.i18n.LocalizedAlg

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import io.taig.babel.Language
import io.taig.babel.Languages
import afpma.firecalc.dto.FireCalcYAML
import cats.data.ValidatedNel
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType
import afpma.firecalc.engine.standard.InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType
import afpma.firecalc.engine.standard.VNelMcalcErr
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_WithThermalAirIntake
import afpma.firecalc.engine.impl.en13384.EN13384_WithThermalAirIntake_Application
import afpma.firecalc.engine.alg.en13384.HasTypeMembers_13384_Alg
import afpma.firecalc.engine.impl.en13384.HasTypeMembers_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.impl.en15544.mce.HasTypeMembers_15544_MCE
import afpma.firecalc.engine.impl.en15544.strict.HasTypeMembers_15544_Strict
import afpma.firecalc.engine.impl.en13384.HasTypeMembers_13384_WithThermalAirIntake
import afpma.firecalc.engine.alg.en15544.HasTypeMembers_15544_Alg

object v0_2024_10:

    trait SimpleStoveProjectDescrFr_15544_Strict_Alg 
        extends v0_2024_10.SimpleStoveProjectDescrFr_15544_Alg
        with v0_2024_10.StoveProjectDescr_15544_Strict_Alg

    trait SimpleStoveProjectDescrFr_15544_MCE_Alg 
        extends v0_2024_10.StoveProjectDescr_15544_MCE_Alg
        with v0_2024_10.SimpleStoveProjectDescrFr_Alg

    trait SimpleStoveProjectDescrFr_15544_Labo_Alg 
        extends v0_2024_10.StoveProjectDescr_15544_Labo_Alg 
        with v0_2024_10.SimpleStoveProjectDescrFr_Alg

    trait SimpleStoveProjectDescrFr_Alg extends StoveProjectDescr_Alg:
        val exercice_name: String
        override val project = ProjectDescr.empty.copy(reference = exercice_name)
        val language: Language = Languages.Fr

    trait SimpleStoveProjectDescrFr_15544_Alg
        extends v0_2024_10.StoveProjectDescr_15544_Alg
        with SimpleStoveProjectDescrFr_Alg

    trait StoveProjectDescr_Alg extends LocalizedAlg:
        def project: ProjectDescr = ProjectDescr.empty
        def typeOfAppliance: TypeOfAppliance
        def localRegulations = LocalRegulations.findBy(
            c = project.country,
            t = typeOfAppliance
        )

    // Flue Pipe

    sealed trait FluePipe_Alg:
        type FluePipeType <: FluePipe_15544 | FluePipe_13384
        def fluePipe: ValidatedNel[IncrementalValidation_Error, FluePipeType]

    trait FluePipe_15544_Alg extends FluePipe_Alg:
        type FluePipeType = FluePipe_15544

    trait FluePipe_13384_Alg extends FluePipe_Alg:
        type FluePipeType = FluePipe_13384

    // Firebox

    trait Firebox_15544_Alg:
        type CombustionAirPipe
        type FireboxPipe

        def combustionAirPipe: ValidatedNel[IncrementalValidation_Error, CombustionAirPipe]
        def fireboxPipe: ValidatedNel[IncrementalValidation_Error, FireboxPipe]

    trait Firebox_15544_Strict_Alg extends Firebox_15544_Alg:
        type CombustionAirPipe  = CombustionAirPipe_Module_15544.FullDescr
        type FireboxPipe        = FireboxPipe_Module_15544.FullDescr

    trait Firebox_15544_MCE_Alg extends Firebox_15544_Alg:
        type CombustionAirPipe = CombustionAirPipe_Module_13384.FullDescr
        type FireboxPipe       = FireboxPipe_Module_13384.FullDescr

    // "One Off" Firebox

    trait Firebox_15544_OneOff_Alg:
        self: Firebox_15544_Alg =>
        
        def firebox: en15544.firebox.From_CalculPdM_V_0_2_32
    
        lazy val design = Design(
            firebox = self.firebox
            // firebox = 
            //     Firebox.OneOff.Minimal(
            //         pn_reduced = HeatOutputReduced.DefinedAsDefault,
            //         heightOfLowestOpening = foyer.heightOfLowestOpening,
            //         dimensions = FireboxDimensions(
            //             base = FireboxDimensions.Base.Squared(
            //                 width = foyer.h12_lrun_en15544_strictargeurDuFoyer,
            //                 depth = foyer.h11_profondeurDuFoyer,
            //             ),
            //             height = foyer.h13_hauteurDuFoyer
            //         ),
            //         glass_area = foyer.surfaceVitre
            // )
        )

    trait Firebox_15544_Strict_OneOff_Alg
        extends Firebox_15544_OneOff_Alg
        with Firebox_15544_Strict_Alg:

        import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.*

        def combustionAirPipe =
            firebox match
                case f: TraditionalFirebox    => 
                    TraditionalFirebox_Module.toCombustionAirPipe_15544(f)
                case f: AFPMA_PRSE              => 
                    AFPMA_PRSE_Module.toCombustionAirPipe_15544(f)
                case f: EcoLabeled => 
                    EcoLabeled_Module.toCombustionAirPipe_15544(f)

        def fireboxPipe =
            firebox match
                case f: TraditionalFirebox    => 
                    TraditionalFirebox_Module.toFireboxPipe_15544(f)
                case f: AFPMA_PRSE              => 
                    AFPMA_PRSE_Module.toFireboxPipe_15544(f)
                case f: EcoLabeled => 
                    EcoLabeled_Module.toFireboxPipe_15544(f)

    trait Firebox_15544_MCE_OneOff_Alg
        extends Firebox_15544_OneOff_Alg
        with Firebox_15544_MCE_Alg:

        import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.*

        def combustionAirPipe =
            firebox match
                case f: TraditionalFirebox    => 
                    TraditionalFirebox_Module.toCombustionAirPipe_13384(f)
                case f: AFPMA_PRSE              => 
                    AFPMA_PRSE_Module.toCombustionAirPipe_13384(f)
                case f: EcoLabeled => 
                    EcoLabeled_Module.toCombustionAirPipe_13384(f)

        def fireboxPipe =
            firebox match
                case f: TraditionalFirebox    => 
                    TraditionalFirebox_Module.toFireboxPipe_13384(f)
                case f: AFPMA_PRSE              => 
                    AFPMA_PRSE_Module.toFireboxPipe_13384(f)
                case f: EcoLabeled => 
                    EcoLabeled_Module.toFireboxPipe_13384(f)

    // Stove Project Description

    trait StoveProjectDescr_13384_Alg
        extends StoveProjectDescr_Alg
        with HasTypeMembers_13384_Alg:
        self =>

        def fuelType: FuelType
        
        def flueGasCondition: FlueGasCondition

        def localConditions: LocalConditions

        def en13384NationalAcceptedData: NationalAcceptedData = 
            NationalAcceptedData.noOverride

        def airIntakePipe: ValidatedNel[IncrementalValidation_Error, AirIntakePipe_Module.PipeCanBe]

        def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]

        def chimneyPipe: ValidatedNel[IncrementalValidation_Error, ChimneyPipe]

        def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384]

        def heatingAppliance: ValidatedNel[MCalc_Error, HeatingAppliance]

    trait StoveProjectDescr_13384_WithFlowOnlyAirIntake_Alg
        extends StoveProjectDescr_13384_Alg
        with HasTypeMembers_13384_WithFlowOnlyAirIntake:
        self =>

        def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384] = 
            (
                airIntakePipe,
                connectorPipe,
                chimneyPipe,
            ).mapN: (_airIntake, _connector, _chimney) =>
                new Pipes_13384_WithFlowOnlyAirIntake:
                    override val airIntake                      = _airIntake
                    override val connector                      = _connector
                    override val chimney                        = _chimney

        def inputsVNel: VNelMcalcErr[Inputs_13384] =
            // ensure type of appliance matches with fuel type
            val checkApplianceAndFuel: VNelMcalcErr[Unit] =
                (typeOfAppliance, fuelType) match
                    case (TypeOfAppliance.Pellets, FuelType.Pellets) => 
                        ().validNel // OK
                    case (TypeOfAppliance.Pellets, ft @ FuelType.WoodLog30pHumidity) => 
                        InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType.invalidNel
                    case (TypeOfAppliance.WoodLogs, FuelType.Pellets) =>
                        InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType.invalidNel
                    case (TypeOfAppliance.WoodLogs, _ ) =>
                        ().validNel // OK
            (
                checkApplianceAndFuel,
                en13384_pipesVNel
            ).mapN: (_, pipes) =>
                Inputs_13384_WithFlowOnlyAirIntake(
                    pipes,
                    en13384NationalAcceptedData,
                    fuelType,
                    localConditions,
                    flueGasCondition
                )

        lazy val en13384_appl: VNelMcalcErr[EN13384_WithFlowOnlyAirIntake_Application] = inputsVNel.map: i =>
            val f = new EN13384_1_A1_2019_Formulas
            EN13384_WithFlowOnlyAirIntake_Application.make(f, i)

    trait StoveProjectDescr_13384_WithThermalAirIntake_Alg
        extends StoveProjectDescr_13384_Alg
        with HasTypeMembers_13384_WithThermalAirIntake:
        self =>

        def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384] = 
            (
                airIntakePipe,
                connectorPipe,
                chimneyPipe,
            ).mapN: (_airIntake, _connector, _chimney) =>
                new Pipes_13384_WithThermalAirIntake:
                    override val airIntake                      = _airIntake
                    override val connector                      = _connector
                    override val chimney                        = _chimney

        def en13384_inputsVNel: VNelMcalcErr[Inputs_13384] =
            // ensure type of appliance matches with fuel type
            val checkApplianceAndFuel: VNelMcalcErr[Unit] =
                (typeOfAppliance, fuelType) match
                    case (TypeOfAppliance.Pellets, FuelType.Pellets) => 
                        ().validNel // OK
                    case (TypeOfAppliance.Pellets, ft @ FuelType.WoodLog30pHumidity) => 
                        InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType.invalidNel
                    case (TypeOfAppliance.WoodLogs, FuelType.Pellets) =>
                        InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType.invalidNel
                    case (TypeOfAppliance.WoodLogs, _ ) =>
                        ().validNel // OK
            (
                checkApplianceAndFuel,
                en13384_pipesVNel
            ).mapN: (_, pipes) =>
                Inputs_13384_WithThermalAirIntake(
                    pipes,
                    en13384NationalAcceptedData,
                    fuelType,
                    localConditions,
                    flueGasCondition
                )

        lazy val en13384_appl: VNelMcalcErr[EN13384_WithThermalAirIntake_Application] = en13384_inputsVNel.map: i =>
            val f = new EN13384_1_A1_2019_Formulas
            EN13384_WithThermalAirIntake_Application.make(f, i)
                

    trait StoveProjectDescr_15544_Alg 
        extends StoveProjectDescr_13384_Alg
        with HasTypeMembers_15544_Alg
        with Firebox_15544_Alg
        with FluePipe_Alg:
        self =>
        
        type EN15544_Alg <: EN15544_V_2023_Common_Application {
            type AirIntakePipe_Module_T     = self.AirIntakePipe_Module_T
            type CombustionAirPipe_Module_T = self.CombustionAirPipe_Module_T
            type FireboxPipe_Module_T       = self.FireboxPipe_Module_T
            type FluePipe_Module_T          = self.FluePipe_Module_T
        }

        val kindOfWood: KindOfWood
        val typeOfAppliance = TypeOfAppliance.WoodLogs

        override def fuelType: FuelType = 
            en15544_Alg
            .toOption.map(_.en13384_inputs_fuelType)
            .getOrElse(throw new IllegalStateException("fuelType should be set"))
        
        override def flueGasCondition: FlueGasCondition =
            en15544_Alg
            .toOption.map(_.en13384_inputs_flueGasCondition)
            .getOrElse(throw new IllegalStateException("flueGasCondition should be set"))

        def localConditions: LocalConditions

        def stoveParams: StoveParams
        
        def en15544_pipesVNel: VNelMcalcErr[Pipes_15544]

        def en15544_inputsVNel: VNelMcalcErr[Inputs_15544]

        def design: Design

        lazy val en15544_Alg: VNelMcalcErr[EN15544_Alg]

        override lazy val heatingAppliance: VNelMcalcErr[HeatingAppliance] = 
            en15544_Alg.andThen: en15544_Alg =>
                (
                    en15544_Alg.en13384_heatingAppliance_pressures,
                    en15544_Alg.en13384_heatingAppliance_temperatures,
                    en15544_Alg.en13384_heatingAppliance_efficiency,
                    en15544_Alg.en13384_heatingAppliance_powers,
                )
                    .mapN: (hap, hat, hae, hapowers) =>
                        HeatingAppliance(
                            design.firebox.reference,
                            design.firebox.type_of_appliance,
                            hae,
                            en15544_Alg.en13384_heatingAppliance_fluegas,
                            hapowers,
                            hat,
                            en15544_Alg.en13384_heatingAppliance_massFlows,
                            hap,
                        )

    trait StoveProjectDescr_15544_Strict_Alg 
        extends StoveProjectDescr_15544_Alg
        with HasTypeMembers_15544_Strict
        with Firebox_15544_Strict_Alg
        with FluePipe_15544_Alg:
        self =>

        val kindOfWood = KindOfWood.HardWood

        override def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384] = 
            (
                airIntakePipe,
                connectorPipe,
                chimneyPipe,
            ).mapN: (_airIntake, _connector, _chimney) =>
                new Pipes_13384_WithFlowOnlyAirIntake:
                    override val airIntake                      = _airIntake
                    override val connector                      = _connector
                    override val chimney                        = _chimney

        override def en15544_inputsVNel: ValidatedNel[MCalc_Error, std.Inputs_15544_Strict] = 
            en15544_pipesVNel.map: pipes =>
                std.Inputs_15544_Strict(
                    localConditions,
                    en13384NationalAcceptedData,
                    stoveParams,
                    design,
                    pipes,
                )

        override lazy val en15544_pipesVNel = 
        (
            airIntakePipe,
            combustionAirPipe,
            fireboxPipe,
            fluePipe,
            connectorPipe,
            chimneyPipe
        ).mapN { (condAir, combChInt, combCh, flue, connector, chimney) =>
            Pipes_15544_Strict(
                condAir,
                combChInt,
                combCh,
                flue,
                connector,
                chimney,
            )
        }

        override type EN15544_Alg = EN15544_Strict_Application

        override lazy val en15544_Alg: ValidatedNel[MCalc_Error, EN15544_Strict_Application] = en15544_inputsVNel.map: i =>
            EN15544_Strict_Application.make(EN15544_Strict_Formulas.make)(i)

    trait StoveProjectDescr_15544_MCE_Alg 
        extends StoveProjectDescr_15544_Alg
        with HasTypeMembers_15544_MCE
        with Firebox_15544_MCE_Alg
        with FluePipe_13384_Alg:
        self =>

        val wComb: WoodCombustionAlg 
        import wComb.*

        def combustion_duration: Duration
        
        def combustion_lambda_nominal: Double
        def combustion_lambda_lowest: Option[Double]
        
        def combustion_lambda(lq: LoadQty) = lq match
            case LoadQty.Nominal => combustion_lambda_nominal.some
            case LoadQty.Reduced  => combustion_lambda_lowest
        
        def exterior_air: afpma.firecalc.engine.wood_combustion.ExteriorAir

        def wood: Wood
        def computeWoodCalorificValueUsingComposition: "Yes" | "No"

        private def _fluegas_o2_wet_and_o2_dry_at(lq: LoadQty): Option[(Percentage, Percentage)] =
            combustion_lambda(lq).map: lambda =>
                val ci = wood
                    .mixWith(exterior_air, lambda)
                    .burnLoad(afpma.firecalc.engine.wood_combustion.Wood.HumidMass(stoveParams.mB.get))
                (ci.output_perfect_percbyvol_humid("O2"), ci.output_perfect_percbyvol_dry("O2"))

        lazy val (fluegas_o2_wet_nominal, fluegas_o2_dry_nominal) = _fluegas_o2_wet_and_o2_dry_at(LoadQty.Nominal).get
        lazy val (fluegas_o2_wet_lowest, fluegas_o2_dry_lowest) = _fluegas_o2_wet_and_o2_dry_at(LoadQty.Reduced).fold((None, None))((x, y) => (x.some, y.some))

        def fluegas_co2_wet_nominal: Percentage = wood.co2_wet_from_o2_wet(fluegas_o2_wet_nominal)
        def fluegas_co2_dry_nominal: Percentage = wood.co2_dry_from_o2_dry(fluegas_o2_dry_nominal)

        def fluegas_co2_wet_lowest: Option[Percentage] = fluegas_o2_wet_lowest.map(wood.co2_wet_from_o2_wet)
        def fluegas_co2_dry_lowest: Option[Percentage] = fluegas_o2_dry_lowest.map(wood.co2_dry_from_o2_dry)

        def fluegas_h2o_perc_vol_nominal: Option[Percentage]
        def fluegas_h2o_perc_vol_lowest: Option[Percentage]

        def massFlows_override: HeatingAppliance.MassFlows = HeatingAppliance.MassFlows.undefined

        override def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384] = 
            (
                airIntakePipe,
                connectorPipe,
                chimneyPipe,
            ).mapN: (_airIntake, _connector, _chimney) =>
                new Pipes_13384_WithThermalAirIntake:
                    override val airIntake                      = _airIntake
                    override val connector                      = _connector
                    override val chimney                        = _chimney
        
        override lazy val en15544_pipesVNel = 
            (
                airIntakePipe,
                combustionAirPipe,
                fireboxPipe,
                fluePipe,
                connectorPipe,
                chimneyPipe
            ).mapN { (airIntake, combAir, fbox, flue, connector, chimney) =>
                Pipes_15544_MCE(
                    airIntake,
                    combAir,
                    fbox,
                    flue,
                    connector,
                    chimney,
                )
            }

        override def en15544_inputsVNel: ValidatedNel[MCalc_Error, std.Inputs_15544_MCE] = 
            en15544_pipesVNel.map: pipes =>
                std.Inputs_15544_MCE(
                    localConditions,
                    en13384NationalAcceptedData,
                    stoveParams,
                    design,
                    pipes,
                    wood,
                    kindOfWood,
                    computeWoodCalorificValueUsingComposition,
                    combustion_duration,
                    fluegas_co2_dry_nominal,
                    fluegas_co2_dry_lowest,
                    fluegas_h2o_perc_vol_nominal,
                    fluegas_h2o_perc_vol_lowest,
                    massFlows_override,
                    exterior_air.relative_humidity,
                )
        
        override type EN15544_Alg = EN15544_MCE_Application

        protected lazy val net_calorific_value_of_dry_wood: HeatCapacity = 
            computeWoodCalorificValueUsingComposition match
                case "Yes" => wood.lower_calorific_value_dry
                case "No"  =>     
                    // Source: Mesure des caractéristiques des combustibles bois « Evaluation et proposition de méthodes d’analyse de combustible » ADEME Critt Bois – Fibois – CTBA JUIN 2001 Ademe 2021
                    // https://cibe.fr/wp-content/uploads/2017/02/21-Mesures-PCI-bois-combustible-CRITT-bois-FIBOIS-CTBA.pdf
                    kindOfWood match
                        case KindOfWood.HardWood => 5.070.kWh_per_kg 
                        case KindOfWood.SoftWood => 5.330.kWh_per_kg

        private lazy val pci: PCI_Conversion_Alg = PCI_Conversion.cibe_fr

        protected lazy val net_calorific_value_of_wet_wood = 
            pci.PCI_sur_brut(net_calorific_value_of_dry_wood, wood.humidity)

        // TODO: rename to en15544_appl
        override lazy val en15544_Alg: ValidatedNel[MCalc_Error, EN15544_MCE_Application] = en15544_inputsVNel.map: i =>
            val bs845 = new BS845_Impl {}
            val en15544_mce_formulas = EN15544_MCE_Formulas.make(
                net_calorific_value_of_wet_wood = net_calorific_value_of_wet_wood,
                net_calorific_value_of_dry_wood = net_calorific_value_of_dry_wood
            )
            val wComb = new WoodCombustionImpl
            EN15544_MCE_Application.make(en15544_mce_formulas, bs845, wComb)(i)

    trait StoveProjectDescr_15544_Labo_Alg
        extends StoveProjectDescr_15544_MCE_Alg with LabConditions:
        labcond =>

        val kindOfWood = KindOfWood.HardWood

        override lazy val en15544_Alg: ValidatedNel[MCalc_Error, EN15544_Labo_Application] = en15544_inputsVNel.map: i =>
            val bs845 = new BS845_Impl {}
            val f = new EN15544_Labo_Formulas(
                net_calorific_value_of_wet_wood = net_calorific_value_of_wet_wood,
                net_calorific_value_of_dry_wood = net_calorific_value_of_dry_wood
            )(labcond)
            val wComb = new WoodCombustionImpl
            EN15544_Labo_Application.make(f, bs845, wComb, labcond)(i)

    object StoveProjectDescr:
        
        def makeFor_EN15544_Strict(fc: FireCalcYAML): StoveProjectDescr_15544_Strict_Alg = 
            val loader = new FireCalcYAML_Loader(fc)
            loader.stoveProjectDescr_EN15544_Strict
            