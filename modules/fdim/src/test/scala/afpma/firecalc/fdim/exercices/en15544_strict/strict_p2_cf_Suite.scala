/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_strict

import org.scalatest.freespec.AnyFreeSpec
import afpma.firecalc.engine.ConfigurationRunners
import afpma.firecalc.fdim.exercices.p2_cf.strict_ex02_kachelofen


class strict_p2_cf_Suite extends ConfigurationRunners {

    "EN_15544 (STRICT)" - {
        
        "p2_cf" - {

            "strict_ex02_kachelofen" in {
                run_exercice_15544_strict(strict_ex02_kachelofen)
            }

        }
    }
            
}

