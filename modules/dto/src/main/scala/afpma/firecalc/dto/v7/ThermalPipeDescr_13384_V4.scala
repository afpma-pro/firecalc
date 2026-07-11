/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.AppendLayerDescr
import afpma.firecalc.dto.v4.AirSpaceDetailed_V2

import afpma.firecalc.domain.{
    IsBackendForbidden,
    IsDirectionChange,
    IsLengthBearingPipeElement,
    IsPressureDiff,
    IsSectionGeometryChange,
    IsSingularFlowResistance,
    IsSplitMergeTurn,
    NbOfFlows,
    SetsInnerShape,
    SetsNumberOfFlows
}

import afpma.firecalc.i18n.*

import magnolia1.Transl

sealed trait ThermalPipeDescr_13384_V4

sealed trait ThermalPreElementOp_13384_V4 extends ThermalPipeDescr_13384_V4

sealed trait SetThermalPipeProp_13384_V4 extends ThermalPreElementOp_13384_V4

object SetThermalPipeProp_13384_V4:

    sealed trait SetSingleProp extends SetThermalPipeProp_13384_V4

    // Dev-only DSL escape hatch: skips the automatic SectionGeometryChange element
    // insertion when the inner shape changes. Rejected by the payments backend via
    // `IsBackendForbidden` if it appears on the wire. See `IsBackendForbidden` scaladoc.
    @Transl(I(_.set_prop.SetInnerShape))
    case class SetInnerShapePreventSectionGeometryChangeAuto(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetSingleProp
        with SetsInnerShape
        with IsBackendForbidden {
        def forbiddenKind = IsBackendForbidden.SetsInnerShapePreventAutoKind
    }

    @Transl(I(_.set_prop.SetInnerShape))
    case class SetInnerShape(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetSingleProp
        with SetsInnerShape

    @Transl(I(_.set_prop.SetOuterShape))
    case class SetOuterShape(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetSingleProp

    @Transl(I(_.set_prop.SetThickness))
    case class SetThickness(
        @Transl(I(_.set_prop.SetThickness))
        thickness: Length
    ) extends SetSingleProp

    @Transl(I(_.set_prop.SetRoughness))
    case class SetRoughness(
        @Transl(I(_.set_prop.SetRoughness))
        roughness: Roughness
    ) extends SetSingleProp

    @Transl(I(_.set_prop.SetMaterial))
    case class SetMaterial(
        @Transl(I(_.set_prop.SetMaterial))
        material: Material_13384_V2
    ) extends SetSingleProp

    @Transl(I(_.set_prop.SetLayer))
    case class SetLayer(
        @Transl(I(_.set_prop.SetThickness))
        thickness           : Length,
        @Transl(I(_.terms.thermal_conductivity_λ))
        thermal_conductivity: WattsPerMeterKelvin
    ) extends SetSingleProp

    @Transl(I(_.set_prop.SetLayers))
    case class SetLayers(
        layers: List[AppendLayerDescr]
    ) extends SetSingleProp

    @Transl(I(_.set_prop.SetAirSpaceAfterLayers))
    case class SetAirSpaceAfterLayers(
        @Transl(I(_.en13384.air_space_detailed))
        air_space_detailed: AirSpaceDetailed_V2
    ) extends SetSingleProp

    @Transl(I(_.set_prop.SetPipeLocation))
    case class SetPipeLocation(
        @Transl(I(_.pipe_location.short))
        pipe_location: PipeLocation
    ) extends SetSingleProp

    @Transl(I(_.set_prop.SetDuctType))
    case class SetDuctType(
        duct: DuctType
    ) extends SetSingleProp

    // TODO: add Transl annotations
    case class SetPropertiesInBatch(
        batch_name: String,
        props     : Seq[SetSingleProp],
        image     : Option[String] = None
    ) extends SetThermalPipeProp_13384_V4

    @Transl(I(_.set_prop.LinedFlue))
    case class LinedFlue(
        batch_name: String,
        @Transl(I(_.set_prop.LinedFlue_liner))
        liner     : SetPropertiesInBatch,
        @Transl(I(_.en13384.air_space_detailed))
        air_space : AirSpaceDetailed_V2,
        @Transl(I(_.set_prop.LinedFlue_casing))
        casing    : SetPropertiesInBatch
    ) extends SetThermalPipeProp_13384_V4

    extension (props: Seq[SetSingleProp])

        /**
         * Extracts the inner shape set by a plain `SetInnerShape` op.
         *
         * Intentionally matches ONLY `SetInnerShape` — not the dev-only
         * `SetInnerShapePreventSectionGeometryChangeAuto` variant (which also extends
         * `SetsInnerShape`). The dev-only variant is a DSL escape hatch rejected by the
         * backend; surfacing it in user-facing form logic (lined-flue casing/liner
         * derivation, UI forms) would be a latent trap. A dev who nests the prevent
         * variant inside a `LinedFlue` batch will get `None` here, which is the correct
         * safe-failure: the lined-flue validation then reports a missing inner shape.
         * Do NOT widen this to `case _: SetsInnerShape`.
         */
        def extractInnerShape: Option[PipeShape] =
            props.collectFirst { case SetInnerShape(shape) => shape }

        def extractLayers: List[AppendLayerDescr] =
            props
                .flatMap:
                    case SetLayers(ls)       => ls
                    case SetLayer(e, lambda) => List(AppendLayerDescr.FromLambdaUsingThickness(e, lambda))
                    case _                   => Nil
                .toList

sealed trait ThermalChannelTopologyOp_13384_V4 extends ThermalPreElementOp_13384_V4

object ThermalChannelTopologyOp_13384_V4:

    @Transl(I(_.set_prop.SetNumberOfFlows))
    case class SetNumberOfFlows(
        @Transl(I(_.set_prop.SetNumberOfFlows_fieldName))
        n_flows: NbOfFlows
    ) extends ThermalChannelTopologyOp_13384_V4
        with SetsNumberOfFlows
        with IsBackendForbidden {
        def forbiddenKind = IsBackendForbidden.SetsNumberOfFlowsKind
    }

sealed trait AddThermalPipeElement_13384_V4 extends ThermalPipeDescr_13384_V4:
    def name: String

object AddThermalPipeElement_13384_V4:

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSlopped(
        @Transl(I(_.terms.name))
        name  : String,
        @Transl(I(_.terms.length))
        length: Length
    ) extends AddThermalPipeElement_13384_V4
        with IsLengthBearingPipeElement

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSloppedForceManualElevationGain(
        @Transl(I(_.terms.name))
        name          : String,
        @Transl(I(_.terms.length))
        length        : Length,
        @Transl(I(_.terms.elevation_gain))
        elevation_gain: Length
    ) extends AddThermalPipeElement_13384_V4
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
    ) extends AddThermalPipeElement_13384_V4
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
    ) extends AddThermalPipeElement_13384_V4
        with IsLengthBearingPipeElement

    sealed abstract class AddDirectionChange(
        @Transl(I(_.terms.name))
        val name  : String,
        @Transl(I(_.terms.angle))
        val angle : Angle,
        @Transl(I(_.terms.absolute_direction))
        val absDir: Option[AbsoluteDirection] = None
    ) extends AddThermalPipeElement_13384_V4
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
    ) extends AddThermalPipeElement_13384_V4
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
    ) extends AddThermalPipeElement_13384_V4
        with IsSingularFlowResistance

    case class AddPressureDiff(
        @Transl(I(_.terms.name))
        name               : String,
        @Transl(I(_.terms.pressure_difference))
        pressure_difference: Pressure
    ) extends AddThermalPipeElement_13384_V4
        with IsPressureDiff

    @Transl(I(_.split_merge.SplitSingleFlowIntoTwoFlowsWith90DegTurn))
    case class SplitSingleFlowIntoTwoFlowsWith90DegTurn(
        @Transl(I(_.terms.name))
        name         : String,
        @Transl(I(_.terms.absolute_direction))
        absDir       : Option[AbsoluteDirection] = None,
        @Transl(I(_.split_merge.newInnerShape))
        newInnerShape: PipeShape,
        @Transl(I(_.split_merge.offset))
        offset       : Length                    = 0.meters
    ) extends AddThermalPipeElement_13384_V4
        with SetsNumberOfFlows
        with SetsInnerShape
        with IsDirectionChange
        with IsSingularFlowResistance
        with IsSplitMergeTurn {
        def n_flows: NbOfFlows = 2
    }

    @Transl(I(_.split_merge.MergeTwoFlowsIntoSingleWith90DegTurn))
    case class MergeTwoFlowsIntoSingleWith90DegTurn(
        @Transl(I(_.terms.name))
        name         : String,
        @Transl(I(_.terms.absolute_direction))
        absDir       : Option[AbsoluteDirection] = None,
        @Transl(I(_.split_merge.newInnerShape))
        newInnerShape: PipeShape
    ) extends AddThermalPipeElement_13384_V4
        with SetsNumberOfFlows
        with SetsInnerShape
        with IsDirectionChange
        with IsSingularFlowResistance
        with IsSplitMergeTurn {
        def n_flows: NbOfFlows = 1
    }

extension (descrs: Seq[ThermalPipeDescr_13384_V4])
    /**
     * True iff the last *pipe element* (excluding `Set...` property setters) carries
     * the `IsSingularFlowResistance` marker — i.e. the pipe ends with a singular
     * flow resistance.
     */
    def endsWithSingularFlowResistance: Boolean =
        descrs
            .collect:
                case el: AddThermalPipeElement_13384_V4 => el
            .lastOption match
            case Some(_: IsSingularFlowResistance) => true
            case _                                 => false
