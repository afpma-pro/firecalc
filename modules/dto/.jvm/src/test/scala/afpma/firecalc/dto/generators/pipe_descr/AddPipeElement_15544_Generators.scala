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

trait AddPipeElement_15544_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators:

    // Section Elements - Vertical (same as 13384)
    def genAddSectionVertical_15544: Gen[AddFlowOnlyPipeElement_15544_V1.AddSectionVertical] =
        for
            name <- genSectionName
            elevation <- Gen.choose(-700.0, 700.0).map(_.cm)
        yield AddFlowOnlyPipeElement_15544_V1.AddSectionVertical(name, elevation)

    // Section Elements - Horizontal (same as 13384)
    def genAddSectionHorizontal_15544: Gen[AddFlowOnlyPipeElement_15544_V1.AddSectionHorizontal] =
        for
            name <- genSectionName
            length <- Gen.choose(5.0, 300.0).map(_.cm)
        yield AddFlowOnlyPipeElement_15544_V1.AddSectionHorizontal(name, length)

    // Section Elements - Slopped (same as 13384)
    def genAddSectionSlopped_15544: Gen[AddFlowOnlyPipeElement_15544_V1.AddSectionSlopped] =
        for
            name <- genSectionName
            length <- Gen.choose(5.0, 700.0).map(_.cm)
            elevation <- Gen.choose(5.0, 700.0).map(_.cm)
        yield AddFlowOnlyPipeElement_15544_V1.AddSectionSlopped(name, length, elevation)

    // Direction Changes - Sharp Angle 0-180 (different from 13384)
    def genAddSharpeAngle_0_to_180_15544: Gen[AddFlowOnlyPipeElement_15544_V1.AddSharpeAngle_0_to_180] =
        for
            name <- genSectionName
            angle <- Gen.choose(0.0, 180.0).map(_.degrees)
            angleToOriginal <- Gen.option(Gen.choose(0.0, 180.0).map(_.degrees))
        yield AddFlowOnlyPipeElement_15544_V1.AddSharpeAngle_0_to_180(name, angle, angleToOriginal)

    // Direction Changes - Circular Arc 60 degrees (EN15544 only)
    def genAddCircularArc_60_15544: Gen[AddFlowOnlyPipeElement_15544_V1.AddCircularArc_60] =
        for
            name <- genSectionName
        yield AddFlowOnlyPipeElement_15544_V1.AddCircularArc_60(name)

    // Section Shape Change (EN15544 only)
    def genAddSectionShapeChange_15544: Gen[AddFlowOnlyPipeElement_15544_V1.AddSectionShapeChange] =
        for
            name <- genSectionName
            shape <- genPipeShape
        yield AddFlowOnlyPipeElement_15544_V1.AddSectionShapeChange(name, shape)

    // Cross-section generator for flow resistance (same as 13384)
    def genCrossSection_15544: Gen[OptionOfEither[AreaInCm2, PipeShape]] =
        Gen.oneOf(
            Gen.const(NoneOfEither),
            genPipeShape.map(SomeRight(_))
        )

    // Flow Resistance (same as 13384)
    def genAddFlowResistance_15544: Gen[AddFlowOnlyPipeElement_15544_V1.AddFlowResistance] =
        for
            name <- genSectionName
            zeta <- genZeta
            crossSection <- genCrossSection_15544
        yield AddFlowOnlyPipeElement_15544_V1.AddFlowResistance(name, zeta, crossSection)

    // Pressure Difference (same as 13384)
    def genAddPressureDiff_15544: Gen[AddFlowOnlyPipeElement_15544_V1.AddPressureDiff] =
        for
            name <- genSectionName
            pressure <- Gen.choose(1, 100).map(_.pascals)
        yield AddFlowOnlyPipeElement_15544_V1.AddPressureDiff(name, pressure)

    // Generic combiner for EN15544 pipe elements
    def genAddFlowOnlyPipeElement_15544: Gen[AddFlowOnlyPipeElement_15544_V1] =
        Gen.oneOf(
            genAddSectionVertical_15544,
            genAddSectionHorizontal_15544,
            genAddSectionSlopped_15544,
            genAddSharpeAngle_0_to_180_15544,
            genAddCircularArc_60_15544,
            genAddSectionShapeChange_15544,
            genAddFlowResistance_15544,
            genAddPressureDiff_15544
        )

    // Sequence generator for realistic combinations
    def genAddFlowOnlyPipeElements_15544_Seq: Gen[Seq[AddFlowOnlyPipeElement_15544_V1]] =
        for
            n <- Gen.choose(1, 5)
            elements <- Gen.listOfN(n, genAddFlowOnlyPipeElement_15544)
        yield elements

end AddPipeElement_15544_Generators
