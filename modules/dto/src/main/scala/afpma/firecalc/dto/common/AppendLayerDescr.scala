/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import cats.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.i18n.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.policy.standard.given
import magnolia1.Transl

@Transl(I(_.append_layer_descr._self))
enum AppendLayerDescr:

    @Transl(I(_.append_layer_descr.FromLambda))
    case FromLambda(
        @Transl(I(_.terms.outer_shape))
        outer_shape: PipeShape,
        @Transl(I(_.terms.thermal_conductivity_λ))
        lambda: WattsPerMeterKelvin
    )

    @Transl(I(_.append_layer_descr.FromLambdaUsingThickness))
    case FromLambdaUsingThickness(
        @Transl(I(_.terms.thickness))
        thickness: Length,
        @Transl(I(_.terms.thermal_conductivity_λ))
        lambda: WattsPerMeterKelvin
    )

    @Transl(I(_.append_layer_descr.FromThermalResistanceUsingThickness))
    case FromThermalResistanceUsingThickness(
        @Transl(I(_.terms.thickness))
        thickness: Length,
        @Transl(I(_.terms.thermal_resistance_Rth))
        thermal_resistance: SquareMeterKelvinPerWatt
    )

    @Transl(I(_.append_layer_descr.FromThermalResistance))
    case FromThermalResistance(
        @Transl(I(_.terms.outer_shape))
        outer_shape: PipeShape,
        @Transl(I(_.terms.thermal_resistance_Rth))
        thermal_resistance: SquareMeterKelvinPerWatt
    )

    @Transl(I(_.append_layer_descr.AirSpaceUsingOuterShape))
    case AirSpaceUsingOuterShape(
        @Transl(I(_.terms.outer_shape))
        outer_shape: PipeShape,
        @Transl(I(_.en13384._air_space_detailed.ventil_direction))
        ventil_direction: VentilDirection, 
        @Transl(I(_.en13384._air_space_detailed.ventil_openings))
        ventil_openings: VentilOpenings
    )

    @Transl(I(_.append_layer_descr.AirSpaceUsingThickness))
    case AirSpaceUsingThickness(
        @Transl(I(_.terms.thickness))
        thickness: Length,
        @Transl(I(_.en13384._air_space_detailed.ventil_direction))
        ventil_direction: VentilDirection, 
        @Transl(I(_.en13384._air_space_detailed.ventil_openings))
        ventil_openings: VentilOpenings
    )

object AppendLayerDescr:

    extension (layers: List[AppendLayerDescr])

        def compute_outer_shape(fromStart: PipeShape): PipeShape =
            layers.foldLeft(fromStart): (ig, l) =>
                l match
                    case FromLambda(outer_shape, _) => outer_shape
                    case FromLambdaUsingThickness(thickness, _) => ig.expandGeomWithThickness(thickness)
                    case FromThermalResistanceUsingThickness(thickness, _) => ig.expandGeomWithThickness(thickness)
                    case FromThermalResistance(og, _) => og
                    case AirSpaceUsingOuterShape(og, _, _) => og
                    case AirSpaceUsingThickness(thickness, _, _) => ig.expandGeomWithThickness(thickness)

        def compute_air_space_detailed_list(
            startGeom: PipeShape, 
        ): Either[String, List[AirSpaceDetailed]] =
            enum Status:
                case Ok
                case Error_MissingLayerAfterAirSpace
            import Status.*

            val z: (PipeShape, Vector[AirSpaceDetailed], Status) =
                (startGeom, Vector.empty, Status.Ok)

            val finalAcc: (PipeShape,  Vector[AirSpaceDetailed], Status) = 
                layers.foldLeft(z): (acc, l) =>
                    acc match
                        case (ig, airSpaces, Ok | Error_MissingLayerAfterAirSpace) =>
                            l match                            
                                case AppendLayerDescr.FromLambda(og, lambda) =>
                                    (og, airSpaces, Status.Ok)
                                case AppendLayerDescr.FromLambdaUsingThickness(e, lambda) =>
                                    val og = ig.expandGeomWithThickness(e)
                                    (og, airSpaces, Status.Ok)
                                case AppendLayerDescr.FromThermalResistanceUsingThickness(e, tr) =>
                                    val og = ig.expandGeomWithThickness(e)
                                    (og, airSpaces, Status.Ok)
                                case AppendLayerDescr.FromThermalResistance(og, tr) =>
                                    (og, airSpaces, Status.Ok)
                                case AppendLayerDescr.AirSpaceUsingOuterShape(og, vdir, vst) =>
                                    val e: QtyD[Meter] = (og.dh - ig.dh) / 2.0
                                    val asp = AirSpaceDetailed.WithAirSpace(e, vdir, vst)
                                    (og, airSpaces.appended(asp), Status.Error_MissingLayerAfterAirSpace)
                                case AppendLayerDescr.AirSpaceUsingThickness(e, vdir, vst) =>
                                    val og = ig.expandGeomWithThickness(e)
                                    val asp = AirSpaceDetailed.WithAirSpace(e, vdir, vst)
                                    (og, airSpaces.appended(asp), Status.Error_MissingLayerAfterAirSpace)
            finalAcc._3 match
                case Error_MissingLayerAfterAirSpace => Left("missing layer after air space")
                case Ok => finalAcc._2.toList.asRight

        def find_biggest_air_space_detailed(start: PipeShape): AirSpaceDetailed = 
            layers.compute_air_space_detailed_list(start)
                .toOption
                .flatMap(_
                    .flatMap:
                        case AirSpaceDetailed.WithoutAirSpace => None
                        case withAirSpace: AirSpaceDetailed.WithAirSpace => withAirSpace.some
                    .sortBy(_.width)
                    .lastOption
                )
                .getOrElse(AirSpaceDetailed.withoutAirSpace)
