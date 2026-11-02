/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.nio.file.Path

import afpma.firecalc.payments.TestDatabaseSetup
import afpma.firecalc.payments.domain.{Currency, OrderStatus}
import afpma.firecalc.payments.repository.CounterRow
import afpma.firecalc.payments.repository.CustomerRepository
import afpma.firecalc.payments.repository.InvoiceCounterRepository
import afpma.firecalc.payments.repository.OrderFact
import afpma.firecalc.payments.repository.OrderRepository
import afpma.firecalc.payments.repository.impl.MoleculeCustomerRepository
import afpma.firecalc.payments.repository.impl.MoleculeDatabaseInspection
import afpma.firecalc.payments.repository.impl.MoleculeInvoiceCounterRepository
import afpma.firecalc.payments.repository.impl.MoleculeOrderRepository
import afpma.firecalc.payments.shared.api.{BackendCompatibleLanguage, CustomerInfo, CustomerType}

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import molecule.db.common.spi.Conn
import utest.*

/**
 * Tests for the boot-time invoice-numbering read path.
 * Databases are built through the repository layer in a temporary directory, mirroring
 * the TestDatabaseSetup / InvoiceCounterRepositoryTest recipe but on a file so the
 * resolved absolute path and journal mode are observable.
 */
object InvoiceNumberingStateTest extends TestSuite with TestDatabaseSetup:

    private def withRepos(
        test: (
            InvoiceCounterRepository[IO],
            OrderRepository[IO],
            CustomerRepository[IO],
            String => IO[Unit],
            Path,
            InvoiceNumberingSource[IO]
        ) => IO[Unit]
    ): Unit =
        tempDatabaseDirectory("invoice-numbering-test")
            .use { dir =>
                val dbFile = dir.resolve("test.db")
                testFileConnectionResource(dbFile)
                    .use { conn =>
                        given Conn       = conn
                        val counterRepo  = new MoleculeInvoiceCounterRepository[IO]()
                        val orderRepo    = new MoleculeOrderRepository[IO]()
                        val customerRepo = new MoleculeCustomerRepository[IO]()
                        // The read API is InvoiceNumberingSource; the companion read is private.
                        val source       = InvoiceNumberingSource[IO](
                            counterRepo,
                            orderRepo,
                            new MoleculeDatabaseInspection[IO](),
                            dbFile.toString
                        )
                        // Raw DML for states the repository layer cannot create (null counter,
                        // a second counter row).
                        val exec         = (sql: String) => IO(molecule.db.sqlite.sync.rawTransact(sql)).void
                        test(counterRepo, orderRepo, customerRepo, exec, dbFile, source)
                    }
            }
            .unsafeRunSync()

    val tests = Tests {

        test("fresh database: no orders, counter just seeded") {
            withRepos { (counterRepo, orderRepo, _, _, dbFile, source) =>
                for
                    _     <- counterRepo.initializeCounter(1L)
                    state <- source.read
                yield
                    assert(state.dbPath == dbFile.toAbsolutePath.normalize.toString)
                    assert(state.journalMode == "wal"                              )
                    assert(state.counterRowCount == 1                              )
                    assert(state.counterCurrent == Some(0L)                        )
                    assert(state.counterStartingNumber == Some(1L)                 )
                    assert(state.orders.isEmpty                                    )
                    assert(state.mostRecentActivity.isDefined                      )
            }
        }

        test("issued numbers: counter, highest issued from trailing digits, exact per-status counts") {
            withRepos { (counterRepo, orderRepo, customerRepo, _, dbFile, source) =>
                for
                    customer <- customerRepo.create(
                        CustomerInfo       (
                            email        = "alice.dupont@example.com",
                            customerType = CustomerType.Individual,
                            language     = BackendCompatibleLanguage.English,
                            givenName    = Some("Alice"),
                            familyName   = Some("Dupont"),
                            companyName  = None,
                            addressLine1 = None,
                            addressLine2 = None,
                            addressLine3 = None,
                            city         = None,
                            region       = None,
                            postalCode   = None,
                            countryCode  = None,
                            phoneNumber  = None
                        )
                    )
                    productId = afpma.firecalc.payments.shared.api.ProductId(java.util.UUID.randomUUID())
                    orderA   <- orderRepo.create(
                        customer.id,
                        productId,
                        BigDecimal(59.0),
                        Currency.EUR,
                        BackendCompatibleLanguage.English,
                        None
                    )
                    orderB   <- orderRepo.create(
                        customer.id,
                        productId,
                        BigDecimal(59.0),
                        Currency.EUR,
                        BackendCompatibleLanguage.English,
                        None
                    )
                    orderC   <- orderRepo.create(
                        customer.id,
                        productId,
                        BigDecimal(59.0),
                        Currency.EUR,
                        BackendCompatibleLanguage.English,
                        None
                    )
                    orderD   <- orderRepo.create(
                        customer.id,
                        productId,
                        BigDecimal(59.0),
                        Currency.EUR,
                        BackendCompatibleLanguage.English,
                        None
                    )
                    orderE   <- orderRepo.create(
                        customer.id,
                        productId,
                        BigDecimal(59.0),
                        Currency.EUR,
                        BackendCompatibleLanguage.English,
                        None
                    )
                    _        <- counterRepo.initializeCounter(1L)
                    n1       <- counterRepo.getNextInvoiceNumber()
                    _        <- orderRepo.updateInvoiceNumber(
                        orderA.id,
                        InvoiceNumberValidator.formatInvoiceNumber("TEST-", n1, 3)
                    )
                    n2       <- counterRepo.getNextInvoiceNumber()
                    _        <- orderRepo.updateInvoiceNumber(
                        orderB.id,
                        InvoiceNumberValidator.formatInvoiceNumber("TEST-", n2, 3)
                    )
                    n3       <- counterRepo.getNextInvoiceNumber()
                    _        <- orderRepo.updateInvoiceNumber(orderE.id, "PREFIX-WIDE-%04d".format(n3 * 1000 + 321))
                    _        <- orderRepo.updateStatus(orderA.id, OrderStatus.Confirmed)
                    _        <- orderRepo.updateStatus(orderB.id, OrderStatus.Processing)
                    _        <- orderRepo.updateStatus(orderD.id, OrderStatus.Failed)
                    _        <- orderRepo.updateStatus(orderE.id, OrderStatus.PaidOut)
                    state    <- source.read
                yield
                    assert(n1 == 1L                                                                              )
                    assert(n2 == 2L                                                                              )
                    assert(n3 == 3L                                                                              )
                    assert(state.orders.size == 5                                                                )
                    assert(state.counterCurrent == Some(3L)                                                      )
                    // Trailing digit run, never a width-3 cut: "PREFIX-WIDE-3321" -> 3321, not 321.
                    assert(state.highestIssuedSequenceNumber == Some(3321L)                                      )
                    assert(state.numberedCount == 3                                                              )
                    assert(state.unnumberedCount == 2                                                            )
                    assert(
                        state.numberedByStatus == Map (
                            OrderStatus.Confirmed  -> 1,
                            OrderStatus.Processing -> 1,
                            OrderStatus.PaidOut    -> 1
                        )
                    )
                    assert(state.unnumberedByStatus == Map(OrderStatus.Pending -> 1, OrderStatus.Failed -> 1)    )
                    assert(state.orders.forall(_.customerEmail == "alice.dupont@example.com")                    )
                    assert(state.orders.find(_.orderId == orderA.id).flatMap(_.invoiceNumber) == Some("TEST-001"))
                    assert(state.orders.find(_.orderId == orderC.id).flatMap(_.invoiceNumber).isEmpty            )
                    assert(state.mostRecentActivity.isDefined                                                    )
            }
        }

        test("counter value null: read returns it as data, no throw") {
            withRepos { (counterRepo, orderRepo, _, exec, dbFile, source) =>
                for
                    _     <- counterRepo.initializeCounter(1L)
                    _     <- exec("UPDATE InvoiceCounter SET currentNumber = NULL")
                    _     <- counterRepo.getNextInvoiceNumber().attempt // today this is where the null first explodes
                    state <- source.read
                yield
                    assert(state.counterRowCount == 1             )
                    assert(state.counterCurrent == None           )
                    assert(state.counterStartingNumber == Some(1L))
            }
        }

        test("no counter row: read returns the state instead of throwing") {
            withRepos { (counterRepo, orderRepo, customerRepo, _, dbFile, source) =>
                for
                    _     <- customerRepo.create(
                        CustomerInfo       (
                            email        = "no-counter@example.com",
                            customerType = CustomerType.Individual,
                            language     = BackendCompatibleLanguage.English,
                            givenName    = None,
                            familyName   = None,
                            companyName  = None,
                            addressLine1 = None,
                            addressLine2 = None,
                            addressLine3 = None,
                            city         = None,
                            region       = None,
                            postalCode   = None,
                            countryCode  = None,
                            phoneNumber  = None
                        )
                    )
                    state <- source.read
                yield
                    assert(state.counterRowCount == 0         )
                    assert(state.counterRows.isEmpty          )
                    assert(state.counterCurrent == None       )
                    assert(state.counterStartingNumber == None)
            }
        }

        test("render block: exact parsed values for an issued-numbers database") {
            val t0     = java.time.Instant.parse("2026-01-01T00:00:00Z")
            val t1     = java.time.Instant.parse("2026-10-14T18:44:02Z")
            val order  = (n: Int, status: OrderStatus, number: Option[String]) =>
                OrderFact      (
                    orderId       = afpma.firecalc.payments.shared.api.OrderId(new java.util.UUID(n.toLong, 0L)),
                    status        = status,
                    invoiceNumber = number,
                    customerEmail = "jean.dupont@example.com",
                    createdAt     = t0,
                    updatedAt     = t1
                )
            val state  = InvoiceNumberingState(
                dbPath             = "/var/lib/firecalc/firecalc-payments-staging.db",
                journalMode        = "wal",
                counterRows        = List(CounterRow(1L, Some(6L), Some(4L))),
                orders             = List(
                    order(1, OrderStatus.Confirmed, Some("FCALC-STG-2026-0004")),
                    order(2, OrderStatus.PaidOut, Some("FCALC-STG-2026-0005")  ),
                    order(3, OrderStatus.PaidOut, Some("FCALC-STG-2026-0006")  ),
                    order(4, OrderStatus.Pending, None                         ),
                    order(5, OrderStatus.Pending, None                         ),
                    order(6, OrderStatus.Failed, None                          )
                ),
                mostRecentActivity = Some(t1)
            )
            val config = afpma.firecalc.payments.config.PaymentsConfig(
                environment         = "staging",
                productCatalog      = "staging",
                invoiceNumberPrefix = "FCALC-STG-[YYYY]-",
                invoiceNumberDigits = 4,
                invoiceCounterStart = 4,
                invoiceTimezone     = java.time.ZoneId.of("Europe/Paris"),
                retryConfig         = afpma.firecalc.payments.config.InvoiceRetryConfig(
                    maxRetries  = 2,
                    baseDelayMs = 10,
                    maxDelayMs  = 100
                ),
                databaseConfig      = afpma.firecalc.payments.config.DatabaseConfig(
                    filename = "firecalc-payments-staging.db",
                    path     = "/var/lib/firecalc/firecalc-payments-staging.db"
                ),
                invoiceConfig       = afpma.firecalc.payments.config.InvoiceConfig(
                    configFilePath = "invoice-config.yaml",
                    enabled        = true
                ),
                adminConfig         = afpma.firecalc.payments.config.AdminConfig(email = "admin@example.com"),
                reportAsDraft       = false,
                jwtConfig           = afpma.firecalc.payments.config.JwtConfig(secret = "x" * 32)
            )

            val block  = InvoiceNumberingReport.renderNumberingBlock(state, config)
            val fields = block.linesIterator.flatMap { line =>
                line.split(" : ", 2) match
                    case Array(k, v) => Some(k.trim -> v.trim)
                    case _           => None
            }.toMap

            assert(fields("db path") == "/var/lib/firecalc/firecalc-payments-staging.db")
            assert(fields("journal mode") == "wal"                                      )
            assert(fields("counter rows") == "1"                                        )
            assert(fields("counter current") == "6"                                     )
            assert(fields("counter start") == "4"                                       )
            assert(fields("highest issued") == "6"                                      )
            assert(fields("orders numbered") == "3"                                     )
            assert(fields("orders unnumbered") == "3"                                   )
            assert(fields("most recent activity") == "2026-10-14T18:44:02Z"             )
            assert(fields("configured prefix") == "FCALC-STG-[YYYY]-"                   )
            assert(fields("configured digits") == "4"                                   )
            // No unmasked customer identifier anywhere in the block.
            assert(!block.contains("jean.dupont")                                       )
            assert(!block.contains("jean")                                              )
            // Both breakdown lines are exact, every status named in enum order:
            assert(
                fields("numbered by status") ==
                    "Pending: 0, Processing: 0, Confirmed: 1, PaidOut: 2, Failed: 0, Cancelled: 0"
            )
            assert(
                fields("unnumbered by status") ==
                    "Pending: 2, Processing: 0, Confirmed: 0, PaidOut: 0, Failed: 1, Cancelled: 0"
            )
        }

        test("second counter row: read returns both rows, current/start ambiguous, no throw") {
            withRepos { (counterRepo, orderRepo, _, exec, dbFile, source) =>
                for
                    _     <- counterRepo.initializeCounter(1L)
                    _     <- exec(
                        "INSERT INTO InvoiceCounter (id, currentNumber, startingNumber, createdAt, updatedAt) " +
                            "VALUES (2, 5, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')"
                    )
                    state <- source.read
                yield
                    assert(state.counterRowCount == 2                 )
                    assert(state.counterRows.map(_.id) == List(1L, 2L))
                    assert(state.counterCurrent == None               )
                    assert(state.counterStartingNumber == None        )
            }
        }
    }
