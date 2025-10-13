/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_mce

import cats.data.*
import org.scalatest.freespec.AnyFreeSpec
import afpma.firecalc.engine.ConfigurationRunners
import afpma.firecalc.fdim.exercices.en15544_mce.p1_decouverte.mce_ex01_colonne_ascendante


class mce_p1_decouverte_Suite extends ConfigurationRunners {

    type VNel[A] = Validated[NonEmptyList[String], A]

    "mce_ex01_colonne_ascendante" - {

        "firecalc en15544 mce" - {

            "for ex01" in {
                run_exercice_15544_mce(mce_ex01_colonne_ascendante)
            }
        }
    }

}
