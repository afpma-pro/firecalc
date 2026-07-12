/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en13384

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.FlowAreaConservation
import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.impl.common.FramedBuilderSupport
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.standard.ShapeNotMaterialized.Operation
import afpma.firecalc.engine.typeclasses.PropsStateOps

import cats.syntax.all.*

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.IsSplitMergeTurn
import afpma.firecalc.domain.NbOfFlows
import afpma.firecalc.domain.PipeShape
import afpma.firecalc.domain.SetsNumberOfFlows

/**
 * Shared split/merge handling for EN 13384 incremental builders.
 *
 * Extracts duplicated logic from [[FlowOnlyIncrementalBuilder_13384]] and
 * [[ThermalIncrementalBuilder_13384]]. Both builders mix this trait in and
 * provide their own [[PropsState]] type and [[stateOps]] instance.
 *
 * Position reads (currentPosition, branchOneOffset) use default values
 * per H4 — these fields are dead state during the fold.
 */
trait IncrementalBuilderAlg_13384[PS] {
    self: IncrementalBuilderAlg =>

    override protected type PropsState = PS

    protected type StateOpsT <: PropsStateOps[PS]

    protected val stateOps: StateOpsT

    /**
     * Concrete split/merge DTO types provided by each builder.
     * Both carry `absDir: Option[AbsoluteDirection]` and `newInnerShape: PipeShape`.
     */
    protected type SplitDTO
    protected type MergeDTO

    /** Union type for split/merge DTOs. */
    type SplitMergeDTO = SplitDTO | MergeDTO

    // -----------------------------------------------------------------------
    // Site 1a: Split state update
    // -----------------------------------------------------------------------

    /**
     * Handles PropsState update after a SplitSingleFlowIntoTwoFlowsWith90DegTurn
     * conversion step. Validates split geometry, updates frame, sets nFlows=2.
     */
    protected def handleSplitStateUpdate(
        propsState: PS,
        op        : SplitDTO,
        absDir    : Option[AbsoluteDirection],
        newShape  : PipeShape,
        opName    : String,
        convStep  : ConversionStep
    ): ValidatedResult[PS] =
        val branchDirOpt: Option[Vec3] = absDir.map { fd =>
            val (az, el) = AbsoluteDirection.toAzimuthElevationDeg(fd)
            Vec3.fromAzimuthElevation(az, el)
        }
        val elemId = convStep.findNextAddElement.map(_._1).getOrElse(-1)
        val splitValid = branchDirOpt
            .map { _ =>
                validateSplitNotOnAscending(
                    propsState,
                    2.flows,
                    IdIncr(elemId ),
                    opName,
                    branchDirOpt,
                    Vec3  (0, 0, 0),
                    Vec3  (0, 0, 0)
                )
            }
            .getOrElse(().validNel)
        splitValid.andThen { _ =>
            val frameUpdated = stateOps.getCurrentFrame(propsState) match
                case Some(frame) =>
                    val targetVec = branchDirOpt.getOrElse(frame.direction)
                    val newFrame  = frame.applyBendForFinalDir(90.0, targetVec)
                    val stWithDir = stateOps.setDirBeforePreviousDC(propsState, Some(frame.direction))
                    stateOps.setCurrentFrame(stWithDir, Some(newFrame))
                case None        => propsState
            stateOps
                .setNFlows(stateOps.setInnerShape(frameUpdated, newShape), 2.flows)
                .validNel
        }

    // -----------------------------------------------------------------------
    // Site 1b: Merge state update
    // -----------------------------------------------------------------------

    /**
     * Handles PropsState update after a MergeTwoFlowsIntoSingleWith90DegTurn
     * conversion step. Updates frame, sets nFlows=1.
     */
    protected def handleMergeStateUpdate(
        propsState: PS,
        op        : MergeDTO,
        absDir    : Option[AbsoluteDirection],
        newShape  : PipeShape
    ): ValidatedResult[PS] =
        val frameUpdate = absDir match
            case Some(fd) =>
                stateOps.getCurrentFrame(propsState) match
                    case Some(frame) =>
                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        val newFrame  = frame.applyBendForFinalDir(90.0, targetVec)
                        val stWithDir = stateOps.setDirBeforePreviousDC(propsState, Some(frame.direction))
                        stateOps.setCurrentFrame(stWithDir, Some(newFrame))
                    case None        => propsState
            case None     => propsState
        stateOps
            .setNFlows(stateOps.setInnerShape(frameUpdate, newShape), 1.flow)
            .validNel

    // -----------------------------------------------------------------------
    // Site 2: SplitMerge90 construction
    // -----------------------------------------------------------------------

    /**
     * Each builder provides its own SplitMerge90 constructor since the case
     * class lives in the builder's PipeDescr module (different types for
     * FlowOnly vs Thermal, though structurally identical).
     */
    protected def mkSplitMerge90Descr(
        nFlows : NbOfFlows,
        angleN2: Option[QtyD[Degree]],
        shape  : PipeShape
    ): PipeElDescr

    /**
     * Constructs a SplitMerge90 pipe element descriptor for the given split/merge
     * operation. Computes the angle between the previous direction change and
     * the post-bend frame direction.
     */
    protected def mkSplitMerge90(
        st      : PS,
        absDir  : Option[AbsoluteDirection],
        nFlows  : NbOfFlows,
        newShape: PipeShape
    ): ValidatedResult[PipeElDescr] =
        val angleN2 = FramedBuilderSupport.computeAngleN2(
            stateOps.getDirBeforePreviousDC(st),
            stateOps.getCurrentFrame       (st),
            absDir,
            90.0
        )
        mkSplitMerge90Descr(nFlows, angleN2, newShape).validNel

    // -----------------------------------------------------------------------
    // Site 3: SetNumberOfFlows body
    // -----------------------------------------------------------------------

    /**
     * Handles SetNumberOfFlows pre-element operation. Computes the updated
     * state via flow area conservation, validates materialization, and checks
     * split geometry if a split element follows.
     */
    protected def handleSetNumberOfFlows(
        st            : PS,
        nf            : NbOfFlows,
        convStep      : ConversionStep,
        nextElemIdIncr: Int,
        nextElemName  : String
    ): ValidatedResult[PS] =
        val updatedSt = FlowAreaConservation.computeSetNFlows(st, nf, pt)(using stateOps)
        stateOps
            .validateMaterialized(st, Operation.SetNumberOfFlows, pt, nextElemIdIncr, nextElemName)
            .andThen { _ =>
                if !nextAddElementIsSplit(convStep) then
                    validateSplitNotOnAscending(
                        st,
                        nf,
                        IdIncr(nextElemIdIncr),
                        nextElemName,
                        None,
                        Vec3  (0, 0, 0       ),
                        Vec3  (0, 0, 0       ),
                        isSplitElement = false
                    ).map(_ => updatedSt)
                else updatedSt.validNel[IncrementalValidation_Error]
            }

    // -----------------------------------------------------------------------
    // Site 4: Split detection
    // -----------------------------------------------------------------------

    override protected def nextAddElementIsSplit(convStep: ConversionStep): Boolean =
        convStep.findNextAddElement.exists {
            case (_, elem: (IsSplitMergeTurn & SetsNumberOfFlows)) if elem.n_flows == 2.flows => true
            case _ => false
        }
}
