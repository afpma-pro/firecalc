/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.domain.ShapeState
import afpma.firecalc.engine.models.geometry.*
import afpma.firecalc.engine.standard.PendingFlowAreaCheck
import afpma.firecalc.engine.typeclasses.*
import afpma.firecalc.units.Vec3

object PropsStateOps_FlowOnly_13384_Instance:

    /**
     * Flow-Only PropsState for EN13384 builders
     *
     * Tracks inner geometry, roughness, and number of flows
     * for flow-only pipe calculations (no thermal properties).
     *
     * Direction tracking fields (optional, only set when SetInitialDirection is used):
     *   - initialFrame: frame set once by SetInitialDirection, never changes
     *   - currentFrame: updated after each DirectionChange with roll defined
     *   - dirBeforePreviousDC: direction BEFORE the previous bend, used to compute angleN2
     */
    case class FlowOnlyPropsState_13384(
        shapeState          : ShapeState                   = ShapeState.Empty,
        roughness           : Option[Roughness]            = None,
        nFlows              : NbOfFlows                    = 1.flow,
        initialFrame        : Option[PipeFrame]            = None,
        currentFrame        : Option[PipeFrame]            = None,
        dirBeforePreviousDC : Option[Vec3]                 = None,
        currentPosition     : Vec3                         = Vec3(0, 0, 0),
        branchOneOffset     : Double                       = 0.0,
        pendingFlowAreaCheck: Option[PendingFlowAreaCheck] = None
    )

    given flowOnlyPropsStateOps13384: PropsStateOps[FlowOnlyPropsState_13384] with
        def isValid(s: FlowOnlyPropsState_13384) =
            s.shapeState.shape.isDefined &&
                s.roughness.isDefined

        def getShapeState(s: FlowOnlyPropsState_13384) = s.shapeState
        def getRoughness (s: FlowOnlyPropsState_13384) = s.roughness

        def getNFlows(s: FlowOnlyPropsState_13384) =
            s.nFlows

        def getPendingFlowAreaCheck(state: FlowOnlyPropsState_13384): Option[PendingFlowAreaCheck] =
            state.pendingFlowAreaCheck

        def setPendingFlowAreaCheck(
            state: FlowOnlyPropsState_13384,
            check: Option[PendingFlowAreaCheck]
        ): FlowOnlyPropsState_13384 =
            state.copy(pendingFlowAreaCheck = check)

        def setInnerShape(state: FlowOnlyPropsState_13384, shape: PipeShape): FlowOnlyPropsState_13384 =
            state.copy(shapeState = ShapeState.Set(shape))

        def setNFlows(state: FlowOnlyPropsState_13384, nFlows: NbOfFlows): FlowOnlyPropsState_13384 =
            state.copy(nFlows = nFlows)

        def materialize(state: FlowOnlyPropsState_13384): FlowOnlyPropsState_13384 =
            state.shapeState match
                case ShapeState.Set(shape) => state.copy(shapeState = ShapeState.Materialized(shape))
                case _                     => state
