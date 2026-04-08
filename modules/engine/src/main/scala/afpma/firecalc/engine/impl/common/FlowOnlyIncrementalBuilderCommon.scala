/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.typeclasses.*

import cats.syntax.all.*

/**
 * Shared logic for FlowOnly IncrementalBuilders (EN13384 and EN15544).
 *
 * Provides concrete implementations for methods that are identical across
 * both standards, using the unified FlowOnlyPropsState type.
 *
 * Each standard's builder extends this trait and provides:
 * - Type bindings for PipeElDescr, IncrDescr, SetProp, AddElement
 * - DTO-specific given instances (HasInnerShapeAtPos, HasLength, HasVerticalElev)
 * - mkFullElementsDescr (different factory wiring per standard)
 * - updateStateBeforeConversionStep (pattern-matches on standard-specific SetProp ADT)
 * - updateStateAfterConversionStep (pattern-matches on standard-specific AddElement ADT)
 * - Standard-specific builder convenience methods
 * - addElementHasAbsDir hook (for postBuildValidation)
 */
trait FlowOnlyIncrementalBuilderCommon extends IncrementalBuilderAlg:

    // ── PropsState is unified ──────────────────────────────────────────
    override protected type PropsState = FlowOnlyPropsState
    protected val stateOps: PropsStateOps[PropsState] = summon[PropsStateOps[PropsState]]

    given nbOfFlowsFromPropsState: Function1[PropsState, NbOfFlows] = stateOps.getNFlows

    extension (propsState: PropsState)
        override def isValid: Boolean   = stateOps.isValid(propsState)
        def nf              : NbOfFlows = stateOps.getNFlows(propsState)

    // ── Abstract hooks ────────────────────────────────────────────────────
    // Both hooks currently have identical implementations across 13384/15544
    // (isInstanceOf[AddDirectionChange]). They are abstract because the AddElement
    // type member differs per standard and the common trait cannot pattern-match on
    // standard-specific subtypes.
    //
    // NOTE: If the AddElement ADT grows beyond 5 variants, consider replacing
    // isInstanceOf checks with exhaustive sealed matches for compile-time safety.
    // See plans/architecture-simplifications-review.md "Hook Drift Risk Assessment".

    /** Whether the given AddElement carries an absDir (direction tracking).
     *
     * Used in postBuildValidation's FinalDirWithoutInitialDirection check.
     * NOTE: For current FlowOnly builders this branch is unreachable because any
     * absDir-bearing AddElement is also an AddElement (making hasGeometry true first,
     * which triggers GeometryWithoutInitialDirection). Retained for semantic clarity,
     * future-proofing, and ThermalBuilder parity.
     */
    protected def addElementHasAbsDir(ae: AddElement): Boolean

    // ── Common pipeline methods ────────────────────────────────────────

    override def define(iDescrs: IncrDescr*): PipeIncrDescr =
        val iiVec = iDescrs.toVector.mapWithIndex((x, i) => (IdIncr(i), x))
        PipeIncrDescrG[Id_IncrDescr](pt, iiVec)

    extension (piDescr: PipeIncrDescr) override def listIncrDescr(): Vector[Id_IncrDescr] = piDescr.idescrs

    override protected def mkInitPropsState(iPipeIncrDescr: PipeIncrDescr): PropsState =
        FlowOnlyPropsState()

    override protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr =
        PipeFullDescr(elements = Vector.empty, iPipeIncrDescr.pipeType)

    override protected def currentFrameFromPropsState(s: PropsState): Option[PipeFrame] =
        s.currentFrame

    override protected def applyExternalFrame(s: PropsState, frame: PipeFrame): PropsState =
        if s.initialFrame.isDefined then s
        else s.copy(initialFrame = Some(frame), currentFrame = Some(frame))

    override protected def postBuildValidation(
        incrDescrs: Vector[Id_IncrDescr],
        finalState: PropsState
    ): ValidatedResult[Unit] =
        val hasGeometry = incrDescrs.exists:
            case (_, _: AddElement) => true
            case _ => false
        if hasGeometry && finalState.initialFrame.isEmpty then GeometryWithoutInitialDirection(pt).invalidNel
        else
            val hasFinalDir = incrDescrs.exists:
                case (_, ae: AddElement) => addElementHasAbsDir(ae)
                case _ => false
            if hasFinalDir && finalState.initialFrame.isEmpty then FinalDirWithoutInitialDirection(pt).invalidNel
            else ().validNel

    // Minimal ElementFactory object required by trait
    object ElementFactory extends ElementFactoryModule

    // ── Shared builder convenience methods ─────────────────────────────

    override protected def isForbiddenAddElementAtStart(
        addElement: AddElement
    ): Boolean = isDirectionChange(addElement)

    override protected def isForbiddenAddElementAtEnd(
        addElement: AddElement
    ): Boolean = isDirectionChange(addElement)

    /** Whether the given AddElement is a direction change.
     * Used by isForbiddenAddElementAtStart/End for boundary validation.
     *
     * WARNING: Do NOT move the `AddElement.name` extension method into this trait —
     * it causes an infinite recursion via the `name` call in IncrementalBuilderAlg's
     * validateBoundaryElements. Each concrete builder must define it locally.
     */
    protected def isDirectionChange(ae: AddElement): Boolean
