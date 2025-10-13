/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en13384

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.cas_types.CasTypesRunner_13384
import afpma.firecalc.engine.cas_types.CasType13384_Result
import cats.syntax.all.*

class cas_types_13384_C2_Suite extends CasTypesRunner_13384:

    val qc2_result = 
        CasType13384_Result(
            descr = "QC2",
            nominal = CasType13384_Result.Values(
                pz        = 8.9.pascals.some,
                pze       = 2.6.pascals.some,
                pb        = 6.9.pascals.some,
                `pz-pze`  = 6.3.pascals.some,
                `pz-pb`   = 2.0.pascals.some,
                tg        = 0.degreesCelsius.some,
                tob       = 73.3.degreesCelsius.some,
                tiob      = 23.5.degreesCelsius.some,
                `tiob-tg` = 23.5.degreesCelsius.some,
            ),
            lowest = CasType13384_Result.Values(
                pz        = 3.pascals.some,
                pze       = -2.pascals.some,
                pb        = 0.7.pascals.some,
                `pz-pze`  = 5.pascals.some,
                `pz-pb`   = 2.3.pascals.some,
                tg        = 0.degreesCelsius.some,
                tob       = 14.8.degreesCelsius.some,
                tiob      = -2.3.degreesCelsius.some,
                `tiob-tg` = -2.3.degreesCelsius.some,
            )
        )

    "cas types EN13384 - C2" in {
        run_cas_type_13384_strict(afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C2)
        compute_and_show_results(
            ex = afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C2,
            compareTo = qc2_result
        )
    }