/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.*
import afpma.firecalc.engine.impl.common.typeclasses.DirectionChangeDSL_15544
import afpma.firecalc.units.coulombutils.*
import coulomb.*

object DirectionChangeDSL_15544_Instances:

    // Instance for FlowOnlyPipeDescr_15544
    given flowOnly15544: DirectionChangeDSL_15544[FlowOnlyPipeDescr_15544] with
        def addSharpAngle_0_to_180deg(
            name: String,
            angle: QtyD[Degree],
            angleN2: Option[QtyD[Degree]] = None
        ) =
            AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                name,
                angle,
                angleN2
            )
        
        def addCircularArc60(name: String) =
            AddFlowOnlyPipeElement_15544.AddCircularArc_60(name)
