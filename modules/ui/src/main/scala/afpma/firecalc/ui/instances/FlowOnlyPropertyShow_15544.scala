/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
import afpma.firecalc.dto.all.FlowOnlyChannelTopologyOp_15544.*

import afpma.firecalc.ui.displayUnits

import cats.Show
import cats.syntax.show.*

import coulomb.policy.standard.given

import io.taig.babel.Locale

/** Show instances for 15544 flow-only SetProperty subtypes (compact summary display). */
class FlowOnlyPropertyShow_15544(using DisplayUnits, Locale):

    private val showPipeShape: Show[PipeShape] = displayUnits(
        PipeShape.show_PipeShape_valueCm_noUnit,
        PipeShape.show_PipeShape_valueIn_noUnit
    )

    private def showXYZ(x: Length, y: Length, z: Length): String =
        s"X = ${x.toUnit[Centimeter].showP}, Y = ${y.toUnit[Centimeter].showP}, Z = ${z.toUnit[Centimeter].showP}"

    given Show[SetInnerShape]                                 = Show.show(s => showPipeShape.show(s.shape))
    // Dev-only variant (backend-forbidden, not menu-reachable). Show mirrors SetInnerShape
    // so the DynamicFlowOnlyPipeSlotPanel exhaustiveness clause can render it read-only.
    given Show[SetInnerShapePreventSectionGeometryChangeAuto] =
        Show.show(s => showPipeShape.show(s.shape))
    given Show[SetRoughness]                                  = Show.show(s => s.roughness.showP)
    given Show[SetMaterial]                                   = Show.show(s => s.material.show)
    given Show[SetNumberOfFlows]                              = Show.show(s => s.n_flows.show)

end FlowOnlyPropertyShow_15544
