/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AbsoluteDirection

import afpma.firecalc.engine.typeclasses.DirectionChangeDSL_15544

object DirectionChangeDSL_15544_Instances:

    // Instance for FlowOnlyPipeDescr_15544
    given flowOnly15544: DirectionChangeDSL_15544[FlowOnlyPipeDescr_15544] with
        def addSharpAngle_0_to_180deg(
            name  : String,
            angle : QtyD[Degree],
            absDir: AbsoluteDirection
        ) =
            AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                name,
                angle,
                Some(absDir)
            )

        def addCircularArc60(name: String, absDir: AbsoluteDirection) =
            AddFlowOnlyPipeElement_15544.AddCircularArc_60(name, Some(absDir))
