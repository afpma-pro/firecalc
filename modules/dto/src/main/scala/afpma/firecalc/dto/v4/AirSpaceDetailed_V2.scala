/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.common.AirSpaceDetailed_V1

import afpma.firecalc.i18n.*

import cats.Show
import cats.syntax.show.toShow

import coulomb.policy.standard.given

import magnolia1.Transl

/**
 * V2 of AirSpaceDetailed, introduced in FireCalcYAML V4.
 *
 * Identical semantics to [[afpma.firecalc.dto.common.AirSpaceDetailed]] but
 * uses a YAML-safe encoding: [[WithoutAirSpace_V2]] is serialized as the plain
 * string `"WithoutAirSpace"` instead of `{"WithoutAirSpace": {}}`. The latter
 * caused the YAML printer to emit `WithoutAirSpace: null`, which could not be
 * decoded back by the default semiauto decoder.
 *
 * The encoder/decoder for this type is defined in
 * [[afpma.firecalc.dto.instances.V4Instances]].
 */
@Transl(I(_.en13384.air_space_detailed))
sealed trait AirSpaceDetailed_V2

object AirSpaceDetailed_V2:
    
    export AirSpaceDetailed_V1.VentilOpenings
    export AirSpaceDetailed_V1.VentilDirection
    
    import VentilDirection.SameDirAsFlueGas
    import VentilDirection.given

    given showAirSpaceDetailed_V2: Show[AirSpaceDetailed_V2] = Show.show:
        case WithoutAirSpace_V2                                                                => "-"
        case WithAirSpace_V2(w, _, VentilOpenings.NoOpening)                                   => s"𐄂${w.to_cm.showP}"
        case WithAirSpace_V2(w, dir, VentilOpenings.AnnularAreaFullyOpened)                    => s"✓${dir.show} ${w.to_cm.showP}"
        case WithAirSpace_V2(w, dir, VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1) =>
            s"~${dir.show} ${w.to_cm.showP}"

    extension (asd: AirSpaceDetailed_V2)

        def isHygienic: Boolean = asd match
            case WithAirSpace_V2(w, SameDirAsFlueGas, VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1)
                if w >= 1.cm && w <= 5.cm =>
                true
            case _ => false

        def isFullyOpened: Boolean = asd match
            case WithAirSpace_V2(_, _, VentilOpenings.AnnularAreaFullyOpened) => true
            case _                                                         => false

        def isConsideredDeadOrStatic: Boolean = !isFullyOpened

        def isNone: Boolean = asd match
            case WithoutAirSpace_V2 => true
            case _               => false

        def hasWidthFrom1to5cm: Boolean = asd match
            case WithAirSpace_V2(w, _, _) if w >= 1.cm && w <= 5.cm => true
            case _                                               => false

    @Transl(I(_.en13384._air_space_detailed.without_air_space))
    case object WithoutAirSpace_V2 extends AirSpaceDetailed_V2

    @Transl(I(_.en13384._air_space_detailed.without_air_space))
    type WithoutAirSpace_V2 = WithoutAirSpace_V2.type

    @Transl(I(_.en13384._air_space_detailed.with_air_space))
    case class WithAirSpace_V2(
        @Transl(I(_.terms.width))
        width          : Length,
        @Transl(I(_.en13384._air_space_detailed.ventil_direction))
        direction      : VentilDirection,
        @Transl(I(_.en13384._air_space_detailed.ventil_openings))
        ventil_openings: VentilOpenings
    ) extends AirSpaceDetailed_V2

    val withoutAirSpace: AirSpaceDetailed_V2.WithoutAirSpace_V2 = AirSpaceDetailed_V2.WithoutAirSpace_V2
