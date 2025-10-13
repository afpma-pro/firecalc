/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte

import org.scalatest.freespec.AnyFreeSpec
import afpma.firecalc.engine.ConfigurationRunners


class strict_p1_decouverte_Suite extends ConfigurationRunners {

    "EN_15544 (STRICT)" - {
        
        "p1_decouverte" - {

            "strict_ex01_colonne_ascendante" in {
                run_exercice_15544_strict(strict_ex01_colonne_ascendante)
            }

            "strict_ex02_carneau_descendant" in {
                run_exercice_15544_strict(strict_ex02_carneau_descendant)
            }
        }
    }
            
}

