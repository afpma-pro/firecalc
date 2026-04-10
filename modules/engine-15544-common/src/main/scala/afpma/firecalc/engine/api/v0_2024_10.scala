/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.LocalizedAlg

import afpma.firecalc.engine.alg.en13384.HasTypeMembers_13384_Alg
import afpma.firecalc.engine.alg.en15544.HasTypeMembers_15544_Alg
import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Application
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import afpma.firecalc.engine.models.en15544.std
import afpma.firecalc.engine.models.en15544.std.Design
import afpma.firecalc.engine.models.gtypedefs.KindOfWood
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.StoveParamsSizingInputMissing
import afpma.firecalc.engine.standard.VNelMcalcErr

import cats.data.ValidatedNel
import cats.syntax.all.*

import io.taig.babel.Language
import io.taig.babel.Languages

/**
 * Core abstract algebra traits — no imports from impl.en15544.strict, impl.en15544.mce,
 * impl.en15544.labo, or impl.en13384.
 *
 * Concrete wiring is provided by the mixin traits (in leaf modules):
 *   - [[v0_2024_10_strict_members]]  (EN 15544 strict)
 *   - [[v0_2024_10_mce_members]]     (EN 15544 MCE)
 *   - [[v0_2024_10_labo_members]]    (EN 15544 labo)
 *   - [[v0_2024_10_13384_members]]   (EN 13384, in engine-15544-strict)
 */
trait v0_2024_10_core:

    trait StoveProjectDescr_Alg extends LocalizedAlg:
        def project         : ProjectDescr     = ProjectDescr.empty
        def typeOfAppliance : TypeOfAppliance
        def localRegulations: LocalRegulations = LocalRegulations.findBy(
            c = project.country,
            t = typeOfAppliance
        )

    trait SimpleStoveProjectDescrFr_Alg extends StoveProjectDescr_Alg:
        val exercice_name: String
        override val project: ProjectDescr = ProjectDescr.empty.copy(reference = exercice_name)
        val language        : Language     = Languages.Fr

    trait SimpleStoveProjectDescrFr_15544_Alg extends StoveProjectDescr_15544_Alg with SimpleStoveProjectDescrFr_Alg

    // Flue Pipe

    sealed trait HasFluePipe_Alg:
        type FluePipeType <: FluePipe_15544 | FluePipe_13384
        def fluePipe: ValidatedNel[IncrementalValidation_Error, FluePipeType]

    trait HasFluePipe_15544_Alg extends HasFluePipe_Alg:
        type FluePipeType = FluePipe_15544

    trait HasFluePipe_13384_Alg extends HasFluePipe_Alg:
        type FluePipeType = FluePipe_13384

    // Firebox

    trait HasFireboxInternalPipes_Alg:
        type CombustionAirPipe
        type FireboxPipe

        def combustionAirPipe: VNelMcalcErr[CombustionAirPipe]
        def fireboxPipe      : VNelMcalcErr[FireboxPipe]

    trait HasFireboxInternalPipes_15544_Strict_Alg extends HasFireboxInternalPipes_Alg:
        type CombustionAirPipe = CombustionAirPipe_15544
        type FireboxPipe       = FireboxPipe_15544

    trait HasFireboxInternalPipes_15544_MCE_Alg extends HasFireboxInternalPipes_Alg:
        type CombustionAirPipe = CombustionAirPipe_13384
        type FireboxPipe       = FireboxPipe_13384

    trait HasFirebox_15544_Alg:
        self: HasFireboxInternalPipes_Alg =>

        type FB <: std.Firebox_15544
        def firebox: FB

        lazy val design: Design = Design(firebox = self.firebox)

    // Stove Project Description

    trait StoveProjectDescr_13384_Alg extends StoveProjectDescr_Alg with HasTypeMembers_13384_Alg:
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

    trait StoveProjectDescr_15544_Alg
        extends StoveProjectDescr_13384_Alg
        with HasTypeMembers_15544_Alg
        with HasFireboxInternalPipes_Alg
        with HasFluePipe_Alg:
        self =>

        /**
         * The ordered post-firebox pipe descriptor slots from the DTO.
         * Defaults to empty; override with the actual `post_firebox_pipes` from FireCalcYAML V6
         * to support arbitrary N-pipe topologies.
         */
        def postFireboxPipeSlots: Seq[afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot] = Seq.empty

        type EN15544_Alg <: EN15544_V_2023_Common_Application {
            type AirIntakePipe_Module_T     = self.AirIntakePipe_Module_T
            type CombustionAirPipe_Module_T = self.CombustionAirPipe_Module_T
            type FireboxPipe_Module_T       = self.FireboxPipe_Module_T
            type FluePipe_Module_T          = self.FluePipe_Module_T
        }

        val kindOfWood: KindOfWood
        val typeOfAppliance = TypeOfAppliance.WoodLogs

        override def fuelType: FuelType =
            en15544_Alg.toOption
                .map(_.en13384_inputs_fuelType)
                .getOrElse(throw new IllegalStateException("fuelType should be set"))

        override def flueGasCondition: FlueGasCondition =
            en15544_Alg.toOption
                .map(_.en13384_inputs_flueGasCondition)
                .getOrElse(throw new IllegalStateException("flueGasCondition should be set"))

        def localConditions: LocalConditions

        def stoveParams: StoveParams

        protected def checkStoveParamsSizingInput: VNelMcalcErr[Unit] =
            stoveParams.mB_or_pn_opt match
                case Some(_) => ().validNel
                case None    => StoveParamsSizingInputMissing.invalidNel

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
                    en15544_Alg.en13384_heatingAppliance_powers
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
                            hap
                        )

// ──────────────────────────────────────────────────────────────────────────────
// Base API object — core algebra + EN 13384 wiring only.
// Standard-specific access is provided by per-module objects:
//   - [[v0_2024_10_strict]] (in engine-15544-strict)
//   - [[v0_2024_10_mce]]    (in engine-15544-mce)
//   - [[v0_2024_10_labo]]   (in engine-15544-labo)
// ──────────────────────────────────────────────────────────────────────────────
object v0_2024_10
    extends v0_2024_10_core
    with v0_2024_10_13384_members
