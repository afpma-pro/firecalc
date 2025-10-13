/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import afpma.firecalc.engine.models.en15544.typedefs
import afpma.firecalc.units.coulombutils.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class coulombutils_Suite extends AnyFreeSpec with Matchers {
    
    "ShowUnit" - {

        "for coulomb unit" - {
            "does not return U for concrete unit" in {
                ShowUnit.fromCoulombUnit[Meter].showUnit `shouldBe` "m"
            }

            "does not return U for StoragePeriod unit" in {
                typedefs.StoragePeriod.showUnitString `shouldBe` "h"
            }

            "does not return U for Meter unit" in {
                val suMeter = ShowUnit.fromCoulombUnit[Meter]
                suMeter.showUnit `shouldBe` "m"
            }
        }
    }
}