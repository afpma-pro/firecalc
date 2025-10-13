/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544

import cats.*
import cats.syntax.all.*

import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.en13384.pipedescr.SectionGeometryChange as SectionGeometryChange_13384
import afpma.firecalc.engine.models.en15544.pipedescr as en15544_pipedescr
import afpma.firecalc.engine.models.en15544.pipedescr.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.models.en15544.std.PressureLossCoeff.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.en13384.dynamicfrictioncoeff as dynamicfrictioncoeff_13384
import afpma.firecalc.engine.ops.resistance.*

import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

object dynamicfrictioncoeff:

    def whenRegularFor(pd: en15544_pipedescr.NotPressureDiff): DynamicFrictionCoeffOp.Result = 
        import regular.given
        pd match 
            case x: en15544_pipedescr.SingularFlowResistance  => x.dynamicFrictionCoeff
            case en15544_pipedescr.SectionGeometryChange(from, to)   => 
                // See RQ_002
                dynamicfrictioncoeff_13384.sectionGeometryChange.dynamicFrictionCoeff(
                    SectionGeometryChange_13384.make(from.area, to.area)
                )
            case x: en15544_pipedescr.DirectionChange         => x.dynamicFrictionCoeff
            case _: en15544_pipedescr.StraightSection         => DynamicFrictionCoeffOp.zero
        
    private object regular:

        given singularFlowResistance: DynamicFrictionCoeffOp[en15544_pipedescr.SingularFlowResistance] =
            DynamicFrictionCoeffOp.fromFunction[en15544_pipedescr.SingularFlowResistance](_.zeta.validNel)

        given directionChange: DynamicFrictionCoeffOp[en15544_pipedescr.DirectionChange]:
            extension (s: en15544_pipedescr.DirectionChange) 
                def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
                    s match
                        case ss: en15544_pipedescr.DirectionChange.AngleVifDe0A180 =>
                            angleVifDe0A180.dynamicFrictionCoeff(ss)
                        case ss: en15544_pipedescr.DirectionChange.CircularArc60 =>
                            circularArc60.dynamicFrictionCoeff(ss)

        // Individual Resistance
        given angleVifDe0A180: DynamicFrictionCoeffOp[en15544_pipedescr.DirectionChange.AngleVifDe0A180] =
            DynamicFrictionCoeffOp.fromFunction { shape =>
                _interpolateHelper(
                    shape = shape,
                    resName = "EN 15544:2023 // Table 3",
                    tsvTableRawString = 
                        """|Angle	ζ
                           |0	0,00
                           |10	0,10
                           |30	0,20
                           |45	0,40
                           |60	0,80
                           |90	1,20
                           |180	2,40""".stripMargin,
                    xHeader = "Angle",
                    xi = shape.α.toUnit[Degree].value,
                    xMinMax = (0, 180),
                    yCriteria = None,
                    yHeaderSelectFunc = _ => Right("ζ")
                )
            }

        // Individual Resistance
        given circularArc60: DynamicFrictionCoeffOp[en15544_pipedescr.DirectionChange.CircularArc60] =
            DynamicFrictionCoeffOp.fromFunction { _ =>
                (0.7.unitless: ζ).validNel[GErr]
            }

        given straightSection: DynamicFrictionCoeffOp[en15544_pipedescr.StraightSection] =
            DynamicFrictionCoeffOp.fromFunction { _ =>
                (0.0.unitless: ζ).validNel[GErr]
            }

    end regular

    def mkInstanceForNamedPipesConcat(
        namedPipesConcat: Vector[models.NamedPipeElDescrG[PipeElDescr]]
    )(using SSAlg: ShortSectionAlg): DynamicFrictionCoeffOp[models.NamedPipeElDescrG[DirectionChange]] = 
        dynfrict.DynamicFrictionCoeffOpForConcatenatedPipeVector(namedPipesConcat)
    
    // given DynamicFrictionCoeffOp[afpma.firecalc.engine.models.DirectionChange] with
    //     extension (s: afpma.firecalc.engine.models.DirectionChange) def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
    //         s match
    //             case dc_EN15544: en15544_pipedescr.DirectionChange =>
    //                 dc_EN15544.dynamicFrictionCoeff
    //             case dc_EN13384: en13384_pipedescr.DirectionChange =>
    //                 dc_EN13384.dynamicFrictionCoeff

    // HELPERS
    private def _interpolateHelper[S: Show](
        shape: S,
        resName: String,
        tsvTableRawString: String,
        xHeader: String,
        xi: Double,
        xMinMax: (Double, Double),
        yHeaderSelectFunc: Option[Double] => Either[GErr, String],
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
end dynamicfrictioncoeff