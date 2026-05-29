/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.config

import afpma.firecalc.domain.FireboxAvailability

import utest.*

object FireboxAvailabilityConfigSuite extends TestSuite:

    val tests = Tests {

        test("parseBool — explicit \"true\" yields true") {
            assert(UIConfig.parseBool("key", "true"))
        }

        test("parseBool — explicit \"false\" yields false") {
            assert(!UIConfig.parseBool("key", "false"))
        }

        test("parseBool — empty string yields true") {
            assert(UIConfig.parseBool("key", ""))
        }

        test("parseBool — garbage string yields true") {
            assert(UIConfig.parseBool("key", "garbage"))
            assert(UIConfig.parseBool("key", "maybe")  )
            assert(UIConfig.parseBool("key", "0")      )
            assert(UIConfig.parseBool("key", "FALSE")  )
        }

        test("FireboxAvailability — all \"true\" builds AllEnabled") {
            val fa = FireboxAvailability(
                traditional    = UIConfig.parseBool("key", "true"),
                ecolabeled     = UIConfig.parseBool("key", "true"),
                afpmaPrse      = UIConfig.parseBool("key", "true"),
                singleTested   = UIConfig.parseBool("key", "true"),
                door15aCatalog = UIConfig.parseBool("key", "true")
            )
            assert(fa == FireboxAvailability.AllEnabled)
        }

        test("FireboxAvailability — all \"false\" builds all disabled") {
            val fa       = FireboxAvailability(
                traditional    = UIConfig.parseBool("key", "false"),
                ecolabeled     = UIConfig.parseBool("key", "false"),
                afpmaPrse      = UIConfig.parseBool("key", "false"),
                singleTested   = UIConfig.parseBool("key", "false"),
                door15aCatalog = UIConfig.parseBool("key", "false")
            )
            val disabled = FireboxAvailability.disabledTypeNames(fa)
            assert(disabled == Set("Traditional", "Ecolabeled", "AFPMA_PRSE", "SingleTested", "Door15aFirebox_Catalog"))
        }

        test("parseBool — unset/null equivalent (empty string) yields true") {
            assert(UIConfig.parseBool("key", ""))
        }

        test("parseBool — null yields true") {
            assert(UIConfig.parseBool("key", null))
        }
    }
