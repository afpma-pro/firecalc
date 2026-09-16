/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.config

import com.typesafe.config.ConfigFactory
import utest.*

/**
 * Configuration parsing for the remote calculation client (ticket 21).
 *
 * The payments module reaches the standalone calculation backend solely
 * through HTTP (GET /v1/kinds, POST /v1/calculate). The base URI must be
 * deployment-configurable so Docker deployments can point at the
 * `calc-backend` service without code changes.
 */
object CalculationClientConfigTest extends TestSuite {

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
        test("block absent defaults to the standalone backend on port 9000, enabled") {
            val cfg = envConfig("")
            val cc  = ConfigLoader.loadCalculationClientConfig(cfg)
            cc.baseUri ==> "http://localhost:9000/v1"
            cc.enabled ==> true
        }

        test("base-uri override is honored (Docker calc-backend service)") {
            val cfg = envConfig(
                """calculation-client {
                  |  base-uri = "http://calc-backend:9000/v1"
                  |}""".stripMargin
            )
            val cc  = ConfigLoader.loadCalculationClientConfig(cfg)
            cc.baseUri ==> "http://calc-backend:9000/v1"
            cc.enabled ==> true
        }

        test("enabled = false is honored") {
            val cfg = envConfig(
                """calculation-client {
                  |  base-uri = "http://calc-backend:9000/v1"
                  |  enabled = false
                  |}""".stripMargin
            )
            val cc  = ConfigLoader.loadCalculationClientConfig(cfg)
            cc.baseUri ==> "http://calc-backend:9000/v1"
            cc.enabled ==> false
        }

        test("empty block keeps the defaults") {
            val cfg = envConfig("calculation-client {}")
            val cc  = ConfigLoader.loadCalculationClientConfig(cfg)
            cc.baseUri ==> "http://localhost:9000/v1"
            cc.enabled ==> true
        }
    }
}
