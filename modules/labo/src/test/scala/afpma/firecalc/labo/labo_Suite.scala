/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.labo

import cats.data.*
import org.scalatest.freespec.AnyFreeSpec
import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.labo.`01_echangeur`.*
import afpma.firecalc.labo.ConfigurationRunners_Labo

class labo_Suite extends ConfigurationRunners_Labo {

    type VNel[A] = Validated[NonEmptyList[String], A]

    "EN15544 (labo)" - {

        "01_echangeur" - {
            
            "01_cloche_medianne_entree_haute_config_1" in {
                run_15544_mce_for_lab_comparison(`01_cloche_medianne_entree_haute_config_1`)
            }

            "07_cloche_intermediaire_entre_basse_avec_colonne_config_1" in {
                run_15544_mce_for_lab_comparison(`07_cloche_intermediaire_entre_basse_avec_colonne_config_1`)
            }
        }
    }

}
