/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.{
    IsDirectionChange,
    IsLengthBearingPipeElement,
    IsPressureDiff,
    IsSectionGeometryChange,
    IsSingularFlowResistance,
    SetsInnerShape,
    SetsNumberOfFlows
}
import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.*

import magnolia1.Transl

sealed trait FlowOnlyPipeDescr_13384_V4

sealed trait FlowOnlyPreElementOp_13384_V4 extends FlowOnlyPipeDescr_13384_V4

sealed trait SetFlowOnlyPipeProp_13384_V4 extends FlowOnlyPreElementOp_13384_V4

object SetFlowOnlyPipeProp_13384_V4:

    @Transl(I(_.set_prop.SetInnerShape))
    case class SetInnerShape(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetFlowOnlyPipeProp_13384_V4
        with SetsInnerShape

    @Transl(I(_.set_prop.SetRoughness))
    case class SetRoughness(
        @Transl(I(_.terms.roughness))
        roughness: Roughness
    ) extends SetFlowOnlyPipeProp_13384_V4

    @Transl(I(_.set_prop.SetMaterial))
    case class SetMaterial(
        @Transl(I(_.set_prop.SetMaterial))
        material: Material_13384_V2
    ) extends SetFlowOnlyPipeProp_13384_V4

sealed trait FlowOnlyChannelTopologyOp_13384_V4 extends FlowOnlyPreElementOp_13384_V4

object FlowOnlyChannelTopologyOp_13384_V4:

    @Transl(I(_.set_prop.SetNumberOfFlows))
    case class SetNumberOfFlows(
        @Transl(I(_.set_prop.SetNumberOfFlows_fieldName))
        n_flows: NbOfFlows
    ) extends FlowOnlyChannelTopologyOp_13384_V4
        with SetsNumberOfFlows

sealed trait AddFlowOnlyPipeElement_13384_V4 extends FlowOnlyPipeDescr_13384_V4:
    def name: String

object AddFlowOnlyPipeElement_13384_V4:

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSlopped(
        @Transl(I(_.terms.name))
        name  : String,
        @Transl(I(_.terms.length))
        length: Length
    ) extends AddFlowOnlyPipeElement_13384_V4
        with IsLengthBearingPipeElement

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSloppedForceManualElevationGain(
        @Transl(I(_.terms.name))
        name          : String,
        @Transl(I(_.terms.length))
        length        : Length,
        @Transl(I(_.terms.elevation_gain))
        elevation_gain: Length
    ) extends AddFlowOnlyPipeElement_13384_V4
        with IsLengthBearingPipeElement

    /**
     * Legacy section type — treated as `AddSectionSlopped(name, length = horizontal_length)` by the engine.
     * The `horizontal_length` parameter is actually the pipe length along the current frame direction.
     * Actual elevation gain is auto-computed as `length × sin(inclination)` from the current direction frame.
     * Kept for backward compatibility; prefer `AddSectionSlopped` for new code.
     */
    @Transl(I(_.add_element.AddSectionHorizontal))
    case class AddSectionHorizontal(
        @Transl(I(_.terms.name))
        name             : String,
        @Transl(I(_.terms.horizontal_length))
        horizontal_length: Length
    ) extends AddFlowOnlyPipeElement_13384_V4
        with IsLengthBearingPipeElement

    /**
     * Legacy section type — treated as `AddSectionSlopped(name, length = elevation_gain)` by the engine.
     * The `elevation_gain` parameter is actually the pipe length along the current frame direction.
     * Actual elevation gain is auto-computed as `length × sin(inclination)` from the current direction frame.
     * Kept for backward compatibility; prefer `AddSectionSlopped` for new code.
     */
    @Transl(I(_.add_element.AddSectionVertical))
    case class AddSectionVertical(
        @Transl(I(_.terms.name))
        name          : String,
        @Transl(I(_.terms.elevation_gain))
        elevation_gain: Length
    ) extends AddFlowOnlyPipeElement_13384_V4
        with IsLengthBearingPipeElement

    sealed abstract class AddDirectionChange(
        @Transl(I(_.terms.name))
        val name  : String,
        @Transl(I(_.terms.angle))
        val angle : Angle,
        @Transl(I(_.terms.absolute_direction))
        val absDir: Option[AbsoluteDirection] = None
    ) extends AddFlowOnlyPipeElement_13384_V4
        with IsDirectionChange

    @Transl(I(_.add_element.AddAngleAdjustable))
    case class AddAngleAdjustable(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.angle))
        override val angle : Angle,
        @Transl(I(_.terms.zeta_ζ))
        val zeta           : QtyD[1],
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, angle, absDir)

    @Transl(I(_.add_element.AddSharpeAngle_0_to_90))
    case class AddSharpeAngle_0_to_90(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.angle))
        override val angle : Angle,
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, angle, absDir)

    // __INTERPRETATION__
    @Transl(I(_.add_element.AddSharpeAngle_0_to_90_Unsafe))
    case class AddSharpeAngle_0_to_90_Unsafe(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.angle))
        override val angle : Angle,
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, angle, absDir)

    @Transl(I(_.add_element.AddSmoothCurve_90))
    case class AddSmoothCurve_90(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.curvature_radius))
        curvature_radius   : Length,
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, 90.degrees, absDir)

    // __INTERPRETATION__
    @Transl(I(_.add_element.AddSmoothCurve_90_Unsafe))
    case class AddSmoothCurve_90_Unsafe(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.curvature_radius))
        curvature_radius   : Length,
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, 90.degrees, absDir)

    @Transl(I(_.add_element.AddSmoothCurve_60))
    case class AddSmoothCurve_60(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.curvature_radius))
        curvature_radius   : Length,
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, 60.degrees, absDir)

    // __INTERPRETATION__
    @Transl(I(_.add_element.AddSmoothCurve_60_Unsafe))
    case class AddSmoothCurve_60_Unsafe(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.curvature_radius))
        curvature_radius   : Length,
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, 60.degrees, absDir)

    // coudes à segments
    sealed abstract class CoudeASegment90(
        @Transl(I(_.terms.name))
        override val name     : String,
        @Transl(I(_.terms.number_of_segments))
        val number_of_segments: 2 | 3 | 4,
        @Transl(I(_.terms.curvature_radius))
        val curvature_radius  : Length,
        @Transl(I(_.terms.absolute_direction))
        override val absDir   : Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, 90.degrees, absDir)

    @Transl(I(_.add_element.AddElbows_2x45))
    case class AddElbows_2x45(
        @Transl(I(_.terms.name))
        override val name            : String,
        @Transl(I(_.terms.curvature_radius))
        override val curvature_radius: Length,
        @Transl(I(_.terms.absolute_direction))
        override val absDir          : Option[AbsoluteDirection] = None
    ) extends CoudeASegment90(name, 2, curvature_radius, absDir)

    @Transl(I(_.add_element.AddElbows_3x30))
    case class AddElbows_3x30(
        @Transl(I(_.terms.name))
        override val name            : String,
        @Transl(I(_.terms.curvature_radius))
        override val curvature_radius: Length,
        @Transl(I(_.terms.absolute_direction))
        override val absDir          : Option[AbsoluteDirection] = None
    ) extends CoudeASegment90(name, 3, curvature_radius, absDir)

    @Transl(I(_.add_element.AddElbows_4x22p5))
    case class AddElbows_4x22p5(
        @Transl(I(_.terms.name))
        override val name            : String,
        @Transl(I(_.terms.curvature_radius))
        override val curvature_radius: Length,
        @Transl(I(_.terms.absolute_direction))
        override val absDir          : Option[AbsoluteDirection] = None
    ) extends CoudeASegment90(name, 4, curvature_radius, absDir)

    // TODO: rename to AddSectionShapeChange
    sealed abstract class AddSectionChange(
        @Transl(I(_.terms.name))
        val name    : String,
        @Transl(I(_.terms.pipe_shape._self))
        val to_shape: PipeShape
    ) extends AddFlowOnlyPipeElement_13384_V4
        with IsSectionGeometryChange

    @Transl(I(_.add_element.AddSectionDecrease))
    case class AddSectionDecrease(
        @Transl(I(_.terms.name))
        override val name: String,
        @Transl(I(_.terms.diameter))
        to_diameter      : Length
    ) extends AddSectionChange(name, PipeShape.Circle(to_diameter))

    @Transl(I(_.add_element.AddSectionIncrease))
    case class AddSectionIncrease(
        @Transl(I(_.terms.name))
        override val name: String,
        @Transl(I(_.terms.diameter))
        to_diameter      : Length
    ) extends AddSectionChange(name, PipeShape.Circle(to_diameter))

    @Transl(I(_.add_element.AddFlowResistance))
    case class AddFlowResistance(
        @Transl(I(_.add_element.name))
        name         : String,
        @Transl(I(_.add_element.zeta))
        zeta         : QtyD[1],
        @Transl(I(_.add_element.cross_section))
        cross_section: OptionOfEither[AreaInCm2, PipeShape]
    ) extends AddFlowOnlyPipeElement_13384_V4
        with IsSingularFlowResistance

    case class AddPressureDiff(
        @Transl(I(_.terms.name))
        name               : String,
        @Transl(I(_.terms.pressure_difference))
        pressure_difference: Pressure
    ) extends AddFlowOnlyPipeElement_13384_V4
        with IsPressureDiff
