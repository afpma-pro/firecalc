/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.engine.models.gtypedefs.*

import afpma.firecalc.ui.formgen.Defaultable

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.payments.shared.api.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import afpma.firecalc.ui.models.*

object defaultable:

    // basic types

    object boolean:
        given asTrue: Defaultable[Boolean] = Defaultable(true)
        given asFalse: Defaultable[Boolean] = Defaultable(false)

    object string   extends EmptyInstance[String]("")
    object int      extends EmptyInstance[Int](0)
    object double   extends EmptyInstance[Double](0.0)
    object float    extends EmptyInstance[Float](0.0)
    object long     extends EmptyInstance[Long](0L)

    trait EmptyInstance[A](zero: A):
        given empty: Defaultable[A] = Defaultable(zero)

    // BillingInfo

    given default_BillingInfo: Defaultable[BillingInfo]:
        def default =
            afpma.firecalc.ui.models.schema.v1.BillingInfo_V1(
                email = "",
                customer_type = BillableCustomerType.Business,
                address_line1 = "",
                city = "FELLETIN",
                postal_code = "23500",
                country_code = BillableCountry.France
            )

    // ClientProjectData

    given default_ClientProjectData: Defaultable[ClientProjectData]:
        def default = ClientProjectData.empty

    // firebox

    val firebox_traditional_empty: Defaultable[Firebox] = Defaultable:
        Firebox.Traditional(
            heat_output_reduced                         = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            firebox_depth                         = 0.cm,
            firebox_width                         = 0.cm,
            firebox_height                        = 0.cm,
            pressure_loss_coefficient_from_door                  = 0.3.unitless,
            total_air_intake_surface_area_on_door  = 0.cm2,
            glass_width                           = 0.cm,
            glass_height                          = 0.cm,
        )

    val firebox_traditional_minimal: Defaultable[Firebox.Traditional] = Defaultable:
        Firebox.Traditional(
            heat_output_reduced                         = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            firebox_depth                         = 33.2.cm,
            firebox_width                         = 33.2.cm,
            firebox_height                        = 52.cm,
            pressure_loss_coefficient_from_door                  = 0.3.unitless,
            total_air_intake_surface_area_on_door  = 100.cm2,
            glass_width                           = 30.cm,
            glass_height                          = 30.cm,
        )

    given firebox_ecolabeled_minimal: Defaultable[Firebox.EcoLabeled] = Defaultable:
        Firebox.EcoLabeled(
            heat_output_reduced = HeatOutputReduced.HalfOfNominal.makeWithoutValue, 
            version = Left("Version 1"), 
            air_intake_shape = None, 
            firebox_depth = 33.cm, 
            firebox_width = 33.cm, 
            firebox_height = 50.cm, 
            door_opening_width = 30.cm, 
            glass_width = 25.cm, 
            glass_height = 25.cm, 
            ash_pit_height = 5.cm, 
            air_manifold_height = 8.cm, 
            firebox_floor_thickness = 4.cm, 
            firebox_inner_wall_thickness = 6.cm, 
            firebox_outer_wall_thickness = 6.cm, 
            air_column_thickness = 3.cm, 
            width_between_two_air_columns_sides = 3.cm, 
            width_between_two_air_columns_rear = 3.cm,
            reinforcement_bars_offset_in_corners = 1.cm,
            injector_height = 0.3.mm
        )

    given given_Firebox: Defaultable[Firebox] = firebox_traditional_minimal

    val divideFlowIn = Defaultable(NbOfFlows(1))

    val pipeLocation = Defaultable(PipeLocation.HeatedArea)

    // PipeShape
    
    given pipeShapeInner: Defaultable[PipeShape]:
        def default = Circle(180.mm)
    given pipeShapeOuter: Defaultable[PipeShape]:
        def default = Circle(200.mm)

    object pipe_shape:
        given circle: Defaultable[PipeShape.Circle]:
            def default = Circle(200.mm)
        given square: Defaultable[PipeShape.Square]:
            def default = PipeShape.Square(200.mm)
        given rectangle: Defaultable[PipeShape.Rectangle]:
            def default = PipeShape.Rectangle(200.mm, 300.mm)


    
    val thermalConductivity = Defaultable[ThermalConductivity.Type](0.44.W_per_mK)
    given given_ThermalConductivity: Defaultable[ThermalConductivity.Type] = thermalConductivity

    val thermalResistance = Defaultable[SquareMeterKelvinPerWatt](SquareMeterKelvinPerWatt(0))
    given given_ThermalResistance: Defaultable[SquareMeterKelvinPerWatt] = thermalResistance

    val radiusOfCurvature = Defaultable(20.cm)
    given given_RadiusOfCurvature: Defaultable[QtyD[Meter]] = radiusOfCurvature

    val roughness = Defaultable(3.mm)
    given given_Roughness: Defaultable[QtyD[Meter]] = roughness

    val tcelsius = Defaultable(0.degreesCelsius)
    given given_TCelsius: Defaultable[TCelsius] = tcelsius

    given given_TuTemperature: Defaultable[AmbiantAirTemperatureSet] = 
        Defaultable(AmbiantAirTemperatureSet.defaultsToTuo)
    
    val thickness = Defaultable(20.mm)
    given given_Thickness: Defaultable[QtyD[Meter]] = thickness

    given zeta: Defaultable[ζ]:
        def default = 1.0.unitless

    val z_geodetical_height = Defaultable(100.meters)
    given given_z_geodetical_height: Defaultable[QtyD[Meter]] = z_geodetical_height

    object qty_d:
        
        inline given zeroWithUnit: [U] => Defaultable[QtyD[U]] = 
            Defaultable(0.withUnit[U])
        
        inline given optionZeroWithUnit: [U] => Defaultable[Option[QtyD[U]]] = 
            zeroWithUnit[U].map(Some.apply)

        object angle:
            given zero: Defaultable[Angle] = zeroWithUnit[Degree]
        object area:
            given zero: Defaultable[Area] = zeroWithUnit[Meter ^ 2]
        object area_in_cm2:
            given zero: Defaultable[AreaInCm2] = zeroWithUnit[Centimeter ^ 2]
        object kilowatt:
            given zero: Defaultable[QtyD[Kilo * Watt]] = zeroWithUnit[Kilo * Watt]
        
        object meter:
            given zero: Defaultable[QtyD[Meter]] = zeroWithUnit[Meter]
        object pascal:
            given zero: Defaultable[QtyD[Pascal]] = zeroWithUnit[Pascal]

        object watt:
            given zero: Defaultable[QtyD[Watt]] = zeroWithUnit[Watt]

        object option:
            object area_in_cm2:
                given zero: Defaultable[Option[AreaInCm2]] = optionZeroWithUnit[Centimeter ^ 2]
            object kilowatt:
                given zero: Defaultable[Option[QtyD[Kilo * Watt]]] = optionZeroWithUnit[Kilo * Watt]
            object watt:
                given zero: Defaultable[Option[QtyD[Watt]]] = optionZeroWithUnit[Watt]

    object chimney_termination:
        given Defaultable[HorizontalDistanceBetweenChimneyAndRidgeline]:
            def default = HorizontalDistanceBetweenChimneyAndRidgeline.MoreThan2m30
        given Defaultable[Slope]:
            def default = Slope.LessThan25Deg
        given Defaultable[OutsideAirIntakeAndChimneyLocations]:
            def default = OutsideAirIntakeAndChimneyLocations.OnSameSideOfRidgeline
        given Defaultable[HorizontalDistanceBetweenChimneyAndRidgelineBis]:
            def default = HorizontalDistanceBetweenChimneyAndRidgelineBis.LessThan1m

        given Defaultable[HorizontalAngleBetweenChimneyAndAdjacentBuildings]:
            def default = HorizontalAngleBetweenChimneyAndAdjacentBuildings.LessThan30Deg
        given Defaultable[VerticalAngleBetweenChimneyAndAdjacentBuildings]:
            def default = VerticalAngleBetweenChimneyAndAdjacentBuildings.LessThan10DegOverHorizon



end defaultable
