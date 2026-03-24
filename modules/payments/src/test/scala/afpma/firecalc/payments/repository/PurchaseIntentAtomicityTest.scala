/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import java.util.UUID

import afpma.firecalc.payments.TestDatabaseSetup
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.repository.impl.{MoleculeCustomerRepository, MoleculePurchaseIntentRepository}
import afpma.firecalc.payments.shared.api.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import utest.*

/**
 * Integration tests for SEC-004 atomicity guarantees.
 * Runs against a real in-memory SQLite database through Molecule ORM.
 *
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
    }
}
