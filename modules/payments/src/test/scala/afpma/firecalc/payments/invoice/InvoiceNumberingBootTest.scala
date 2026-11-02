/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.nio.file.Path
import java.time.{Instant, LocalDate}

import scala.collection.mutable.ListBuffer

import afpma.firecalc.payments.{TestDatabaseSetup, TestSupport}
import afpma.firecalc.payments.config.PaymentsConfig
import afpma.firecalc.payments.domain.{Currency, OrderStatus}
import afpma.firecalc.payments.repository.{CustomerRepository, InvoiceCounterRepository, OrderRepository}
import afpma.firecalc.payments.repository.impl.{
    MoleculeCustomerRepository,
    MoleculeDatabaseInspection,
    MoleculeInvoiceCounterRepository,
    MoleculeOrderRepository
}
import afpma.firecalc.payments.service.InvoiceNumberService
import afpma.firecalc.payments.shared.api.{BackendCompatibleLanguage, CustomerInfo, CustomerType, OrderId}

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*

import molecule.db.common.spi.Conn
import org.typelevel.log4cats.Logger
import utest.*

/**
 * In-process tests for the boot sequence itself — `InvoiceNumberingBoot.run` driven
 * directly, on a real SQLite file in a temp directory (the TestDatabaseSetup /
 * testFileConnectionResource recipe, with the fixture shapes of
 * InvoiceNumberingGuardIntegrationTest), with a recording logger and a counting stub
 * for the retroactive pass. The guard points and the renderers have their own tests;
 * the forked process pins the whole JVM. What is pinned HERE is exactly what only run
 * wires together, and it is asserted through observed order and effects, never
 * internals: the state block prints before any verdict; guard point 1 refuses BEFORE
 * the retroactive pass runs (a refused boot never asked the pass); a consistent file
 * with a refused config value logs the not-applied warning exactly once and the pass
 * DOES run; guard point 2 refuses only after the pass had its chance.
 */
