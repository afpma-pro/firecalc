/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import cats.*

import afpma.firecalc.i18n.*

import afpma.firecalc.units.coulombutils.*
import magnolia1.Transl

// ambiant air temperatures

/**
     * Define a set of ambiant air temperature depending on conditions (wet, dry)
     * 
     * The set can defined as having the same values as 't_uo'
     * 
     * Other factors such as 'airSpace' or 'unheated area' inside or outside the building (below or above 5m) can be used
     *
     * @param withoutairspace_drycond ambiant air temperature in dry conditions and no air space
     * @param withairspace_drycond_hextAbove5m ambiant air temperature if unheated area inside or outside does exceed 5m, in dry conditions with air space
     * @param withairspace_drycond_hextBelow5m ambiant air temperature if unheated area inside or outside does not exceed 5m, in dry conditions with air space
     */
case class AmbiantAirTemperatureSet(
    val withoutairspace_wetcond: Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius],
    val withoutairspace_drycond: Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius],
    val withairspace_wetcond_hextAbove5m: Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius],
    val withairspace_wetcond_hextBelow5m: Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius],
    val withairspace_drycond_hextAbove5m: Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius],
    val withairspace_drycond_hextBelow5m: Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius],
)

export AmbiantAirTemperatureSet.UseTuoOverride

object AmbiantAirTemperatureSet:

    @Transl(I(_.en13384.reuse_tuo))
    type UseTuoOverride = UseTuoOverride.type
    case object UseTuoOverride

    given show_useTuOO: Show[UseTuoOverride] = Show.show(_ => "{tu_override_value}")

    lazy val defaultsToTuo = AmbiantAirTemperatureSet(
        Left(UseTuoOverride),
        Left(UseTuoOverride),
        Left(UseTuoOverride),
        Left(UseTuoOverride),
        Left(UseTuoOverride),
        Left(UseTuoOverride),
    )

    def fromSingleTu(temp: TCelsius) = AmbiantAirTemperatureSet(
        Right(temp),
        Right(temp),
        Right(temp),
        Right(temp),
        Right(temp),
        Right(temp),
    )

    def from(withoutAirSpace: TCelsius, withAirSpace: TCelsius) = 
        AmbiantAirTemperatureSet(
            withoutairspace_wetcond           = Right(withoutAirSpace),
            withoutairspace_drycond           = Right(withoutAirSpace),
            withairspace_wetcond_hextAbove5m  = Right(withAirSpace),
            withairspace_wetcond_hextBelow5m  = Right(withAirSpace),
            withairspace_drycond_hextAbove5m  = Right(withAirSpace),
            withairspace_drycond_hextBelow5m  = Right(withAirSpace),
        )

    def from(
        withoutAirSpace: TCelsius,
        withAirSpace_hextAbove5m: TCelsius,
        withAirSpace_hextBelow5m: TCelsius,
    ) = 
        AmbiantAirTemperatureSet(
            withoutairspace_wetcond           = Right(withoutAirSpace),
            withoutairspace_drycond           = Right(withoutAirSpace),
            withairspace_wetcond_hextAbove5m  = Right(withAirSpace_hextAbove5m),
            withairspace_wetcond_hextBelow5m  = Right(withAirSpace_hextBelow5m),
            withairspace_drycond_hextAbove5m  = Right(withAirSpace_hextAbove5m),
            withairspace_drycond_hextBelow5m  = Right(withAirSpace_hextBelow5m),
        )
end AmbiantAirTemperatureSet
