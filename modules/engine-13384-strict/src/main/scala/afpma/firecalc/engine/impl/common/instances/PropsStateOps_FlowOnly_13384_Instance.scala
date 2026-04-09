/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.typeclasses.*
import afpma.firecalc.engine.models.geometry.*

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
        innerShape         : Option[PipeShape] = None,
        roughness          : Option[Roughness] = None,
        nFlows             : Option[NbOfFlows] = Some(1.flow),
        initialFrame       : Option[PipeFrame] = None,
        currentFrame       : Option[PipeFrame] = None,
        dirBeforePreviousDC: Option[Vec3]      = None
    )

    given flowOnlyPropsStateOps13384: PropsStateOps[FlowOnlyPropsState_13384] with
        def isValid(s: FlowOnlyPropsState_13384) =
            s.innerShape.isDefined &&
                s.roughness.isDefined &&
                s.nFlows.isDefined

        def getInnerShape(s: FlowOnlyPropsState_13384) = s.innerShape
        def getRoughness (s: FlowOnlyPropsState_13384) = s.roughness

        def getNFlows(s: FlowOnlyPropsState_13384) =
            s.nFlows.getOrElse(1.flow)
