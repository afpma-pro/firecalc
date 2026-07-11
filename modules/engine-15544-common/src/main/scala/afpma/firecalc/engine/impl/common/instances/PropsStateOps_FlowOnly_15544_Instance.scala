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

object PropsStateOps_FlowOnly_15544_Instance:

    /**
     * Flow-Only PropsState for EN15544 builders
     *
     * Note: Uses 'geometry' instead of 'innerShape' to match
     * EN15544 naming conventions. Tracks geometry, roughness,
     * and number of flows for flow-only pipe calculations.
     *
     * Direction tracking fields (optional, only set when SetInitialDirection is used):
     *   - initialFrame: frame set once by SetInitialDirection, never changes
     *   - currentFrame: updated after each DirectionChange with roll defined
     *   - dirBeforePreviousDC: direction BEFORE the previous bend, used to compute angleN2
     */
    case class FlowOnlyPropsState_15544(
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

    given flowOnlyPropsStateOps15544: PropsStateOps[FlowOnlyPropsState_15544] with
        def isValid(s: FlowOnlyPropsState_15544) =
            s.shapeState.shape.isDefined &&
                s.roughness.isDefined

        def getShapeState(s: FlowOnlyPropsState_15544) = s.shapeState
        def getRoughness (s: FlowOnlyPropsState_15544) = s.roughness

        def getNFlows(s: FlowOnlyPropsState_15544) =
            s.nFlows

        def getPendingFlowAreaCheck(state: FlowOnlyPropsState_15544): Option[PendingFlowAreaCheck] =
            state.pendingFlowAreaCheck

        def setPendingFlowAreaCheck(
            state: FlowOnlyPropsState_15544,
            check: Option[PendingFlowAreaCheck]
        ): FlowOnlyPropsState_15544 =
            state.copy(pendingFlowAreaCheck = check)

        def setInnerShape(state: FlowOnlyPropsState_15544, shape: PipeShape): FlowOnlyPropsState_15544 =
            state.copy(shapeState = ShapeState.Set(shape))

        def setNFlows(state: FlowOnlyPropsState_15544, nFlows: NbOfFlows): FlowOnlyPropsState_15544 =
            state.copy(nFlows = nFlows)

        def materialize(state: FlowOnlyPropsState_15544): FlowOnlyPropsState_15544 =
            state.shapeState match
                case ShapeState.Set(s) => state.copy(shapeState = ShapeState.Materialized(s))
                case other             => state
