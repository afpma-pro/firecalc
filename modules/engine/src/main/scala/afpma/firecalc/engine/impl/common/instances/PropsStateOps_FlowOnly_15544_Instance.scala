/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.impl.common.typeclasses.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.units.coulombutils.*
import coulomb.*

object PropsStateOps_FlowOnly_15544_Instance:

    /**
     * Flow-Only PropsState for EN15544 builders
     * 
     * Note: Uses 'geometry' instead of 'innerShape' to match
     * EN15544 naming conventions. Tracks geometry, roughness,
     * and number of flows for flow-only pipe calculations.
     */
    case class FlowOnlyPropsState_15544(
        geometry: Option[PipeShape] = None,
        roughness: Option[Roughness] = None,
        nFlows: Option[NbOfFlows] = Some(1.flow)
    )

    given flowOnlyPropsStateOps15544: PropsStateOps[FlowOnlyPropsState_15544] with
        def isValid(s: FlowOnlyPropsState_15544) = 
            s.geometry.isDefined && 
            s.roughness.isDefined && 
            s.nFlows.isDefined
        
        def getInnerShape(s: FlowOnlyPropsState_15544) = s.geometry
        def getRoughness(s: FlowOnlyPropsState_15544) = s.roughness
        
        def getNFlows(s: FlowOnlyPropsState_15544) = 
            s.nFlows.getOrElse(1.flow)
