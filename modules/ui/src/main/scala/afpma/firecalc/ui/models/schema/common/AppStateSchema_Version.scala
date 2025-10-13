/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.common

import io.circe.*

opaque type AppStateSchema_Version = Int

object AppStateSchema_Version:

    given Encoder[AppStateSchema_Version] = Encoder.encodeInt
    given Decoder[AppStateSchema_Version] = Decoder.decodeInt
    
    def apply(i: Int): AppStateSchema_Version = i
    
    extension (v: AppStateSchema_Version)
        def unwrap: Int = v