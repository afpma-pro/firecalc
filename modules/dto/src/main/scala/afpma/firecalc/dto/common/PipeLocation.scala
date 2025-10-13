/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import cats.*
import cats.derived.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.units.coulombutils.{*, given}
import io.taig.babel.Locale
import magnolia1.Transl

@Transl(I(_.pipe_location.short))
sealed trait PipeLocation(
    val areaName: String,
    val areaHeatingStatus: PipeLocation.AreaHeatingStatus
) 
    extends InsideOrOutside

object PipeLocation:

    import InsideOrOutside.*

    @Transl(I(_.area_heating_status._self))
    enum AreaHeatingStatus:    
        case Heated
        case NotHeated
    
    object AreaHeatingStatus:
        @Transl(I(_.area_heating_status.heated))
        type Heated = AreaHeatingStatus.Heated.type

        @Transl(I(_.area_heating_status.not_heated))
        type NotHeated = AreaHeatingStatus.NotHeated.type

    given show_AreaHeatingStatus: Locale => Show[AreaHeatingStatus] = Show.show:
        case AreaHeatingStatus.Heated    => I18N.area_heating_status.heated
        case AreaHeatingStatus.NotHeated => I18N.area_heating_status.not_heated

    given show_PipeLocation: Locale => Show[PipeLocation] = Show.show:
        case BoilerRoom         => I18N.pipe_location.boiler_room
        case HeatedArea         => I18N.pipe_location.heated_area
        case UnheatedInside     => I18N.pipe_location.unheated_inside
        case OutsideOrExterior  => I18N.pipe_location.outside_or_exterior
        case _: CustomArea   => I18N.pipe_location.custom_area
        
    case object BoilerRoom          extends PipeLocation("BoilerRoom"       , AreaHeatingStatus.NotHeated)    with Inside
    @Transl(I(_.pipe_location.boiler_room))
    type BoilerRoom = BoilerRoom.type
    
    case object HeatedArea          extends PipeLocation("HeatedArea"       , AreaHeatingStatus.Heated)       with Inside
    @Transl(I(_.pipe_location.heated_area))
    type HeatedArea = HeatedArea.type
    
    case object UnheatedInside      extends PipeLocation("UnheatedInside"   , AreaHeatingStatus.NotHeated)    with Inside
    @Transl(I(_.pipe_location.unheated_inside))
    type UnheatedInside = UnheatedInside.type
    
    case object OutsideOrExterior   extends PipeLocation("OutsideOrExterior", AreaHeatingStatus.NotHeated)    with Outside
    @Transl(I(_.pipe_location.outside_or_exterior))
    type OutsideOrExterior = OutsideOrExterior.type
    
    /**
      * Custom Location defined for Laboratory use case
      *
      * @param area_name
      * @param area_heating_status
      * @param inside
      * @param withoutairspace_drycond ambiant air temperature in dry conditions and no air space
      * @param withairspace_drycond_hextAbove5m ambiant air temperature if unheated area inside or outside does exceed 5m, in dry conditions with air space
      * @param withairspace_drycond_hextBelow5m ambiant air temperature if unheated area inside or outside does not exceed 5m, in dry conditions with air space
      */
    @Transl(I(_.pipe_location.custom_area))
    case class CustomArea(
        area_name: String,
        area_heating_status: AreaHeatingStatus, 
        inside: Boolean,
        ambiant_air_temperature_set: AmbiantAirTemperatureSet
    ) extends PipeLocation(area_name, area_heating_status) with InsideOrOutside derives Show:
        def isInside: Boolean = inside

    object CustomArea:
        def sameTuForAllConditions(
            area_name: String, 
            area_heating_status: AreaHeatingStatus, 
            inside: Boolean, 
            tu_forAllConditions: TCelsius
        ) = CustomArea(
            area_name, 
            area_heating_status, 
            inside, 
            AmbiantAirTemperatureSet.fromSingleTu(tu_forAllConditions)
        )