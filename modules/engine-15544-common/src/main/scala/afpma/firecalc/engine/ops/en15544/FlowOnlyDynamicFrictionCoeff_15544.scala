/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.SectionGeometryChange as SectionGeometryChange_13384
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544 as en15544_pipedescr
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.resistance.*
import afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError
import afpma.firecalc.engine.standard.SlotContext

import cats.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

class FlowOnlyDynamicFrictionCoeff_15544()(using
    sectionTyp     : PipeType,
    sc             : SlotContext,
    dynFrictFactory: FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory
):

    def whenRegularFor(pd: en15544_pipedescr.NotPressureDiff): DynamicFrictionCoeffOp.Result =
        pd match
            case x: en15544_pipedescr.SingularFlowResistance =>
                x.zeta.validNel[SingularFlowResistanceCoeffError]
            case en15544_pipedescr.SectionGeometryChange(from, to) =>
                // See RQ_002
                val delegate = dynFrictFactory.make(sectionTyp, sc)
                delegate.thermalSectionGeometryChange.dynamicFrictionCoeff(
                    SectionGeometryChange_13384.make(from.area, to.area)
                )
            case _: en15544_pipedescr.StraightSection => DynamicFrictionCoeffOp.zero
            case dc: en15544_pipedescr.DirectionChange =>
                dc match
                    case ss: en15544_pipedescr.DirectionChange.AngleVifDe0A180 =>
                        _interpolateHelper            (
                            shape             = ss,
                            resName           = "EN 15544:2023 // Table 3",
                            tsvTableRawString = """|Angle	ζ
                                   |0	0,00
                                   |10	0,10
                                   |30	0,20
                                   |45	0,40
                                   |60	0,80
                                   |90	1,20
                                   |180	2,40""".stripMargin,
                            xHeader           = "Angle",
                            xi                = ss.α.toUnit[Degree].value,
                            xMinMax           = (0, 180),
                            yCriteria         = None,
                            yHeaderSelectFunc = _ => Right("ζ")
                        )
                    case _ : en15544_pipedescr.DirectionChange.CircularArc60   =>
                        (0.7.unitless: ζ).validNel[SingularFlowResistanceCoeffError]
                    case _ : en15544_pipedescr.SplitMerge90                    =>
                        CoefficientOfFlowResistance.splitMerge90Zeta.validNel[SingularFlowResistanceCoeffError]

    def mkInstanceForNamedPipesConcat(
        namedPipesConcat: Vector[models.NamedPipeElDescrG[PipeElDescr]]
    )(using SSAlg: ShortSectionAlg): DynamicFrictionCoeffOp[models.NamedPipeElDescrG[DirectionChange]] =
        dynfrict.DynamicFrictionCoeffOpForConcatenatedPipeVector(namedPipesConcat, sc)

    // given DynamicFrictionCoeffOp[afpma.firecalc.engine.models.DirectionChange] with
    //     extension (s: afpma.firecalc.engine.models.DirectionChange) def dynamicFrictionCoeff: DynamicFrictionCoeffOp.Result =
    //         s match
    //             case dc_EN15544: en15544_pipedescr.DirectionChange =>
    //                 dc_EN15544.dynamicFrictionCoeff
    //             case dc_EN13384: en13384_pipedescr.DirectionChange =>
    //                 dc_EN13384.dynamicFrictionCoeff

    // HELPERS
    private def _interpolateHelper[S](
        shape            : S,
        resName          : String,
        tsvTableRawString: String,
        xHeader          : String,
        xi               : Double,
        xMinMax          : (Double, Double),
        yHeaderSelectFunc: Option[Double] => Either[SingularFlowResistanceCoeffError, String],
        yCriteria        : Option[Double]
    )(using showS: Show[S]): DynamicFrictionCoeffOp.Result =
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
end FlowOnlyDynamicFrictionCoeff_15544

object FlowOnlyDynamicFrictionCoeff_15544:

    /**
     * Abstraction over the EN 13384 section-geometry-change friction coefficient.
     *  Provides the specific operation needed by the 15544 flow-only calculator.
     */
    trait DynFrict13384Like:
        def thermalSectionGeometryChange: DynamicFrictionCoeffOp[SectionGeometryChange_13384]

    /**
     * Factory that creates a [[DynFrict13384Like]] for a given [[PipeType]] and slot index.
     *  Leaf modules provide a concrete implementation backed by `DynamicFrictionCoeff_13384`.
     *  The `slotIndex` parameter ensures error targets correctly identify the affected slot.
     */
    trait DynFrict13384Factory:
        def make(pt: PipeType, sc: SlotContext): DynFrict13384Like
