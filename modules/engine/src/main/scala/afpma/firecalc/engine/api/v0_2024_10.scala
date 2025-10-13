/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import cats.syntax.all.*

import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application.ComputeAt
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas
import afpma.firecalc.engine.impl.en13384.EN13384_Strict_Application
import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Application
import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Application
import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Application.LabConditions
import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Formulas
import afpma.firecalc.engine.impl.en15544.mce.EN15544_MCE_Application
import afpma.firecalc.engine.impl.en15544.mce.EN15544_MCE_Formulas
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.Inputs
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.std.Wood
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import afpma.firecalc.engine.models.en15544
import afpma.firecalc.engine.models.en15544.std
import afpma.firecalc.engine.models.en15544.std.Design
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

object v0_2024_10:

    trait SimpleStoveProjectDescrFr_EN15544_Strict_Alg 
        extends v0_2024_10.SimpleStoveProjectDescrFr_EN15544_Alg
        with v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg

    trait SimpleStoveProjectDescrFr_EN15544_MCE_Alg 
        extends v0_2024_10.StoveProjectDescr_EN15544_MCE_Alg
        with v0_2024_10.SimpleStoveProjectDescrFr_Alg

    trait SimpleStoveProjectDescrFr_EN15544_Labo_Alg 
        extends v0_2024_10.StoveProjectDescr_EN15544_Labo_Alg 
        with v0_2024_10.SimpleStoveProjectDescrFr_Alg

    trait SimpleStoveProjectDescrFr_Alg extends StoveProjectDescr_Alg:
        val exercice_name: String
        override val project = ProjectDescr.empty.copy(reference = exercice_name)
        val language: Language = Languages.Fr

    trait SimpleStoveProjectDescrFr_EN15544_Alg
        extends v0_2024_10.StoveProjectDescr_EN15544_Alg
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
        type FluePipeType <: FluePipe_EN15544 | FluePipe_EN13384
        def fluePipe: VNelString[FluePipeType]

    trait FluePipe_EN15544_Alg extends FluePipe_Alg:
        type FluePipeType = FluePipe_EN15544

    trait FluePipe_EN13384_Alg extends FluePipe_Alg:
        type FluePipeType = FluePipe_EN13384

    // Firebox

    trait Firebox_15544_Alg:
        type CombustionAirPipe
        type FireboxPipe

        def combustionAirPipe: VNelString[CombustionAirPipe]
        def fireboxPipe: VNelString[FireboxPipe]

    trait Firebox_EN15544_Strict_Alg extends Firebox_15544_Alg:
        type CombustionAirPipe  = CombustionAirPipe_Module_EN15544.FullDescr
        type FireboxPipe        = FireboxPipe_Module_EN15544.FullDescr

    trait Firebox_15544_MCE_Alg extends Firebox_15544_Alg:
        type CombustionAirPipe = CombustionAirPipe_Module_EN13384.FullDescr
        type FireboxPipe       = FireboxPipe_Module_EN13384.FullDescr

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
        with Firebox_EN15544_Strict_Alg:

        import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.*

        def combustionAirPipe =
            firebox match
                case f: TraditionalFirebox    => 
                    TraditionalFirebox_Module.toCombustionAirPipe_EN15544(f)
                case f: AFPMA_PRSE              => 
                    AFPMA_PRSE_Module.toCombustionAirPipe_EN15544(f)
                case f: EcoLabeled => 
                    EcoLabeled_Module.toCombustionAirPipe_EN15544(f)

        def fireboxPipe =
            firebox match
                case f: TraditionalFirebox    => 
                    TraditionalFirebox_Module.toFireboxPipe_EN15544(f)
                case f: AFPMA_PRSE              => 
                    AFPMA_PRSE_Module.toFireboxPipe_EN15544(f)
                case f: EcoLabeled => 
                    EcoLabeled_Module.toFireboxPipe_EN15544(f)

    trait Firebox_15544_MCE_OneOff_Alg
        extends Firebox_15544_OneOff_Alg
        with Firebox_15544_MCE_Alg:

        import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.*

        def combustionAirPipe =
            firebox match
                case f: TraditionalFirebox    => 
                    TraditionalFirebox_Module.toCombustionAirPipe_EN13384(f)
                case f: AFPMA_PRSE              => 
                    AFPMA_PRSE_Module.toCombustionAirPipe_EN13384(f)
                case f: EcoLabeled => 
                    EcoLabeled_Module.toCombustionAirPipe_EN13384(f)

        def fireboxPipe =
            firebox match
                case f: TraditionalFirebox    => 
                    TraditionalFirebox_Module.toFireboxPipe_EN13384(f)
                case f: AFPMA_PRSE              => 
                    AFPMA_PRSE_Module.toFireboxPipe_EN13384(f)
                case f: EcoLabeled => 
                    EcoLabeled_Module.toFireboxPipe_EN13384(f)

    // Stove Project Description

    trait StoveProjectDescr_EN13384_Alg
        extends StoveProjectDescr_Alg:

        def localConditions: LocalConditions

        def en13384NationalAcceptedData: NationalAcceptedData = 
            NationalAcceptedData.noOverride

        def airIntakePipe: VNelString[AirIntakePipe]

        def connectorPipe: VNelString[ConnectorPipe]

        def chimneyPipe: VNelString[ChimneyPipe]

        def en13384_pipesVNel: VNelString[Pipes_EN13384] = 
            (
                airIntakePipe,
                connectorPipe,
                chimneyPipe
            ).mapN { (condAir, connector, chimney) =>
                Pipes_EN13384(
                    condAir,
                    connector,
                    chimney,
                )
            }

        def heatingAppliance            : VNelString[HeatingAppliance]

    trait StoveProjectDescr_EN13384_Strict_Alg
        extends StoveProjectDescr_EN13384_Alg:
        self =>

        def fuelType: FuelType
        def flueGasCondition: FlueGasCondition

        def inputsVNel: VNelString[Inputs] =
            // ensure type of appliance matches with fuel type
            val checkApplianceAndFuel =
                (typeOfAppliance, fuelType) match
                    case (TypeOfAppliance.Pellets, FuelType.Pellets) => 
                        ().validNel // OK
                    case (TypeOfAppliance.Pellets, ft) => 
                        s"${TypeOfAppliance.Pellets} can not match ${ft.show}".invalidNel
                    case (TypeOfAppliance.WoodLogs, FuelType.Pellets) =>
                        s"${TypeOfAppliance.WoodLogs} can not match ${FuelType.Pellets.show}".invalidNel
                    case (TypeOfAppliance.WoodLogs, _ ) =>
                        ().validNel // OK
            (
                checkApplianceAndFuel,
                en13384_pipesVNel
            ).mapN: (_, pipes) =>
                Inputs(
                    pipes,
                    en13384NationalAcceptedData,
                    fuelType,
                    localConditions,
                    flueGasCondition
                )

        lazy val en13384_appl: VNelString[EN13384_Strict_Application] = inputsVNel.map: i =>
            val f = new EN13384_1_A1_2019_Formulas
            new EN13384_Strict_Application(f, i):
                final override lazy val computeAt = ComputeAt.Mean
                final override lazy val p_L_override = None
                

    trait StoveProjectDescr_EN15544_Alg 
        extends StoveProjectDescr_EN13384_Alg
        with Firebox_15544_Alg
        with FluePipe_Alg:

        val kindOfWood: KindOfWood
        val typeOfAppliance = TypeOfAppliance.WoodLogs

        def localConditions: LocalConditions

        def stoveParams: StoveParams

        type PipesType <: Pipes_EN15544_Strict | Pipes_EN15544_MCE
        
        def pipesVNel: VNelString[PipesType]

        def design: Design

        type EN15544_Alg_Type <: EN15544_V_2023_Common_Application[?]

        lazy val en15544_Alg: VNelString[EN15544_Alg_Type]

        override lazy val heatingAppliance: VNelString[HeatingAppliance] = 
            en15544_Alg.andThen: en15544_Alg =>
                en15544_Alg.en13384_heatingAppliance_pressures
                    .map: hap =>
                        HeatingAppliance(
                            design.firebox.reference,
                            design.firebox.type_of_appliance,
                            en15544_Alg.en13384_heatingAppliance_efficiency,
                            en15544_Alg.en13384_heatingAppliance_fluegas,
                            en15544_Alg.en13384_heatingAppliance_powers,
                            en15544_Alg.en13384_heatingAppliance_temperatures,
                            en15544_Alg.en13384_heatingAppliance_massFlows,
                            hap,
                        )
                    .asVNelString

    trait StoveProjectDescr_EN15544_Strict_Alg 
        extends StoveProjectDescr_EN15544_Alg
        with Firebox_EN15544_Strict_Alg
        with FluePipe_EN15544_Alg:

        val kindOfWood = KindOfWood.HardWood

        def inputsVNel: VNelString[std.Inputs_EN15544_Strict] = 
            pipesVNel.map: pipes =>
                std.Inputs_EN15544_Strict(
                    localConditions,
                    en13384NationalAcceptedData,
                    stoveParams,
                    design,
                    pipes,
                )
        
        type PipesType = Pipes_EN15544_Strict 

        override lazy val pipesVNel = 
        (
            airIntakePipe,
            combustionAirPipe,
            fireboxPipe,
            fluePipe,
            connectorPipe,
            chimneyPipe
        ).mapN { (condAir, combChInt, combCh, flue, connector, chimney) =>
            Pipes_EN15544_Strict(
                condAir,
                combChInt,
                combCh,
                flue,
                connector,
                chimney,
            )
        }

        type EN15544_Alg_Type = EN15544_Strict_Application

        override lazy val en15544_Alg: VNelString[EN15544_Strict_Application] = inputsVNel.map: i =>
            EN15544_Strict_Application.make(EN15544_Strict_Formulas.make)(i)

    trait StoveProjectDescr_EN15544_MCE_Alg 
        extends StoveProjectDescr_EN15544_Alg
        with Firebox_15544_MCE_Alg
        with FluePipe_EN13384_Alg:

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

        type PipesType = Pipes_EN15544_MCE
        
        override lazy val pipesVNel = 
            (
                airIntakePipe,
                combustionAirPipe,
                fireboxPipe,
                fluePipe,
                connectorPipe,
                chimneyPipe
            ).mapN { (airIntake, combAir, fbox, flue, connector, chimney) =>
                Pipes_EN15544_MCE(
                    airIntake,
                    combAir,
                    fbox,
                    flue,
                    connector,
                    chimney,
                )
            }

        def inputsVNel: VNelString[std.Inputs_EN15544_MCE] = 
            pipesVNel.map: pipes =>
                std.Inputs_EN15544_MCE(
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
        
        type EN15544_Alg_Type = EN15544_MCE_Application

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
        override lazy val en15544_Alg: VNelString[EN15544_MCE_Application] = inputsVNel.map: i =>
            val bs845 = new BS845_Impl {}
            val en15544_mce_formulas = EN15544_MCE_Formulas.make(
                net_calorific_value_of_wet_wood = net_calorific_value_of_wet_wood,
                net_calorific_value_of_dry_wood = net_calorific_value_of_dry_wood
            )
            val wComb = new WoodCombustionImpl
            EN15544_MCE_Application.make(en15544_mce_formulas, bs845, wComb)(i)

    trait StoveProjectDescr_EN15544_Labo_Alg
        extends StoveProjectDescr_EN15544_MCE_Alg with LabConditions:
        labcond =>

        val kindOfWood = KindOfWood.HardWood

        override lazy val en15544_Alg: VNelString[EN15544_Labo_Application] = inputsVNel.map: i =>
            val bs845 = new BS845_Impl {}
            val f = new EN15544_Labo_Formulas(
                net_calorific_value_of_wet_wood = net_calorific_value_of_wet_wood,
                net_calorific_value_of_dry_wood = net_calorific_value_of_dry_wood
            )(labcond)
            val wComb = new WoodCombustionImpl
            new EN15544_Labo_Application(f, bs845, wComb)(i)(labcond)

    object StoveProjectDescr:
        
        def makeFor_EN15544_Strict(fc: FireCalcYAML): StoveProjectDescr_EN15544_Strict_Alg = 
            val loader = new FireCalcYAML_Loader(fc)
            loader.stoveProjectDescr_EN15544_Strict
            