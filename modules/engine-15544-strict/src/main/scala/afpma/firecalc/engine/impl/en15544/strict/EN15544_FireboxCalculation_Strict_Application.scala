/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.alg.en15544.FireboxCalculation
import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.FireboxOperatingEnvelope
import afpma.firecalc.engine.alg.en15544.OpPoint
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en15544.FramedIncrAirIntakePipe_FlowOnly
import afpma.firecalc.engine.models.en15544.FramedIncrPostFireboxPipes
import afpma.firecalc.engine.models.en15544.IncrementalPipeInputs_15544_Strict
import afpma.firecalc.engine.models.en15544.Inputs_15544_Strict
import afpma.firecalc.engine.models.en15544.std.Design
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.standard.UnexpectedDevError
import afpma.firecalc.engine.standard.VNelMcalcErr

import cats.syntax.all.*

/**
 * The standalone EN 15544 strict implementation of [[FireboxCalculation]].
 *
 * Instantiable WITHOUT the full strict inputs record: it synthesises a minimal
 * [[Inputs_15544_Strict]] from the firebox, `m_B`, and the operating envelope,
 * building the pipe descriptors from the engine registry's contributions (the
 * same generic firebox gas-path builder and per-kind combustion-air builders
 * that run locally today).
 *
 * The firebox-scope derivations (prescribed temperatures, altitude correction,
 * densities, volume/mass flows, and the two pipe solves) are shared with the
 * full application through the [[FireboxScopeCore_Strict]] mixin — the physics
 * exists once.
 */
object EN15544_FireboxCalculation_Strict_Application extends FireboxCalculation[Firebox_15544]:

    def solve(
        firebox : Firebox_15544,
        m_B     : Option[Mass],
        envelope: FireboxOperatingEnvelope
    ): VNelMcalcErr[Map[OpPoint, (PipeResult, Option[PipeResult])]] =
        m_B match
            case None        =>
                UnexpectedDevError(
                    "FireboxCalculation.solve: m_B is required but was None"
                ).invalidNel
            case Some(mBVal) =>
                buildApplication(firebox, mBVal, envelope).andThen(_.fireboxScopeGrid)

    // ── Minimal application synthesis ────────────────────────────────────

    private def buildApplication(
        firebox : Firebox_15544,
        mB      : Mass,
        envelope: FireboxOperatingEnvelope
    ): VNelMcalcErr[EN15544_Strict_Application] =
        (buildFireboxPipe(firebox), buildCombustionAirPipe(firebox)).mapN: (fireboxPipe, combustionAirPipe) =>
            val _inputs     = synthesizeInputs(firebox, mB, envelope, fireboxPipe, combustionAirPipe)
            val _incrInputs = synthesizeIncrInputs()
            EN15544_Strict_Application.makeWithEnvelope(EN15544_Strict_Formulas.make)(
                _inputs,
                _incrInputs,
                envelope.T_L_override
            )

    private def buildFireboxPipe(
        firebox: Firebox_15544
    ): VNelMcalcErr[FireboxPipe_15544] =
        FireboxEngineRegistry_Strict
            .engineFor(firebox)
            .leftMap(msg => UnexpectedDevError(msg))
            .toValidatedNel
            .andThen: fc =>
                fc.engine
                    .fireboxContribution(fc.firebox)
                    .andThen:
                        case FireboxContribution.AsDescr(descr) => descr.validNel
                        case FireboxContribution.Without        =>
                            UnexpectedDevError(
                                "firebox engine returned Without for the firebox gas path"
                            ).invalidNel
                        case FireboxContribution.AsResult(_)    =>
                            UnexpectedDevError(
                                "firebox engine returned AsResult; descriptor seam unreachable"
                            ).invalidNel

    private def buildCombustionAirPipe(
        firebox: Firebox_15544
    ): VNelMcalcErr[CombustionAirPipe_15544] =
        FireboxEngineRegistry_Strict
            .engineFor(firebox)
            .leftMap(msg => UnexpectedDevError(msg))
            .toValidatedNel
            .andThen: fc =>
                fc.engine
                    .combustionAirContribution(fc.firebox)
                    .andThen:
                        case FireboxContribution.Without        =>
                            CombustionAirPipe_Module_15544.without.validNel
                        case FireboxContribution.AsDescr(descr) => descr.validNel
                        case FireboxContribution.AsResult(_)    =>
                            UnexpectedDevError(
                                "firebox engine returned AsResult; descriptor seam unreachable"
                            ).invalidNel

    private def synthesizeInputs(
        firebox          : Firebox_15544,
        mB               : Mass,
        envelope         : FireboxOperatingEnvelope,
        fireboxPipe      : FireboxPipe_15544,
        combustionAirPipe: CombustionAirPipe_15544
    ): Inputs_15544_Strict =
        val localConditions             = LocalConditions(
            altitude            = envelope.altitude,
            coastal_region      = false,
            chimney_termination = LocalConditions.ChimneyTermination.Classic
        )
        val nationalData                = NationalAcceptedData(
            T_uo_override = afpma.firecalc.engine.models.en13384.typedefs.T_uo_temperature_override.none,
            T_L_override  = envelope.T_L_override
        )
        val stoveParams                 = StoveParams.fromMaxLoadAndStoragePeriod(
            maximum_load   = mB,
            heating_cycle  = 8.0.hours,
            min_efficiency = 78.0.percent,
            facing_type    = FacingType.WithoutAirGap
        )
        val pipes                       = Pipes_15544_Strict(
            airIntake     = FlowOnlyAirIntakePipe_Module_13384.noVentilationOpenings,
            combustionAir = combustionAirPipe,
            firebox       = fireboxPipe
        )
        Inputs_15544_Strict(
            localConditions             = localConditions,
            en13384NationalAcceptedData = nationalData,
            stoveParams                 = stoveParams,
            design                      = Design(firebox),
            pipes                       = pipes
        )

    private def synthesizeIncrInputs(): IncrementalPipeInputs_15544_Strict =
        IncrementalPipeInputs_15544_Strict(
            postFirebox = FramedIncrPostFireboxPipes(
                initialDirection = None,
                positionMode     = afpma.firecalc.engine.models.geometry.PostFireboxStartPositionMode.Auto,
                resolvedPosition = None,
                slots            = Seq.empty
            ),
            airIntake   = FramedIncrAirIntakePipe_FlowOnly(
                initialDirection = None,
                positionMode     = afpma.firecalc.engine.models.geometry.AirIntakePositionMode.FinalAuto,
                resolvedPosition = None,
                descr            = Seq.empty
            )
        )

end EN15544_FireboxCalculation_Strict_Application
