/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.{PipeType, NamedPipeElDescrG}
import afpma.firecalc.engine.models.gtypedefs.{ζ, CoefficientOfFlowResistance}
import afpma.firecalc.engine.ops.resistance.*
import afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError
import afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError.*
import afpma.firecalc.engine.standard.SlotContext

import cats.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

/**
 * Unified dynamic friction coefficient calculations for EN13384.
 * Provides given instances for both FlowOnly and Thermal pipe descriptor types.
 */
class DynamicFrictionCoeff_13384()(using sectionTyp: PipeType, sc: SlotContext):
    // Import both PipeDescr modules
    import afpma.firecalc.engine.models.en13384.{FlowOnlyPipeDescr_13384 => FlowOnly, ThermalPipeDescr_13384 => Thermal}

    // ============================================
    // FlowOnly PipeDescr given instances
    // ============================================

    given flowOnlyDirectionChange: DynamicFrictionCoeffOp[FlowOnly.DirectionChange] =
        new DynamicFrictionCoeffOp[FlowOnly.DirectionChange]:
            extension (s: FlowOnly.DirectionChange)
                def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                    s match
                        case ss: FlowOnly.AngleSpecifique       => flowOnlyAngleSpecifique.dynamicFrictionCoeff(ss)
                        case ss: FlowOnly.AngleVifDe0A90_Unsafe =>
                            flowOnlyAngleVifDe0A90_unsafe.dynamicFrictionCoeff(ss)
                        case ss: FlowOnly.AngleVifDe0A90        => flowOnlyAngleVifDe0A90.dynamicFrictionCoeff(ss)
                        case ss: FlowOnly.CoudeCourbe90         => flowOnlyCoudeCourbe90.dynamicFrictionCoeff(ss)
                        case ss: FlowOnly.CoudeCourbe90_Unsafe  => flowOnlyCoudeCourbe90_unsafe.dynamicFrictionCoeff(ss)
                        case ss: FlowOnly.CoudeCourbe60         => flowOnlyCoudeCourbe60.dynamicFrictionCoeff(ss)
                        case ss: FlowOnly.CoudeCourbe60_Unsafe  => flowOnlyCoudeCourbe60_unsafe.dynamicFrictionCoeff(ss)
                        case ss: FlowOnly.CoudeASegment90       => flowOnlyCoudeASegment90.dynamicFrictionCoeff(ss)
                        case ss: FlowOnly.SplitMerge90          => flowOnlySplitMerge90.dynamicFrictionCoeff(ss)

    given flowOnlySectionGeometryChange: DynamicFrictionCoeffOp[FlowOnly.SectionGeometryChange] =
        new DynamicFrictionCoeffOp[FlowOnly.SectionGeometryChange]:
            extension (s: FlowOnly.SectionGeometryChange)
                def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                    s match
                        case ss: FlowOnly.SectionDecrease => flowOnlyDecrease.dynamicFrictionCoeff(ss)
                        case ss: FlowOnly.SectionIncrease => flowOnlyIncrease.dynamicFrictionCoeff(ss)

    given flowOnlySingularFlowResistance: DynamicFrictionCoeffOp[FlowOnly.SingularFlowResistance] =
        DynamicFrictionCoeffOp.fromFunction[FlowOnly.SingularFlowResistance](_.zeta.validNel)

    given flowOnlyUnion: DynamicFrictionCoeffOp[
        FlowOnly.SingularFlowResistance | FlowOnly.DirectionChange | FlowOnly.SectionGeometryChange
    ] =
        new DynamicFrictionCoeffOp[
            FlowOnly.SingularFlowResistance | FlowOnly.DirectionChange | FlowOnly.SectionGeometryChange
        ]:
            extension (s: FlowOnly.SingularFlowResistance | FlowOnly.DirectionChange | FlowOnly.SectionGeometryChange)
                def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                    s match
                        case x: FlowOnly.SingularFlowResistance =>
                            flowOnlySingularFlowResistance.dynamicFrictionCoeff(x)
                        case x: FlowOnly.DirectionChange        => flowOnlyDirectionChange.dynamicFrictionCoeff(x)
                        case x: FlowOnly.SectionGeometryChange  => flowOnlySectionGeometryChange.dynamicFrictionCoeff(x)

    // ============================================
    // Thermal PipeDescr given instances
    // ============================================

    given thermalDirectionChange: DynamicFrictionCoeffOp[Thermal.DirectionChange] =
        new DynamicFrictionCoeffOp[Thermal.DirectionChange]:
            extension (s: Thermal.DirectionChange)
                def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                    s match
                        case ss: Thermal.AngleSpecifique       => thermalAngleSpecifique.dynamicFrictionCoeff(ss)
                        case ss: Thermal.AngleVifDe0A90_Unsafe => thermalAngleVifDe0A90_unsafe.dynamicFrictionCoeff(ss)
                        case ss: Thermal.AngleVifDe0A90        => thermalAngleVifDe0A90.dynamicFrictionCoeff(ss)
                        case ss: Thermal.CoudeCourbe90         => thermalCoudeCourbe90.dynamicFrictionCoeff(ss)
                        case ss: Thermal.CoudeCourbe90_Unsafe  => thermalCoudeCourbe90_unsafe.dynamicFrictionCoeff(ss)
                        case ss: Thermal.CoudeCourbe60         => thermalCoudeCourbe60.dynamicFrictionCoeff(ss)
                        case ss: Thermal.CoudeCourbe60_Unsafe  => thermalCoudeCourbe60_unsafe.dynamicFrictionCoeff(ss)
                        case ss: Thermal.CoudeASegment90       => thermalCoudeASegment90.dynamicFrictionCoeff(ss)
                        case ss: Thermal.SplitMerge90          => thermalSplitMerge90.dynamicFrictionCoeff(ss)

    given thermalSectionGeometryChange: DynamicFrictionCoeffOp[Thermal.SectionGeometryChange] =
        new DynamicFrictionCoeffOp[Thermal.SectionGeometryChange]:
            extension (s: Thermal.SectionGeometryChange)
                def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                    s match
                        case ss: Thermal.SectionDecrease => thermalDecrease.dynamicFrictionCoeff(ss)
                        case ss: Thermal.SectionIncrease => thermalIncrease.dynamicFrictionCoeff(ss)

    given thermalSingularFlowResistance: DynamicFrictionCoeffOp[Thermal.SingularFlowResistance] =
        DynamicFrictionCoeffOp.fromFunction[Thermal.SingularFlowResistance](_.zeta.validNel)

    given thermalUnion: DynamicFrictionCoeffOp[
        Thermal.SingularFlowResistance | Thermal.DirectionChange | Thermal.SectionGeometryChange
    ] =
        new DynamicFrictionCoeffOp[
            Thermal.SingularFlowResistance | Thermal.DirectionChange | Thermal.SectionGeometryChange
        ]:
            extension (s: Thermal.SingularFlowResistance | Thermal.DirectionChange | Thermal.SectionGeometryChange)
                def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                    s match
                        case x: Thermal.SingularFlowResistance => thermalSingularFlowResistance.dynamicFrictionCoeff(x)
                        case x: Thermal.DirectionChange        => thermalDirectionChange.dynamicFrictionCoeff(x)
                        case x: Thermal.SectionGeometryChange  => thermalSectionGeometryChange.dynamicFrictionCoeff(x)

    // ============================================
    // FlowOnly Individual Type Instances
    // ============================================

    given flowOnlyAngleSpecifique: DynamicFrictionCoeffOp[FlowOnly.AngleSpecifique] =
        DynamicFrictionCoeffOp.fromFunction(_.zeta.validNel)

    given flowOnlyAngleVifDe0A90_unsafe: DynamicFrictionCoeffOp[FlowOnly.AngleVifDe0A90_Unsafe] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateAngleVif(shape, ɣ.value, (Ld / Dh).value)
        }

    given flowOnlyAngleVifDe0A90: DynamicFrictionCoeffOp[FlowOnly.AngleVifDe0A90] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateAngleVif(shape, ɣ.value, (Ld / Dh).value)
        }

    given flowOnlyCoudeCourbe90: DynamicFrictionCoeffOp[FlowOnly.CoudeCourbe90] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateCoudeCourbe90(shape, (R / Dh).value, (Ld / Dh).value)
        }

    given flowOnlyCoudeCourbe90_unsafe: DynamicFrictionCoeffOp[FlowOnly.CoudeCourbe90_Unsafe] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateCoudeCourbe90(shape, (R / Dh).value, (Ld / Dh).value)
        }

    given flowOnlyCoudeCourbe60: DynamicFrictionCoeffOp[FlowOnly.CoudeCourbe60] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateCoudeCourbe60(shape, (R / Dh).value, (Ld / Dh).value)
        }

    given flowOnlyCoudeCourbe60_unsafe: DynamicFrictionCoeffOp[FlowOnly.CoudeCourbe60_Unsafe] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateCoudeCourbe60(shape, (R / Dh).value, (Ld / Dh).value)
        }

    given flowOnlyCoudeASegment90: DynamicFrictionCoeffOp[FlowOnly.CoudeASegment90] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            interpolateCoudeASegment90(shape, shape.R, shape.Dh)
        }

    given flowOnlySplitMerge90: DynamicFrictionCoeffOp[FlowOnly.SplitMerge90] =
        DynamicFrictionCoeffOp.fromFunction { _ =>
            CoefficientOfFlowResistance.splitMerge90Zeta.validNel
        }

    given flowOnlyDecrease: DynamicFrictionCoeffOp[FlowOnly.SectionDecrease] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            interpolateDecrease(shape, (shape.toA2 / shape.fromA1).value, shape.to.diameter, shape.from.diameter)
        }

    given flowOnlyIncrease: DynamicFrictionCoeffOp[FlowOnly.SectionIncrease] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            interpolateIncrease(shape, (shape.fromA1 / shape.toA2).value, shape.from.diameter, shape.to.diameter)
        }

    // ============================================
    // Thermal Individual Type Instances
    // ============================================

    given thermalAngleSpecifique: DynamicFrictionCoeffOp[Thermal.AngleSpecifique] =
        DynamicFrictionCoeffOp.fromFunction(_.zeta.validNel)

    given thermalAngleVifDe0A90_unsafe: DynamicFrictionCoeffOp[Thermal.AngleVifDe0A90_Unsafe] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateAngleVif(shape, ɣ.value, (Ld / Dh).value)
        }

    given thermalAngleVifDe0A90: DynamicFrictionCoeffOp[Thermal.AngleVifDe0A90] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateAngleVif(shape, ɣ.value, (Ld / Dh).value)
        }

    given thermalCoudeCourbe90: DynamicFrictionCoeffOp[Thermal.CoudeCourbe90] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateCoudeCourbe90(shape, (R / Dh).value, (Ld / Dh).value)
        }

    given thermalCoudeCourbe90_unsafe: DynamicFrictionCoeffOp[Thermal.CoudeCourbe90_Unsafe] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateCoudeCourbe90(shape, (R / Dh).value, (Ld / Dh).value)
        }

    given thermalCoudeCourbe60: DynamicFrictionCoeffOp[Thermal.CoudeCourbe60] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateCoudeCourbe60(shape, (R / Dh).value, (Ld / Dh).value)
        }

    given thermalCoudeCourbe60_unsafe: DynamicFrictionCoeffOp[Thermal.CoudeCourbe60_Unsafe] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            import shape.*
            interpolateCoudeCourbe60(shape, (R / Dh).value, (Ld / Dh).value)
        }

    given thermalCoudeASegment90: DynamicFrictionCoeffOp[Thermal.CoudeASegment90] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            interpolateCoudeASegment90(shape, shape.R, shape.Dh)
        }

    given thermalSplitMerge90: DynamicFrictionCoeffOp[Thermal.SplitMerge90] =
        DynamicFrictionCoeffOp.fromFunction { _ =>
            CoefficientOfFlowResistance.splitMerge90Zeta.validNel
        }

    given thermalDecrease: DynamicFrictionCoeffOp[Thermal.SectionDecrease] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            interpolateDecrease(shape, (shape.toA2 / shape.fromA1).value, shape.to.diameter, shape.from.diameter)
        }

    given thermalIncrease: DynamicFrictionCoeffOp[Thermal.SectionIncrease] =
        DynamicFrictionCoeffOp.fromFunction { shape =>
            interpolateIncrease(shape, (shape.fromA1 / shape.toA2).value, shape.from.diameter, shape.to.diameter)
        }

    // ============================================
    // Shared Tables (EN 13384-1:2015+A1:2019)
    // ============================================

    val EN13384_1_2015_A1_2019_table_B8_shape1 =
        """|gamma	Ld/Dh >= 30	30 > Ld/Dh >= 2
           |0	0,00	0,00
           |10	0,10	0,10
           |30	0,20	0,30
           |45	0,30	0,40
           |60	0,50	0,70
           |90	1,20	1,60""".stripMargin

    // __INTERPRETATION__ R/Dh < 0.5 should not happen. R/Dh = 0,00 added for interpolation
    val EN13384_1_2015_A1_2019_table_B8_shape2_updated =
        """|R/Dh	Ld/Dh >= 30	30 > Ld/Dh >= 2
           |0,00	1,20	1,20
           |0,50	1,00	1,20
           |0,75	0,40	0,50
           |1,00	0,25	0,30
           |1,50	0,20	0,20
           |2,00	0,20	0,20""".stripMargin

    // __INTERPRETATION__ R/Dh < 0.5 should not happen. R/Dh = 0,00 added for interpolation
    val EN13384_1_2015_A1_2019_table_B8_shape3_updated =
        """|R/Dh	Ld/Dh >= 30	30 > Ld/Dh >= 2
           |0,00	1,20	1,20
           |0,50	0,60	1,00
           |0,75	0,30	0,40
           |1,00	0,20	0,30
           |1,50	0,20	0,20
           |2,00	0,10	0,10""".stripMargin

    val EN13384_1_2015_A1_2019_table_B8_shape4 =
        """|a/Dh	Coude 2 x 45 deg	Coude 3 x 30 deg	Coude 4 x 22.5 deg
           |1,00	0,40	0,25	0,17
           |1,50	0,30	0,18	0,13
           |2,00	0,30	0,17	0,12
           |3,00	0,35	0,19	0,13
           |5,00	0,40	0,20	0,15""".stripMargin

    // ============================================
    // Shared Interpolation Helpers
    // ============================================

    private def interpolateAngleVif[S: Show](
        shape                 : S,
        gammaValue            : Double,
        ldDhRatio             : Double
    ): DynamicFrictionCoeffOp.Result =
        _interpolateHelper(
            shape             = shape,
            resName           = "EN 13384-1:2015+A1:2019 // table-B8_shape1.csv",
            tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape1,
            xHeader           = "gamma",
            xi                = gammaValue,
            xMinMax           = (0, 90),
            yCriteria         = Some(ldDhRatio),
            yHeaderSelectFunc = yHeaderForShape123(shape)
        )

    private def interpolateCoudeCourbe90[S: Show](
        shape                : S,
        rDhRatio             : Double,
        ldDhRatio            : Double
    ): DynamicFrictionCoeffOp.Result =
        _interpolateHelper(
            shape             = shape,
            resName           = "EN 13384-1:2015+A1:2019 // table-B8_shape2_updated.csv",
            tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape2_updated,
            xHeader           = "R/Dh",
            xi                = rDhRatio,
            xMinMax           = (0.0, 2.0),
            yCriteria         = Some(ldDhRatio),
            yHeaderSelectFunc = yHeaderForShape123(shape)
        )

    private def interpolateCoudeCourbe60[S: Show](
        shape                : S,
        rDhRatio             : Double,
        ldDhRatio            : Double
    ): DynamicFrictionCoeffOp.Result =
        _interpolateHelper(
            shape             = shape,
            resName           = "EN 13384-1:2015+A1:2019 // table-B8_shape3_updated.csv",
            tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape3_updated,
            xHeader           = "R/Dh",
            xi                = rDhRatio,
            xMinMax           = (0.0, 2.0),
            yCriteria         = Some(ldDhRatio),
            yHeaderSelectFunc = yHeaderForShape123(shape)
        )

    private def interpolateCoudeASegment90[S: Show](
        shape: S,
        R    : Length,
        Dh   : Length
    ): DynamicFrictionCoeffOp.Result =
        // Determine angle and header based on shape type name
        val (alpha, yHeader) = shape.show match
            case s if s.contains("2A45")   => (45.degrees, "Coude 2 x 45 deg"    )
            case s if s.contains("3A30")   => (30.degrees, "Coude 3 x 30 deg"    )
            case s if s.contains("4A22p5") => (22.5.degrees, "Coude 4 x 22.5 deg")
            case _                         => (45.degrees, "Coude 2 x 45 deg"    ) // fallback

        val a = R * 2 * math.tan(alpha.toUnit[Radian].value / 2)
        _interpolateHelper            (
            shape             = shape,
            resName           = "EN 13384-1:2015+A1:2019 // table-B8_shape4.csv",
            tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape4,
            xHeader           = "a/Dh",
            xi                = (a / Dh).value,
            xMinMax           = (1.0, 5.0),
            yCriteria         = None,
            yHeaderSelectFunc = _ => Right(yHeader)
        )

    private def interpolateDecrease[S: Show](
        shape    : S,
        a2a1Ratio: Double,
        toD      : Length,
        fromD    : Length
    ): DynamicFrictionCoeffOp.Result =
        DynamicFrictionCoeffOp.interpolateHelperE[S]            (
            shape             = shape,
            sectionTyp        = sectionTyp,
            resName           = "EN 13384-1:2015+A1:2019 // table-B8_shape6.csv",
            tsvTableRawString = """|A2/A1	Valeurs zeta
                   |0,40	0,33
                   |0,60	0,25
                   |0,80	0,15
                   |1,00	0,00""".stripMargin,
            xHeader           = "A2/A1",
            xi                = a2a1Ratio,
            xMinMax           = (0.4, 1.0),
            yCriteria         = None,
            yHeaderSelectFunc = _ => Right("Valeurs zeta")
        ) match
            case Left(_: SingularFlowResistanceCoeffError.ValueOutOfBound[?]) =>
                val ratio = (toD / fromD).value
                val KSC   = 0.42 * (1 - math.pow(ratio, 2))
                (KSC.unitless: ζ).asRight.toValidatedNel
            case Left(err)                                                    => err.asLeft.toValidatedNel
            case Right(z)                                                     => z.asRight.toValidatedNel

    private def interpolateIncrease[S: Show](
        shape    : S,
        a1a2Ratio: Double,
        fromD    : Length,
        toD      : Length
    ): DynamicFrictionCoeffOp.Result =
        DynamicFrictionCoeffOp.interpolateHelperE[S]            (
            shape             = shape,
            sectionTyp        = sectionTyp,
            resName           = "EN 13384-1:2015+A1:2019 // table-B8_shape7.csv",
            tsvTableRawString = """|A1/A2	Valeurs zeta
                   |0,00	1,00
                   |0,20	0,70
                   |0,40	0,40
                   |0,60	0,20
                   |0,80	0,10
                   |1,00	0,00""".stripMargin,
            xHeader           = "A1/A2",
            xi                = a1a2Ratio,
            xMinMax           = (0.0, 1.0),
            yCriteria         = None,
            yHeaderSelectFunc = _ => Right("Valeurs zeta")
        ) match
            case Left(_: SingularFlowResistanceCoeffError.ValueOutOfBound[?]) =>
                val ratio = (fromD / toD).value
                val KSE   = math.pow((1 - math.pow(ratio, 2)), 2)
                (KSE.unitless: ζ).asRight.toValidatedNel
            case Left(err)                                                    => err.asLeft.toValidatedNel
            case Right(z)                                                     => z.asRight.toValidatedNel

    private def _interpolateHelper[S: Show](
        shape            : S,
        resName          : String,
        tsvTableRawString: String,
        xHeader          : String,
        xi               : Double,
        xMinMax          : (Double, Double),
        yHeaderSelectFunc: Option[Double] => Either[DynamicFrictionCoeffOp.Err, String],
        yCriteria        : Option[Double]
    ): DynamicFrictionCoeffOp.Result =
        DynamicFrictionCoeffOp.interpolateHelper[S](
            shape,
            sectionTyp,
            resName,
            tsvTableRawString,
            xHeader,
            xi,
            xMinMax,
            yHeaderSelectFunc,
            yCriteria
        )

    private def yHeaderForShape123[S](shape: S)(using
        show_shape: Show[S]
    ): Option[Double] => Either[DynamicFrictionCoeffOp.Err, String] =
        val isUnsafe = shape.toString.contains("Unsafe")
        {
            case Some(ratio) if ratio >= 30              => Right("Ld/Dh >= 30")
            case Some(ratio) if 30 > ratio && ratio >= 2 => Right("30 > Ld/Dh >= 2")
            case Some(_) if isUnsafe                     =>
                Right("30 > Ld/Dh >= 2")
            case Some(ratio)                             => Left(UnexpectedRatio_Ld_Dh(shape, sectionTyp, ratio)(using show_shape, sc.slotIndex))
            case None                                    => Left(NoGivenRatio_Ld_Dh(shape, sectionTyp)(using show_shape, sc.slotIndex)          )
        }

    /**
     * Factory method to create a chain-aware dynamic friction coefficient operator.
     *
     * Wraps the provided DFC instance with pipe-chain awareness (e.g., SplitMerge90
     * position-based zeta computation). The `given DynamicFrictionCoeffOp[DC]` must
     * be available in scope (provided by this instance's given instances when
     * `import this.given` or `import dcDfc.given` is used).
     */
    def makeChainAwareDfc[PipeElDescr <: Matchable, DC <: Matchable](
        pipeElements: Vector[NamedPipeElDescrG[PipeElDescr]],
        sc          : SlotContext
    )(using dfc: DynamicFrictionCoeffOp[DC]): DynamicFrictionCoeffOp[NamedPipeElDescrG[DC]] =
        DynamicFrictionCoeffOpForPipeChain[PipeElDescr, DC](pipeElements, dfc, sc)

end DynamicFrictionCoeff_13384
