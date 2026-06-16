/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

/**
 * Initial direction of the pipe chain — the azimuth/inclination
 * at the start, before any concrete pipe sections.
 *
 * Azimuth is `None` for purely vertical pipes (Up/Down), where azimuth is meaningless.
 * The `apply(azimuth, inclination)` overload auto-normalizes vertical inclinations
 * to `azimuth = None`, following the same pattern as `AbsoluteDirection`.
 */
final case class PipeInitialDirection(
    azimuth    : Option[AzimuthDirection],
    inclination: InclinationDirection
)

object PipeInitialDirection:
    import afpma.firecalc.dto.instances.V4Instances.given

    given Encoder[PipeInitialDirection] = semiauto.deriveEncoder
    given Decoder[PipeInitialDirection] = semiauto.deriveDecoder

    /** Vertical-only constructor (no azimuth). */
    def apply(inclination: InclinationDirection): PipeInitialDirection =
        new PipeInitialDirection(None, inclination)

    /** Full constructor — auto-normalizes vertical inclinations to azimuth=None. */
    def apply(azimuth: AzimuthDirection, inclination: InclinationDirection): PipeInitialDirection =
        val az = inclination match
            case InclinationDirection.Up | InclinationDirection.Down => None
            case _                                                   => Some(azimuth)
        new PipeInitialDirection(az, inclination)

    /** Default: right (azimuth) + horizontal (inclination). */
    val default: PipeInitialDirection =
        PipeInitialDirection(Some(AzimuthDirection.Right), InclinationDirection.Horizontal)
