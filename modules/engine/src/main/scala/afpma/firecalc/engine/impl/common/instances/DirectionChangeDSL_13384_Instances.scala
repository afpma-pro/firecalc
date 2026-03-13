/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.common.typeclasses.DirectionChangeDSL_13384

object DirectionChangeDSL_13384_Instances:

    // Instance for ThermalPipeDescr_13384
    given thermal13384: DirectionChangeDSL_13384[ThermalPipeDescr_13384] with
        def addAngleVifDe0A90(name: String, angle: QtyD[Degree], roll: QtyD[Degree]) =
            AddThermalPipeElement_13384.AddSharpeAngle_0_to_90(name, angle, Some(roll))

        def addAngleVifDe0A90_unsafe(name: String, angle: QtyD[Degree], roll: QtyD[Degree]) =
            AddThermalPipeElement_13384.AddSharpeAngle_0_to_90_Unsafe(name, angle, Some(roll))

        def addCoudeCourbe90(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddThermalPipeElement_13384.AddSmoothCurve_90(name, R, Some(roll))

        def addCoudeCourbe90_unsafe(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddThermalPipeElement_13384.AddSmoothCurve_90_Unsafe(name, R, Some(roll))

        def addCoudeCourbe60(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddThermalPipeElement_13384.AddSmoothCurve_60(name, R, Some(roll))

        def addCoudeCourbe60_unsafe(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddThermalPipeElement_13384.AddSmoothCurve_60_Unsafe(name, R, Some(roll))

        def addCoudeASegment90Avec2A45(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddThermalPipeElement_13384.AddElbows_2x45(name, R, Some(roll))

        def addCoudeASegment90Avec3A30(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddThermalPipeElement_13384.AddElbows_3x30(name, R, Some(roll))

        def addCoudeASegment90Avec4A22p5(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddThermalPipeElement_13384.AddElbows_4x22p5(name, R, Some(roll))

        def addAngleSpecifique(
            name : String,
            angle: QtyD[Degree],
            zeta : Double,
            roll : QtyD[Degree]
        ) =
            AddThermalPipeElement_13384.AddAngleAdjustable(
                name,
                angle,
                zeta.unitless,
                Some(roll)
            )

    // Instance for FlowOnlyPipeDescr_13384
    given flowOnly13384: DirectionChangeDSL_13384[FlowOnlyPipeDescr_13384] with
        def addAngleVifDe0A90(name: String, angle: QtyD[Degree], roll: QtyD[Degree]) =
            AddFlowOnlyPipeElement_13384.AddSharpeAngle_0_to_90(name, angle, Some(roll))

        def addAngleVifDe0A90_unsafe(name: String, angle: QtyD[Degree], roll: QtyD[Degree]) =
            AddFlowOnlyPipeElement_13384.AddSharpeAngle_0_to_90_Unsafe(name, angle, Some(roll))

        def addCoudeCourbe90(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddFlowOnlyPipeElement_13384.AddSmoothCurve_90(name, R, Some(roll))

        def addCoudeCourbe90_unsafe(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddFlowOnlyPipeElement_13384.AddSmoothCurve_90_Unsafe(name, R, Some(roll))

        def addCoudeCourbe60(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddFlowOnlyPipeElement_13384.AddSmoothCurve_60(name, R, Some(roll))

        def addCoudeCourbe60_unsafe(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddFlowOnlyPipeElement_13384.AddSmoothCurve_60_Unsafe(name, R, Some(roll))

        def addCoudeASegment90Avec2A45(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddFlowOnlyPipeElement_13384.AddElbows_2x45(name, R, Some(roll))

        def addCoudeASegment90Avec3A30(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddFlowOnlyPipeElement_13384.AddElbows_3x30(name, R, Some(roll))

        def addCoudeASegment90Avec4A22p5(name: String, R: QtyD[Meter], roll: QtyD[Degree]) =
            AddFlowOnlyPipeElement_13384.AddElbows_4x22p5(name, R, Some(roll))

        def addAngleSpecifique(
            name : String,
            angle: QtyD[Degree],
            zeta : Double,
            roll : QtyD[Degree]
        ) =
            AddFlowOnlyPipeElement_13384.AddAngleAdjustable(
                name,
                angle,
                zeta.unitless,
                Some(roll)
            )
