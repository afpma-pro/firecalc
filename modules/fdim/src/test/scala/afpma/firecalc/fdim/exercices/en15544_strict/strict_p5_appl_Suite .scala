/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_strict

import org.scalatest.freespec.AnyFreeSpec
import afpma.firecalc.engine.ConfigurationRunners
import afpma.firecalc.fdim.exercices.en15544_strict.p5_application.strict_ex03_cas_pratique


class strict_p5_appl_Suite extends ConfigurationRunners {

    "EN_15544 (STRICT)" - {
        
        "p5_appl" - {

            "strict_ex03_kachelofen" in {
                run_exercice_15544_strict(strict_ex03_cas_pratique)
            }

        }
    }
            
}

