/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.syntax.*

object transformers:

    // Firebox_V1 -> Firebox_V2

    given Transformer[v1.Firebox_V1.Traditional, v2.Firebox_V2.Traditional] =
        Transformer.define[v1.Firebox_V1.Traditional, v2.Firebox_V2.Traditional]
            .withFieldConst(_.height_of_first_row_of_air_injectors, 5.cm)
            .buildTransformer

    given Transformer[v1.Firebox_V1.EcoLabeled, v2.Firebox_V2.EcoLabeled] =
        Transformer.define[v1.Firebox_V1.EcoLabeled, v2.Firebox_V2.EcoLabeled]
            .withFieldConst(_.height_of_first_row_of_air_injectors, 5.cm)
            .buildTransformer


