/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.all.FlowOnlyPipeDescr_15544
import afpma.firecalc.dto.all.ThermalPipeDescr_13384

import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.impl.en15544.strict.FireboxToCombustionAirPipe_15544_Strict
import afpma.firecalc.engine.impl.en15544.strict.FireboxToFireboxPipe_15544_Strict
import afpma.firecalc.engine.impl.en15544.strict.HasTypeMembers_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.Inputs_15544_Strict
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.VNelMcalcErr

import cats.data.ValidatedNel
import cats.syntax.all.*

/** EN 15544 strict-mode concrete wiring traits (split from v0_2024_10). */
trait v0_2024_10_strict_members extends v0_2024_10_core:

    trait Firebox_15544_Strict_Alg extends HasFirebox_15544_Alg with HasFireboxInternalPipes_15544_Strict_Alg:
        protected val toCombustionAirPipeTC: FireboxToCombustionAirPipe_15544_Strict[FB]
        protected val toFireboxPipeTC      : FireboxToFireboxPipe_15544_Strict[FB]

        override def combustionAirPipe = {
            given FireboxToCombustionAirPipe_15544_Strict[FB] = toCombustionAirPipeTC;
            firebox.toCombustionAirPipe_FullDescr
        }
        override def fireboxPipe       = {
            given FireboxToFireboxPipe_15544_Strict[FB] = toFireboxPipeTC; firebox.toFireboxPipe_FullDescr
        }

    trait StoveProjectDescr_15544_Strict_Alg
        extends StoveProjectDescr_15544_Alg
        with HasTypeMembers_15544_Strict
        with HasFireboxInternalPipes_15544_Strict_Alg:
        self =>

        val kindOfWood = afpma.firecalc.engine.models.gtypedefs.KindOfWood.HardWood

        override def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384] =
            airIntakePipe.map: _airIntake =>
                new Pipes_13384_WithFlowOnlyAirIntake_PreFireboxOnly:
                    override val airIntake: FlowOnlyAirIntakePipe_13384 = _airIntake

        override def en15544_inputsVNel: ValidatedNel[MCalc_Error, Inputs_15544_Strict] =
            en15544_pipesVNel.map: pipes =>
                Inputs_15544_Strict(
                    localConditions,
                    en13384NationalAcceptedData,
                    stoveParams,
                    design,
                    pipes
                )

        override lazy val en15544_pipesVNel: VNelMcalcErr[Pipes_15544] =
            (
                airIntakePipe,
                combustionAirPipe,
                fireboxPipe
            ).mapN { (condAir, combChInt, combCh) =>
                Pipes_15544_Strict(
                    condAir,
                    combChInt,
                    combCh
                )
            }

        override type EN15544_Alg = EN15544_Strict_Application

        def postFireboxInitialDirection: Option[afpma.firecalc.dto.v7.PostFireboxInitialDirection] = None
        def postFireboxInitialPosition: Option[afpma.firecalc.dto.v7.PostFireboxInitialPosition] = None

        override lazy val en15544_Alg: ValidatedNel[MCalc_Error, EN15544_Strict_Application] = en15544_inputsVNel.map:
            i => EN15544_Strict_Application.make(EN15544_Strict_Formulas.make)(
                i, postFireboxPipeSlots, postFireboxInitialDirection, postFireboxInitialPosition
            )

    trait SimpleStoveProjectDescrFr_15544_Strict_Alg
        extends SimpleStoveProjectDescrFr_15544_Alg
        with StoveProjectDescr_15544_Strict_Alg

    trait WithPipeChain_15544_Strict:
        self: StoveProjectDescr_15544_Strict_Alg =>

        def fluePipeDescr     : Seq[FlowOnlyPipeDescr_15544]
        def connectorPipeDescr: Seq[ThermalPipeDescr_13384]
        def chimneyPipeDescr  : Seq[ThermalPipeDescr_13384]

        override def postFireboxPipeSlots: Seq[afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot] =
            PipeChain_15544_Strict.toSlots(
                PipeChain_15544_Strict.Descriptors(fluePipeDescr, connectorPipeDescr, chimneyPipeDescr)
            )
