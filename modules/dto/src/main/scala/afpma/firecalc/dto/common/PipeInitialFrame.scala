/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

final case class PipeInitialFrame(
    direction: PipeInitialDirection,
    position : Position3D
)

object PipeInitialFrame:
    given Encoder[PipeInitialFrame] = semiauto.deriveEncoder
    given Decoder[PipeInitialFrame] = semiauto.deriveDecoder
