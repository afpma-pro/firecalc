/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Application_Alg
import afpma.firecalc.engine.alg.en13384.HasTypeMembers_13384_Alg
import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.standard.MecaFlu_Error

import cats.syntax.all.*

/**
 * Algebra for fluid mechanics calculations on pipe descriptions.
 *
 * This trait defines the common interface for computing fluid mechanics
 * results on different types of pipe descriptions (EN13384 flow-only,
 * EN13384 thermal, EN15544 flow-only).
 */
trait MecaFluAlg:

    /** The pipe description algebra this MecaFlu operates on */
    type PipeDescr <: PipeDescrAlg

    /** Reference to the pipe description instance */
    val pipeDescr: PipeDescr

    /** The application algebra used for computation */
    // type ApplicationAlg

    /** Parameters type for the calculation */
    type Params

    /** Result type for a single pipe section */
    type SectionResult <: PipeSectionResult[pipeDescr.PipeElDescr]

    /** Result type for a full pipe */
    type FullResult <: PipeResult

    /**
     * Compute full pipe result using unified context object.
     * Replaces scattered parameters with a single typed context.
     */
    def makePipeResult(
        ctx   : MecaFluPipeContext[pipeDescr.PipeElDescr],
        params: Params
    )                 (using appCtx: MecaFluAppContext): Either[MecaFlu_Error, FullResult]

/** Base application context for MecaFlu operations. Sub-traits add standard-specific fields. */
trait MecaFluAppContext

/** EN13384 application context — carries HeatingAppliance data. */
trait MecaFlu_13384_AppCtx extends MecaFluAppContext:
    def en13384: EN13384_1_A1_2019_Application_Alg
    def hafg   : HeatingAppliance.FlueGas
    def hamf   : HeatingAppliance.MassFlows

object MecaFlu_13384_AppCtx:
    def apply(
        _en13384: EN13384_1_A1_2019_Application_Alg,
        _hafg   : HeatingAppliance.FlueGas,
        _hamf   : HeatingAppliance.MassFlows
    ): MecaFlu_13384_AppCtx = new MecaFlu_13384_AppCtx:
        def en13384 = _en13384
        def hafg    = _hafg
        def hamf    = _hamf

/** Intermediate algebra for EN13384-based fluid mechanics calculations. */
trait MecaFlu_13384_Alg extends MecaFluAlg with HasTypeMembers_13384_Alg:
    self =>

    type ApplicationAlg <: EN13384_1_A1_2019_Application_Alg {
        type AirIntakePipe_Module_T = self.AirIntakePipe_Module_T
        type Inputs_13384           = self.Inputs_13384
    }

    override type Params = Params_13384

/**
 * Shared helper operations for MecaFlu calculations.
 * These generic functions extract common patterns used across
 * EN13384 (FlowOnly, Thermal) and EN15544 implementations.
 */
object MecaFluOps:

    // ============================================
    // Gas Type Discriminator
    // ============================================

    /**
     * Discriminate computation based on gas type determined by PipeType.
     *
     * @param pt The pipe type to check
     * @param ifCombustionAir Value/computation for combustion air pipes (AirIntake, CombustionAir)
     * @param ifFlueGas Value/computation for flue gas pipes (Firebox, Flue, Connector, Chimney)
     * @return The appropriate value based on pipe type
     */
    def whenGasType[A](pt: PipeType)(ifCombustionAir: => A, ifFlueGas: => A): A =
        pt match
            case AirIntakePipeT | CombustionAirPipeT                                    =>
                ifCombustionAir
            case FireboxPipeT | FluePipeT | ConnectorPipeT | ChimneyPipeT | NoFluePipeT =>
                ifFlueGas

    // ============================================
    // Error Handling Helpers
    // ============================================

    /**
     * Throw a structured MecaFlu_Error as an exception.
     * Used within lazy-val pipe section implementations; caught by [[catchMecaFluErrors]].
     */
    def throwMecaFluError(err: MecaFlu_Error): Nothing =
        throw MecaFlu_Error.MecaFluErrorException(err)

    /**
     * Execute a block and catch MecaFlu errors into Either.
     * Used as the try/catch wrapper in all makePipeResult methods.
     */
    def catchMecaFluErrors[A](pipeType: PipeType)(f: => A): Either[MecaFlu_Error, A] =
        try f.asRight
        catch
            case mee: MecaFlu_Error.MecaFluErrorException =>
                mee.printStackTrace(         )
                Left               (mee.error)
            case e =>
                e.printStackTrace(                                              )
                Left             (MecaFlu_Error.UnexpectedThrowable(e, pipeType))

end MecaFluOps
