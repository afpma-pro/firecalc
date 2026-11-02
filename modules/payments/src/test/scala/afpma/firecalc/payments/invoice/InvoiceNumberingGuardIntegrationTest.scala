/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.nio.file.Path
import java.time.LocalDate

import scala.collection.mutable.ListBuffer

import afpma.firecalc.payments.{TestDatabaseSetup, TestSupport}
import afpma.firecalc.payments.config.PaymentsConfig
import afpma.firecalc.payments.domain.{Currency, OrderStatus}
import afpma.firecalc.payments.invoice.InvoiceNumberingGuard.Finding
import afpma.firecalc.payments.repository.impl.MoleculeDatabaseInspection
import afpma.firecalc.payments.repository.impl.MoleculeOrderRepository
import afpma.firecalc.payments.repository.{CustomerRepository, InvoiceCounterRepository, OrderRepository}
import afpma.firecalc.payments.service.InvoiceNumberService
import afpma.firecalc.payments.shared.api.{BackendCompatibleLanguage, CustomerInfo, CustomerType}

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*

import molecule.db.common.spi.Conn
import org.typelevel.log4cats.Logger
import utest.*

/**
 * Integration tests for the boot guard at the repository layer: a real
 * SQLite file in a temp directory, the real InvoiceNumberServiceImpl, the guard called
 * at the two real startup points. The cases prove the ORDER of the points — clause 1–7
 * findings refuse BEFORE the retroactive pass could paper over a rewound counter, and
 * clause 8 is judged only AFTER the machinery that legitimately numbers Confirmed rows.
 *
 * The bounded repair cases: a configured `invoice.counter-start` that
 * differs from an existing counter is verified before any write — only the highest
 * issued number passes (and is then raised and logged); below it the attempted state
 * presents the rewind finding, above it the hole finding, and in every refused case
 * the row is read back UNCHANGED. A refusal is never independently fatal: what decides
 * the start is whether the ACTUAL state passes guard point 1. A self-contradicting
 * file gets the fatal block carrying the refused-value framing; a file that is
 * consistent at its own counter boots past guard point 1, and the unapplied config
 * value surfaces in one explicit warning line — never silently, never fatally.
 */
