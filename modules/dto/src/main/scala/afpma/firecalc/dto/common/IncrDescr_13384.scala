/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import afpma.firecalc.i18n.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import magnolia1.Transl

sealed trait IncrDescr_13384

sealed trait SetProp_13384 extends IncrDescr_13384

object SetProp_13384:

    @Transl(I(_.set_prop.SetInnerShape))
    case class SetInnerShape(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetProp_13384

    @Transl(I(_.set_prop.SetOuterShape))
    case class SetOuterShape(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetProp_13384

    @Transl(I(_.set_prop.SetThickness))
    case class SetThickness(
        @Transl(I(_.set_prop.SetThickness))
        thickness: Length
    ) extends SetProp_13384

    @Transl(I(_.set_prop.SetRoughness))
    case class SetRoughness(
        @Transl(I(_.set_prop.SetRoughness))
        roughness: Roughness
    ) extends SetProp_13384

    @Transl(I(_.set_prop.SetMaterial))
    case class SetMaterial(
        @Transl(I(_.set_prop.SetMaterial))
        material: Material_13384
    ) extends SetProp_13384

    @Transl(I(_.set_prop.SetLayer))
    case class SetLayer(
        @Transl(I(_.set_prop.SetThickness))
        thickness: Length,
        @Transl(I(_.terms.thermal_conductivity_λ))
        thermal_conductivity: WattsPerMeterKelvin
    ) extends SetProp_13384

    @Transl(I(_.set_prop.SetLayers))
    case class SetLayers(
        layers: List[AppendLayerDescr]
    ) extends SetProp_13384

    @Transl(I(_.set_prop.SetAirSpaceAfterLayers))
    case class SetAirSpaceAfterLayers(
        @Transl(I(_.en13384.air_space_detailed))
        air_space_detailed: AirSpaceDetailed
    ) extends SetProp_13384


    @Transl(I(_.set_prop.SetPipeLocation))
    case class SetPipeLocation(
        @Transl(I(_.pipe_location.short))
        pipe_location: PipeLocation
    ) extends SetProp_13384

    @Transl(I(_.set_prop.SetDuctType))
    case class SetDuctType(
        duct: DuctType
    ) extends SetProp_13384

    @Transl(I(_.set_prop.SetNumberOfFlows))
    case class SetNumberOfFlows(
        @Transl(I(_.set_prop.SetNumberOfFlows_fieldName))
        n_flows: NbOfFlows
    ) extends SetProp_13384


sealed trait AddElement_13384 extends IncrDescr_13384:
    def name: String

object AddElement_13384:

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSlopped(
        @Transl(I(_.terms.name))
        name: String,
        @Transl(I(_.terms.length))
        length: Length,
        @Transl(I(_.terms.elevation_gain))
        elevation_gain: Length
    ) extends AddElement_13384

    @Transl(I(_.add_element.AddSectionHorizontal))
    case class AddSectionHorizontal(
        @Transl(I(_.terms.name))
        name: String, 
        @Transl(I(_.terms.horizontal_length))
        horizontal_length: Length
    )
        extends AddElement_13384

    @Transl(I(_.add_element.AddSectionVertical))
    case class AddSectionVertical(
        @Transl(I(_.terms.name))
        name: String, 
        @Transl(I(_.terms.elevation_gain))
        elevation_gain: Length)
        extends AddElement_13384

    sealed abstract class AddDirectionChange(
        @Transl(I(_.terms.name))
        val name: String,
        @Transl(I(_.terms.angle))
        val angle: Angle,
        @Transl(I(_.en15544.angle_to_original_direction))
        val angle_to_original_direction: Option[Angle] = None, // TO FIX or IMPLEMENT ??
    ) extends AddElement_13384

    @Transl(I(_.add_element.AddAngleAdjustable))
    case class AddAngleAdjustable(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.angle))
        override val angle: Angle, 
        @Transl(I(_.terms.zeta_ζ))
        val zeta: QtyD[1]
    ) extends AddDirectionChange(name, angle, None)

    @Transl(I(_.add_element.AddSharpeAngle_0_to_90))
    case class AddSharpeAngle_0_to_90(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.angle))
        override val angle: Angle) extends AddDirectionChange(name, angle)

    // __INTERPRETATION__
    @Transl(I(_.add_element.AddSharpeAngle_0_to_90_Unsafe))
    case class AddSharpeAngle_0_to_90_Unsafe(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.angle))
        override val angle: Angle) extends AddDirectionChange(name, angle)

    @Transl(I(_.add_element.AddSmoothCurve_90))
    case class AddSmoothCurve_90(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.curvature_radius))
        curvature_radius: Length
    ) extends AddDirectionChange(name, 90.degrees)

    // __INTERPRETATION__
    @Transl(I(_.add_element.AddSmoothCurve_90_Unsafe))
    case class AddSmoothCurve_90_Unsafe(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.curvature_radius))
        curvature_radius: Length
    ) extends AddDirectionChange(name, 90.degrees)

    @Transl(I(_.add_element.AddSmoothCurve_60))
    case class AddSmoothCurve_60(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.curvature_radius))
        curvature_radius: Length
    ) extends AddDirectionChange(name, 60.degrees)

    // __INTERPRETATION__
    @Transl(I(_.add_element.AddSmoothCurve_60_Unsafe))
    case class AddSmoothCurve_60_Unsafe(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.curvature_radius))
        curvature_radius: Length
    ) extends AddDirectionChange(name, 60.degrees)

    // coudes à segments
    sealed abstract class CoudeASegment90(
        @Transl(I(_.terms.name))
        override val name: String,
        @Transl(I(_.terms.number_of_segments))
        val number_of_segments: 2 | 3 | 4,
        @Transl(I(_.terms.curvature_radius))
        val curvature_radius: Length,
    ) extends AddDirectionChange(name, 90.degrees)

    @Transl(I(_.add_element.AddElbows_2x45))
    case class AddElbows_2x45(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.curvature_radius))
        override val curvature_radius: Length) extends CoudeASegment90(name, 2, curvature_radius)

    @Transl(I(_.add_element.AddElbows_3x30))
    case class AddElbows_3x30(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.curvature_radius))
        override val curvature_radius: Length) extends CoudeASegment90(name, 3, curvature_radius)

    @Transl(I(_.add_element.AddElbows_4x22p5))
    case class AddElbows_4x22p5(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.curvature_radius))
        override val curvature_radius: Length
    ) extends CoudeASegment90(name, 4, curvature_radius)

    // TODO: rename to AddSectionShapeChange
    sealed abstract class AddSectionChange(
        @Transl(I(_.terms.name))
        val name: String, 
        @Transl(I(_.terms.pipe_shape._self))
        val to_shape: PipeShape
    )
        extends AddElement_13384

    @Transl(I(_.add_element.AddSectionDecrease))
    case class AddSectionDecrease(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.diameter))
        to_diameter: Length
    ) extends AddSectionChange(name, PipeShape.Circle(to_diameter))

    @Transl(I(_.add_element.AddSectionIncrease))
    case class AddSectionIncrease(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.diameter))
        to_diameter: Length
    ) extends AddSectionChange(name, PipeShape.Circle(to_diameter))
    // case class AddSectionDecreaseProgressive(override val name: String, toDiameter: Length, ɣ: Angle) extends AddSectionChange(name, PipeShape.Circle(toDiameter))

    @Transl(I(_.add_element.AddFlowResistance))
    case class AddFlowResistance(
        @Transl(I(_.add_element.name))
        name: String, 
        @Transl(I(_.add_element.zeta))
        zeta: QtyD[1], 
        @Transl(I(_.add_element.cross_section))
        cross_section: OptionOfEither[AreaInCm2, PipeShape] // Option[Either[L, R]] has issues when serializing via circe, so custom type with custom encoder/decoder as a workaround
    ) extends AddElement_13384

    case class AddPressureDiff(
        @Transl(I(_.terms.name))
        name: String, 
        @Transl(I(_.terms.pressure_difference))
        pressure_difference: Pressure
    ) extends AddElement_13384