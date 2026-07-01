/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.dto.v7.AirIntakePosition

/**
 * Engine-side mirror of the DTO wire-format enum [[AirIntakePosition]].
 *
 * Carries the '''raw user-intent mode''' for the air intake pipe position
 * (Initial/Final × Auto/Manual), as opposed to the loader's ''resolved''
 * `Option[Position3D]` projection. Lives on the `FramedIncrAirIntakePipe`
 * wrapper's `positionMode` field, sibling to `resolvedPosition`.
 *
 * The DTO enum remains the YAML wire format (`dto` cannot depend on the engine);
 * the strict `FireCalcYAML_Loader` converts DTO modes to engine modes at the layer
 * boundary via [[AirIntakePositionMode.fromDto]].
 *
 * '''Drift safety.''' [[fromDto]] is a total (exhaustive) match over the DTO enum, so
 * any new DTO case forces a compiler error here until the engine enum and converter
 * are updated in lockstep.
 */
enum AirIntakePositionMode:
    case InitialAuto
    case InitialManual(position: Position3D)
    case FinalAuto
    case FinalManual(position: Position3D)

object AirIntakePositionMode:

    /** Convert DTO wire-format mode to engine mode. Total — compiler-enforced drift check. */
    def fromDto(mode: AirIntakePosition): AirIntakePositionMode = mode match
        case AirIntakePosition.InitialAuto      => InitialAuto
        case AirIntakePosition.InitialManual(p) => InitialManual(p)
        case AirIntakePosition.FinalAuto        => FinalAuto
        case AirIntakePosition.FinalManual(p)   => FinalManual(p)
