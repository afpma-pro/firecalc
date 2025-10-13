/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import scala.annotation.targetName

import cats.syntax.all.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.common.LocalConditions.*
import afpma.firecalc.units.coulombutils.*
import io.taig.babel.Locale
import magnolia1.Transl

@Transl(I(_.headers.local_conditions))
case class LocalConditions(
    @Transl(I(_.local_conditions.altitude))
    altitude: QtyD[Meter],
    @Transl(I(_.local_conditions.coastal_region))
    coastal_region: Boolean,
    @Transl(I(_.local_conditions.chimney_termination.explain))
    chimney_termination: LocalConditions.ChimneyTermination,
)

object LocalConditions:

    val default = LocalConditions(
        altitude                    = 100.meters,
        coastal_region              = false,
        chimney_termination    = ChimneyTermination.Classic,
    )

    import ChimneyTermination.*

    case class ChimneyTermination(
        @Transl(I(_.local_conditions.chimney_termination.chimney_location_on_roof.explain))
        chimney_location_on_roof: ChimneyTermination.ChimneyLocationOnRoof,
        @Transl(I(_.local_conditions.chimney_termination.adjacent_buildings.explain))
        adjacent_buildings: ChimneyTermination.AdjacentBuildings,
    )

    object ChimneyTermination:

        val Classic: ChimneyTermination = ChimneyTermination(
            ChimneyLocationOnRoof.Classic,
            AdjacentBuildings.Classic
        )

        case class ChimneyLocationOnRoof private (
            chimney_height_above_ridgeline: ChimneyLocationOnRoof.ChimneyHeightAboveRidgeline,
            horizontal_distance_between_chimney_and_ridgeline: Option[ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgeline]           ,
            slope: Option[ChimneyLocationOnRoof.Slope]                               ,
            outside_air_intake_and_chimney_locations: Option[ChimneyLocationOnRoof.OutsideAirIntakeAndChimneyLocations]  ,
            horizontal_distance_between_chimney_and_ridgeline_bis: Option[ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgelineBis]              ,
        )

        object ChimneyLocationOnRoof:

            val Classic = ChimneyLocationOnRoof.from(ChimneyHeightAboveRidgeline.MoreThan40cm)

            def I18N_WRoof(using Locale): afpma.firecalc.i18n.I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof = 
                I18N.local_conditions.chimney_termination.chimney_location_on_roof

            // Reduce amount of availabe constructors
            // 
            // allow only the minimum we need 
            // (not really decoupling to the standard implementation but prevent misuse, and better for UX)
            def from(h: ChimneyHeightAboveRidgeline.MoreThan40cm): ChimneyLocationOnRoof = 
                ChimneyLocationOnRoof(h, None, None, None, None)

            def from(
                h: ChimneyHeightAboveRidgeline.LessThan40cm, 
                d: HorizontalDistanceBetweenChimneyAndRidgeline.MoreThan2m30
            ): ChimneyLocationOnRoof = 
                ChimneyLocationOnRoof(h, d.some, None, None, None)

            @targetName("h_lessThan40cm__d_lessThan2m30__rs_lessThan25Deg")
            def from(
                h: ChimneyHeightAboveRidgeline.LessThan40cm, 
                d: HorizontalDistanceBetweenChimneyAndRidgeline.LessThan2m30, 
                rs: Slope.LessThan25Deg
            ): ChimneyLocationOnRoof = 
                ChimneyLocationOnRoof(h, d.some, rs.some, None, None)

            @targetName("h_lessThan40cm__d_lessThan2m30__rs_moreThan40deg")
            def from(
                h: ChimneyHeightAboveRidgeline.LessThan40cm, 
                d: HorizontalDistanceBetweenChimneyAndRidgeline.LessThan2m30, 
                rs: Slope.MoreThan40Deg
            ): ChimneyLocationOnRoof = 
                ChimneyLocationOnRoof(h, d.some, rs.some, None, None)

            def from(
                h: ChimneyHeightAboveRidgeline.LessThan40cm, 
                d: HorizontalDistanceBetweenChimneyAndRidgeline.LessThan2m30, 
                rs: Slope.From25DegTo40Deg,
                o: OutsideAirIntakeAndChimneyLocations.OnSameSideOfRidgeline
            ): ChimneyLocationOnRoof = 
                ChimneyLocationOnRoof(h, d.some, rs.some, o.some, None)

            def from(
                h: ChimneyHeightAboveRidgeline.LessThan40cm, 
                d: HorizontalDistanceBetweenChimneyAndRidgeline.LessThan2m30, 
                rs: Slope.From25DegTo40Deg,
                o: OutsideAirIntakeAndChimneyLocations.OnDifferentSidesOfRidgeline,
                s: HorizontalDistanceBetweenChimneyAndRidgelineBis
            ): ChimneyLocationOnRoof = 
                ChimneyLocationOnRoof(h, d.some, rs.some, o.some, s.some)

            enum ChimneyHeightAboveRidgeline:
                case MoreThan40cm, LessThan40cm
            object ChimneyHeightAboveRidgeline:
                type MoreThan40cm               = MoreThan40cm.type
                type LessThan40cm               = LessThan40cm.type
                given ShowUsingLocale[ChimneyHeightAboveRidgeline] = showUsingLocale:
                    case MoreThan40cm => I18N_WRoof.chimney_height_above_ridgeline.more_than_40cm
                    case LessThan40cm => I18N_WRoof.chimney_height_above_ridgeline.less_than_40cm

            enum HorizontalDistanceBetweenChimneyAndRidgeline:
                case LessThan2m30, MoreThan2m30
            object HorizontalDistanceBetweenChimneyAndRidgeline:
                type LessThan2m30               = LessThan2m30.type
                type MoreThan2m30               = MoreThan2m30.type
                given ShowUsingLocale[HorizontalDistanceBetweenChimneyAndRidgeline] = showUsingLocale:
                    case LessThan2m30 => I18N_WRoof.horizontal_distance_between_chimney_and_ridgeline.less_than_2m30
                    case MoreThan2m30 => I18N_WRoof.horizontal_distance_between_chimney_and_ridgeline.more_than_2m30

            enum Slope:
                case LessThan25Deg, From25DegTo40Deg, MoreThan40Deg
            object Slope:
                type LessThan25Deg              = LessThan25Deg.type
                type From25DegTo40Deg           = From25DegTo40Deg.type
                type MoreThan40Deg              = MoreThan40Deg.type
                given ShowUsingLocale[Slope] = showUsingLocale:
                    case LessThan25Deg        => I18N_WRoof.slope.less_than_25deg
                    case From25DegTo40Deg     => I18N_WRoof.slope.between_25deg_and_40deg
                    case MoreThan40Deg        => I18N_WRoof.slope.more_than_40deg

            enum OutsideAirIntakeAndChimneyLocations:
                case OnDifferentSidesOfRidgeline, OnSameSideOfRidgeline
            object OutsideAirIntakeAndChimneyLocations:
                type OnDifferentSidesOfRidgeline = OnDifferentSidesOfRidgeline.type
                type OnSameSideOfRidgeline       = OnSameSideOfRidgeline.type
                given ShowUsingLocale[OutsideAirIntakeAndChimneyLocations] = showUsingLocale:
                    case OnDifferentSidesOfRidgeline => I18N_WRoof.outside_air_intake_and_chimney_locations.on_different_sides_of_the_ridge
                    case OnSameSideOfRidgeline       => I18N_WRoof.outside_air_intake_and_chimney_locations.on_same_side_of_the_ridge

            enum HorizontalDistanceBetweenChimneyAndRidgelineBis:
                case LessThan1m, MoreThan1m
            object HorizontalDistanceBetweenChimneyAndRidgelineBis:
                type LessThan1m                 = LessThan1m.type
                type MoreThan1m                 = MoreThan1m.type
                given ShowUsingLocale[HorizontalDistanceBetweenChimneyAndRidgelineBis] = showUsingLocale:
                    case LessThan1m => I18N_WRoof.horizontal_distance_between_chimney_and_ridgeline_bis.less_than_1m
                    case MoreThan1m => I18N_WRoof.horizontal_distance_between_chimney_and_ridgeline_bis.more_than_1m
        end ChimneyLocationOnRoof

        import AdjacentBuildings.*

        case class AdjacentBuildings private (
            horizontal_distance_between_chimney_and_adjacent_buildings: HorizontalDistanceBetweenChimneyAndAdjacentBuildings                 ,
            horizontal_angle_between_chimney_and_adjacent_buildings: Option[HorizontalAngleBetweenChimneyAndAdjacentBuildings]                   ,
            vertical_angle_between_chimney_and_adjacent_buildings: Option[VerticalAngleBetweenChimneyAndAdjacentBuildings]     ,
        )

        object AdjacentBuildings:

            val Classic = AdjacentBuildings.from(HorizontalDistanceBetweenChimneyAndAdjacentBuildings.MoreThan15m)

            def I18N_WAdj(using Locale): afpma.firecalc.i18n.I18nData.LocalConditions.ChimneyTermination.AdjacentBuildings = 
                I18N.local_conditions.chimney_termination.adjacent_buildings

            def from(
                l: HorizontalDistanceBetweenChimneyAndAdjacentBuildings.MoreThan15m
            ): AdjacentBuildings = 
                AdjacentBuildings(l, None, None)

            def from(
                l: HorizontalDistanceBetweenChimneyAndAdjacentBuildings.LessThan15m,
                alpha: HorizontalAngleBetweenChimneyAndAdjacentBuildings.LessThan30Deg
            ): AdjacentBuildings = 
                AdjacentBuildings(l, alpha.some, None)

            def from(
                l: HorizontalDistanceBetweenChimneyAndAdjacentBuildings.LessThan15m,
                beta: VerticalAngleBetweenChimneyAndAdjacentBuildings.LessThan10DegOverHorizon
            ): AdjacentBuildings = 
                AdjacentBuildings(l, None, beta.some)

            def from(
                l: HorizontalDistanceBetweenChimneyAndAdjacentBuildings.LessThan15m,
                alpha: HorizontalAngleBetweenChimneyAndAdjacentBuildings.MoreThan30Deg,
                beta: VerticalAngleBetweenChimneyAndAdjacentBuildings
            ): AdjacentBuildings = 
                AdjacentBuildings(l, alpha.some, beta.some)

            enum HorizontalDistanceBetweenChimneyAndAdjacentBuildings:
                case LessThan15m, MoreThan15m
            object HorizontalDistanceBetweenChimneyAndAdjacentBuildings:
                type LessThan15m = LessThan15m.type
                type MoreThan15m = MoreThan15m.type
                given ShowUsingLocale[HorizontalDistanceBetweenChimneyAndAdjacentBuildings] = showUsingLocale:
                    case LessThan15m => I18N_WAdj.horizontal_distance_between_chimney_and_adjacent_buildings.less_than_15m
                    case MoreThan15m => I18N_WAdj.horizontal_distance_between_chimney_and_adjacent_buildings.more_than_15m

            enum HorizontalAngleBetweenChimneyAndAdjacentBuildings:
                case MoreThan30Deg, LessThan30Deg
            object HorizontalAngleBetweenChimneyAndAdjacentBuildings:
                type MoreThan30Deg = MoreThan30Deg.type
                type LessThan30Deg = LessThan30Deg.type
                given ShowUsingLocale[HorizontalAngleBetweenChimneyAndAdjacentBuildings] = showUsingLocale:
                    case MoreThan30Deg => I18N_WAdj.horizontal_angle_between_chimney_and_adjacent_buildings.more_than_30deg
                    case LessThan30Deg => I18N_WAdj.horizontal_angle_between_chimney_and_adjacent_buildings.less_than_30deg


            enum VerticalAngleBetweenChimneyAndAdjacentBuildings:
                case MoreThan10DegAboveHorizon, LessThan10DegOverHorizon
            object VerticalAngleBetweenChimneyAndAdjacentBuildings:
                type MoreThan10DegAboveHorizon = MoreThan10DegAboveHorizon.type
                type LessThan10DegOverHorizon = LessThan10DegOverHorizon.type
                given ShowUsingLocale[VerticalAngleBetweenChimneyAndAdjacentBuildings] = showUsingLocale:
                    case MoreThan10DegAboveHorizon  => I18N_WAdj.vertical_angle_between_chimney_and_adjacent_buildings.more_than_10deg_above_horizon
                    case LessThan10DegOverHorizon  =>  I18N_WAdj.vertical_angle_between_chimney_and_adjacent_buildings.less_than_10deg_above_horizon