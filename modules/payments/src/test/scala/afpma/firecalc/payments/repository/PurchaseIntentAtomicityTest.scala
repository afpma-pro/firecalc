/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import java.util.UUID

import afpma.firecalc.payments.TestDatabaseSetup
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.repository.impl.{MoleculeCustomerRepository, MoleculePurchaseIntentRepository}
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.metadb.MoleculeDomain_sqlite
import afpma.firecalc.payments.shared.api.*
import molecule.db.common.marshalling.JdbcProxy
import molecule.db.sqlite.facade.{JdbcConnSQlite_JVM, JdbcHandlerSQlite_JVM}

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import utest.*

/**
 * Integration tests for SEC-004 atomicity guarantees.
 * Runs against real SQLite databases through Molecule ORM, including both
 * in-memory and file-backed multi-connection scenarios.
 * Validates that `rawTransact("UPDATE ... WHERE processed = 0")` provides
 * true atomic check-and-set semantics at the database level.
 */
object PurchaseIntentAtomicityTest extends TestSuite with TestDatabaseSetup {

    // Eagerly load SQLite JDBC driver to avoid race with DriverManager
    Class.forName("org.sqlite.JDBC")

    val testCustomerInfo = CustomerInfo(
        email = "atomicity-test@example.com",
        customerType = CustomerType.Individual,
        language = BackendCompatibleLanguage.English,
        givenName = Some("Atomicity"),
        familyName = Some("Tester"),
        phoneNumber = None,
        companyName = None,
        addressLine1 = None,
        addressLine2 = None,
        addressLine3 = None,
        city = None,
        region = None,
        postalCode = None,
        countryCode = None
    )

    val testProductId = ProductId(UUID.randomUUID())
    val testAuthCode  = "123456"

    /** Creates both repos and seeds a Customer + PurchaseIntent, returning the intent's token. */
    def withSeededIntent(
        test: (PurchaseIntentRepository[IO], PurchaseToken) => IO[Unit]
    ): Unit = {
        testConnectionResource.use { implicit conn =>
            val customerRepo       = new MoleculeCustomerRepository[IO]()
            val purchaseIntentRepo = new MoleculePurchaseIntentRepository[IO]()
            for {
                customer <- customerRepo.create(testCustomerInfo)
                intent   <- purchaseIntentRepo.create(
                    testProductId,
                    BigDecimal("29.99"),
                    Currency.EUR,
                    testAuthCode,
                    customer.id,
                    None
                )
                _ <- test(purchaseIntentRepo, intent.token)
            } yield ()
        }.unsafeRunSync()
    }

    /** Creates repos without seeding — for negative tests. */
    def withRepository(
        test: PurchaseIntentRepository[IO] => IO[Unit]
    ): Unit = {
        testConnectionResource.use { implicit conn =>
            val repo = new MoleculePurchaseIntentRepository[IO]()
            test(repo)
        }.unsafeRunSync()
    }

