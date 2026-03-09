/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.common.typeclasses.ElementFactory
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544
import afpma.firecalc.engine.models.geometry.*
import afpma.firecalc.engine.standard.*

import cats.data.Validated
import cats.data.Validated.*
import cats.data.ValidatedNel
import cats.syntax.all.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

object ElementFactory_15544_Instances:

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

    case class FlowOnlyStraightSectionCtx_15544(
        geometry : Option[PipeShape],
        roughness: Option[Roughness],
        pipeType : PipeType
    )

    given flowOnlyStraightSection15544: ElementFactory[
        AddFlowOnlyPipeElement_15544.AddSectionSlopped | AddFlowOnlyPipeElement_15544.AddSectionHorizontal |
            AddFlowOnlyPipeElement_15544.AddSectionVertical,
        FlowOnlyPipeDescr_15544.StraightSection,
        FlowOnlyStraightSectionCtx_15544
    ] with
        def make(
            op: AddFlowOnlyPipeElement_15544.AddSectionSlopped | AddFlowOnlyPipeElement_15544.AddSectionHorizontal |
                AddFlowOnlyPipeElement_15544.AddSectionVertical
        )(using ctx: FlowOnlyStraightSectionCtx_15544) =
            val vg = ctx.getValidated(
                _.geometry,
                InnerGeometryMustBeSet(op.name, ctx.pipeType)
            )
            val vr = ctx.getValidated(
                _.roughness,
                RoughnessMustBeSet(op.name, ctx.pipeType)
            )

            val (len, elev_gain) = op match
                case AddFlowOnlyPipeElement_15544.AddSectionSlopped(
                        _,
                        len,
                        elev_gain
                    ) =>
                    (len, elev_gain)
                case AddFlowOnlyPipeElement_15544.AddSectionHorizontal(
                        _,
                        len
                    ) =>
                    (len, 0.0.m)
                case AddFlowOnlyPipeElement_15544.AddSectionVertical(
                        _,
                        elev_gain
                    ) =>
                    val len =
                        if (elev_gain < 0.meters) -elev_gain
                        else elev_gain
                    (len, elev_gain)

            (vg, vr).mapN { (g, r) =>
                FlowOnlyPipeDescr_15544.StraightSection        (
                    length         = len,
                    geometry       = g,
                    roughness      = r,
                    elevation_gain = elev_gain
                )
            }

    // ========== Direction Change Factory ==========

    case class DirectionChangeCtx_15544(
        geometry            : Option[PipeShape],
        pipeType            : PipeType,
        dirBeforePreviousDC : Option[Vec3]      = None,
        currentFrame        : Option[PipeFrame] = None
    )

    given directionChange15544: ElementFactory[
        AddFlowOnlyPipeElement_15544.AddDirectionChange,
        FlowOnlyPipeDescr_15544.DirectionChange,
        DirectionChangeCtx_15544
    ] with
        def make(op: AddFlowOnlyPipeElement_15544.AddDirectionChange)(using
            ctx: DirectionChangeCtx_15544
        ) =
            ctx.getValidated(
                _.geometry.map(_.dh),
                DirectionChangeRequiresSectionGeometry(ctx.pipeType)
            ).map { _ =>
                // Compute angleN2 from direction tracking if available, otherwise use DTO value
                val computedAngleN2: Option[QtyD[Degree]] =
                    (ctx.dirBeforePreviousDC, ctx.currentFrame) match
                        case (Some(dirBefore), Some(frame)) =>
                            Some(dirBefore.angleTo(frame.direction).withUnit[Degree])
                        case _ => None

                op match
                    case AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                            _,
                            angle,
                            angleN2
                        ) =>
                        FlowOnlyPipeDescr_15544.DirectionChange
                            .AngleVifDe0A180(angle, computedAngleN2.orElse(angleN2))
                    case AddFlowOnlyPipeElement_15544.AddCircularArc_60(_) =>
                        FlowOnlyPipeDescr_15544.DirectionChange.CircularArc60
            }

    // ========== Flow Resistance Factory ==========

    case class FlowResistanceCtx_15544(
        geometry: Option[PipeShape],
        pipeType: PipeType
    )

    given flowResistance15544: ElementFactory[
        AddFlowOnlyPipeElement_15544.AddFlowResistance,
        FlowOnlyPipeDescr_15544.SingularFlowResistance,
        FlowResistanceCtx_15544
    ] with
        def make(op: AddFlowOnlyPipeElement_15544.AddFlowResistance)(using
            ctx: FlowResistanceCtx_15544
        ) =
            op match
                case AddFlowOnlyPipeElement_15544.AddFlowResistance(
                        name,
                        zeta,
                        NoneOfEither
                    ) =>
                    ctx.getValidated(
                        _.geometry,
                        FlowResistanceRequiresGeometry(
                            op.name,
                            "EN15544",
                            ctx.pipeType
                        )
                    ).andThen { geom =>
                        FlowOnlyPipeDescr_15544
                            .SingularFlowResistance(
                                zeta,
                                crossSectionO = Some(geom.area)
                            )
                            .validNel
                    }
                case AddFlowOnlyPipeElement_15544.AddFlowResistance(
                        name,
                        zeta,
                        SomeLeft(area)
                    ) =>
                    FlowOnlyPipeDescr_15544
                        .SingularFlowResistance(
                            zeta,
                            Some(area.toUnit[Meter ^ 2])
                        )
                        .validNel
                case AddFlowOnlyPipeElement_15544.AddFlowResistance(
                        name,
                        zeta,
                        SomeRight(geom)
                    ) =>
                    FlowOnlyPipeDescr_15544
                        .SingularFlowResistance(
                            zeta,
                            Some(geom.area)
                        )
                        .validNel

    // ========== Section Geometry Change Factory ==========

    case class SectionGeometryChangeCtx_15544(
        geometry                 : Option[PipeShape],
        setPropsHasGeometryChange: Boolean,
        pipeType                 : PipeType
    )

    given sectionGeometryChange15544: ElementFactory[
        AddFlowOnlyPipeElement_15544.AddSectionShapeChange,
        FlowOnlyPipeDescr_15544.SectionGeometryChange,
        SectionGeometryChangeCtx_15544
    ] with
        def make(op: AddFlowOnlyPipeElement_15544.AddSectionShapeChange)(using
            ctx: SectionGeometryChangeCtx_15544
        ) =
            if (ctx.setPropsHasGeometryChange)
                CannotSetGeometryBeforeChange(ctx.pipeType).invalidNel
            else
                ctx.getValidated(
                    _.geometry,
                    SectionGeometryMustBeDefined(ctx.pipeType)
                ).map { fromGeom =>
                    FlowOnlyPipeDescr_15544.SectionGeometryChange(
                        from = fromGeom,
                        to   = op.to_shape
                    )
                }

    // ========== Pressure Diff Factory ==========

    given pressureDiff15544: ElementFactory[
        AddFlowOnlyPipeElement_15544.AddPressureDiff,
        FlowOnlyPipeDescr_15544.PressureDiff,
        FlowResistanceCtx_15544
    ] with
        def make(op: AddFlowOnlyPipeElement_15544.AddPressureDiff)(using
            ctx: FlowResistanceCtx_15544
        ) =
            ctx.getValidated(
                _.geometry,
                PressureDiffRequiresGeometry(
                    op.name,
                    "EN15544",
                    ctx.pipeType
                )
            ).andThen { geom =>
                FlowOnlyPipeDescr_15544
                    .PressureDiff(
                    pa = op.pressure_difference,
                    crossSectionO = ctx.geometry.map(_.area)
                )
                .validNel
            }
