/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.AirSpaceDetailed_V1
import afpma.firecalc.dto.common.PipeShape

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Application_Alg
import afpma.firecalc.engine.alg.en13384.HasTypeMembers_13384_Alg
import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Application_Alg
import afpma.firecalc.engine.alg.en15544.HasTypeMembers_15544_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.ops.Position.*
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

/** Intermediate algebra for EN13384-based fluid mechanics calculations. */
trait MecaFlu_13384_Alg extends MecaFluAlg with HasTypeMembers_13384_Alg:
    self =>

    type ApplicationAlg <: EN13384_1_A1_2019_Application_Alg {
        type AirIntakePipe_Module_T = self.AirIntakePipe_Module_T
        type Inputs_13384           = self.Inputs_13384
    }

    override type Params = Params_13384

    // def makePipeSectionResult(
    //     gp: GasInPipeEl[NamedPipeElDescrG[pipeDescr.PipeElDescr], Gas, Params],
    //     hafg: HeatingAppliance.FlueGas,
    //     hamf: HeatingAppliance.MassFlows,
    //     hapwr: HeatingAppliance.Powers,
    //     haeff: HeatingAppliance.Efficiency,
    //     temp_start: TCelsius,
    //     last_pipe_density: Option[Density],
    //     last_pipe_velocity: Option[FlowVelocity],
    //     last_CrossSectionArea: Option[Area],
    //     last_InnerGeom: Option[PipeShape],
    //     prevO: Option[PipeSectionResult[pipeDescr.PipeElDescr]],
    // )(using alg: ApplicationAlg): SectionResult

    // def makePipeResult(
    //     fd: PipeFullDescrG[pipeDescr.PipeElDescr],
    //     hafg: HeatingAppliance.FlueGas,
    //     hamf: HeatingAppliance.MassFlows,
    //     hapwr: HeatingAppliance.Powers,
    //     haeff: HeatingAppliance.Efficiency,
    //     temp_start: TCelsius,
    //     last_pipe_density: Option[Density],
    //     last_pipe_velocity: Option[FlowVelocity],
    //     gas: Gas,
    // )(using params: Params_13384, alg: ApplicationAlg): Either[MecaFlu_Error, FullResult]

