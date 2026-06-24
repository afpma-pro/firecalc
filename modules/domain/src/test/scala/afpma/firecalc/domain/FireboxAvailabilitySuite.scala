/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FireboxAvailabilitySuite extends AnyFreeSpec with Matchers:

    "FireboxAvailability" - {

        "AllEnabled" - {
            "has all five fields set to true" in {
                FireboxAvailability.AllEnabled.traditional shouldBe true
                FireboxAvailability.AllEnabled.ecolabeled shouldBe true
                FireboxAvailability.AllEnabled.afpmaPrse shouldBe true
                FireboxAvailability.AllEnabled.singleTested shouldBe true
                FireboxAvailability.AllEnabled.door15aCatalog shouldBe true
            }
        }

        "disabledTypeNames" - {
            "returns empty set when all enabled" in {
                FireboxAvailability.disabledTypeNames(FireboxAvailability.AllEnabled) shouldBe Set.empty
            }

            "returns all five names when all disabled" in {
                val allDisabled = FireboxAvailability(
                    traditional    = false,
                    ecolabeled     = false,
                    afpmaPrse      = false,
                    singleTested   = false,
                    door15aCatalog = false
                )
                FireboxAvailability.disabledTypeNames(allDisabled) shouldBe Set(
                    "Traditional",
                    "Ecolabeled",
                    "AFPMA_PRSE",
                    "SingleTested",
                    "Door15aFirebox_Catalog"
                )
            }

            "returns only the disabled names for a mixed configuration" in {
                val mixed = FireboxAvailability(
                    traditional    = true,
                    ecolabeled     = false,
                    afpmaPrse      = true,
                    singleTested   = false,
                    door15aCatalog = true
                )
                FireboxAvailability.disabledTypeNames(mixed) shouldBe Set("Ecolabeled", "SingleTested")
            }

            "uses the same canonical name format as FireboxCacheState.cacheKey" in {
                val onlyTraditionalDisabled = FireboxAvailability(
                    traditional    = false,
                    ecolabeled     = true,
                    afpmaPrse      = true,
                    singleTested   = true,
                    door15aCatalog = true
                )
                FireboxAvailability.disabledTypeNames(onlyTraditionalDisabled) shouldBe Set("Traditional")
            }

            "correctly identifies a single disabled subtype" in {
                val onlyDoorDisabled = FireboxAvailability(
                    traditional    = true,
                    ecolabeled     = true,
                    afpmaPrse      = true,
                    singleTested   = true,
                    door15aCatalog = false
                )
                FireboxAvailability.disabledTypeNames(onlyDoorDisabled) shouldBe Set("Door15aFirebox_Catalog")
            }
        }
    }

end FireboxAvailabilitySuite
