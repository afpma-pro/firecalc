/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en13384

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.cas_types.CasType13384_Result
import cats.syntax.all.*

object CasTypes13384_ExpectedValues:

    val C2: CasType13384_Result =
        CasType13384_Result  (
            descr   = "QC2",
            nominal = CasType13384_Result.Values(
                pz        = 8.9.pascals.some,
                pze       = 2.6.pascals.some,
                pb        = 6.9.pascals.some,
                `pz-pze`  = 6.3.pascals.some,
                `pz-pb`   = 2.0.pascals.some,
                tg        = 0.degreesCelsius.some,
                tob       = 73.3.degreesCelsius.some,
                tiob      = 23.5.degreesCelsius.some,
                `tiob-tg` = 23.5.degreesCelsius.some
            ),
            lowest  = CasType13384_Result.Values(
                pz        = 3.pascals.some,
                pze       = -2.pascals.some,
                pb        = 0.7.pascals.some,
                `pz-pze`  = 5.pascals.some,
                `pz-pb`   = 2.3.pascals.some,
                tg        = 0.degreesCelsius.some,
                tob       = 14.8.degreesCelsius.some,
                tiob      = -2.3.degreesCelsius.some,
                `tiob-tg` = -2.3.degreesCelsius.some
            )
        )

    val C16: CasType13384_Result =
        CasType13384_Result  (
            descr   = "QC2",
            nominal = CasType13384_Result.Values(
                pz        = 5.3.pascals.some,
                pze       = 24.6.pascals.some,
                pb        = 30.7.pascals.some,
                `pz-pze`  = -19.3.pascals.some,
                `pz-pb`   = -25.3.pascals.some,
                tg        = 0.degreesCelsius.some,
                tob       = 104.7.degreesCelsius.some,
                tiob      = 65.1.degreesCelsius.some,
                `tiob-tg` = 65.1.degreesCelsius.some
            ),
            lowest  = CasType13384_Result.Values(
                pz        = 2.8.pascals.some,
                pze       = -0.4.pascals.some,
                pb        = 3.7.pascals.some,
                `pz-pze`  = 3.2.pascals.some,
                `pz-pb`   = 3.2.pascals.some,
                tg        = 0.degreesCelsius.some,
                tob       = 40.5.degreesCelsius.some,
                tiob      = 16.7.degreesCelsius.some,
                `tiob-tg` = 16.7.degreesCelsius.some
            )
        )
