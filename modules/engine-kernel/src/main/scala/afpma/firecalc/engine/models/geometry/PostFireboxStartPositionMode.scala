/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.dto.v7.PostFireboxStartPosition

/**
 * Engine-side mirror of the DTO wire-format enum [[PostFireboxStartPosition]].
 *
 * Carries the '''raw user-intent mode''' for the post-firebox pipe start position
 * (Auto vs Manual override), as opposed to the loader's ''resolved''
 * `Option[Position3D]` projection. Lives on the `FramedIncrPostFireboxPipes`
 * wrapper's `positionMode` field, sibling to `resolvedPosition`.
 *
 * The DTO enum remains the YAML wire format (`dto` cannot depend on the engine);
 * the strict `FireCalcYAML_Loader` converts DTO modes to engine modes at the layer
 * boundary via [[PostFireboxStartPositionMode.fromDto]].
 *
 * '''Drift safety.''' [[fromDto]] is a total (exhaustive) match over the DTO enum, so
 * any new DTO case forces a compiler error here until the engine enum and converter
 * are updated in lockstep.
 */
enum PostFireboxStartPositionMode:
    case Auto
    case Manual(position: Position3D)

object PostFireboxStartPositionMode:

    /** Convert DTO wire-format mode to engine mode. Total — compiler-enforced drift check. */
    def fromDto(mode: PostFireboxStartPosition): PostFireboxStartPositionMode = mode match
        case PostFireboxStartPosition.Auto      => Auto
        case PostFireboxStartPosition.Manual(p) => Manual(p)
