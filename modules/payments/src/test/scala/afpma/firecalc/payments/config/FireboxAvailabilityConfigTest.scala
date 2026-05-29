/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.config

import afpma.firecalc.domain.FireboxAvailability
import com.typesafe.config.ConfigFactory
import utest.*

object FireboxAvailabilityConfigTest extends TestSuite {

    private def envConfig(innerHocon: String) =
        val full = s"""payments {
                  |  environment = "development"
                  |  development {
                  |    product-catalog = "development"
                  |    database { filename = "test.db", path = "/tmp/test.db" }
                  |    invoice { number-prefix = "FC", number-digits = 4, counter-starting-number = 1 }
                  |    invoice-generation { config-file-path = "test.yaml" }
                  |    retry { max-retries = 3, base-delay-ms = 1000, max-delay-ms = 30000 }
                  |    admin { email = "admin@test.com" }
                  |    jwt { secret = "this-is-a-long-enough-secret-for-hmac-256!", expiration-minutes = 60 }
                  |    report-as-draft = true
                  |    $innerHocon
                  |  }
                  |}""".stripMargin
        ConfigFactory.parseString(full).resolve().getConfig("payments.development")

    val tests = Tests {
        test("full block with mixed values") {
            val cfg = envConfig(
                """firebox-availability {
          |  traditional = true
          |  ecolabeled = false
          |  afpma-prse = true
          |  single-tested = false
          |  door15a-catalog = true
          |}""".stripMargin
            )
            val fa  = ConfigLoader.loadFireboxAvailability(cfg)
            fa.traditional ==> true
            fa.ecolabeled ==> false
            fa.afpmaPrse ==> true
            fa.singleTested ==> false
            fa.door15aCatalog ==> true
        }

        test("block present but some keys missing defaults to true") {
            val cfg = envConfig(
                """firebox-availability {
          |  traditional = true
          |  ecolabeled = false
          |}""".stripMargin
            )
            val fa  = ConfigLoader.loadFireboxAvailability(cfg)
            fa.traditional ==> true
            fa.ecolabeled ==> false
            fa.afpmaPrse ==> true
            fa.singleTested ==> true
            fa.door15aCatalog ==> true
        }

        test("block entirely absent defaults to AllEnabled") {
            val cfg = envConfig("")
            val fa  = ConfigLoader.loadFireboxAvailability(cfg)
            fa ==> FireboxAvailability.AllEnabled
        }

        test("non-boolean value raises clear error") {
            val cfg    = envConfig(
                """firebox-availability {
          |  traditional = true
          |  ecolabeled = "not-a-boolean"
          |}""".stripMargin
            )
            val result =
                try {
                    ConfigLoader.loadFireboxAvailability(cfg)
                    None
                } catch {
                    case e: IllegalArgumentException => Some(e)
                }
            assert(result.isDefined)
            assert(result.get.getMessage.contains("firebox-availability.ecolabeled"))
        }

        test("non-boolean numeric value raises clear error") {
            val cfg    = envConfig(
                """firebox-availability {
          |  traditional = 42
          |}""".stripMargin
            )
            val result =
                try {
                    ConfigLoader.loadFireboxAvailability(cfg)
                    None
                } catch {
                    case e: IllegalArgumentException => Some(e)
                }
            assert(result.isDefined)
            assert(result.get.getMessage.contains("firebox-availability.traditional"))
        }
    }
}
