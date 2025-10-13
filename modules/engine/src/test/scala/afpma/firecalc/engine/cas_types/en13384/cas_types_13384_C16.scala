/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */


package afpma.firecalc.engine.cas_types.en13384

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.cas_types.CasTypesRunner_13384
import afpma.firecalc.engine.cas_types.CasType13384_Result
import cats.syntax.all.*

class cas_types_13384_C16_Suite extends CasTypesRunner_13384:

    val qc2_result = 
        CasType13384_Result(
            descr = "QC2",
            nominal = CasType13384_Result.Values(
                pz        = 5.3.pascals.some,
                pze       = 24.6.pascals.some,
                pb        = 30.7.pascals.some,
                `pz-pze`  = -19.3.pascals.some,
                `pz-pb`   = -25.3.pascals.some,
                tg        = 0.degreesCelsius.some,
                tob       = 104.7.degreesCelsius.some,
                tiob      = 65.1.degreesCelsius.some,
                `tiob-tg` = 65.1.degreesCelsius.some,
            ),
            lowest = CasType13384_Result.Values(
                pz        = 2.8.pascals.some,
                pze       = -0.4.pascals.some,
                pb        = 3.7.pascals.some,
                `pz-pze`  = 3.2.pascals.some,
                `pz-pb`   = 3.2.pascals.some,
                tg        = 0.degreesCelsius.some,
                tob       = 40.5.degreesCelsius.some,
                tiob      = 16.7.degreesCelsius.some,
                `tiob-tg` = 16.7.degreesCelsius.some,
            )
        )
        

    "cas types EN13384 - C16" in {
        run_cas_type_13384_strict(afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C16)
        compute_and_show_results(
            ex = afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C16,
            compareTo = qc2_result
        )
    }