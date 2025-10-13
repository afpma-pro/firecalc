/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import cats.Show
import cats.data.*
import cats.syntax.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.units.coulombutils.*

trait DynamicFrictionCoeffOp[A]:

    extension (a: A)
        def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result

object DynamicFrictionCoeffOp:

    // // Ops
    // extension [S: DynamicFrictionCoeffOp](s: S)
    //     def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
    //         DynamicFrictionCoeffOp.apply[S].ζ(s)

    // ERROR TYPES
    export afpma.firecalc.engine.standard.PressureLossCoeff_Error
    export afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError

    // according to EN 13384-1:2015+A1:2019

    type Err = afpma.firecalc.engine.standard.PressureLossCoeff_Error
    type Result = ValidatedNel[Err, ζ]

    extension (res: Result)
        def toENelString: ValidatedNel[String, ζ] = res.leftMap(_.map(_.msg))

    // summon helper
    def apply[S](using
        inst: DynamicFrictionCoeffOp[S]
    ): DynamicFrictionCoeffOp[S] =
        inst

    import SingularFlowResistanceCoeffError.*

    val zero: Result = CoefficientOfFlowResistance.zero.validNel

    // creation methods
    def fromFunction[S](
        f: S => Result
    ): DynamicFrictionCoeffOp[S] =
        new DynamicFrictionCoeffOp[S] {
            extension (s: S) def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result = f(s)
        }

    def interpolateHelperE[S: Show](
        shape: S,
        resName: String,
        tsvTableRawString: String,
        xHeader: String,
        xi: Double,
        xMinMax: (Double, Double),
        yHeaderSelectFunc: Option[Double] => Either[Err, String],
        yCriteria: Option[Double]
    ): Either[Err, ζ] = {
        val (xmin, xmax) = xMinMax

        if (xi < xmin) Left(ValueOutOfBound[S](shape, "x", xi, xmin, xmax))
        else if (xi > xmax) Left(ValueOutOfBound[S](shape, "x", xi, xmin, xmax))
        else
            val data = TSVTableString.fromString(tsvTableRawString)
            for
                yHeader <- yHeaderSelectFunc(yCriteria)
                out <- data.getUsingLinearInterpolation(xHeader, yHeader)(
                    xi
                ) match
                    case Some(coeff) => 
                        Right(coeff)
                    case None =>
                        Left(
                            CouldNotComputeIndividualCoefficientForShape[S](
                                shape,
                                s"interpolation error for resource $resName, xHeader=$xHeader, yHeader=$yHeader, xi=$xi"
                            )
                        )
            yield out.ea: ζ
    }

    def interpolateHelper[S: Show](
        shape: S,
        resName: String,
        tsvTableRawString: String,
        xHeader: String,
        xi: Double,
        xMinMax: (Double, Double),
        yHeaderSelectFunc: Option[Double] => Either[Err, String],
        yCriteria: Option[Double]
    ): Result = 
        interpolateHelperE(
            shape, resName, tsvTableRawString, xHeader, xi, xMinMax, yHeaderSelectFunc, yCriteria
        ).toValidatedNel

end DynamicFrictionCoeffOp