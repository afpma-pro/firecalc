/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import cats.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.i18n.*

import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.policy.standard.given
import magnolia1.Transl

@Transl(I(_.en13384.air_space_detailed))
sealed trait AirSpaceDetailed

object AirSpaceDetailed:
    
    import VentilDirection.*

    given showAirSpaceDetailed: Show[AirSpaceDetailed] = Show.show:
        case WithoutAirSpace                                                                => "-"
        case WithAirSpace(w, _  , VentilOpenings.NoOpening)                                 => s"𐄂${w.to_cm.showP}"
        case WithAirSpace(w, dir, VentilOpenings.AnnularAreaFullyOpened)                    => s"✓${dir.show} ${w.to_cm.showP}"
        case WithAirSpace(w, dir, VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1) => s"~${dir.show} ${w.to_cm.showP}"

    extension (asd: AirSpaceDetailed)

        def isHygienic: Boolean = asd match
            case WithAirSpace(w, SameDirAsFlueGas, VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1) if w >= 1.cm && w <= 5.cm => true
            case _ => false

        def isFullyOpened: Boolean = asd match
            case WithAirSpace(_, _, VentilOpenings.AnnularAreaFullyOpened) => true
            case _ => false

        def isConsideredDeadOrStatic: Boolean = !isFullyOpened
        
        def isNone: Boolean = asd match
            case WithoutAirSpace => true
            case _ => false

        def hasWidthFrom1to5cm: Boolean = asd match
            case WithAirSpace(w, _, _) if w >= 1.cm && w <= 5.cm => true
            case _ => false

    @Transl(I(_.en13384._air_space_detailed.without_air_space))
    case object WithoutAirSpace extends AirSpaceDetailed

    @Transl(I(_.en13384._air_space_detailed.without_air_space))
    type WithoutAirSpace = WithoutAirSpace.type

    @Transl(I(_.en13384._air_space_detailed.with_air_space))
    case class WithAirSpace(
        @Transl(I(_.terms.width))
        width: Length,
        @Transl(I(_.en13384._air_space_detailed.ventil_direction))
        direction: VentilDirection,
        @Transl(I(_.en13384._air_space_detailed.ventil_openings))
        ventil_openings: VentilOpenings,
    ) extends AirSpaceDetailed

    @Transl(I(_.en13384._air_space_detailed.ventil_direction))
    enum VentilDirection:
        case UndefinedDir
        case SameDirAsFlueGas
        case OppositeDirOfFlueGas

    object VentilDirection:
        given Show[VentilDirection] = Show.show:
            case UndefinedDir         => ""
            case SameDirAsFlueGas     => "⇑"
            case OppositeDirOfFlueGas => "⇓"
        
        @Transl(I(_.en13384._air_space_detailed._ventil_direction.undefined_dir))
        type UndefinedDir = UndefinedDir.type
        
        @Transl(I(_.en13384._air_space_detailed._ventil_direction.same_dir_as_fluegas))
        type SameDirAsFlueGas = SameDirAsFlueGas.type
        
        @Transl(I(_.en13384._air_space_detailed._ventil_direction.inverse_dir_as_fluegas))
        type OppositeDirOfFlueGas = OppositeDirOfFlueGas.type
        

    @Transl(I(_.en13384._air_space_detailed.ventil_openings))
    enum VentilOpenings:
        case NoOpening
        /** espace annulaire tout ouvert */
        case AnnularAreaFullyOpened
        /** 5 cm2 at the top, 20 cm2 at the bottom */
        case PartiallyOpened_InAccordanceWith_DTU_24_1

    object VentilOpenings:
        @Transl(I(_.en13384._air_space_detailed._ventil_openings.no_opening))
        type NoOpening = NoOpening.type

        @Transl(I(_.en13384._air_space_detailed._ventil_openings.annular_area_fully_opened))
        type AnnularAreaFullyOpened = AnnularAreaFullyOpened.type

        @Transl(I(_.en13384._air_space_detailed._ventil_openings.partially_opened_in_accordance_with_dtu_24_1))
        type PartiallyOpened_InAccordanceWith_DTU_24_1 = PartiallyOpened_InAccordanceWith_DTU_24_1.type


    val withoutAirSpace: AirSpaceDetailed.WithoutAirSpace = AirSpaceDetailed.WithoutAirSpace