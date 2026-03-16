/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.units.coulombutils.*

/**
 * Azimuth direction in the horizontal plane, relative to the stove.
 * 0° = Rear (+Y), 90° = Right (+X), ±180° = Front (-Y), -90° = Left (-X).
 * Clockwise positive when viewed from above.
 */
enum AzimuthDirection:
    case Rear                                //   0°
    case RearRight                           //  +45°
    case Right                               //  +90°
    case FrontRight                          // +135°
    case Front                               // ±180°
    case FrontLeft                           // -135°
    case Left                                //  -90°
    case RearLeft                            //  -45°
    case Custom(azimuth: Angle)              // arbitrary angle

object AzimuthDirection:
    /** Convert to degrees (Double). Named cases map to their fixed angle. */
    def toDegrees(d: AzimuthDirection): Double = d match
        case Rear      => 0.0
        case RearRight => 45.0
        case Right     => 90.0
        case FrontRight => 135.0
        case Front     => 180.0
        case FrontLeft => -135.0
        case Left      => -90.0
        case RearLeft  => -45.0
        case Custom(az) => az.value

    /** All named (non-Custom) cases. */
    val namedCases: List[AzimuthDirection] =
        List(Rear, RearRight, Right, FrontRight, Front, FrontLeft, Left, RearLeft)

    /** Snap a degree value to a named case if within tolerance, else Custom. */
    def fromDegrees(deg: Double, tolerance: Double = 1.0): AzimuthDirection =
        // Normalize to [-180, 180)
        val normalized = ((deg % 360) + 540) % 360 - 180
        namedCases.find { c =>
            val cdeg = toDegrees(c)
            val cnorm = ((cdeg % 360) + 540) % 360 - 180
            math.abs(cnorm - normalized) < tolerance
        }.getOrElse(Custom(deg.degrees))

/**
 * Inclination direction (elevation from horizontal plane).
 * +90° = Up (+Z), -90° = Down (-Z), 0° = Horizontal.
 */
enum InclinationDirection:
    case Up                                   // +90°
    case Down                                 // -90°
    case Horizontal                           //   0°
    case Custom(inclination: Angle)           // arbitrary angle

object InclinationDirection:
    /** Convert to degrees (Double). Named cases map to their fixed angle. */
    def toDegrees(d: InclinationDirection): Double = d match
        case Up         => 90.0
        case Down       => -90.0
        case Horizontal => 0.0
        case Custom(el) => el.value

    /** All named (non-Custom) cases. */
    val namedCases: List[InclinationDirection] =
        List(Up, Down, Horizontal)

    /** Snap a degree value to a named case if within tolerance, else Custom. */
    def fromDegrees(deg: Double, tolerance: Double = 1.0): InclinationDirection =
        namedCases.find { c =>
            math.abs(toDegrees(c) - deg) < tolerance
        }.getOrElse(Custom(deg.degrees))

/**
 * A complete direction specification: azimuth in horizontal plane + inclination from horizontal.
 * Used to replace the raw `roll` angle on direction-change DTOs.
 *
 * Azimuth is `None` for vertical directions (Up/Down) where it is physically meaningless.
 */
case class FinalDirection(
    azimuth    : Option[AzimuthDirection],
    inclination: InclinationDirection
)

object FinalDirection:
    /** Convenience constructor that normalizes vertical directions to azimuth=None. */
    def apply(azimuth: AzimuthDirection, inclination: InclinationDirection): FinalDirection =
        val az = inclination match
            case InclinationDirection.Up | InclinationDirection.Down => None
            case _ => Some(azimuth)
        new FinalDirection(az, inclination)

    /** Convert to (azimuthDeg, elevationDeg) pair. Returns 0.0 azimuth when None. */
    def toAzimuthElevationDeg(fd: FinalDirection): (Double, Double) =
        val azDeg = fd.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0)
        val elDeg = InclinationDirection.toDegrees(fd.inclination)
        (azDeg, elDeg)
