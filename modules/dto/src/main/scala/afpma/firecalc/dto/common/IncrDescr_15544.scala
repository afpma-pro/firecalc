/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import afpma.firecalc.i18n.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import magnolia1.Transl

sealed trait IncrDescr_15544

sealed trait SetProp_15544 extends IncrDescr_15544

object SetProp_15544:

    @Transl(I(_.set_prop.SetInnerShape))
    case class SetInnerShape(
        @Transl(I(_.terms.pipe_shape._self))
        shape: PipeShape
    ) extends SetProp_15544

    @Transl(I(_.set_prop.SetRoughness))
    case class SetRoughness(
        @Transl(I(_.terms.roughness))
        roughness: Roughness
    ) extends SetProp_15544

    @Transl(I(_.set_prop.SetMaterial))
    case class SetMaterial(
        @Transl(I(_.set_prop.SetMaterial))
        material: Material_15544
    ) extends SetProp_15544

    @Transl(I(_.set_prop.SetNumberOfFlows))
    case class SetNumberOfFlows(            
        @Transl(I(_.set_prop.SetNumberOfFlows_fieldName))
        n_flows: NbOfFlows
    ) extends SetProp_15544

sealed trait AddElement_15544 extends IncrDescr_15544:
    def name: String

object AddElement_15544:

    @Transl(I(_.add_element.AddSectionSlopped))
    case class AddSectionSlopped(
        @Transl(I(_.terms.name))
        name: String,
        @Transl(I(_.terms.length))
        length: Length,
        @Transl(I(_.terms.elevation_gain))
        elevation_gain: Length
    ) extends AddElement_15544

    @Transl(I(_.add_element.AddSectionHorizontal))
    case class AddSectionHorizontal(
        @Transl(I(_.terms.name))
        name: String, 
        @Transl(I(_.terms.horizontal_length))
        horizontal_length: Length
    ) extends AddElement_15544

    @Transl(I(_.add_element.AddSectionVertical))
    case class AddSectionVertical(
        @Transl(I(_.terms.name))
        name: String, 
        @Transl(I(_.terms.elevation_gain))
        elevation_gain: Length
    ) extends AddElement_15544

    @Transl(I(_.add_element.add_direction_change_element))
    sealed abstract class AddDirectionChange(
        @Transl(I(_.terms.name))
        override val name: String,
        @Transl(I(_.terms.angle))
        val angle: Angle,
        @Transl(I(_.en15544.angle_to_original_direction))
        val angle_to_original_direction: Option[Angle] = None // TO FIX or IMPLEMENT ??
    ) extends AddElement_15544


    @Transl(I(_.add_element.AddSharpeAngle_0_to_180))
    case class AddSharpeAngle_0_to_180(
        @Transl(I(_.terms.name))
        override val name: String, 
        @Transl(I(_.terms.angle))
        override val angle: Angle, 
        @Transl(I(_.en15544.angle_to_original_direction))
        override val angle_to_original_direction: Option[Angle]
    ) extends AddDirectionChange(name, angle, angle_to_original_direction)

    @Transl(I(_.add_element.AddCircularArc_60))
    case class AddCircularArc_60(
        @Transl(I(_.terms.name))
        override val name: String
    ) extends AddDirectionChange(name, 60.degrees)


    @Transl(I(_.add_element.AddSectionShapeChange))
    case class AddSectionShapeChange(
        @Transl(I(_.terms.name))
        val name: String, 
        @Transl(I(_.terms.pipe_shape._self))
        val to_shape: PipeShape
    ) extends AddElement_15544

    @Transl(I(_.add_element.AddFlowResistance))
    case class AddFlowResistance(
        @Transl(I(_.add_element.name))
        name: String, 
        @Transl(I(_.add_element.zeta))
        zeta: QtyD[1], 
        @Transl(I(_.add_element.cross_section))
        cross_section: OptionOfEither[AreaInCm2, PipeShape] // Option[Either[L, R]] has issues when serializing via circe, so custom type with custom encoder/decoder as a workaround
    ) extends AddElement_15544

    @Transl(I(_.add_element.AddPressureDiff))
    case class AddPressureDiff(
        @Transl(I(_.add_element.name))
        name: String, 
        @Transl(I(_.terms.pressure_difference))
        pressure_difference: Pressure
    ) extends AddElement_15544
