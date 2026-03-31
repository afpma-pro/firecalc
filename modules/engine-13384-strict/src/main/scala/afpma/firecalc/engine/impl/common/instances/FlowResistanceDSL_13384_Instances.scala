/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.typeclasses.FlowResistanceDSL
import afpma.firecalc.engine.models.gtypedefs.*

object FlowResistanceDSL_13384_Instances:

    // Instance for ThermalPipeDescr_13384
    given thermal13384: FlowResistanceDSL[ThermalPipeDescr_13384] with
        def addFlowResistance(name: String, zeta: ζ) =
            AddThermalPipeElement_13384.AddFlowResistance(
                name,
                zeta,
                None
            )

        def addFlowResistance_crossSection(
            name         : String,
            zeta         : ζ,
            cross_section: AreaInCm2
        ) =
            AddThermalPipeElement_13384.AddFlowResistance(
                name,
                zeta,
                Some(Left(cross_section))
            )

        def addFlowResistance_dh(
            name              : String,
            zeta              : ζ,
            hydraulic_diameter: QtyD[Meter]
        ) =
            AddThermalPipeElement_13384.AddFlowResistance(
                name,
                zeta,
                Some(Right(PipeShape.circle(hydraulic_diameter)))
            )

        def addPressureDiff(
            name               : String,
            pressure_difference: QtyD[Pascal]
        ) =
            AddThermalPipeElement_13384.AddPressureDiff(
                name,
                pressure_difference
            )

    // Instance for FlowOnlyPipeDescr_13384
    given flowOnly13384: FlowResistanceDSL[FlowOnlyPipeDescr_13384] with
        def addFlowResistance(name: String, zeta: ζ) =
            AddFlowOnlyPipeElement_13384.AddFlowResistance(
                name,
                zeta,
                None
            )

        def addFlowResistance_crossSection(
            name         : String,
            zeta         : ζ,
            cross_section: AreaInCm2
        ) =
            AddFlowOnlyPipeElement_13384.AddFlowResistance(
                name,
                zeta,
                Some(Left(cross_section))
            )

        def addFlowResistance_dh(
            name              : String,
            zeta              : ζ,
            hydraulic_diameter: QtyD[Meter]
        ) =
            AddFlowOnlyPipeElement_13384.AddFlowResistance(
                name,
                zeta,
                Some(Right(PipeShape.circle(hydraulic_diameter)))
            )

        def addPressureDiff(
            name               : String,
            pressure_difference: QtyD[Pascal]
        ) =
            AddFlowOnlyPipeElement_13384.AddPressureDiff(
                name,
                pressure_difference
            )
