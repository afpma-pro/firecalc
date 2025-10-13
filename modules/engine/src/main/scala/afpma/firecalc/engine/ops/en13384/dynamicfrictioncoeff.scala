/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import cats.*
import cats.syntax.all.*

import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.ops.resistance.*
import afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError.*

import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import algebra.instances.all.given
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

object dynamicfrictioncoeff:
    
    import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp.* 
    import afpma.firecalc.engine.models.en13384.pipedescr.*
    
    given directionChange
        : DynamicFrictionCoeffOp[DirectionChange] =
        new DynamicFrictionCoeffOp[DirectionChange] {
            extension (s: DirectionChange) def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                s match
                    case ss: AngleSpecifique        =>   angleSpecifique        .dynamicFrictionCoeff(ss)
                    case ss: AngleVifDe0A90_Unsafe  =>   angleVifDe0A90_unsafe  .dynamicFrictionCoeff(ss)
                    case ss: AngleVifDe0A90         =>   angleVifDe0A90         .dynamicFrictionCoeff(ss)
                    case ss: CoudeCourbe90          =>   coudeCourbe90          .dynamicFrictionCoeff(ss)
                    case ss: CoudeCourbe90_Unsafe   =>   coudeCourbe90_unsafe   .dynamicFrictionCoeff(ss)
                    case ss: CoudeCourbe60          =>   coudeCourbe60          .dynamicFrictionCoeff(ss)
                    case ss: CoudeCourbe60_Unsafe   =>   coudeCourbe60_unsafe   .dynamicFrictionCoeff(ss)
                    case ss: CoudeASegment90        =>   coudeASegment90        .dynamicFrictionCoeff(ss)
        }

    given sectionGeometryChange
        : DynamicFrictionCoeffOp[SectionGeometryChange] =
        new DynamicFrictionCoeffOp[SectionGeometryChange] {
            extension (s: SectionGeometryChange) def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                s match
                    case ss: SectionDecrease            => decrease.dynamicFrictionCoeff(ss)
                    case ss: SectionIncrease            => increase.dynamicFrictionCoeff(ss)
                    // case ss: SectionDecreaseProgressive => ss.dynamicFrictionCoeff
        }

    given singularFlowResistance: DynamicFrictionCoeffOp[SingularFlowResistance] =
        DynamicFrictionCoeffOp.fromFunction[SingularFlowResistance](_.zeta.validNel)

    given DynamicFrictionCoeffOp[SingularFlowResistance | DirectionChange | SectionGeometryChange] =
        new DynamicFrictionCoeffOp[SingularFlowResistance | DirectionChange | SectionGeometryChange] {
            extension (s: SingularFlowResistance | DirectionChange | SectionGeometryChange) def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                s match
                    case x: SingularFlowResistance => singularFlowResistance    .dynamicFrictionCoeff(x)
                    case x: DirectionChange        => directionChange           .dynamicFrictionCoeff(x)
                    case x: SectionGeometryChange  => sectionGeometryChange     .dynamicFrictionCoeff(x)

        }

    given angleSpecifique: DynamicFrictionCoeffOp[AngleSpecifique] = 
        DynamicFrictionCoeffOp.fromFunction(_.zeta.validNel)

    val EN13384_1_2015_A1_2019_table_B8_shape1 = 
        """|ɣ	Ld/Dh ≥ 30	30 > Ld/Dh ≥ 2
           |0	0,00	0,00
           |10	0,10	0,10
           |30	0,20	0,30
           |45	0,30	0,40
           |60	0,50	0,70
           |90	1,20	1,60""".stripMargin

    given angleVifDe0A90_unsafe: DynamicFrictionCoeffOp[AngleVifDe0A90_Unsafe] =
        DynamicFrictionCoeffOp.fromFunction {
            case shape: AngleVifDe0A90_Unsafe =>
                import shape.*
                _interpolateHelper(
                    shape = shape,
                    resName = "EN 13384-1:2015+A1:2019 // table-B8_shape1.csv",
                    tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape1,
                    xHeader = "ɣ",
                    xi = ɣ.value,
                    xMinMax = (0, 90),
                    yCriteria = Some((Ld / Dh).value),
                    yHeaderSelectFunc = yHeaderForShape123(shape)
                )
        }

    given angleVifDe0A90: DynamicFrictionCoeffOp[AngleVifDe0A90] =
        DynamicFrictionCoeffOp.fromFunction {
            case shape: AngleVifDe0A90 =>
                import shape.*
                _interpolateHelper(
                    shape = shape,
                    resName = "EN 13384-1:2015+A1:2019 // table-B8_shape1.csv",
                    tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape1,
                    xHeader = "ɣ",
                    xi = ɣ.value,
                    xMinMax = (0, 90),
                    yCriteria = Some((Ld / Dh).value),
                    yHeaderSelectFunc = yHeaderForShape123(shape)
                )
        }

    // __INTERPRETATION__ R/Dh < 0.5 should not happen. R/Dh = 0,00 added for interpolation (but should not be needed ? TOTRY or TODO)
    val EN13384_1_2015_A1_2019_table_B8_shape2_updated = 
            """|R/Dh	Ld/Dh ≥ 30	30 > Ld/Dh ≥ 2
               |0,00	1,20	1,20
               |0,50	1,00	1,20
               |0,75	0,40	0,50
               |1,00	0,25	0,30
               |1,50	0,20	0,20
               |2,00	0,20	0,20""".stripMargin

    given coudeCourbe90: DynamicFrictionCoeffOp[CoudeCourbe90] =
        DynamicFrictionCoeffOp.fromFunction {
            case shape: CoudeCourbe90 =>
                import shape.*
                _interpolateHelper(
                    shape = shape,
                    resName = "EN 13384-1:2015+A1:2019 // table-B8_shape2_updated.csv", // __INTERPRETATION__: UPDDATED to interpoloate between 0 and 0.5
                    tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape2_updated,
                    xHeader = "R/Dh",
                    xi = (R / Dh).value,
                    // xMinMax = (0.5, 2.0), // __INTERPRETATION__: UPDDATED to interpoloate between 0 and 0.5
                    xMinMax = (0.0, 2.0),
                    yCriteria = Some((Ld / Dh).value),
                    yHeaderSelectFunc = yHeaderForShape123(shape)
                )
        }

    given coudeCourbe90_unsafe: DynamicFrictionCoeffOp[CoudeCourbe90_Unsafe] =
        DynamicFrictionCoeffOp.fromFunction {
            case shape: CoudeCourbe90_Unsafe =>
                import shape.*
                _interpolateHelper(
                    shape = shape,
                    resName = "EN 13384-1:2015+A1:2019 // table-B8_shape2_updated.csv", // __INTERPRETATION__: UPDDATED to interpoloate between 0 and 0.5
                    tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape2_updated,
                    xHeader = "R/Dh",
                    xi = (R / Dh).value,
                    // xMinMax = (0.5, 2.0), // __INTERPRETATION__: UPDDATED to interpoloate between 0 and 0.5
                    xMinMax = (0.0, 2.0),
                    yCriteria = Some((Ld / Dh).value),
                    yHeaderSelectFunc = yHeaderForShape123(shape)
                )
        }

    // __INTERPRETATION__ R/Dh < 0.5 should not happen. R/Dh = 0,00 added for interpolation (but should not be needed ? TOTRY or TODO)
    val EN13384_1_2015_A1_2019_table_B8_shape3_updated = 
        """|R/Dh	Ld/Dh ≥ 30	30 > Ld/Dh ≥ 2
           |0,00	1,20	1,20
           |0,50	0,60	1,00
           |0,75	0,30	0,40
           |1,00	0,20	0,30
           |1,50	0,20	0,20
           |2,00	0,10	0,10""".stripMargin

    given coudeCourbe60: DynamicFrictionCoeffOp[CoudeCourbe60] =
        DynamicFrictionCoeffOp.fromFunction {
            case shape: CoudeCourbe60 =>
                import shape.*
                _interpolateHelper(
                    shape = shape,
                    resName = "EN 13384-1:2015+A1:2019 // table-B8_shape3_updated.csv", // __INTERPRETATION__: UPDATED to interpoloate between 0 and 0.5
                    tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape3_updated,
                    xHeader = "R/Dh",
                    xi = (R / Dh).value,
                    // xMinMax = (0.5, 2.0), // __INTERPRETATION__: UPDATED to interpoloate between 0 and 0.5
                    xMinMax = (0.0, 2.0),
                    yCriteria = Some((Ld / Dh).value),
                    yHeaderSelectFunc = yHeaderForShape123(shape)
                )
        }

    given coudeCourbe60_unsafe: DynamicFrictionCoeffOp[CoudeCourbe60_Unsafe] =
        DynamicFrictionCoeffOp.fromFunction {
            case shape: CoudeCourbe60_Unsafe =>
                import shape.*
                _interpolateHelper(
                    shape = shape,
                    resName = "EN 13384-1:2015+A1:2019 // table-B8_shape3_updated.csv", // __INTERPRETATION__: UPDATED to interpoloate between 0 and 0.5
                    tsvTableRawString = EN13384_1_2015_A1_2019_table_B8_shape3_updated,
                    xHeader = "R/Dh",
                    xi = (R / Dh).value,
                    // xMinMax = (0.5, 2.0), // __INTERPRETATION__: UPDATED to interpoloate between 0 and 0.5
                    xMinMax = (0.0, 2.0),
                    yCriteria = Some((Ld / Dh).value),
                    yHeaderSelectFunc = yHeaderForShape123(shape)
                )
        }

    given coudeASegment90: DynamicFrictionCoeffOp[CoudeASegment90] =
        DynamicFrictionCoeffOp.fromFunction {
            case c: CoudeASegment90Avec2A45 =>
                interpolateCoudeASegment90(c)

            case c: CoudeASegment90Avec3A30 =>
                interpolateCoudeASegment90(c)

            case c: CoudeASegment90Avec4A22p5 =>
                interpolateCoudeASegment90(c)
        }

    given decrease: DynamicFrictionCoeffOp[SectionDecrease] =
        DynamicFrictionCoeffOp.fromFunction {
            case shape: SectionDecrease =>
                // try EN13384-1 values first
                interpolateHelperE[SectionDecrease](
                    shape = shape,
                    resName = "EN 13384-1:2015+A1:2019 // table-B8_shape6.csv",
                    tsvTableRawString = 
                        """|A2/A1	Valeurs ζ
                           |0,40	0,33
                           |0,60	0,25
                           |0,80	0,15
                           |1,00	0,00""".stripMargin,
                    xHeader = "A2/A1",
                    xi = (shape.toA2 / shape.fromA1).value,
                    xMinMax = (0.4, 1.0),
                    yCriteria = None,
                    yHeaderSelectFunc = _ => Right("Valeurs ζ")
                ) match
                    case Left(_ @ ValueOutOfBound(_, _, _, _, _)) => 
                        val d = shape.to.diameter
                        val D = shape.from.diameter
                        val ratio = (d / D).value
                        val KSC = 0.42 * ( 1 - math.pow(ratio, 2)) // source: Fluid Mechanics, 9th edition, p. 395, equation (6.79)
                        (KSC.unitless: ζ).asRight.toValidatedNel
                    case Left(err) => err.asLeft.toValidatedNel
                    case Right(zeta) => 
                        zeta.asRight.toValidatedNel
        }

    given increase: DynamicFrictionCoeffOp[SectionIncrease] =
        DynamicFrictionCoeffOp.fromFunction {
            case shape: SectionIncrease =>
                interpolateHelperE[SectionIncrease](
                    shape = shape,
                    resName = "EN 13384-1:2015+A1:2019 // table-B8_shape7.csv",
                    tsvTableRawString = 
                        """|A1/A2	Valeurs ζ
                           |0,00	1,00
                           |0,20	0,70
                           |0,40	0,40
                           |0,60	0,20
                           |0,80	0,10
                           |1,00	0,00""".stripMargin,
                    xHeader = "A1/A2",
                    xi = (shape.fromA1 / shape.toA2).value,
                    xMinMax = (0.0, 1.0),
                    yCriteria = None,
                    yHeaderSelectFunc = _ => Right("Valeurs ζ")
                ) match
                    case Left(_ @ ValueOutOfBound(_, _, _, _, _)) => 
                        val d = shape.from.diameter
                        val D = shape.to.diameter
                        val ratio = (d / D).value
                        val KSE = math.pow(( 1 - math.pow(ratio, 2) ), 2) // source: Fluid Mechanics, 9th edition, p. 395, equation (6.78)
                        (KSE.unitless: ζ).asRight.toValidatedNel
                    case Left(err) => 
                        err.asLeft.toValidatedNel
                    case Right(zeta) => 
                        zeta.asRight.toValidatedNel
        }

    // given decreaseProgressive: DynamicFrictionCoeffOp[SectionDecreaseProgressive] =
    //     DynamicFrictionCoeffOp.fromFunction {
    //         case shape: SectionDecreaseProgressive =>
    //             val (a1, a2, ɣ) = (shape.fromA1, shape.toA2, shape.ɣ)

    //             val ɣ0: 0 = 0
    //             val ɣ30: 30 = 30
    //             val ɣ60: 60 = 60
    //             val ɣ90: 90 = 90

    //             type Zeroor30or60or90 = 0 | 30 | 60 | 90

    //             def interpolateFor(
    //                 ɣ: Zeroor30or60or90
    //             ): DynamicFrictionCoeffOp.Result =
    //                 _interpolateHelper(
    //                     shape = shape,
    //                     resFileName =
    //                         "EN 13384-1:2015+A1:2019 // table-B8_shape8.csv",
    //                     xHeader = "A2/A1",
    //                     xi = (a2 / a1).value,
    //                     xMinMax = (0.1, 1.0),
    //                     yCriteria = Some(ɣ.toDouble),
    //                     yHeaderSelectFunc = ɣ => Right(s"ɣ = ${ɣ.get.toInt}°")
    //                 )

    //             // interpolation 1/2
    //             val either_ɣLɣR
    //                 : ValidatedNel[DynamicFrictionCoeffOp.Err, (Zeroor30or60or90, Zeroor30or60or90)] =
    //                 ɣ.value match
    //                     case gamma if gamma < 30  => 
    //                         Valid(ɣ0, ɣ30)
    //                     case gamma if gamma == 30 => 
    //                         Valid((ɣ30, ɣ30))
    //                     case gamma if 30 < gamma && gamma < 60 =>
    //                         Valid(ɣ30, ɣ60)
    //                     case gamma if gamma == 60 => 
    //                         Valid((ɣ60, ɣ60))
    //                     case gamma if 60 < gamma && gamma < 90 =>
    //                         Valid((ɣ60, ɣ90))
    //                     case gamma if gamma == 90 => 
    //                         Valid((ɣ90, ɣ90))
    //                     case _ =>
    //                         Invalid(
    //                             ValueOutOfBound[SectionDecreaseProgressive](
    //                                 shape,
    //                                 vTermName = "ɣ",
    //                                 v = ɣ.value,
    //                                 vMin = 0,
    //                                 vMax = 90
    //                             )
    //                         ).toValidatedNel

    //             val either_ζLζR: ValidatedNel[DynamicFrictionCoeffOp.Err, (ζ, ζ)] = either_ɣLɣR match {
    //                 case i @ Invalid(_) => i
    //                 case Valid((ɣL: Zeroor30or60or90, ɣR: Zeroor30or60or90)) =>
    //                     (interpolateFor(ɣL), interpolateFor(ɣR)) match
    //                         case (Valid(zetaL), Valid(zetaR)) =>
    //                             Valid((zetaL, zetaR))
    //                         case (Invalid(errL), Valid(_))     => Invalid(errL)
    //                         case (Valid(zetaL), Invalid(errR)) => Invalid(errR)
    //                         case (Invalid(errL), Invalid(errR)) =>
    //                             Invalid(errL ::: errR)

    //             }

    //             // interpolation 2/2
    //             import afpma.firecalc.engine.utils.getWithLinearInterpolation

    //             val ɣLv = either_ɣLɣR.map(_._1.toDouble)
    //             val ɣRv = either_ɣLɣR.map(_._2.toDouble)
    //             val ζLv = either_ζLζR.map(_._1)
    //             val ζRv = either_ζLζR.map(_._2)

    //             (ɣLv, ɣRv, ζLv, ζRv).tupled
    //                 .andThen { case (ɣL, ɣR, ζL, ζR) =>
    //                     val it = Map[Double, Double](ɣL -> ζL.value, ɣR -> ζR.value)

    //                     it.getWithLinearInterpolation(ɣ.value) match
    //                         case Some(z) => Valid(z.ea: ζ)
    //                         case None =>
    //                             Invalid(
    //                                 CouldNotComputeIndividualCoefficientForShape[
    //                                     SectionDecreaseProgressive
    //                                 ](
    //                                     shape,
    //                                     s"""could not interpolate (ɣ, ζ) between ($ɣL, $ζL) and ($ɣR, $ζR) at point ɣ = $ɣ"""
    //                                 )
    //                             ).toValidatedNel
    //                 }
    //     }

    // HELPERS
    private def _interpolateHelper[S: Show](
        shape: S,
        resName: String,
        tsvTableRawString: String,
        xHeader: String,
        xi: Double,
        xMinMax: (Double, Double),
        yHeaderSelectFunc: Option[Double] => Either[DynamicFrictionCoeffOp.Err, String],
        yCriteria: Option[Double]
    ): DynamicFrictionCoeffOp.Result =
        DynamicFrictionCoeffOp.interpolateHelper[S](
            shape,
            resName,
            tsvTableRawString,
            xHeader,
            xi,
            xMinMax,
            yHeaderSelectFunc,
            yCriteria
        )

    private def interpolateCoudeASegment90(
        shape: CoudeASegment90
    ): DynamicFrictionCoeffOp.Result =
        val (ɑ, yHeader) = shape match
            case CoudeASegment90Avec2A45(_, _) => (45.degrees, "Coude 2 x 45°")
            case CoudeASegment90Avec3A30(_, _) => (30.degrees, "Coude 3 x 30°")
            case CoudeASegment90Avec4A22p5(_, _) =>
                (22.5.degrees, "Coude 4 x 22.5°")

        val r = shape.R
        val dh = shape.Dh

        val a = r * 2 * math.tan(ɑ.toUnit[Radian].value / 2)
        _interpolateHelper(
            shape = shape,
            resName = "EN 13384-1:2015+A1:2019 // table-B8_shape4.csv",
            tsvTableRawString = 
                """|a/Dh	Coude 2 x 45°	Coude 3 x 30°	Coude 4 x 22.5°
                   |1,00	0,40	0,25	0,17
                   |1,50	0,30	0,18	0,13
                   |2,00	0,30	0,17	0,12
                   |3,00	0,35	0,19	0,13
                   |5,00	0,40	0,20	0,15""".stripMargin,
            xHeader = "a/Dh",
            xi = (a / dh).value,
            xMinMax = (1.0, 5.0),
            yCriteria = None,
            yHeaderSelectFunc = _ => Right(yHeader)
        )

    private def yHeaderForShape123(
        s: AngleVifDe0A90_Unsafe | AngleVifDe0A90 | CoudeCourbe90 | CoudeCourbe90_Unsafe | CoudeCourbe60 | CoudeCourbe60_Unsafe
    ): Option[Double] => Either[DynamicFrictionCoeffOp.Err, String] =
        case Some(ratio) if ratio >= 30              => Right("Ld/Dh ≥ 30")
        case Some(ratio) if 30 > ratio && ratio >= 2 => Right("30 > Ld/Dh ≥ 2")
        case Some(_) if (
            s.isInstanceOf[AngleVifDe0A90_Unsafe] || 
            s.isInstanceOf[CoudeCourbe90_Unsafe] || 
            s.isInstanceOf[CoudeCourbe60_Unsafe]
        ) =>
            // println(s"WARNING: using ζ value as if Ld/Dh ≥ 2 but Ld/Dh = ${"%.3f".format(ratio)}") // TODO: print in log with level WARN
            Right("30 > Ld/Dh ≥ 2")
        case Some(ratio)    => Left(UnexpectedRatio_Ld_Dh(s, ratio))
        case None           => Left(NoGivenRatio_Ld_Dh(s))

end dynamicfrictioncoeff
