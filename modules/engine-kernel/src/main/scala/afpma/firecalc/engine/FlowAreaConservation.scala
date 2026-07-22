/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import algebra.instances.all.doubleAlgebra

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.ExpectedDimCircle
import afpma.firecalc.engine.ExpectedDimRectangle
import afpma.firecalc.engine.ExpectedDimSquare
import afpma.firecalc.engine.ExpectedDimension
import afpma.firecalc.engine.FlowAreaTransition
import afpma.firecalc.engine.standard.FlowTransitionChangesTotalCrossSection
import afpma.firecalc.engine.PendingFlowAreaCheck
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.typeclasses.PropsStateOps

import coulomb.*
import coulomb.policy.standard.given

import scala.annotation.nowarn

import afpma.firecalc.domain.NbOfFlows
import afpma.firecalc.domain.PipeShape
import afpma.firecalc.domain.PipeShape.Circle
import afpma.firecalc.domain.PipeShape.Rectangle
import afpma.firecalc.domain.PipeShape.Square

object FlowAreaConservation:

    /**
     * ⚠ FLOW AREA CHECK DEACTIVATED
     *
     * The flow area conservation constraint (splits/merges must preserve total
     * cross-sectional area within 5 cm² tolerance) is temporarily disabled.
     *
     * Reason: <TBD — fill on commit>
     * Re-enable: set FLOW_AREA_CHECK_ENABLED = true and un-ignore tests.
     *
     * Affected subsystem (all temporarily dormant):
     *   - FlowAreaTransition, PendingFlowAreaCheck, FlowTransitionChangesTotalCrossSection (standard.scala)
     *   - ExpectedDimRectangle/Square/Circle (standard.scala)
     *   - totalFlowArea, approximatelySameArea, computeExpectedDimension (this file)
     *   - Tests: AreaConservationSuite, Pipes_15544_IncrementalBuilder (area checks)
     */
    private val FLOW_AREA_CHECK_ENABLED: Boolean = false

    /**
     * Absolute tolerance for flow area conservation on splits/merges.
     * A split or merge is considered valid when |beforeTotalArea − afterTotalArea| ≤ this tolerance.
     * Default: 5 cm² — tight enough to catch genuine mistakes, forgiving enough for floating-point rounding.
     * Applies to both EN 13384 and EN 15544 incremental builders.
     */
    val FLOW_AREA_CONSERVATION_TOLERANCE: Area = 5.0.squareCentimeters

    def totalFlowArea(shape: PipeShape, nFlows: NbOfFlows): Area =
        shape.area * nFlows.asQty

    def approximatelySameArea(a: Area, b: Area): Boolean =
        val av = a.toUnit[Meter ^ 2].value
        val bv = b.toUnit[Meter ^ 2].value
        math.abs(av - bv) <= FLOW_AREA_CONSERVATION_TOLERANCE.toUnit[Meter ^ 2].value

    // @deprecated usage: ExpectedDimRectangle/Square/Circle are dormant
    // while FLOW_AREA_CHECK_ENABLED = false. See banner doc-comment above.
    @nowarn("cat=deprecation")
    def computeExpectedDimension(
        afterShape         : PipeShape,
        expectedAreaPerFlow: Area
    ): ExpectedDimension =
        afterShape match
            case Rectangle(a, b)  =>
                val enteredArea = PipeShape.Rectangle(a, b).area
                ExpectedDimRectangle  (
                    enteredWidth   = a,
                    enteredHeight  = b,
                    enteredArea    = enteredArea,
                    expectedHeight = expectedAreaPerFlow / a,
                    expectedArea   = expectedAreaPerFlow
                )
            case Square(side)     =>
                val enteredArea    = PipeShape.Square(side).area
                val expectedSquare = PipeShape.Square.fromArea(expectedAreaPerFlow)
                ExpectedDimSquare (
                    enteredSide  = side,
                    enteredArea  = enteredArea,
                    expectedSide = expectedSquare.side,
                    expectedArea = expectedAreaPerFlow
                )
            case Circle(diameter) =>
                val enteredArea    = PipeShape.Circle(diameter).area
                val expectedCircle = PipeShape.Circle.fromArea(expectedAreaPerFlow)
                ExpectedDimCircle (
                    enteredDiameter  = diameter,
                    enteredArea      = enteredArea,
                    expectedDiameter = expectedCircle.diameter,
                    expectedArea     = expectedAreaPerFlow
                )

    /**
     * Centralized SetNumberOfFlows handler — creates PendingFlowAreaCheck when flows change.
     * Uses PropsStateOps typeclass for state access — eliminates copy-paste across builders.
     *
     * No-op guard: if nFlows already equals the requested value, returns state unchanged
     * (preserves any existing pendingFlowAreaCheck). This prevents silent cancellation of
     * pending checks from redundant SetNumberOfFlows calls (e.g., UI re-renders).
     */
    // @deprecated usage: PendingFlowAreaCheck / FlowAreaTransition are dormant
    // while FLOW_AREA_CHECK_ENABLED = false. See banner doc-comment above.
    @nowarn("cat=deprecation")
    def computeSetNFlows[S](
        st: S,
        nf: NbOfFlows,
        pt: PipeType
    )(using ops: PropsStateOps[S]): S =
        val currentFlows = ops.getNFlows(st)
        if currentFlows == nf then st // no-op — preserve pendingFlowAreaCheck
        else
            ops.getInnerShape(st) match
                case None              =>
                    // No shape yet — still set the nFlows value, but skip the
                    // PendingFlowAreaCheck. There's no "before" shape to check
                    // against; the area conservation check will happen when the
                    // shape is set later (if it changes after nFlows is set).
                    ops.setNFlows(st, nf)
                case Some(beforeShape) =>
                    val nextSt = ops.setNFlows(st, nf)
                    if !FLOW_AREA_CHECK_ENABLED then nextSt
                    else
                        val pending = Some(
                            PendingFlowAreaCheck(
                                beforeShape = beforeShape,
                                beforeFlows = currentFlows,
                                afterFlows  = nf,
                                transition  =
                                    if nf > currentFlows then FlowAreaTransition.Split
                                    else FlowAreaTransition.Merge
                            )
                        )
                        ops.setPendingFlowAreaCheck(nextSt, pending)

    /**
     * Centralized SetInnerShape validation with area conservation check.
     * Uses PropsStateOps typeclass for state access — eliminates copy-paste across builders.
     */
    // @deprecated usage: FlowTransitionChangesTotalCrossSection is dormant
    // while FLOW_AREA_CHECK_ENABLED = false. See banner doc-comment above.
    @nowarn("cat=deprecation")
    def validateSetInnerShape[S](
        st          : S,
        afterShape  : PipeShape,
        pt          : PipeType,
        elementIndex: Int,
        elementName : String
    )(using ops: PropsStateOps[S]): Either[FlowTransitionChangesTotalCrossSection, S] =
        ops.getPendingFlowAreaCheck(st) match
            case None        =>
                Right(ops.setInnerShape(st, afterShape))
            case Some(check) =>
                val beforeTotalArea = totalFlowArea(check.beforeShape, check.beforeFlows)
                val afterTotalArea  = totalFlowArea(afterShape, check.afterFlows)
                if !FLOW_AREA_CHECK_ENABLED then
                    Right(ops.setInnerShape(ops.setPendingFlowAreaCheck(st, None), afterShape))
                else if approximatelySameArea(beforeTotalArea, afterTotalArea) then
                    Right(ops.setInnerShape(ops.setPendingFlowAreaCheck(st, None), afterShape))
                else
                    val expectedAreaPerFlow = beforeTotalArea / check.afterFlows.asQty
                    val expectedDimension   = computeExpectedDimension(afterShape, expectedAreaPerFlow)
                    Left(
                        FlowTransitionChangesTotalCrossSection       (
                            transition        = check.transition,
                            beforeTotalArea   = beforeTotalArea,
                            beforeFlows       = check.beforeFlows,
                            afterFlows        = check.afterFlows,
                            expectedDimension = expectedDimension,
                            sectionTyp        = pt,
                            elementIndex      = elementIndex,
                            elementName       = elementName
                        )
                    )