object InvoiceNumberingGuardIntegrationTest extends TestSuite with TestDatabaseSetup:

    private val today = LocalDate.of(2026, 11, 2)

    private def config(dbFile: Path, counterStart: Long = 1L): PaymentsConfig =
        TestSupport.paymentsConfig(counterStart = counterStart, dbPath = dbFile.toString)

    private def withEnv(
        counterStart: Long = 1L
    )(
        test: (
            InvoiceCounterRepository[IO],
            OrderRepository[IO],
            CustomerRepository[IO],
            InvoiceNumberService[IO],
            String => IO[Unit],
            Path,
            ListBuffer[String],
            InvoiceNumberingSource[IO]
        ) => IO[Unit]
    ): Unit =
        tempDatabaseDirectory("invoice-guard-test")
            .use { dir =>
                val dbFile = dir.resolve("test.db")
                testFileConnectionResource(dbFile)
                    .use { conn =>
                        given Conn       = conn
                        val counterRepo  =
                            new afpma.firecalc.payments.repository.impl.MoleculeInvoiceCounterRepository[IO]()
                        val orderRepo    = new MoleculeOrderRepository[IO]()
                        val customerRepo = new afpma.firecalc.payments.repository.impl.MoleculeCustomerRepository[IO]()
                        val service      = InvoiceNumberService
                            .create[IO](config(dbFile, counterStart), counterRepo, orderRepo)
                            .unsafeRunSync()
                        // The read API is InvoiceNumberingSource; the companion read is private.
                        val source       = InvoiceNumberingSource[IO](
                            counterRepo,
                            orderRepo,
                            new MoleculeDatabaseInspection[IO](),
                            dbFile.toString
                        )
                        val exec         = (sql: String) => IO(molecule.db.sqlite.sync.rawTransact(sql)).void
                        test(
                            counterRepo,
                            orderRepo,
                            customerRepo,
                            service,
                            exec,
                            dbFile,
                            ListBuffer.empty[String],
                            source
                        )
                    }
            }
            .unsafeRunSync()

    /** Mint `count` paid-out orders, leaving the counter exactly at `count`. */
    private def issuedOrders(
        counterRepo: InvoiceCounterRepository[IO],
        orderRepo  : OrderRepository[IO]
    )(cust: afpma.firecalc.payments.domain.Customer, count: Int): IO[Unit] =
        (1 to count).toList.traverse_ { _ =>
            for
                o  <- mkOrder(orderRepo, cust)
                nv <- counterRepo.getNextInvoiceNumber()
                _  <- orderRepo.updateInvoiceNumber(o.id, InvoiceNumberValidator.formatInvoiceNumber("TEST-", nv, 3))
                _  <- orderRepo.updateStatus(o.id, OrderStatus.PaidOut)
            yield ()
        }

    private def refusedOf(outcome: InvoiceCounterRepair.Outcome): InvoiceCounterRepair.Outcome.Refused =
        outcome match
            case refused: InvoiceCounterRepair.Outcome.Refused => refused
            case other =>
                throw new java.lang.AssertionError(s"expected a refused repair, got $other")

    private def customer(customerRepo: CustomerRepository[IO], email: String) =
        customerRepo.create(
            CustomerInfo       (
                email        = email,
                customerType = CustomerType.Individual,
                language     = BackendCompatibleLanguage.English,
                givenName    = Some("Jean"),
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

    private def mkOrder(orderRepo: OrderRepository[IO], customer: afpma.firecalc.payments.domain.Customer) =
        orderRepo.create(
            customer.id,
            afpma.firecalc.payments.shared.api.ProductId(java.util.UUID.randomUUID()),
            BigDecimal(59.0),
            Currency.EUR,
            BackendCompatibleLanguage.English,
            None
        )

    val tests = Tests {

        test("rewound fixture with a Confirmed order to number: point 1 refuses before the pass runs") {
            withEnv() { (counterRepo, orderRepo, customerRepo, service, exec, dbFile, _, source) =>
                for
                    cust  <- customer(customerRepo, "rewound@example.com")
                    _     <- counterRepo.initializeCounter(1L)
                    _     <- (1 to 6).toList.traverse_ { _ =>
                        for
                            o <- mkOrder(orderRepo, cust)
                            n <- counterRepo.getNextInvoiceNumber()
                            _ <- orderRepo.updateInvoiceNumber(
                                o.id,
                                InvoiceNumberValidator.formatInvoiceNumber("TEST-", n, 3)
                            )
                            _ <- orderRepo.updateStatus(o.id, OrderStatus.PaidOut)
                        yield ()
                    }
                    // the actual staging incident: a copy rewound to a counter of 1…
                    _     <- exec("UPDATE InvoiceCounter SET currentNumber = 1")
                    // …and, on top, one Confirmed order the retroactive pass WOULD number.
                    owed  <- mkOrder(orderRepo, cust)
                    _     <- orderRepo.updateStatus(owed.id, OrderStatus.Confirmed)
                    state <- source.read
                    findings = InvoiceNumberingGuard.checkBeforeNumbering(state, config(dbFile), today)
                    // The pass would have taken this order — and minted a duplicate.
                    wouldNumber <- orderRepo.findConfirmedOrdersWithoutInvoiceNumber()
                    // Guard point 1 refuses; the pass never runs.
                    after       <- source.read
                yield
                    assert(wouldNumber.map(_.id).contains(owed.id))
                    assert(
                        findings.contains(Finding.CounterBelowHighestIssued(1L, 6L))
                    ) // clause 2: the rewound counter is a contradiction, not a re-run invitation
                    assert(
                        findings.exists {
                            case Finding.NextMintCollision("TEST-002", _) => true
                            case _                                        => false
                        }
                    ) // clause 7: the very next mint would repeat TEST-002
                    assert(findings.forall(_.clause != "8")               ) // clause 8 is not visible at point 1
                    assert(after.orders.flatMap(_.invoiceNumber).size == 6) // the refusal itself minted nothing
            }
        }

        test(
            "fresh database with one Confirmed unnumbered order: point 1 passes, the pass numbers it, point 2 passes"
        ) {
            withEnv() { (counterRepo, orderRepo, customerRepo, service, _, dbFile, _, source) =>
                for
                    cust  <- customer(customerRepo, "confirmed-fresh@example.com")
                    _     <- counterRepo.initializeCounter(1L)
                    owed  <- mkOrder(orderRepo, cust)
                    _     <- orderRepo.updateStatus(owed.id, OrderStatus.Confirmed)
                    state <- source.read
                    findings = InvoiceNumberingGuard.checkBeforeNumbering(state, config(dbFile), today)
                    generated <- service.generateInvoiceNumbersRetroactively()
                    after     <- source.read
                    findings2 = InvoiceNumberingGuard.checkAfterNumbering(after)
                    findings3 = InvoiceNumberingGuard.checkBeforeNumbering(after, config(dbFile), today)
                yield
                    assert(findings.isEmpty                                                                    ) // clause 8 could not be here: the pass exists to fix exactly this
                    assert(generated == 1                                                                      )
                    assert(after.counterCurrent == Some(1L)                                                    )
                    assert(after.orders.find(_.orderId == owed.id).flatMap(_.invoiceNumber) == Some("TEST-001"))
                    assert(findings2.isEmpty                                                                   )
                    assert(findings3.isEmpty                                                                   )
            }
        }

        test(
            "point 2 snapshot where the pass could not number a Confirmed order: refusal names it and says the repair failed"
        ) {
            withEnv() { (counterRepo, orderRepo, customerRepo, service, exec, dbFile, _, source) =>
                for
                    cust  <- customer(customerRepo, "unnumberable@example.com")
                    _     <- counterRepo.initializeCounter(1L)
                    owed  <- mkOrder(orderRepo, cust)
                    _     <- orderRepo.updateStatus(owed.id, OrderStatus.Confirmed)
                    state <- source.read
                    findings = InvoiceNumberingGuard.checkBeforeNumbering(state, config(dbFile), today)
                    // something breaks the counter during the startup window:
                    _         <- exec("DELETE FROM InvoiceCounter")
                    generated <- service.generateInvoiceNumbersRetroactively()
                    after     <- source.read
                    findings2 = InvoiceNumberingGuard.checkAfterNumbering(after)
                yield
                    assert(findings.isEmpty                      ) // clean at point 1
                    assert(generated == 0                        ) // the pass could not number the Confirmed order
                    assert(
                        findings2.contains(
                            Finding.UnnumberedOwedOrder(
                                after.orders.find(_.orderId == owed.id).get
                            )
                        )
                    )
                    val block =
                        InvoiceNumberingReport.renderFatalBlock(
                            after,
                            config                 (dbFile                ),
                            findings2,
                            today,
                            java.time.Instant.parse("2026-11-02T12:00:00Z")
                        )
                    assert(block.contains(owed.id.value.toString)) // names the order
                    assert(block.contains("startup repair")      )
                    assert(block.contains("attempted and failed"))
            }
        }

        // The bounded repair. The config value is verified against the numbers
        // this database holds BEFORE anything is written; the only value that can pass is
        // the highest already issued; a refused value leaves the row byte-for-byte unchanged.

        test(
            "bounded repair: the highest issued value raises the rewound counter, logs the raise, boots past guard point 1"
        ) {
            withEnv(6L) { (counterRepo, orderRepo, customerRepo, _, exec, dbFile, logs, source) =>
                given Logger[IO] = TestSupport.recordingLogger(logs)
                for
                    cust  <- customer(customerRepo, "repair-raise@example.com")
                    _     <- counterRepo.initializeCounter(1L)
                    _     <- issuedOrders(counterRepo, orderRepo)(cust, 6)
                    _     <- exec("UPDATE InvoiceCounter SET currentNumber = 1") // the staging-rewind shape
                    state <- source.read
                    cfg = config(dbFile, 6L)
                    outcome <- InvoiceCounterRepair.attempt[IO](counterRepo, state, cfg, today)
                    after   <- source.read
                    findings = InvoiceNumberingGuard.checkBeforeNumbering(after, cfg, today)
                yield
                    assert(outcome == InvoiceCounterRepair.Outcome.Raised(1L, 6L)  )
                    assert(after.counterCurrent == Some(6L)                        ) // the row now holds the verified value
                    assert(after.counterStartingNumber == Some(1L)                 ) // the anchor was never rewritten
                    assert(findings.isEmpty                                        ) // boots past guard point 1
                    // the boot log records previous value, new value, and the configuration origin
                    assert(logs.contains(InvoiceCounterRepair.raiseLogLine(1L, 6L)))
                    assert(logs.exists(_.contains("from 1 to 6"))                  )
                    assert(
                        logs.exists(l => l.toLowerCase.contains("configuration") && l.contains("invoice.counter-start"))
                    )
            }
        }

        test("bounded repair refused below the highest: presents the rewind finding, the row is not written") {
            withEnv(4L) { (counterRepo, orderRepo, customerRepo, _, exec, dbFile, _, source) =>
                for
                    cust  <- customer(customerRepo, "repair-below@example.com")
                    _     <- counterRepo.initializeCounter(1L)
                    _     <- issuedOrders(counterRepo, orderRepo)(cust, 6)
                    _     <- exec("UPDATE InvoiceCounter SET currentNumber = 1") // the staging-rewind shape
                    state <- source.read
                    cfg = config(dbFile, 4L)
                    outcome <- InvoiceCounterRepair.attempt[IO](counterRepo, state, cfg, today)
                    after   <- source.read
                    // what makes the start fatal is the ACTUAL state at guard point 1 — this
                    // file contradicts itself (counter below the issued numbers), the refusal
                    // only frames the block.
                    findingsOnActual = InvoiceNumberingGuard.checkBeforeNumbering(after, cfg, today)
                yield
                    val refused = refusedOf(outcome)
                    assert(refused.requested == 4L                                             )
                    assert(refused.databaseHolds == 1L                                         )
                    assert(findingsOnActual.nonEmpty                                           ) // fatal because the FILE is inconsistent
                    // a value BELOW the highest issued presents as the rewind finding —
                    // evaluated on the attempted (raised) state, not the untouched one
                    assert(refused.findings.contains(Finding.CounterBelowHighestIssued(4L, 6L)))
                    assert(after.counterCurrent == Some(1L)                                    ) // verify-before-write: the row was NEVER written
                    val block =
                        InvoiceNumberingReport.renderFatalBlock(
                            refused.attemptedState,
                            cfg,
                            refused.findings,
                            today,
                            java.time.Instant.parse("2026-11-02T12:00:00Z"),
                            Some(InvoiceNumberingReport.RefusedRepair(4L, 1L))
                        )
                    assert(block.contains("[2]")) // presented as below-highest
                    assert(block.contains("counter-start = 4")             ) // config asked for 4…
                    assert(block.contains("the database still holds 1")    ) // …DB still holds 1…
                    assert(block.contains("nothing was written")           ) // …nothing was written
                    assert(block.contains("set invoice.counter-start to 6")) // the one bootable value
            }
        }

        test(
            "consistent database with a stale config above the counter: repair refused, the boot proceeds with the warning, the row is unchanged"
        ) {
            withEnv(8L) { (counterRepo, orderRepo, customerRepo, _, _, dbFile, _, source) =>
                for
                    cust  <- customer(customerRepo, "stale-above@example.com")
                    _     <- counterRepo.initializeCounter(1L)
                    _     <- issuedOrders(counterRepo, orderRepo)(cust, 6) // consistent database: counter 6, issued 1..6
                    state <- source.read
                    cfg = config(dbFile, 8L)
                    outcome <- InvoiceCounterRepair.attempt[IO](counterRepo, state, cfg, today)
                    after   <- source.read
                    // guard point 1 judges the ACTUAL state — the file is consistent at 6, so
                    // the start boots; the refusal itself is not fatal.
                    findingsOnActual = InvoiceNumberingGuard.checkBeforeNumbering(after, cfg, today)
                yield
                    val refused = refusedOf(outcome)
                    assert(refused.requested == 8L         )
                    assert(refused.databaseHolds == 6L     )
                    assert(findingsOnActual.isEmpty        ) // boots past guard point 1
                    assert(after.counterCurrent == Some(6L)) // verify-before-write: the row was NEVER written
                    // the attempted value still fails as the holes finding — it just no longer decides the start
                    assert(refused.findings.exists {
                        case Finding.SequenceHoles(8L, List(7L, 8L), false, _) => true
                        case _                                                 => false
                    })
                    // not silent: the boot prints one explicit warning naming both values and
                    // why the config value was not applied (the outcome is the same data the
                    // boot logs from)
                    val warning = InvoiceCounterRepair.notAppliedWarningLine(refused.requested, refused.databaseHolds)
                    assert(warning.contains("invoice.counter-start = 8")) // names the config value
                    assert(warning.contains("counter at 6")             ) // names the database's counter
                    assert(warning.contains("NOT applied")              ) // and that it was not applied
            }
        }

        test(
            "consistent database with a stale config below the counter: repair refused, the boot proceeds with the warning, the row is unchanged"
        ) {
            withEnv(1L) { (counterRepo, orderRepo, customerRepo, _, _, dbFile, _, source) =>
                for
                    cust  <- customer(customerRepo, "stale-below@example.com")
                    _     <- counterRepo.initializeCounter(1L)
                    _     <- issuedOrders(counterRepo, orderRepo)(cust, 6) // consistent database: counter 6, issued 1..6
                    state <- source.read
                    cfg = config(dbFile, 1L) // the template value left behind by a running database
                    outcome <- InvoiceCounterRepair.attempt[IO](counterRepo, state, cfg, today)
                    after   <- source.read
                    findingsOnActual = InvoiceNumberingGuard.checkBeforeNumbering(after, cfg, today)
                yield
                    val refused = refusedOf(outcome)
                    assert(refused.requested == 1L         )
                    assert(refused.databaseHolds == 6L     )
                    assert(findingsOnActual.isEmpty        ) // boots past guard point 1
                    assert(after.counterCurrent == Some(6L)) // the row keeps its value
                    val warning = InvoiceCounterRepair.notAppliedWarningLine(refused.requested, refused.databaseHolds)
                    assert(warning.contains("invoice.counter-start = 1")) // names the config value
                    assert(warning.contains("counter at 6")             ) // names the database's counter
                    assert(warning.contains("NOT applied")              ) // and that it was not applied
            }
        }

        test(
            "refused config above the highest on a rewound file: fatal, the block carries the holes finding of the attempted value"
        ) {
            withEnv(8L) { (counterRepo, orderRepo, customerRepo, _, exec, dbFile, _, source) =>
                for
                    cust  <- customer(customerRepo, "repair-rewound-above@example.com")
                    _     <- counterRepo.initializeCounter(1L)
                    _     <- issuedOrders(counterRepo, orderRepo)(cust, 6)
                    _     <- exec("UPDATE InvoiceCounter SET currentNumber = 1") // the staging-rewind shape
                    state <- source.read
                    cfg = config(dbFile, 8L)
                    outcome <- InvoiceCounterRepair.attempt[IO](counterRepo, state, cfg, today)
                    after   <- source.read
                    // the file contradicts itself (rewound counter): that, not the refusal, is fatal
                    findingsOnActual = InvoiceNumberingGuard.checkBeforeNumbering(after, cfg, today)
                yield
                    val refused = refusedOf(outcome)
                    assert(refused.requested == 8L         )
                    assert(refused.databaseHolds == 1L     )
                    assert(findingsOnActual.nonEmpty       ) // fatal because the FILE is inconsistent
                    // a value ABOVE the highest issued presents as the holes finding on the
                    // attempted (raised) state
                    assert(refused.findings.exists {
                        case Finding.SequenceHoles(8L, List(7L, 8L), false, _) => true
                        case _                                                 => false
                    })
                    assert(after.counterCurrent == Some(1L)) // verify-before-write: the row was NEVER written
                    val block =
                        InvoiceNumberingReport.renderFatalBlock(
                            refused.attemptedState,
                            cfg,
                            refused.findings,
                            today,
                            java.time.Instant.parse("2026-11-02T12:00:00Z"),
                            Some(InvoiceNumberingReport.RefusedRepair(8L, 1L))
                        )
                    assert(block.contains("[3a]")) // presented as holes 7..8
                    assert(block.contains("sequences 7, 8")            )
                    assert(block.contains("counter-start = 8")         ) // config asked for 8…
                    assert(block.contains("the database still holds 1")) // …DB still holds 1…
                    assert(block.contains("nothing was written")       ) // …nothing was written
            }
        }
    }
