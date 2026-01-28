/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.*
import afpma.firecalc.engine.impl.common.typeclasses.FlowResistanceDSL
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.units.coulombutils.*
import coulomb.*

object FlowResistanceDSL_15544_Instances:

    // Instance for FlowOnlyPipeDescr_15544
    given flowOnly15544: FlowResistanceDSL[FlowOnlyPipeDescr_15544] with
        def addFlowResistance(name: String, zeta: ζ) =
            AddFlowOnlyPipeElement_15544.AddFlowResistance(
                name,
                zeta,
                None
            )
        
        def addFlowResistance_crossSection(
            name: String,
            zeta: ζ,
            cross_section: AreaInCm2
        ) =
            AddFlowOnlyPipeElement_15544.AddFlowResistance(
                name,
                zeta,
                Some(Left(cross_section))
            )
        
        def addFlowResistance_dh(
            name: String,
            zeta: ζ,
            hydraulic_diameter: QtyD[Meter]
        ) =
            AddFlowOnlyPipeElement_15544.AddFlowResistance(
                name,
                zeta,
                Some(Right(PipeShape.circle(hydraulic_diameter)))
            )
        
        def addPressureDiff(
            name: String,
            pressure_difference: QtyD[Pascal]
        ) =
            AddFlowOnlyPipeElement_15544.AddPressureDiff(
                name,
                pressure_difference
            )
