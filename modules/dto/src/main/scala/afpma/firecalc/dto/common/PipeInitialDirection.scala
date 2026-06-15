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
 * TODO(azimuth-optional): azimuth should be Option[AzimuthDirection] to support
 * purely vertical pipes where azimuth is meaningless. The DirectionBadgeComponent
 * in the UI already uses optional azimuth for better modeling. Purely vertical
 * connectors (e.g. CasType_13384_C2) have no meaningful azimuth — they go straight
 * up from the firebox. Currently, callers must provide a dummy azimuth value (e.g.
 * AzimuthDirection.Rear) even though it has no physical meaning for vertical pipes.
 *
 * Actionable plan for future migration:
 * 1. Change `azimuth: AzimuthDirection` → `azimuth: Option[AzimuthDirection]`
 * 2. Update PipeInitialDirection companion object:
 *    - Add `apply(inclination: InclinationDirection)` overload for vertical-only pipes
 *    - Add `apply(azimuth: AzimuthDirection, inclination: InclinationDirection)` overload
 * 3. Update PipeFrame.initial(Vec3) to convert Vec3 → PipeInitialDirection with optional azimuth:
 *    - Vertical (Up/Down): azimuth = None
 *    - Horizontal: azimuth = derived from Vec3 projection onto XY plane
 * 4. Update all callers — search for `PipeInitialDirection(` and add azimuth = None where appropriate
 * 5. Update DirectionBadgeComponent to handle Option[AzimuthDirection] display
 * 6. Update serialization (Encoder/Decoder) for optional azimuth
 * 7. Update i18n labels for "no azimuth" case
 *
 * See: .scratch/remove-init-descr-in-V7-migration/ISSUES.md Issue 1
 */
final case class PipeInitialDirection(
    azimuth    : AzimuthDirection,
    inclination: InclinationDirection
)

object PipeInitialDirection:
    import afpma.firecalc.dto.instances.V4Instances.given

    given Encoder[PipeInitialDirection] = semiauto.deriveEncoder
    given Decoder[PipeInitialDirection] = semiauto.deriveDecoder

    /** Default: right (azimuth) + horizontal (inclination). */
    val default: PipeInitialDirection =
        PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
