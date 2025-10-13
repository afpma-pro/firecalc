/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import io.circe.*

opaque type FireCalc_Version = Int

object FireCalc_Version:

    given Encoder[FireCalc_Version] = Encoder.encodeInt
    given Decoder[FireCalc_Version] = Decoder.decodeInt
    
    def apply(i: Int): FireCalc_Version = i
    
    extension (v: FireCalc_Version)
        def unwrap: Int = v
