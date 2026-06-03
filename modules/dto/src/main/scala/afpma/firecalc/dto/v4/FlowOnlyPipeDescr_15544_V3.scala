/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.*

import magnolia1.Transl

sealed trait FlowOnlyPipeDescr_15544_V3

sealed trait SetFlowOnlyPipeProp_15544_V3 extends FlowOnlyPipeDescr_15544_V3

object SetFlowOnlyPipeProp_15544_V3:

    @Transl(I(_.set_prop.SetInnerShape))
    case class SetInnerShape(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetFlowOnlyPipeProp_15544_V3

    @Transl(I(_.set_prop.SetRoughness))
    case class SetRoughness(
        @Transl(I(_.terms.roughness))
        roughness: Roughness
    ) extends SetFlowOnlyPipeProp_15544_V3

    @Transl(I(_.set_prop.SetMaterial))
    case class SetMaterial(
        @Transl(I(_.set_prop.SetMaterial))
        material: Material_15544_V2 // Material_15544 updated to Material_15544_V2
    ) extends SetFlowOnlyPipeProp_15544_V3

    @Transl(I(_.set_prop.SetNumberOfFlows))
    case class SetNumberOfFlows(
        @Transl(I(_.set_prop.SetNumberOfFlows_fieldName))
        n_flows: NbOfFlows
    ) extends SetFlowOnlyPipeProp_15544_V3

    @Transl(I(_.set_prop.SetInitialDirection))
    @deprecated("Use PostFireboxInitialDirection on PostFireboxPipes instead. Will be removed in V8.", "V7")
    case class SetInitialDirection(
        @Transl(I(_.terms.azimuth))
        azimuth    : AzimuthDirection,
        @Transl(I(_.terms.inclination))
        inclination: InclinationDirection
    ) extends SetFlowOnlyPipeProp_15544_V3

    @Transl(I(_.set_prop.SetInitialPosition))
    @deprecated("Use PostFireboxInitialPosition on PostFireboxPipes instead. Will be removed in V8.", "V7")
    case class SetInitialPosition(
        @Transl(I(_.terms.x)) x: Length,
        @Transl(I(_.terms.y)) y: Length,
        @Transl(I(_.terms.z)) z: Length
    ) extends SetFlowOnlyPipeProp_15544_V3

    @Transl(I(_.set_prop.SetFinalPosition))
    @deprecated("Not supported in V7 post-firebox pipes. Use PositionTracker instead. Will be removed in V8.", "V7")
    case class SetFinalPosition(
        @Transl(I(_.terms.x)) x: Length,
        @Transl(I(_.terms.y)) y: Length,
        @Transl(I(_.terms.z)) z: Length
    ) extends SetFlowOnlyPipeProp_15544_V3

sealed trait AddFlowOnlyPipeElement_15544_V3 extends FlowOnlyPipeDescr_15544_V3:
    def name: String

object AddFlowOnlyPipeElement_15544_V3:

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSlopped(
        @Transl(I(_.terms.name))
        name  : String,
        @Transl(I(_.terms.length))
        length: Length
    ) extends AddFlowOnlyPipeElement_15544_V3

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSloppedForceManualElevationGain(
        @Transl(I(_.terms.name))
        name          : String,
        @Transl(I(_.terms.length))
        length        : Length,
        @Transl(I(_.terms.elevation_gain))
        elevation_gain: Length
    ) extends AddFlowOnlyPipeElement_15544_V3

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
    ) extends AddFlowOnlyPipeElement_15544_V3

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
    ) extends AddFlowOnlyPipeElement_15544_V3

    @Transl(I(_.add_element.add_direction_change_element))
    sealed abstract class AddDirectionChange(
        @Transl(I(_.terms.name))
        override val name: String,
        @Transl(I(_.terms.angle))
        val angle        : Angle,
        @Transl(I(_.terms.absolute_direction))
        val absDir       : Option[AbsoluteDirection] = None
    ) extends AddFlowOnlyPipeElement_15544_V3

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
    ) extends AddFlowOnlyPipeElement_15544_V3

    @Transl(I(_.add_element.AddFlowResistance))
    case class AddFlowResistance(
        @Transl(I(_.add_element.name))
        name         : String,
        @Transl(I(_.add_element.zeta))
        zeta         : QtyD[1],
        @Transl(I(_.add_element.cross_section))
        cross_section: OptionOfEither[AreaInCm2, PipeShape] // Option[Either[L, R]] has issues when serializing via circe, so custom type with custom encoder/decoder as a workaround
    ) extends AddFlowOnlyPipeElement_15544_V3

    @Transl(I(_.add_element.AddPressureDiff))
    case class AddPressureDiff(
        @Transl(I(_.add_element.name))
        name               : String,
        @Transl(I(_.terms.pressure_difference))
        pressure_difference: Pressure
    ) extends AddFlowOnlyPipeElement_15544_V3
