/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.config

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import utest.*

object CorsValidationTest extends TestSuite {

    // Create minimal PaymentsConfig instances for testing
    // PaymentsConfig requires these fields:
    // environment, productCatalog, invoiceNumberPrefix, invoiceNumberDigits (Int),
    // invoiceCounterStartingNumber (Int >=1), invoiceTimezone (ZoneId), retryConfig, databaseConfig,
    // invoiceConfig, adminConfig, reportAsDraft (Boolean), jwtConfig,
    // loggingConfig (has default), corsAllowedOrigins (has default)

    // productCatalog must be "development", "staging", or "production"

    private def mkConfig(environment: String, productCatalog: String, corsOrigins: List[String]) =
        PaymentsConfig                 (
            environment                  = environment,
            productCatalog               = productCatalog,
            invoiceNumberPrefix          = "FC",
            invoiceNumberDigits          = 6,
            invoiceCounterStartingNumber = 1,
            invoiceTimezone              = java.time.ZoneId.of("Europe/Paris"),
            retryConfig                  = InvoiceRetryConfig(),
            databaseConfig               = DatabaseConfig(filename = "test.db", path = "/tmp/test.db"),
            invoiceConfig                = InvoiceConfig(configFilePath = "test.yaml"),
            adminConfig                  = AdminConfig(email = "admin@test.com"),
            reportAsDraft                = true,
            jwtConfig                    = JwtConfig(secret = "test-secret-that-is-long-enough-for-hmac-256", expirationMinutes = 60),
            corsAllowedOrigins           = corsOrigins
        )

    val tests = Tests {
        test("staging with wildcard CORS crashes on startup") {
            val config = mkConfig("staging", "staging", List("*"))
            val ex     = scala.util.Try(ConfigLoader.validatePaymentsConfig[IO](config).unsafeRunSync())
            assert(ex.isFailure                                     )
            assert(ex.failed.get.isInstanceOf[IllegalStateException])
        }

        test("production with wildcard CORS crashes on startup") {
            val config = mkConfig("production", "production", List("*"))
            val ex     = scala.util.Try(ConfigLoader.validatePaymentsConfig[IO](config).unsafeRunSync())
            assert(ex.isFailure                                     )
            assert(ex.failed.get.isInstanceOf[IllegalStateException])
        }

        test("production with empty CORS crashes on startup") {
            val config = mkConfig("production", "production", List.empty)
            val ex     = scala.util.Try(ConfigLoader.validatePaymentsConfig[IO](config).unsafeRunSync())
            assert(ex.isFailure                                     )
            assert(ex.failed.get.isInstanceOf[IllegalStateException])
        }

        test("development with wildcard CORS passes") {
            val config = mkConfig("development", "development", List("*"))
            val result = ConfigLoader.validatePaymentsConfig[IO](config).unsafeRunSync()
            assert(result == config)
        }

        test("staging with explicit origin passes") {
            val config = mkConfig("staging", "staging", List("https://firecalc.afpma.fr"))
            val result = ConfigLoader.validatePaymentsConfig[IO](config).unsafeRunSync()
            assert(result == config)
        }

        test("development with explicit origin passes") {
            val config = mkConfig("development", "development", List("https://localhost:5173"))
            val result = ConfigLoader.validatePaymentsConfig[IO](config).unsafeRunSync()
            assert(result == config)
        }
    }
}
