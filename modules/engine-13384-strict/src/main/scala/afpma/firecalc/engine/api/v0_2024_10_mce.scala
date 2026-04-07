/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.impl.en15544.mce.*
import afpma.firecalc.engine.impl.en15544.mce.EN15544_MCE_Application
import afpma.firecalc.engine.impl.en15544.mce.EN15544_MCE_Formulas
import afpma.firecalc.engine.impl.en15544.mce.HasTypeMembers_15544_MCE
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.Wood
import afpma.firecalc.engine.models.en15544.std
import afpma.firecalc.engine.models.gtypedefs.KindOfWood
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.VNelMcalcErr
import afpma.firecalc.engine.wood_combustion.*
import afpma.firecalc.engine.wood_combustion.bs845.BS845_Impl

import cats.data.ValidatedNel
import cats.syntax.all.*

/** EN 15544 MCE-mode concrete wiring traits (split from v0_2024_10). */
trait v0_2024_10_mce_members extends v0_2024_10_core:

    trait Firebox_15544_MCE_Alg extends HasFirebox_15544_Alg with HasFireboxInternalPipes_15544_MCE_Alg:
        protected val toCombustionAirPipeTC: FireboxToCombustionAirPipe_15544_MCE[FB]
        protected val toFireboxPipeTC      : FireboxToFireboxPipe_15544_MCE[FB]

        override def combustionAirPipe = {
            given FireboxToCombustionAirPipe_15544_MCE[FB] = toCombustionAirPipeTC;
            firebox.toCombustionAirPipe_FullDescr
        }
        override def fireboxPipe       = {
            given FireboxToFireboxPipe_15544_MCE[FB] = toFireboxPipeTC; firebox.toFireboxPipe_FullDescr
        }

    trait StoveProjectDescr_15544_MCE_Alg
        extends StoveProjectDescr_15544_Alg
        with HasTypeMembers_15544_MCE
        with HasFireboxInternalPipes_15544_MCE_Alg
        with HasFluePipe_13384_Alg:
        self =>

        val wComb: WoodCombustionAlg
        import wComb.*

        def combustion_duration: Duration

        def combustion_lambda_nominal: Double
        def combustion_lambda_lowest : Option[Double]

        def combustion_lambda(lq: LoadQty): Option[Double] = lq match
            case LoadQty.Nominal => combustion_lambda_nominal.some
            case LoadQty.Reduced => combustion_lambda_lowest

        def exterior_air: afpma.firecalc.engine.wood_combustion.ExteriorAir

        def wood                                     : Wood
        def computeWoodCalorificValueUsingComposition: "Yes" | "No"

        private def _fluegas_o2_wet_and_o2_dry_at(lq: LoadQty): Option[(Percentage, Percentage)] =
            combustion_lambda(lq).map: lambda =>
                val ci = wood
                    .mixWith(exterior_air, lambda)
                    .burnLoad(afpma.firecalc.engine.wood_combustion.Wood.HumidMass(stoveParams.mB.get))
                (ci.output_perfect_percbyvol_humid("O2"), ci.output_perfect_percbyvol_dry("O2"))

        lazy val (fluegas_o2_wet_nominal, fluegas_o2_dry_nominal) = _fluegas_o2_wet_and_o2_dry_at(LoadQty.Nominal).get
        lazy val (fluegas_o2_wet_lowest, fluegas_o2_dry_lowest) =
            _fluegas_o2_wet_and_o2_dry_at(LoadQty.Reduced).fold((None, None))((x, y) => (x.some, y.some))

        def fluegas_co2_wet_nominal: Percentage = wood.co2_wet_from_o2_wet(fluegas_o2_wet_nominal)
        def fluegas_co2_dry_nominal: Percentage = wood.co2_dry_from_o2_dry(fluegas_o2_dry_nominal)

        def fluegas_co2_wet_lowest: Option[Percentage] = fluegas_o2_wet_lowest.map(wood.co2_wet_from_o2_wet)
        def fluegas_co2_dry_lowest: Option[Percentage] = fluegas_o2_dry_lowest.map(wood.co2_dry_from_o2_dry)

        def fluegas_h2o_perc_vol_nominal: Option[Percentage]
        def fluegas_h2o_perc_vol_lowest : Option[Percentage]

        def massFlows_override: HeatingAppliance.MassFlows = HeatingAppliance.MassFlows.undefined

        override def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384] =
            (
                airIntakePipe,
                connectorPipe,
                chimneyPipe
            ).mapN: (_airIntake, _connector, _chimney) =>
                new Pipes_13384_WithThermalAirIntake:
                    override val airIntake: ThermalAirIntakePipe_13384 = _airIntake
                    override val connector: ConnectorPipe              = _connector
                    override val chimney  : ChimneyPipe                = _chimney

        override lazy val en15544_pipesVNel: VNelMcalcErr[Pipes_15544] =
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
                    chimney
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
                    exterior_air.relative_humidity
                )

        override type EN15544_Alg = EN15544_MCE_Application

        protected lazy val net_calorific_value_of_dry_wood: HeatCapacity =
            computeWoodCalorificValueUsingComposition match
                case "Yes" => wood.lower_calorific_value_dry
                case "No"  =>
                    // Source: Mesure des caractéristiques des combustibles bois « Evaluation et proposition de méthodes d'analyse de combustible » ADEME Critt Bois – Fibois – CTBA JUIN 2001 Ademe 2021
                    // https://cibe.fr/wp-content/uploads/2017/02/21-Mesures-PCI-bois-combustible-CRITT-bois-FIBOIS-CTBA.pdf
                    kindOfWood match
                        case KindOfWood.HardWood => 5.070.kWh_per_kg
                        case KindOfWood.SoftWood => 5.330.kWh_per_kg

        private lazy val pci: PCI_Conversion_Alg = PCI_Conversion.cibe_fr

        protected lazy val net_calorific_value_of_wet_wood: HeatCapacity =
            pci.PCI_sur_brut(net_calorific_value_of_dry_wood, wood.humidity)

        // TODO: rename to en15544_appl
        override lazy val en15544_Alg: ValidatedNel[MCalc_Error, EN15544_MCE_Application] = en15544_inputsVNel.map: i =>
            val bs845                = new BS845_Impl {}
            val en15544_mce_formulas = EN15544_MCE_Formulas.make(
                net_calorific_value_of_wet_wood = net_calorific_value_of_wet_wood,
                net_calorific_value_of_dry_wood = net_calorific_value_of_dry_wood
            )
            val wComb                = new WoodCombustionImpl
            EN15544_MCE_Application.make(en15544_mce_formulas, bs845, wComb)(i)

    trait SimpleStoveProjectDescrFr_15544_MCE_Alg
        extends StoveProjectDescr_15544_MCE_Alg
        with SimpleStoveProjectDescrFr_Alg

    trait WithPipeChain_15544_MCE:
        self: StoveProjectDescr_15544_MCE_Alg =>

        def fluePipeDescr     : Seq[FluePipe_Module_13384.incremental.IncrDescr]
        def connectorPipeDescr: Seq[ConnectorPipe_Module.incremental.IncrDescr]
        def chimneyPipeDescr  : Seq[ChimneyPipe_Module.incremental.IncrDescr]

        private lazy val pipeChain = PipeChain_15544_MCE.build(
            PipeChain_15544_MCE.Descriptors(fluePipeDescr, connectorPipeDescr, chimneyPipeDescr)
        )

        override lazy val fluePipe     : ValidatedNel[IncrementalValidation_Error, FluePipe_13384] = pipeChain.fluePipe
        override lazy val connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]  =
            pipeChain.connectorPipe
        override lazy val chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]    = pipeChain.chimneyPipe
