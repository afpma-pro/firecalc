/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments

import java.time.ZoneId
import scala.collection.mutable.ListBuffer

import afpma.firecalc.payments.config.{
    AdminConfig,
    DatabaseConfig,
    InvoiceConfig,
    InvoiceRetryConfig,
    JwtConfig,
    PaymentsConfig
}
import cats.effect.IO
import org.typelevel.log4cats.Logger

/**
 * The pieces the invoice boot/guard tests all build the same way — one recording
 * Logger and one PaymentsConfig shape. Domain fixture recipes (mkOrder, customers,
 * counter rows) stay per-suite: they state what each test is about.
 */
object TestSupport:

    /** A Logger whose warn and info lines land in `sink`, in the order they were logged. */
    def recordingLogger(sink: ListBuffer[String]): Logger[IO] =
        new Logger[IO]:
            def error(message: => String): IO[Unit] = IO.unit
            def error(t      : Throwable)(message: => String): IO[Unit] = IO.unit
            def warn (message: => String): IO[Unit] = IO { sink += message; () }
            def warn (t      : Throwable)(message: => String): IO[Unit] = IO { sink += message; () }
            def info (message: => String): IO[Unit] = IO { sink += message; () }
            def info (t      : Throwable)(message: => String): IO[Unit] = IO { sink += message; () }
            def debug(message: => String): IO[Unit] = IO.unit
            def debug(t      : Throwable)(message: => String): IO[Unit] = IO.unit
            def trace(message: => String): IO[Unit] = IO.unit
            def trace(t      : Throwable)(message: => String): IO[Unit] = IO.unit

    /**
     * The one PaymentsConfig the invoice boot/guard tests share — fixed test identity
     * (Paris dates, no retries, draft reports off, 32-char JWT), everything a fixture
     * varies is a parameter. `invoiceCounterStart` is a Long like every counter fact
     * on the boot path.
     */
    def paymentsConfig(
        environment           : String = "test",
        productCatalog        : String = "development",
        prefix                : String = "TEST-",
        digits                : Int    = 3,
        counterStart          : Long   = 1L,
        dbFilename            : String = "test.db",
        dbPath                : String = "/tmp/firecalc-invoice-test.db"
    ): PaymentsConfig =
        PaymentsConfig(
            environment         = environment,
            productCatalog      = productCatalog,
            invoiceNumberPrefix = prefix,
            invoiceNumberDigits = digits,
            invoiceCounterStart = counterStart,
            invoiceTimezone     = ZoneId.of("Europe/Paris"),
            retryConfig         = InvoiceRetryConfig(maxRetries = 0, baseDelayMs = 1, maxDelayMs = 10),
            databaseConfig      = DatabaseConfig(filename = dbFilename, path = dbPath),
            invoiceConfig       = InvoiceConfig(configFilePath = "invoice-config.yaml", enabled = true),
            adminConfig         = AdminConfig(email = "admin@example.com"),
            reportAsDraft       = false,
            jwtConfig           = JwtConfig(secret = "x" * 32)
        )