    val tests = Tests {

        test("atomicMarkAsProcessed returns true then false for same token") {
            withSeededIntent { (repo, token) =>
                for {
                    first  <- repo.atomicMarkAsProcessed(token)
                    second <- repo.atomicMarkAsProcessed(token)
                } yield {
                    assert(first == true)
                    assert(second == false)
                }
            }
        }

        test("atomicMarkAsProcessed returns false for nonexistent token") {
            withRepository { repo =>
                for {
                    result <- repo.atomicMarkAsProcessed(PurchaseToken(UUID.randomUUID()))
                } yield {
                    assert(result == false)
                }
            }
        }

        test("findByTokenAndCode filters out processed intents") {
            withSeededIntent { (repo, token) =>
                for {
                    // Before processing: findByTokenAndCode should return the intent
                    before <- repo.findByTokenAndCode(token, testAuthCode)
                    _       = assert(before.isDefined)

                    // Mark as processed
                    marked <- repo.atomicMarkAsProcessed(token)
                    _       = assert(marked == true)

                    // After processing: findByTokenAndCode should return None
                    // (defense-in-depth .processed(false) filter)
                    after <- repo.findByTokenAndCode(token, testAuthCode)
                } yield {
                    assert(after.isEmpty)
                }
            }
        }

        test("concurrent atomicMarkAsProcessed - exactly one wins") {
            withSeededIntent { (repo, token) =>
                // Fire 10 parallel atomicMarkAsProcessed calls.
                // SQLite's write lock ensures at most one UPDATE matches processed = 0.
                val parallelCalls = IO.parTraverseN(10)((1 to 10).toList)(_ =>
                    repo.atomicMarkAsProcessed(token)
                ).attempt

                for {
                    result <- parallelCalls
                } yield {
                    result match
                        // Happy path: all calls completed, exactly one got true
                        case Right(results) =>
                            val trueCount = results.count(_ == true)
                            assert(trueCount == 1)
                            assert(results.count(_ == false) == 9)

                        // Also valid: SQLite in-memory rejected concurrent transactions
                        // (proves atomicity — the DB refused concurrent writes)
                        case Left(error) =>
                            assert(
                                error.getMessage.contains("database in auto-commit mode") ||
                                error.getMessage.contains("cannot start a transaction within a transaction") ||
                                error.getMessage.contains("cannot rollback") ||
                                error.getMessage.contains("SQLITE_BUSY")
                            )
                }
            }
        }

        test("concurrent atomicMarkAsProcessed on file-backed SQLite - multi-connection strict one-winner") {
            // SEC-004: each parallel call uses its own independent JDBC + Molecule
            // connection to the same file-backed DB, proving true multi-connection
            // WAL contention atomicity — not just single-connection serialization.
            val tmpFile = java.io.File.createTempFile("atomicity-test-", ".db")
            tmpFile.deleteOnExit()

            val sqliteUrl = s"jdbc:sqlite:${tmpFile.getAbsolutePath}"
            val metaDb    = MoleculeDomain_sqlite()

            def configurePragmas(sqlConn: java.sql.Connection): Unit = {
                val stmt = sqlConn.createStatement()
                try {
                    stmt.execute("PRAGMA journal_mode = WAL;")
                    stmt.execute("PRAGMA synchronous = NORMAL;")
                    stmt.execute("PRAGMA foreign_keys = ON;")
                    stmt.execute("PRAGMA busy_timeout = 5000;")
                } finally stmt.close()
            }

            // Phase 1: seed schema + data with a single connection
            val seedSqlConn = java.sql.DriverManager.getConnection(sqliteUrl)
            configurePragmas(seedSqlConn)

            val seedProxy = JdbcProxy(sqliteUrl, metaDb)
            given seedConn: molecule.db.common.spi.Conn =
                JdbcHandlerSQlite_JVM.recreateDb(seedProxy, seedSqlConn)

            val token = {
                val customerRepo       = new MoleculeCustomerRepository[IO]()
                val purchaseIntentRepo = new MoleculePurchaseIntentRepository[IO]()
                (for {
                    customer <- customerRepo.create(testCustomerInfo)
                    intent   <- purchaseIntentRepo.create(
                        testProductId,
                        BigDecimal("29.99"),
                        Currency.EUR,
                        testAuthCode,
                        customer.id,
                        None
                    )
                } yield intent.token).unsafeRunSync()
            }

            seedSqlConn.close()

            // Phase 2: 10 independent connections race to mark the same intent.
            // Each fiber opens its own JDBC connection and Molecule conn, ensuring
            // contention happens at the SQLite WAL level, not the JDBC driver level.
            val results = IO.parTraverseN(10)((1 to 10).toList) { _ =>
                IO.delay {
                    val sqlConn = java.sql.DriverManager.getConnection(sqliteUrl)
                    configurePragmas(sqlConn)
                    val proxy = JdbcProxy(sqliteUrl, metaDb)
                    val moleculeConn: molecule.db.common.spi.Conn =
                        new JdbcConnSQlite_JVM(proxy, sqlConn)
                    (sqlConn, moleculeConn)
                }.flatMap { case (sqlConn, moleculeConn) =>
                    given molecule.db.common.spi.Conn = moleculeConn
                    val repo = new MoleculePurchaseIntentRepository[IO]()
                    repo.atomicMarkAsProcessed(token)
                        .guarantee(IO.delay(sqlConn.close()))
                }
            }.unsafeRunSync()

            val trueCount  = results.count(_ == true)
            val falseCount = results.count(_ == false)
            assert(trueCount == 1)
            assert(falseCount == 9)
        }
    }
}
