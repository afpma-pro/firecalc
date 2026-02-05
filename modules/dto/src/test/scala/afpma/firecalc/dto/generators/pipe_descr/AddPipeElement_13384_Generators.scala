/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import org.scalacheck.Gen
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.generators.base.*
import afpma.firecalc.utils.*
import afpma.firecalc.units.coulombutils.*

trait AddPipeElement_13384_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators:

    // Section Elements - Vertical
    def genAddSectionVertical_13384: Gen[AddThermalPipeElement_13384_V1.AddSectionVertical] =
        for
            name <- genSectionName
            elevation <- Gen.choose(-700.0, 700.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddSectionVertical(name, elevation)

    def genAddSectionVertical_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSectionVertical] =
        for
            name <- genSectionName
            elevation <- Gen.choose(-700.0, 700.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddSectionVertical(name, elevation)

    // Section Elements - Horizontal
    def genAddSectionHorizontal_13384: Gen[AddThermalPipeElement_13384_V1.AddSectionHorizontal] =
        for
            name <- genSectionName
            length <- Gen.choose(5.0, 300.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddSectionHorizontal(name, length)

    def genAddSectionHorizontal_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSectionHorizontal] =
        for
            name <- genSectionName
            length <- Gen.choose(5.0, 300.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddSectionHorizontal(name, length)

    // Section Elements - Slopped
    def genAddSectionSlopped_13384: Gen[AddThermalPipeElement_13384_V1.AddSectionSlopped] =
        for
            name <- genSectionName
            length <- Gen.choose(5.0, 700.0).map(_.cm)
            elevation <- Gen.choose(5.0, 700.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddSectionSlopped(name, length, elevation)

    def genAddSectionSlopped_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSectionSlopped] =
        for
            name <- genSectionName
            length <- Gen.choose(5.0, 700.0).map(_.cm)
            elevation <- Gen.choose(5.0, 700.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddSectionSlopped(name, length, elevation)

    // Direction Changes - Adjustable Angle
    def genAddAngleAdjustable_13384: Gen[AddThermalPipeElement_13384_V1.AddAngleAdjustable] =
        for
            name <- genSectionName
            angle <- Gen.choose(0.0, 180.0).map(_.degrees)
            zeta <- genZeta
        yield AddThermalPipeElement_13384_V1.AddAngleAdjustable(name, angle, zeta)

    def genAddAngleAdjustable_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddAngleAdjustable] =
        for
            name <- genSectionName
            angle <- Gen.choose(0.0, 180.0).map(_.degrees)
            zeta <- genZeta
        yield AddFlowOnlyPipeElement_13384_V1.AddAngleAdjustable(name, angle, zeta)

    // Direction Changes - Sharp Angle 0-90
    def genAddSharpeAngle_0_to_90_13384: Gen[AddThermalPipeElement_13384_V1.AddSharpeAngle_0_to_90] =
        for
            name <- genSectionName
            angle <- Gen.choose(0.0, 90.0).map(_.degrees)
        yield AddThermalPipeElement_13384_V1.AddSharpeAngle_0_to_90(name, angle)

    def genAddSharpeAngle_0_to_90_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSharpeAngle_0_to_90] =
        for
            name <- genSectionName
            angle <- Gen.choose(0.0, 90.0).map(_.degrees)
        yield AddFlowOnlyPipeElement_13384_V1.AddSharpeAngle_0_to_90(name, angle)

    // Direction Changes - Sharp Angle 0-90 Unsafe
    def genAddSharpeAngle_0_to_90_Unsafe_13384: Gen[AddThermalPipeElement_13384_V1.AddSharpeAngle_0_to_90_Unsafe] =
        for
            name <- genSectionName
            angle <- Gen.choose(0.0, 90.0).map(_.degrees)
        yield AddThermalPipeElement_13384_V1.AddSharpeAngle_0_to_90_Unsafe(name, angle)

    def genAddSharpeAngle_0_to_90_Unsafe_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSharpeAngle_0_to_90_Unsafe] =
        for
            name <- genSectionName
            angle <- Gen.choose(0.0, 90.0).map(_.degrees)
        yield AddFlowOnlyPipeElement_13384_V1.AddSharpeAngle_0_to_90_Unsafe(name, angle)

    // Direction Changes - Smooth Curve 90 degrees
    def genAddSmoothCurve_90_13384: Gen[AddThermalPipeElement_13384_V1.AddSmoothCurve_90] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddSmoothCurve_90(name, radius)

    def genAddSmoothCurve_90_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_90] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_90(name, radius)

    // Direction Changes - Smooth Curve 90 degrees Unsafe
    def genAddSmoothCurve_90_Unsafe_13384: Gen[AddThermalPipeElement_13384_V1.AddSmoothCurve_90_Unsafe] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddSmoothCurve_90_Unsafe(name, radius)

    def genAddSmoothCurve_90_Unsafe_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_90_Unsafe] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_90_Unsafe(name, radius)

    // Direction Changes - Smooth Curve 60 degrees
    def genAddSmoothCurve_60_13384: Gen[AddThermalPipeElement_13384_V1.AddSmoothCurve_60] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddSmoothCurve_60(name, radius)

    def genAddSmoothCurve_60_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_60] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_60(name, radius)

    // Direction Changes - Smooth Curve 60 degrees Unsafe
    def genAddSmoothCurve_60_Unsafe_13384: Gen[AddThermalPipeElement_13384_V1.AddSmoothCurve_60_Unsafe] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddSmoothCurve_60_Unsafe(name, radius)

    def genAddSmoothCurve_60_Unsafe_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_60_Unsafe] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_60_Unsafe(name, radius)

    // Direction Changes - Elbows 2x45
    def genAddElbows_2x45_13384: Gen[AddThermalPipeElement_13384_V1.AddElbows_2x45] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddElbows_2x45(name, radius)

    def genAddElbows_2x45_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddElbows_2x45] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddElbows_2x45(name, radius)

    // Direction Changes - Elbows 3x30
    def genAddElbows_3x30_13384: Gen[AddThermalPipeElement_13384_V1.AddElbows_3x30] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddElbows_3x30(name, radius)

    def genAddElbows_3x30_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddElbows_3x30] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddElbows_3x30(name, radius)

    // Direction Changes - Elbows 4x22.5
    def genAddElbows_4x22p5_13384: Gen[AddThermalPipeElement_13384_V1.AddElbows_4x22p5] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddElbows_4x22p5(name, radius)

    def genAddElbows_4x22p5_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddElbows_4x22p5] =
        for
            name <- genSectionName
            radius <- Gen.choose(5.0, 100.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddElbows_4x22p5(name, radius)

    // Section Changes - Decrease
    def genAddSectionDecrease_13384: Gen[AddThermalPipeElement_13384_V1.AddSectionDecrease] =
        for
            name <- genSectionName
            diameter <- Gen.choose(10.0, 30.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddSectionDecrease(name, diameter)

    def genAddSectionDecrease_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSectionDecrease] =
        for
            name <- genSectionName
            diameter <- Gen.choose(10.0, 30.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddSectionDecrease(name, diameter)

    // Section Changes - Increase
    def genAddSectionIncrease_13384: Gen[AddThermalPipeElement_13384_V1.AddSectionIncrease] =
        for
            name <- genSectionName
            diameter <- Gen.choose(10.0, 30.0).map(_.cm)
        yield AddThermalPipeElement_13384_V1.AddSectionIncrease(name, diameter)

    def genAddSectionIncrease_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddSectionIncrease] =
        for
            name <- genSectionName
            diameter <- Gen.choose(10.0, 30.0).map(_.cm)
        yield AddFlowOnlyPipeElement_13384_V1.AddSectionIncrease(name, diameter)

    // Cross-section generator for flow resistance
    def genCrossSection_13384: Gen[OptionOfEither[AreaInCm2, PipeShape]] =
        Gen.oneOf(
            Gen.const(NoneOfEither),
            genPipeShape.map(SomeRight(_))
        )

    // Flow Resistance
    def genAddFlowResistance_13384: Gen[AddThermalPipeElement_13384_V1.AddFlowResistance] =
        for
            name <- genSectionName
            zeta <- genZeta
            crossSection <- genCrossSection_13384
        yield AddThermalPipeElement_13384_V1.AddFlowResistance(name, zeta, crossSection)

    def genAddFlowResistance_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddFlowResistance] =
        for
            name <- genSectionName
            zeta <- genZeta
            crossSection <- genCrossSection_13384
        yield AddFlowOnlyPipeElement_13384_V1.AddFlowResistance(name, zeta, crossSection)

    // Pressure Difference
    def genAddPressureDiff_13384: Gen[AddThermalPipeElement_13384_V1.AddPressureDiff] =
        for
            name <- genSectionName
            pressure <- Gen.choose(1, 100).map(_.pascals)
        yield AddThermalPipeElement_13384_V1.AddPressureDiff(name, pressure)

    def genAddPressureDiff_FlowOnly_13384: Gen[AddFlowOnlyPipeElement_13384_V1.AddPressureDiff] =
        for
            name <- genSectionName
            pressure <- Gen.choose(1, 100).map(_.pascals)
        yield AddFlowOnlyPipeElement_13384_V1.AddPressureDiff(name, pressure)

    // Generic combiner for Thermal pipe elements
    def genAddThermalPipeElement_13384: Gen[AddThermalPipeElement_13384_V1] =
        Gen.oneOf(
            genAddSectionVertical_13384,
            genAddSectionHorizontal_13384,
            genAddSectionSlopped_13384,
            genAddAngleAdjustable_13384,
            genAddSharpeAngle_0_to_90_13384,
            genAddSharpeAngle_0_to_90_Unsafe_13384,
            genAddSmoothCurve_90_13384,
            genAddSmoothCurve_90_Unsafe_13384,
            genAddSmoothCurve_60_13384,
            genAddSmoothCurve_60_Unsafe_13384,
            genAddElbows_2x45_13384,
            genAddElbows_3x30_13384,
            genAddElbows_4x22p5_13384,
            genAddSectionDecrease_13384,
            genAddSectionIncrease_13384,
            genAddFlowResistance_13384,
            genAddPressureDiff_13384
        )

    // Generic combiner for FlowOnly pipe elements
    def genAddFlowOnlyPipeElement_13384: Gen[AddFlowOnlyPipeElement_13384_V1] =
        Gen.oneOf(
            genAddSectionVertical_FlowOnly_13384,
            genAddSectionHorizontal_FlowOnly_13384,
            genAddSectionSlopped_FlowOnly_13384,
            genAddAngleAdjustable_FlowOnly_13384,
            genAddSharpeAngle_0_to_90_FlowOnly_13384,
            genAddSharpeAngle_0_to_90_Unsafe_FlowOnly_13384,
            genAddSmoothCurve_90_FlowOnly_13384,
            genAddSmoothCurve_90_Unsafe_FlowOnly_13384,
            genAddSmoothCurve_60_FlowOnly_13384,
            genAddSmoothCurve_60_Unsafe_FlowOnly_13384,
            genAddElbows_2x45_FlowOnly_13384,
            genAddElbows_3x30_FlowOnly_13384,
            genAddElbows_4x22p5_FlowOnly_13384,
            genAddSectionDecrease_FlowOnly_13384,
            genAddSectionIncrease_FlowOnly_13384,
            genAddFlowResistance_FlowOnly_13384,
            genAddPressureDiff_FlowOnly_13384
        )

    // Sequence generators for realistic combinations
    def genAddThermalPipeElements_13384_Seq: Gen[Seq[AddThermalPipeElement_13384_V1]] =
        for
            n <- Gen.choose(1, 5)
            elements <- Gen.listOfN(n, genAddThermalPipeElement_13384)
        yield elements

    def genAddFlowOnlyPipeElements_13384_Seq: Gen[Seq[AddFlowOnlyPipeElement_13384_V1]] =
        for
            n <- Gen.choose(1, 5)
            elements <- Gen.listOfN(n, genAddFlowOnlyPipeElement_13384)
        yield elements

end AddPipeElement_13384_Generators