/** Intermediate algebra for EN15544-based fluid mechanics calculations. */
trait MecaFlu_15544_Alg extends MecaFluAlg with HasTypeMembers_15544_Alg:
    self =>

    type ApplicationAlg <: EN15544_V_2023_Application_Alg {
        type AirIntakePipe_Module_T     = self.AirIntakePipe_Module_T
        type CombustionAirPipe_Module_T = self.CombustionAirPipe_Module_T
        type FireboxPipe_Module_T       = self.FireboxPipe_Module_T
        type FluePipe_Module_T          = self.FluePipe_Module_T
        type Pipes_13384                = self.Pipes_13384
        type Pipes_15544                = self.Pipes_15544
        type Inputs_13384               = self.Inputs_13384
        type Inputs_15544               = self.Inputs_15544
    }

    override type Params = DraftCondition

    type DirectionChangeT <: Matchable

    // def makePipeSectionResult(
    //     gip: GasInPipeEl[NamedPipeElDescrG[pipeDescr.PipeElDescr], Gas, Params],
    //     loadQty: LoadQty,
    //     last_CrossSectionArea: Option[Area],
    //     last_InnerGeom: Option[PipeShape],
    //     next_Velocity_middle: Option[FlowVelocity],
    //     next_Density_middle: Option[Density],
    //     gas_temp: PositionOp[TCelsius],
    // )(using
    //     alg: ApplicationAlg,
    //     dfc: DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChangeT]]
    // ): SectionResult

    // def makePipeResult(
    //     fd: PipeFullDescrG[pipeDescr.PipeElDescr],
    //     gas: Gas,
    //     loadQty: LoadQty,
    //     z_geodetical_height: z_geodetical_height,
    //     params: Params,
    // )(using
    //     alg: ApplicationAlg,
    //     ssa: ShortSectionAlg
    // ): Either[MecaFlu_Error, FullResult]

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
            case AirIntakePipeT | CombustionAirPipeT                      =>
                ifCombustionAir
            case FireboxPipeT | FluePipeT | ConnectorPipeT | ChimneyPipeT =>
                ifFlueGas

    // ============================================
    // Cross-Section Area Computation
    // ============================================

    /**
     * Compute cross-section area for a pipe element using type-specific extractors.
     * This pattern is common across all MecaFlu implementations.
     *
     * @param lastCrossSectionArea Previous element's cross-section (for singular elements)
     * @param elementRef String reference for error messages
     * @param pipeType The pipe type for error context
     * @param getStraightArea Extractor: returns Some(area) if straight section, None otherwise
     * @param getSectionChangeAreas Extractor: returns Some((fromArea, toArea)) if section change
     * @param getSingularCrossSection Extractor: returns cross-section if explicitly specified on singular element
     * @return Either an error or the cross-section area at Start|End positions
     */
    def computeCrossSectionArea(
        lastCrossSectionArea: Option[Area],
        elementRef          : String,
        pipeType            : PipeType
    )(
        getStraightArea        : => Option[Area],
        getSectionChangeAreas  : => Option[(Area, Area)],
        getSingularCrossSection: => Option[Area]
    ): Either[MecaFlu_Error, PositionOpX[Start | End, Area]] =
        getStraightArea match
            case Some(area) =>
                QtyDAtPosition.constantAtStartEnd(area).asRight.map(_.atPos)
            case None       =>
                getSectionChangeAreas match
                    case Some((fromArea, toArea)) =>
                        QtyDAtPosition.from(start = fromArea, end = toArea).asRight.map(_.atPos)
                    case None                     =>
                        getSingularCrossSection match
                            case Some(crossSection) =>
                                QtyDAtPosition.constantAtStartEnd(crossSection).asRight.map(_.atPos)
                            case None               =>
                                lastCrossSectionArea match
                                    case Some(lastArea) =>
                                        QtyDAtPosition.constantAtStartEnd(lastArea).asRight.map(_.atPos)
                                    case None           =>
                                        MecaFlu_Error
                                            .CouldNotDetermineCrossSectionArea(
                                                s"$elementRef: could not determine 'cross section area' !!",
                                                pipeType
                                            )
                                            .asLeft

    // ============================================
    // Pipe Section Accumulator State
    // ============================================

    /**
     * State carried between pipe section calculations during accumulation.
     * Used by the mapAccumulate pattern in PipeResult implementations.
     */
    case class AccumulatorState(
        gasTempStart        : TCelsius,
        lastCrossSectionArea: Option[Area],
        lastInnerGeom       : Option[PipeShape],
        // Optional fields for thermal calculations
        lastAirSpaceDetailed: Option[AirSpaceDetailed_V1] = None
    )

    /** Create initial accumulator state for pipe result calculation. */
    def initialAccumulatorState(
        startTemp           : TCelsius,
        lastAirSpace        : Option[AirSpaceDetailed_V1] = None
    ): AccumulatorState =
        AccumulatorState(
            gasTempStart         = startTemp,
            lastCrossSectionArea = None,
            lastInnerGeom        = None,
            lastAirSpaceDetailed = lastAirSpace
        )

    /** Update accumulator state after processing a pipe section. */
    def updateAccumulatorState(
        current                    : AccumulatorState,
        newGasTempEnd              : TCelsius,
        newCrossSectionArea        : Area,
        newInnerGeom               : PipeShape,
        newAirSpaceDetailed        : Option[AirSpaceDetailed_V1] = None
    ): AccumulatorState =
        current.copy(
            gasTempStart         = newGasTempEnd,
            lastCrossSectionArea = Some(newCrossSectionArea),
            lastInnerGeom        = Some(newInnerGeom),
            lastAirSpaceDetailed = newAirSpaceDetailed.orElse(current.lastAirSpaceDetailed)
        )

end MecaFluOps
