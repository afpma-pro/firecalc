/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.{
    AbsoluteDirection,
    HasDirectionChangeData,
    HasInnerShapeValue,
    HasSectionElevation,
    HasSectionLength,
    HasSplitMergeData,
    IsBackendForbidden,
    IsDirectionChange,
    IsPressureDiff,
    IsSectionGeometryChange,
    IsSingularFlowResistance,
    IsSplitMergeTurn,
    NbOfFlows,
    SetsInnerShape,
    SetsNumberOfFlows
}
import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.*

import magnolia1.Transl

sealed trait FlowOnlyPipeDescr_15544_V4

sealed trait FlowOnlyPreElementOp_15544_V4 extends FlowOnlyPipeDescr_15544_V4

sealed trait SetFlowOnlyPipeProp_15544_V4 extends FlowOnlyPreElementOp_15544_V4

object SetFlowOnlyPipeProp_15544_V4:

    // Dev-only DSL escape hatch: skips the automatic SectionGeometryChange element
    // insertion when the inner shape changes. Rejected by the payments backend via
    // `IsBackendForbidden` if it appears on the wire. See `IsBackendForbidden` scaladoc.
    @Transl(I(_.set_prop.SetInnerShape))
    case class SetInnerShapePreventSectionGeometryChangeAuto(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetFlowOnlyPipeProp_15544_V4
        with HasInnerShapeValue
        with IsBackendForbidden {
        def innerShapeValue = shape
        def forbiddenKind   = IsBackendForbidden.SetsInnerShapePreventAutoKind
    }

    @Transl(I(_.set_prop.SetInnerShape))
    case class SetInnerShape(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetFlowOnlyPipeProp_15544_V4
        with HasInnerShapeValue {
        def innerShapeValue = shape
    }

    @Transl(I(_.set_prop.SetRoughness))
    case class SetRoughness(
        @Transl(I(_.terms.roughness))
        roughness: Roughness
    ) extends SetFlowOnlyPipeProp_15544_V4

    @Transl(I(_.set_prop.SetMaterial))
    case class SetMaterial(
        @Transl(I(_.set_prop.SetMaterial))
        material: Material_15544_V2
    ) extends SetFlowOnlyPipeProp_15544_V4

sealed trait FlowOnlyChannelTopologyOp_15544_V4 extends FlowOnlyPreElementOp_15544_V4

object FlowOnlyChannelTopologyOp_15544_V4:

    @Transl(I(_.set_prop.SetNumberOfFlows))
    case class SetNumberOfFlows(
        @Transl(I(_.set_prop.SetNumberOfFlows_fieldName))
        n_flows: NbOfFlows
    ) extends FlowOnlyChannelTopologyOp_15544_V4
        with SetsNumberOfFlows
        with IsBackendForbidden {
        def forbiddenKind = IsBackendForbidden.SetsNumberOfFlowsKind
    }

sealed trait AddFlowOnlyPipeElement_15544_V4 extends FlowOnlyPipeDescr_15544_V4:
    def name: String

object AddFlowOnlyPipeElement_15544_V4:

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSlopped(
        @Transl(I(_.terms.name))
        name  : String,
        @Transl(I(_.terms.length))
        length: Length
    ) extends AddFlowOnlyPipeElement_15544_V4
        with HasSectionLength {
        def sectionLength = length
    }

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSloppedForceManualElevationGain(
        @Transl(I(_.terms.name))
        name          : String,
        @Transl(I(_.terms.length))
        length        : Length,
        @Transl(I(_.terms.elevation_gain))
        elevation_gain: Length
    ) extends AddFlowOnlyPipeElement_15544_V4
        with HasSectionElevation {
        def sectionLength        = length
        def sectionElevationGain = elevation_gain
    }

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
    ) extends AddFlowOnlyPipeElement_15544_V4
        with HasSectionLength {
        def sectionLength = horizontal_length
    }

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
    ) extends AddFlowOnlyPipeElement_15544_V4
        with HasSectionLength {
        def sectionLength = elevation_gain
    }

    @Transl(I(_.add_element.add_direction_change_element))
    sealed abstract class AddDirectionChange(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.angle))
        val angle          : Angle,
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddFlowOnlyPipeElement_15544_V4
        with HasDirectionChangeData

    @Transl(I(_.add_element.AddSharpeAngle_0_to_180))
    case class AddSharpeAngle_0_to_180(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.angle))
        override val angle : Angle,
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, angle, absDir)

    @Transl(I(_.add_element.AddCircularArc_60))
    case class AddCircularArc_60(
        @Transl(I(_.terms.name))
        override val name  : String,
        @Transl(I(_.terms.absolute_direction))
        override val absDir: Option[AbsoluteDirection] = None
    ) extends AddDirectionChange(name, 60.degrees, absDir)

    @Transl(I(_.add_element.AddSectionShapeChange))
    case class AddSectionShapeChange(
        @Transl(I(_.terms.name))
        val name    : String,
        @Transl(I(_.terms.pipe_shape._self))
        val to_shape: PipeShape
    ) extends AddFlowOnlyPipeElement_15544_V4
        with IsSectionGeometryChange

    @Transl(I(_.add_element.AddFlowResistance))
    case class AddFlowResistance(
        @Transl(I(_.add_element.name))
        name         : String,
        @Transl(I(_.add_element.zeta))
        zeta         : QtyD[1],
        @Transl(I(_.add_element.cross_section))
        cross_section: OptionOfEither[AreaInCm2, PipeShape]
    ) extends AddFlowOnlyPipeElement_15544_V4
        with IsSingularFlowResistance

    @Transl(I(_.add_element.AddPressureDiff))
    case class AddPressureDiff(
        @Transl(I(_.add_element.name))
        name               : String,
        @Transl(I(_.terms.pressure_difference))
        pressure_difference: Pressure
    ) extends AddFlowOnlyPipeElement_15544_V4
        with IsPressureDiff

    @Transl(I(_.split_merge.SplitSingleFlowIntoTwoFlowsWith90DegTurn))
    case class SplitSingleFlowIntoTwoFlowsWith90DegTurn(
        @Transl(I(_.terms.name))
        name                             : String,
        @Transl(I(_.terms.absolute_direction))
        override val absDir              : Option[AbsoluteDirection] = None,
        @Transl(I(_.split_merge.newInnerShape))
        newInnerShape                    : PipeShape,
        @Transl(I(_.split_merge.symmetryPlaneAzimuth))
        override val symmetryPlaneAzimuth: Option[AzimuthDirection]  = None
    ) extends AddFlowOnlyPipeElement_15544_V4
        with SetsNumberOfFlows
        with SetsInnerShape
        with IsDirectionChange
        with IsSingularFlowResistance
        with IsSplitMergeTurn
        with HasSplitMergeData
        with HasDirectionChangeData {
        def n_flows: NbOfFlows = 2
        def angle  : Angle     = 90.degrees
    }

    @Transl(I(_.split_merge.MergeTwoFlowsIntoSingleWith90DegTurn))
    case class MergeTwoFlowsIntoSingleWith90DegTurn(
        @Transl(I(_.terms.name))
        name                             : String,
        @Transl(I(_.terms.absolute_direction))
        override val absDir              : Option[AbsoluteDirection] = None,
        @Transl(I(_.split_merge.newInnerShape))
        newInnerShape                    : PipeShape,
        @Transl(I(_.split_merge.symmetryPlaneAzimuth))
        override val symmetryPlaneAzimuth: Option[AzimuthDirection]  = None
    ) extends AddFlowOnlyPipeElement_15544_V4
        with SetsNumberOfFlows
        with SetsInnerShape
        with IsDirectionChange
        with IsSingularFlowResistance
        with IsSplitMergeTurn
        with HasSplitMergeData
        with HasDirectionChangeData {
        def n_flows: NbOfFlows = 1
        def angle  : Angle     = 90.degrees
    }
