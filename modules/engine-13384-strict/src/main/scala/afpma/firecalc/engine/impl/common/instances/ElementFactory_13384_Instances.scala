/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384
import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384
import afpma.firecalc.engine.models.geometry.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.typeclasses.ElementFactory

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.Validated.*
import cats.data.ValidatedNel
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.*

object ElementFactory_13384_Instances:

    // ========== Helper for validated access ==========

    extension [S](state: S)
        def getValidated[A](
            get  : S => Option[A],
            error: IncrementalValidation_Error
        ): ValidatedNel[IncrementalValidation_Error, A] =
            Validated
                .fromOption(get(state), error)
                .toValidatedNel

    // ========== Flow-Only Straight Section Factory ==========

    case class FlowOnlyStraightSectionCtx_13384(
        innerShape  : Option[PipeShape],
        roughness   : Option[Roughness],
        pipeType    : PipeType,
        currentFrame: Option[PipeFrame] = None
    )

    given flowOnlyStraightSection13384: ElementFactory[
        AddFlowOnlyPipeElement_13384.AddSectionSlopped |
            AddFlowOnlyPipeElement_13384.AddSectionSloppedForceManualElevationGain |
            AddFlowOnlyPipeElement_13384.AddSectionHorizontal | AddFlowOnlyPipeElement_13384.AddSectionVertical,
        FlowOnlyPipeDescr_13384.StraightSection,
        FlowOnlyStraightSectionCtx_13384
    ] with
        def make(
            op: AddFlowOnlyPipeElement_13384.AddSectionSlopped |
                AddFlowOnlyPipeElement_13384.AddSectionSloppedForceManualElevationGain |
                AddFlowOnlyPipeElement_13384.AddSectionHorizontal | AddFlowOnlyPipeElement_13384.AddSectionVertical
        )(using ctx: FlowOnlyStraightSectionCtx_13384) =
            val vig = ctx.getValidated(
                _.innerShape,
                InnerGeometryMustBeSet(op.name, ctx.pipeType)
            )
            val vr  = ctx.getValidated(
                _.roughness,
                RoughnessMustBeSet(op.name, ctx.pipeType)
            )

            val (len, elev_gain, auto_compute_elev_gain) = op match
                case AddFlowOnlyPipeElement_13384.AddSectionSloppedForceManualElevationGain(
                        _,
                        len,
                        elev_gain
                    ) =>
                    (len, elev_gain, false)
                case AddFlowOnlyPipeElement_13384.AddSectionSlopped(
                        _,
                        len
                    ) =>
                    (len, 0.0.m, true)
                case AddFlowOnlyPipeElement_13384.AddSectionHorizontal(
                        _,
                        len
                    ) =>
                    (len, 0.0.m, true)
                case AddFlowOnlyPipeElement_13384.AddSectionVertical(
                        _,
                        elev_gain
                    ) =>
                    val len =
                        if (elev_gain < 0.meters) -elev_gain
                        else elev_gain
                    (len, elev_gain, true)

            val finalElevGain = ctx.currentFrame match
                case Some(frame) =>
                    if auto_compute_elev_gain
                    then (math.abs(len.value) * frame.direction.z).m
                    else elev_gain
                case None        => elev_gain

            (vig, vr).mapN { (ig, r) =>
                FlowOnlyPipeDescr_13384.StraightSection        (
                    length         = len,
                    innerShape     = ig,
                    roughness      = r,
                    elevation_gain = finalElevGain
                )
            }

    // ========== Thermal Straight Section Factory ==========

    case class ThermalStraightSectionCtx_13384(
        innerShape          : Option[PipeShape],
        outer_shape         : Option[PipeShape],
        roughness           : Option[Roughness],
        layers              : Option[List[AppendLayerDescr]],
        airSpace_afterLayers: Option[AirSpaceDetailed],
        pipeLoc             : Option[PipeLocation],
        ductType            : Option[DuctType],
        pipeType            : PipeType,
        currentFrame        : Option[PipeFrame] = None
    )

    given thermalStraightSection13384: ElementFactory[
        AddThermalPipeElement_13384.AddSectionSlopped |
            AddThermalPipeElement_13384.AddSectionSloppedForceManualElevationGain |
            AddThermalPipeElement_13384.AddSectionHorizontal | AddThermalPipeElement_13384.AddSectionVertical,
        ThermalPipeDescr_13384.StraightSection,
        ThermalStraightSectionCtx_13384
    ] with
        def make(
            op: AddThermalPipeElement_13384.AddSectionSlopped |
                AddThermalPipeElement_13384.AddSectionSloppedForceManualElevationGain |
                AddThermalPipeElement_13384.AddSectionHorizontal | AddThermalPipeElement_13384.AddSectionVertical
        )(using ctx: ThermalStraightSectionCtx_13384) =
            val vig     = ctx.getValidated(
                _.innerShape,
                InnerGeometryMustBeSet(op.name, ctx.pipeType)
            )
            val vog     = ctx.getValidated(
                _.outer_shape,
                OuterGeometryMustBeSet(op.name, ctx.pipeType)
            )
            val vr      = ctx.getValidated(
                _.roughness,
                RoughnessMustBeSet(op.name, ctx.pipeType)
            )
            val vlayers = ctx.getValidated(
                _.layers,
                LayersMustBeSet(op.name, ctx.pipeType)
            )
            val vasp    = ctx.getValidated(
                _.airSpace_afterLayers,
                AirSpaceAfterLayersMustBeSet(op.name, ctx.pipeType)
            )
            val vpl     = ctx.getValidated(
                _.pipeLoc,
                PipeLocationMustBeSet(op.name, ctx.pipeType)
            )
            val vduct   = ctx.getValidated(
                _.ductType,
                DuctTypeMustBeSet(op.name, ctx.pipeType)
            )

            val (len, elev_gain, auto_compute_elev_gain) = op match
                case AddThermalPipeElement_13384.AddSectionSloppedForceManualElevationGain(
                        _,
                        len,
                        elev_gain
                    ) =>
                    (len, elev_gain, false)
                case AddThermalPipeElement_13384.AddSectionSlopped(
                        _,
                        len
                    ) =>
                    (len, 0.0.m, true)
                case AddThermalPipeElement_13384.AddSectionHorizontal(
                        _,
                        len
                    ) =>
                    (len, 0.0.m, true)
                case AddThermalPipeElement_13384.AddSectionVertical(
                        _,
                        elev_gain
                    ) =>
                    val len =
                        if (elev_gain < 0.meters) -elev_gain
                        else elev_gain
                    (len, elev_gain, true)

            val finalElevGain = ctx.currentFrame match
                case Some(frame) =>
                    if auto_compute_elev_gain
                    then (math.abs(len.value) * frame.direction.z).m
                    else elev_gain
                case None        => elev_gain

            (vig, vog, vr, vlayers, vasp, vpl, vduct).mapN { (ig, og, r, layers, asp, pl, duct) =>
                ThermalPipeDescr_13384.StraightSection          (
                    length           = len,
                    innerShape       = ig,
                    outer_shape      = og,
                    roughness        = r,
                    layers           = layers,
                    elevation_gain   = finalElevGain,
                    airSpaceDetailed = asp,
                    pipeLoc          = pl,
                    ductType         = duct
                )
            }

    // ========== FlowOnly Direction Change Factory ==========

    case class DirectionChangeCtx_13384(
        innerShape         : Option[PipeShape],
        nextSectionLength  : Option[QtyD[Meter]],
        pipeType           : PipeType,
        dirBeforePreviousDC: Option[Vec3]      = None,
        currentFrame       : Option[PipeFrame] = None
    )

    given flowOnlyDirectionChange13384: ElementFactory[
        AddFlowOnlyPipeElement_13384.AddDirectionChange,
        FlowOnlyPipeDescr_13384.DirectionChange,
        DirectionChangeCtx_13384
    ] with
        def make(op: AddFlowOnlyPipeElement_13384.AddDirectionChange)(using
            ctx: DirectionChangeCtx_13384
        ) =
            val vDh: ValidatedNel[IncrementalValidation_Error, QtyD[Meter]] =
                ctx.getValidated(
                    _.innerShape.map(_.dh),
                    SectionGeometryMustBeDefined(ctx.pipeType)
                )
            val vLd: ValidatedNel[IncrementalValidation_Error, QtyD[Meter]] =
                Validated.fromOption(
                    ctx.nextSectionLength,
                    ifNone = NonEmptyList.one(
                        NextSectionLengthMustBeDefined(ctx.pipeType)
                    )
                )

            // Compute angleN2 from direction tracking if available
            val angleN2: Option[QtyD[Degree]] =
                (ctx.dirBeforePreviousDC, ctx.currentFrame) match
                    case (Some(dirBefore), Some(frame)) =>
                        Some(dirBefore.angleTo(frame.direction).withUnit[Degree])
                    case _ => None

            (vDh, vLd).mapN { (dh: QtyD[Meter], ld: QtyD[Meter]) =>
                op match
                    case AddFlowOnlyPipeElement_13384.AddAngleAdjustable(
                            name,
                            angle,
                            zeta,
                            _
                        ) =>
                        FlowOnlyPipeDescr_13384.AngleSpecifique(angle, zeta, angleN2)
                    case AddFlowOnlyPipeElement_13384.AddSharpeAngle_0_to_90(
                            name,
                            angle,
                            _
                        ) =>
                        FlowOnlyPipeDescr_13384.AngleVifDe0A90     (
                            angle,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddFlowOnlyPipeElement_13384
                            .AddSharpeAngle_0_to_90_Unsafe(name, angle, _) =>
                        FlowOnlyPipeDescr_13384.AngleVifDe0A90_Unsafe     (
                            angle,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddFlowOnlyPipeElement_13384.AddSmoothCurve_90(
                            name,
                            r,
                            _
                        ) =>
                        FlowOnlyPipeDescr_13384.CoudeCourbe90     (
                            r,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddFlowOnlyPipeElement_13384.AddSmoothCurve_90_Unsafe(
                            name,
                            r,
                            _
                        ) =>
                        FlowOnlyPipeDescr_13384.CoudeCourbe90_Unsafe     (
                            r,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddFlowOnlyPipeElement_13384.AddSmoothCurve_60(
                            name,
                            r,
                            _
                        ) =>
                        FlowOnlyPipeDescr_13384.CoudeCourbe60     (
                            r,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddFlowOnlyPipeElement_13384.AddSmoothCurve_60_Unsafe(
                            name,
                            r,
                            _
                        ) =>
                        FlowOnlyPipeDescr_13384.CoudeCourbe60_Unsafe     (
                            r,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddFlowOnlyPipeElement_13384.AddElbows_2x45(name, r, _) =>
                        FlowOnlyPipeDescr_13384.CoudeASegment90Avec2A45     (
                            r,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddFlowOnlyPipeElement_13384.AddElbows_3x30(name, r, _) =>
                        FlowOnlyPipeDescr_13384.CoudeASegment90Avec3A30     (
                            r,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddFlowOnlyPipeElement_13384.AddElbows_4x22p5(
                            name,
                            r,
                            _
                        ) =>
                        FlowOnlyPipeDescr_13384.CoudeASegment90Avec4A22p5     (
                            r,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
            }

    // ========== Thermal Direction Change Factory ==========

    given thermalDirectionChange13384: ElementFactory[
        AddThermalPipeElement_13384.AddDirectionChange,
        ThermalPipeDescr_13384.DirectionChange,
        DirectionChangeCtx_13384
    ] with
        def make(op: AddThermalPipeElement_13384.AddDirectionChange)(using
            ctx: DirectionChangeCtx_13384
        ) =
            val vDh: ValidatedNel[IncrementalValidation_Error, QtyD[Meter]] =
                ctx.getValidated(
                    _.innerShape.map(_.dh),
                    SectionGeometryMustBeDefined(ctx.pipeType)
                )
            val vLd: ValidatedNel[IncrementalValidation_Error, QtyD[Meter]] =
                Validated.fromOption(
                    ctx.nextSectionLength,
                    ifNone = NonEmptyList.one(
                        NextSectionLengthMustBeDefined(ctx.pipeType)
                    )
                )

            // Compute angleN2 from direction tracking if available
            val angleN2: Option[QtyD[Degree]] =
                (ctx.dirBeforePreviousDC, ctx.currentFrame) match
                    case (Some(dirBefore), Some(frame)) =>
                        Some(dirBefore.angleTo(frame.direction).withUnit[Degree])
                    case _ => None

            (vDh, vLd).mapN { (dh: QtyD[Meter], ld: QtyD[Meter]) =>
                op match
                    case AddThermalPipeElement_13384.AddAngleAdjustable(
                            name,
                            angle,
                            zeta,
                            _
                        ) =>
                        ThermalPipeDescr_13384.AngleSpecifique(angle, zeta, angleN2)
                    case AddThermalPipeElement_13384.AddSharpeAngle_0_to_90(
                            name,
                            angle,
                            _
                        ) =>
                        ThermalPipeDescr_13384.AngleVifDe0A90     (
                            angle,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddThermalPipeElement_13384
                            .AddSharpeAngle_0_to_90_Unsafe(name, angle, _) =>
                        ThermalPipeDescr_13384.AngleVifDe0A90_Unsafe     (
                            angle,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddThermalPipeElement_13384.AddSmoothCurve_90(
                            name,
                            r,
                            _
                        ) =>
                        ThermalPipeDescr_13384.CoudeCourbe90     (
                            r,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddThermalPipeElement_13384.AddSmoothCurve_90_Unsafe(
                            name,
                            r,
                            _
                        ) =>
                        ThermalPipeDescr_13384.CoudeCourbe90_Unsafe     (
                            r,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddThermalPipeElement_13384.AddSmoothCurve_60(
                            name,
                            r,
                            _
                        ) =>
                        ThermalPipeDescr_13384.CoudeCourbe60     (
                            r,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddThermalPipeElement_13384.AddSmoothCurve_60_Unsafe(
                            name,
                            r,
                            _
                        ) =>
                        ThermalPipeDescr_13384.CoudeCourbe60_Unsafe     (
                            r,
                            Ld      = ld,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddThermalPipeElement_13384.AddElbows_2x45(name, r, _) =>
                        ThermalPipeDescr_13384.CoudeASegment90Avec2A45     (
                            r,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddThermalPipeElement_13384.AddElbows_3x30(name, r, _) =>
                        ThermalPipeDescr_13384.CoudeASegment90Avec3A30     (
                            r,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
                    case AddThermalPipeElement_13384.AddElbows_4x22p5(
                            name,
                            r,
                            _
                        ) =>
                        ThermalPipeDescr_13384.CoudeASegment90Avec4A22p5     (
                            r,
                            Dh      = dh,
                            angleN2 = angleN2
                        )
            }

    // ========== FlowOnly Flow Resistance Factory ==========

    case class FlowResistanceCtx_13384(
        innerShape: Option[PipeShape],
        pipeType  : PipeType
    )

    given flowOnlyFlowResistance13384: ElementFactory[
        AddFlowOnlyPipeElement_13384.AddFlowResistance,
        FlowOnlyPipeDescr_13384.SingularFlowResistance,
        FlowResistanceCtx_13384
    ] with
        def make(op: AddFlowOnlyPipeElement_13384.AddFlowResistance)(using
            ctx: FlowResistanceCtx_13384
        ) =
            op match
                case AddFlowOnlyPipeElement_13384.AddFlowResistance(
                        name,
                        zeta,
                        NoneOfEither
                    ) =>
                    ctx.getValidated(
                        _.innerShape,
                        FlowResistanceRequiresGeometry(
                            op.name,
                            "EN13384",
                            ctx.pipeType
                        )
                    ).andThen { geom =>
                        FlowOnlyPipeDescr_13384
                            .SingularFlowResistance(
                                zeta,
                                crossSectionO = Some(geom.area)
                            )
                            .validNel
                    }
                case AddFlowOnlyPipeElement_13384.AddFlowResistance(
                        name,
                        zeta,
                        SomeLeft(area)
                    ) =>
                    FlowOnlyPipeDescr_13384
                        .SingularFlowResistance(
                            zeta,
                            Some(area.toUnit[Meter ^ 2])
                        )
                        .validNel
                case AddFlowOnlyPipeElement_13384.AddFlowResistance(
                        name,
                        zeta,
                        SomeRight(geom)
                    ) =>
                    FlowOnlyPipeDescr_13384
                        .SingularFlowResistance(
                            zeta,
                            Some(geom.area)
                        )
                        .validNel

    // ========== Thermal Flow Resistance Factory ==========

    given thermalFlowResistance13384: ElementFactory[
        AddThermalPipeElement_13384.AddFlowResistance,
        ThermalPipeDescr_13384.SingularFlowResistance,
        FlowResistanceCtx_13384
    ] with
        def make(op: AddThermalPipeElement_13384.AddFlowResistance)(using
            ctx: FlowResistanceCtx_13384
        ) =
            op match
                case AddThermalPipeElement_13384.AddFlowResistance(
                        name,
                        zeta,
                        NoneOfEither
                    ) =>
                    ctx.getValidated(
                        _.innerShape,
                        FlowResistanceRequiresGeometry(
                            op.name,
                            "EN13384",
                            ctx.pipeType
                        )
                    ).andThen { geom =>
                        ThermalPipeDescr_13384
                            .SingularFlowResistance(
                                zeta,
                                crossSectionO = Some(geom.area)
                            )
                            .validNel
                    }
                case AddThermalPipeElement_13384.AddFlowResistance(
                        name,
                        zeta,
                        SomeLeft(area)
                    ) =>
                    ThermalPipeDescr_13384
                        .SingularFlowResistance(
                            zeta,
                            Some(area.toUnit[Meter ^ 2])
                        )
                        .validNel
                case AddThermalPipeElement_13384.AddFlowResistance(
                        name,
                        zeta,
                        SomeRight(geom)
                    ) =>
                    ThermalPipeDescr_13384
                        .SingularFlowResistance(
                            zeta,
                            Some(geom.area)
                        )
                        .validNel

    // ========== FlowOnly Section Geometry Change Factory ==========

    case class SectionGeometryChangeCtx_13384(
        innerShape               : Option[PipeShape],
        setPropsHasGeometryChange: Boolean,
        pipeType                 : PipeType
    )

    given flowOnlySectionGeometryChange13384: ElementFactory[
        AddFlowOnlyPipeElement_13384.AddSectionChange,
        FlowOnlyPipeDescr_13384.SectionGeometryChange,
        SectionGeometryChangeCtx_13384
    ] with
        def make(op: AddFlowOnlyPipeElement_13384.AddSectionChange)(using
            ctx: SectionGeometryChangeCtx_13384
        ) =
            if (ctx.setPropsHasGeometryChange)
                CannotSetGeometryBeforeChange(ctx.pipeType).invalidNel
            else
                ctx.getValidated(
                    _.innerShape,
                    SectionGeometryMustBeDefined(ctx.pipeType)
                ).andThen {
                    case fromCircleGeom: PipeShape.Circle =>
                        val sec = op match
                            case AddFlowOnlyPipeElement_13384
                                    .AddSectionDecrease(
                                        _,
                                        diam
                                    ) =>
                                FlowOnlyPipeDescr_13384.SectionDecrease
                                    .mkFromDiameters(
                                        fromD1 = fromCircleGeom.diameter,
                                        toD2   = diam
                                    )
                            case AddFlowOnlyPipeElement_13384
                                    .AddSectionIncrease(
                                        _,
                                        diam
                                    ) =>
                                FlowOnlyPipeDescr_13384.SectionIncrease
                                    .mkFromDiameters(
                                        fromD1 = fromCircleGeom.diameter,
                                        toD2   = diam
                                    )
                        sec.validNel
                    case notACircleGeom: PipeShape        =>
                        SectionChangeRequiresCircle(
                            notACircleGeom.toString,
                            ctx.pipeType
                        ).invalidNel
                }

    // ========== Thermal Section Geometry Change Factory ==========

    given thermalSectionGeometryChange13384: ElementFactory[
        AddThermalPipeElement_13384.AddSectionChange,
        ThermalPipeDescr_13384.SectionGeometryChange,
        SectionGeometryChangeCtx_13384
    ] with
        def make(op: AddThermalPipeElement_13384.AddSectionChange)(using
            ctx: SectionGeometryChangeCtx_13384
        ) =
            if (ctx.setPropsHasGeometryChange)
                CannotSetGeometryBeforeChange(ctx.pipeType).invalidNel
            else
                ctx.getValidated(
                    _.innerShape,
                    SectionGeometryMustBeDefined(ctx.pipeType)
                ).andThen {
                    case fromCircleGeom: PipeShape.Circle =>
                        val sec = op match
                            case AddThermalPipeElement_13384.AddSectionDecrease(
                                    _,
                                    diam
                                ) =>
                                ThermalPipeDescr_13384.SectionDecrease
                                    .mkFromDiameters(
                                        fromD1 = fromCircleGeom.diameter,
                                        toD2   = diam
                                    )
                            case AddThermalPipeElement_13384.AddSectionIncrease(
                                    _,
                                    diam
                                ) =>
                                ThermalPipeDescr_13384.SectionIncrease
                                    .mkFromDiameters(
                                        fromD1 = fromCircleGeom.diameter,
                                        toD2   = diam
                                    )
                        sec.validNel
                    case notACircleGeom: PipeShape        =>
                        SectionChangeRequiresCircle(
                            notACircleGeom.toString,
                            ctx.pipeType
                        ).invalidNel
                }

    // ========== FlowOnly Pressure Diff Factory ==========

    given flowOnlyPressureDiff13384: ElementFactory[
        AddFlowOnlyPipeElement_13384.AddPressureDiff,
        FlowOnlyPipeDescr_13384.PressureDiff,
        FlowResistanceCtx_13384
    ] with
        def make(op: AddFlowOnlyPipeElement_13384.AddPressureDiff)(using
            ctx: FlowResistanceCtx_13384
        ) =
            ctx.getValidated(
                _.innerShape,
                FlowResistanceRequiresGeometry(
                    op.name,
                    "EN13384",
                    ctx.pipeType
                )
            ).andThen { geom =>
                FlowOnlyPipeDescr_13384
                    .PressureDiff           (
                        pa            = op.pressure_difference,
                        crossSectionO = Some(geom.area)
                    )
                    .validNel
            }

    // ========== Thermal Pressure Diff Factory ==========

    given thermalPressureDiff13384: ElementFactory[
        AddThermalPipeElement_13384.AddPressureDiff,
        ThermalPipeDescr_13384.PressureDiff,
        FlowResistanceCtx_13384
    ] with
        def make(op: AddThermalPipeElement_13384.AddPressureDiff)(using
            ctx: FlowResistanceCtx_13384
        ) =
            ctx.getValidated(
                _.innerShape,
                FlowResistanceRequiresGeometry(
                    op.name,
                    "EN13384",
                    ctx.pipeType
                )
            ).andThen { geom =>
                ThermalPipeDescr_13384
                    .PressureDiff           (
                        pa            = op.pressure_difference,
                        crossSectionO = Some(geom.area)
                    )
                    .validNel
            }
