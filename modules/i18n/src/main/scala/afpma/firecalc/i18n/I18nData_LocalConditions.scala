/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

object I18nData_LocalConditions:

    case class LocalConditions(
        altitude           : String,
        coastal_region     : String,
        chimney_termination: LocalConditions.ChimneyTermination
    )

    object LocalConditions:
        case class ChimneyTermination(
            explain                 : String,
            chimney_location_on_roof: ChimneyTermination.ChimneyLocationOnRoof,
            adjacent_buildings      : ChimneyTermination.AdjacentBuildings
        )
        object ChimneyTermination:
            import ChimneyLocationOnRoof.*
            case class ChimneyLocationOnRoof(
                explain                                              : String,
                chimney_height_above_ridgeline                       : ChimneyHeightAboveRidgeline,
                horizontal_distance_between_chimney_and_ridgeline    : HorizontalDistanceBetweenChimneyAndRidgeline,
                slope                                                : Slope,
                outside_air_intake_and_chimney_locations             : OutsideAirIntakeAndChimneyLocations,
                horizontal_distance_between_chimney_and_ridgeline_bis: HorizontalDistanceBetweenChimneyAndRidgelineBis
            )
            object ChimneyLocationOnRoof:
                case class ChimneyHeightAboveRidgeline(
                    explain       : String,
                    more_than_40cm: String,
                    less_than_40cm: String
                )
                case class HorizontalDistanceBetweenChimneyAndRidgeline(
                    explain       : String,
                    less_than_2m30: String,
                    more_than_2m30: String
                )
                case class Slope(
                    explain                : String,
                    less_than_25deg        : String,
                    between_25deg_and_40deg: String,
                    more_than_40deg        : String
                )
                case class OutsideAirIntakeAndChimneyLocations(
                    explain                        : String,
                    on_different_sides_of_the_ridge: String,
                    on_same_side_of_the_ridge      : String
                )
                case class HorizontalDistanceBetweenChimneyAndRidgelineBis(
                    explain     : String,
                    less_than_1m: String,
                    more_than_1m: String
                )

            import AdjacentBuildings.*
            case class AdjacentBuildings(
                explain                                                   : String,
                horizontal_distance_between_chimney_and_adjacent_buildings: HorizontalDistanceBetweenChimneyAndAdjacentBuildings,
                horizontal_angle_between_chimney_and_adjacent_buildings   : HorizontalAngleBetweenChimneyAndAdjacentBuildings,
                vertical_angle_between_chimney_and_adjacent_buildings     : VerticalAngleBetweenChimneyAndAdjacentBuildings
            )
            object AdjacentBuildings:
                case class HorizontalDistanceBetweenChimneyAndAdjacentBuildings(
                    explain      : String,
                    less_than_15m: String,
                    more_than_15m: String
                )
                case class HorizontalAngleBetweenChimneyAndAdjacentBuildings(
                    explain        : String,
                    more_than_30deg: String,
                    less_than_30deg: String
                )
                case class VerticalAngleBetweenChimneyAndAdjacentBuildings(
                    explain                      : String,
                    more_than_10deg_above_horizon: String,
                    less_than_10deg_above_horizon: String
                )
