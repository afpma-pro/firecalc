/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.all.FlowOnlyPipeDescr_15544
import afpma.firecalc.dto.all.ThermalPipeDescr_13384

import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.FireboxEngine
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.impl.en15544.strict.HasTypeMembers_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.en15544.FramedIncrAirIntakePipe_FlowOnly
import afpma.firecalc.engine.models.en15544.FramedIncrPostFireboxPipes
import afpma.firecalc.engine.models.en15544.IncrementalPipeInputs_15544_Strict
import afpma.firecalc.engine.models.en15544.Inputs_15544_Strict
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.UnexpectedDevError
import afpma.firecalc.engine.standard.VNelMcalcErr

import cats.data.ValidatedNel
import cats.syntax.all.*

/** EN 15544 strict-mode concrete wiring traits (split from v0_2024_10). */
trait v0_2024_10_strict_members extends v0_2024_10_core:

    trait Firebox_15544_Strict_Alg extends HasFirebox_15544_Alg with HasFireboxInternalPipes_15544_Strict_Alg:

        /**
         * The bundled firebox engine for this project's firebox type.
         *
         * Replaces the former pair of injected pipe typeclasses
         * (`toCombustionAirPipeTC` / `toFireboxPipeTC`): one summon instead of two,
         * and the same instance now also carries constraints and formulas.
         *
         * Typed as the descriptor-refined [[FireboxEngine]] rather than
         * [[FireboxEngine_15544_Strict]] so the loader can supply the engine
         * resolved by the registry (`withEngine`) — which pairs a
         * `FireboxEngine[FB]` with its refined descriptor members — without
         * naming the concrete model type. `FireboxEngine_15544_Strict[FB]`
         * conforms to this refinement, so every per-kind engine still fits.
         */
        protected val fireboxEngine: FireboxEngine[FB] {
            type FireboxPipe_FullDescr       = FireboxPipe_Module_15544.FullDescr
            type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_15544.FullDescr
        }

        override def combustionAirPipe =
            fireboxEngine
                .combustionAirContribution(firebox)
                .andThen:
                    case FireboxContribution.Without        => CombustionAirPipe_Module_15544.without.validNel
                    case FireboxContribution.AsDescr(descr) => descr.validNel
                    case FireboxContribution.AsResult(_)    =>
                        UnexpectedDevError(
                            "firebox engine returned AsResult for the combustion-air path; " +
                                "that contribution is consumed in combustionAir_PipeResult and must not reach the descriptor seam"
                        ).invalidNel

        override def fireboxPipe =
            fireboxEngine
                .fireboxContribution(firebox)
                .andThen:
                    // `FireboxPipe_Module_15544.PipeCanBe = FullDescr` — unlike the
                    // combustion-air module there is no `Without` member, because a
                    // stove always has a firebox gas path. `fireboxContribution`
                    // returns `FireboxContribution.AsDescr` for every registered
                    // engine; the match is exhaustive over what can actually occur.
                    // `AsResult` is intercepted in firebox_PipeResult and never reaches here.
                    case FireboxContribution.AsDescr(descr) => descr.validNel
                    case FireboxContribution.Without        =>
                        UnexpectedDevError(
                            "firebox engine returned Without for the firebox gas path; " +
                                "a stove always has a firebox gas path so this is an internal invariant violation"
                        ).invalidNel
                    case FireboxContribution.AsResult(_)    =>
                        UnexpectedDevError(
                            "firebox engine returned AsResult for the firebox gas path; " +
                                "that contribution is consumed in firebox_PipeResult and must not reach the descriptor seam"
                        ).invalidNel

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

        /**
         * Raw IncrDescr/seed input layer — bundles the post-firebox + air-intake framed
         * wrappers from the project-trait override points. Plain `def` (no validation):
         * the IncrDescr-input layer carries already-validated passthrough values.
         */
        override def en15544_incrInputs: IncrementalPipeInputs_15544_Strict =
            IncrementalPipeInputs_15544_Strict(
                postFirebox = FramedIncrPostFireboxPipes(
                    initialDirection = postFireboxInitialDirection,
                    positionMode     = postFireboxPositionMode,
                    resolvedPosition = postFireboxInitialPosition,
                    slots            = postFireboxPipeSlots
                ),
                airIntake   = FramedIncrAirIntakePipe_FlowOnly(
                    initialDirection = None,
                    positionMode     = airIntakePositionMode,
                    resolvedPosition = airIntakeInitialPosition,
                    descr            = airIntakeDescriptors
                )
            )

        override lazy val en15544_Alg: ValidatedNel[MCalc_Error, EN15544_Strict_Application] = en15544_inputsVNel.map:
            i =>
                EN15544_Strict_Application.make(EN15544_Strict_Formulas.make)(
                    i,
                    en15544_incrInputs
                )

    trait SimpleStoveProjectDescrFr_15544_Strict_Alg
        extends SimpleStoveProjectDescrFr_15544_Alg
        with StoveProjectDescr_15544_Strict_Alg

    trait WithPipeChain_15544_Strict:
        self: StoveProjectDescr_15544_Strict_Alg =>

        def fluePipeDescr     : Seq[FlowOnlyPipeDescr_15544]
        def connectorPipeDescr: Seq[ThermalPipeDescr_13384]
        def chimneyPipeDescr  : Seq[ThermalPipeDescr_13384]

        override def postFireboxPipeSlots: Seq[PostFireboxPipeSlot] =
            PipeChain_15544_Strict.toSlots(
                PipeChain_15544_Strict.Descriptors(fluePipeDescr, connectorPipeDescr, chimneyPipeDescr)
            )