object InvoiceNumberingBootTest extends TestSuite with TestDatabaseSetup:

    private val today = LocalDate.of(2026, 11, 2)
    private val now   = Instant.parse("2026-11-02T12:00:00Z")

    private def config(dbFile: Path, counterStart: Long): PaymentsConfig =
        TestSupport.paymentsConfig(counterStart = counterStart, dbPath = dbFile.toString)

    /** The pass counted, never run: `run` must ask for it exactly when the sequence says so. */
    private final class CountingPassService extends InvoiceNumberService[IO]:
        private var runs = 0
        def passCount                                            : Int        = runs
        def generateInvoiceNumber()                              : IO[String] =
            IO.raiseError(new java.lang.AssertionError("InvoiceNumberingBoot.run must not mint a single number"))
        def generateInvoiceNumberAndUpdateOrder(orderId: OrderId): IO[String] =
            IO.raiseError(new java.lang.AssertionError("InvoiceNumberingBoot.run must not mint a single number"))
        def generateInvoiceNumbersRetroactively(): IO[Int] = IO { runs += 1; 0 }

    /** Drives one boot with the recorded logger and the injected clock; no callback, no port. */
    private def runBoot(
        source : InvoiceNumberingSource[IO],
        service: InvoiceNumberService[IO],
        cfg    : PaymentsConfig,
        logs   : ListBuffer[String]
    ): (InvoiceNumberingBoot.Outcome, String) =
        given Logger[IO] = TestSupport.recordingLogger(logs)
        val blocks       = ListBuffer.empty[String]
        val outcome      = InvoiceNumberingBoot[IO](
            source,
            service,
            cfg,
            today,
            now,
            (block: String) => IO { blocks += block; () }
        ).run.unsafeRunSync()
        (outcome, blocks.mkString("\n"))

    private def withBoot(
        test: (
            InvoiceCounterRepository[IO],
            OrderRepository[IO],
            CustomerRepository[IO],
            InvoiceNumberingSource[IO],
            String => IO[Unit],
            Path
        ) => IO[Unit]
    ): Unit =
        tempDatabaseDirectory("invoice-boot-test")
            .use { dir =>
                val dbFile = dir.resolve("test.db")
                testFileConnectionResource(dbFile)
                    .use { conn =>
                        given Conn       = conn
                        val counterRepo  = new MoleculeInvoiceCounterRepository[IO]()
                        val orderRepo    = new MoleculeOrderRepository[IO]()
                        val customerRepo = new MoleculeCustomerRepository[IO]()
                        val source       = InvoiceNumberingSource[IO](
                            counterRepo,
                            orderRepo,
                            new MoleculeDatabaseInspection[IO](),
                            dbFile.toString
                        )
                        val exec         = (sql: String) => IO(molecule.db.sqlite.sync.rawTransact(sql)).void
                        test(counterRepo, orderRepo, customerRepo, source, exec, dbFile)
                    }
            }
            .unsafeRunSync()

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

    private val stateBlockMarker = "=== invoice numbering state (boot) ==="
    private val fatalBlockMarker = "=== INVOICE NUMBERING GUARD: refusing to start ==="

    val tests = Tests {

        test("rewound file: the state block precedes the verdict and guard point 1 refuses before the pass runs") {
            withBoot { (counterRepo, orderRepo, customerRepo, source, exec, dbFile) =>
                for
                    cust <- customer(customerRepo, "boot-rewound@example.com")
                    _    <- counterRepo.initializeCounter(1L)
                    _    <- issuedOrders(counterRepo, orderRepo)(cust, 6)
                    _    <- exec("UPDATE InvoiceCounter SET currentNumber = 1")    // the staging-rewind shape
                    owed <- mkOrder(orderRepo, cust)
                    _    <- orderRepo.updateStatus(owed.id, OrderStatus.Confirmed) // the pass WOULD number this
                    // config matches the rewound counter: no repair is attempted at all
                    cfg    = config(dbFile, 1L)
                    logs   = ListBuffer.empty[String]
                    pass   = new CountingPassService
                    result = runBoot(source, pass, cfg, logs)
                    (outcome, stdout) = result
                    after <- source.read
                yield
                    val stateAt = stdout.indexOf(stateBlockMarker)
                    val fatalAt = stdout.indexOf(fatalBlockMarker)
                    assert(outcome == InvoiceNumberingBoot.Outcome.Refused)
                    assert(stateAt >= 0 && fatalAt >= 0                   ) // both blocks printed
                    assert(stateAt < fatalAt                              ) // state block before any verdict
                    assert(stdout.contains("counter current")             ) // the verdict explains itself
                    assert(stdout.contains("highest issued")              )
                    assert(pass.passCount == 0                            ) // point 1 refused: the pass never ran
                    assert(logs.isEmpty                                   ) // no raise, no warning, no pass log
                    assert(after.orders.flatMap(_.invoiceNumber).size == 6) // the refusal minted nothing
            }
        }

        test(
            "consistent file with a refused config value: exactly one warning line, the pass runs, the boot proceeds"
        ) {
            withBoot { (counterRepo, orderRepo, customerRepo, source, _, dbFile) =>
                for
                    cust <- customer(customerRepo, "boot-stale-above@example.com")
                    _    <- counterRepo.initializeCounter(1L)
                    _    <- issuedOrders(counterRepo, orderRepo)(cust, 6) // consistent: counter 6, issued 1..6
                    cfg    = config(dbFile, 8L) // stale above the counter: evaluated and refused
                    logs   = ListBuffer.empty[String]
                    pass   = new CountingPassService
                    result = runBoot(source, pass, cfg, logs)
                    (outcome, stdout) = result
                yield
                    val warning   = InvoiceCounterRepair.notAppliedWarningLine(8L, 6L)
                    val generated = logs.indexWhere(_.contains("retroactively during startup"))
                    assert(outcome == InvoiceNumberingBoot.Outcome.Proceed)
                    assert(logs.count(_ == warning) == 1                  ) // exactly one not-applied warning, no more
                    assert(generated >= 0                                 ) // the pass DID run and was logged
                    assert(logs.indexOf(warning) < generated              ) // the warning precedes the pass
                    assert(pass.passCount == 1                            ) // asked for exactly once
                    assert(stdout.contains(stateBlockMarker)              )
                    assert(!stdout.contains(fatalBlockMarker)             ) // a refused config value is not fatal here
            }
        }

        test("an order the pass could not number: guard point 2 refuses only after the pass ran") {
            withBoot { (counterRepo, orderRepo, customerRepo, source, _, dbFile) =>
                for
                    cust <- customer(customerRepo, "boot-unnumberable@example.com")
                    _    <- counterRepo.initializeCounter(1L)
                    owed <- mkOrder(orderRepo, cust)
                    _    <- orderRepo.updateStatus(owed.id, OrderStatus.Confirmed) // point 1 expects exactly this
                    cfg    = config(dbFile, 1L)
                    logs   = ListBuffer.empty[String]
                    pass   = new CountingPassService // the pass runs but numbers nothing
                    result = runBoot(source, pass, cfg, logs)
                    (outcome, stdout) = result
                yield
                    val stateAt = stdout.indexOf(stateBlockMarker)
                    val fatalAt = stdout.indexOf(fatalBlockMarker)
                    assert(pass.passCount == 1                            ) // the pass had its chance first
                    assert(outcome == InvoiceNumberingBoot.Outcome.Refused)
                    assert(stateAt >= 0 && fatalAt >= 0                   )
                    assert(stateAt < fatalAt                              ) // state block before any verdict
                    assert(stdout.contains(owed.id.value.toString)        ) // the block names the order left bare
                    assert(stdout.contains("startup repair")              )
                    assert(stdout.contains("attempted and failed")        )
            }
        }
    }
