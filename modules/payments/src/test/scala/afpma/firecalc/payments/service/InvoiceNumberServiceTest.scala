/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import java.time.ZoneId

import afpma.firecalc.payments.TestDatabaseSetup
import afpma.firecalc.payments.config.AdminConfig
import afpma.firecalc.payments.config.DatabaseConfig
import afpma.firecalc.payments.config.InvoiceConfig
import afpma.firecalc.payments.config.InvoiceRetryConfig
import afpma.firecalc.payments.config.JwtConfig
import afpma.firecalc.payments.config.PaymentsConfig
import afpma.firecalc.payments.repository.impl.MoleculeInvoiceCounterRepository
import afpma.firecalc.payments.repository.impl.MoleculeOrderRepository

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import molecule.db.common.spi.Conn
import utest.*

object InvoiceNumberServiceTest extends TestSuite with TestDatabaseSetup {
  
  val testConfig = PaymentsConfig(
    environment = "test",
    productCatalog = "development",
    invoiceNumberPrefix = "TEST-",
    invoiceNumberDigits = 3,
    invoiceCounterStartingNumber = 1,
    invoiceTimezone = ZoneId.of("Europe/Paris"),
    retryConfig = InvoiceRetryConfig(
      maxRetries = 2,
      baseDelayMs = 10,
      maxDelayMs = 100
    ),
    databaseConfig = DatabaseConfig(
      filename = "test-in-memory.db",
      path = ":memory:"
    ),
    invoiceConfig = InvoiceConfig(
      configFilePath = "test-invoice-config.yaml",
      enabled = false  // Disabled for tests
    ),
    adminConfig = AdminConfig(
      email = "admin@example.com"
    ),
    reportAsDraft = true,
    jwtConfig = JwtConfig(
      secret = "test-only-secret-not-used-in-invoice-tests"
    )
  )
  
  def withService(test: (InvoiceNumberService[IO], MoleculeInvoiceCounterRepository[IO], Conn) => IO[Unit]): Unit = {
    testConnectionResource.use { implicit conn =>
      val invoiceCounterRepo = new MoleculeInvoiceCounterRepository[IO]()
      val orderRepo = new MoleculeOrderRepository[IO]()
      val service = new InvoiceNumberServiceImpl[IO](testConfig, invoiceCounterRepo, orderRepo)
      test(service, invoiceCounterRepo, conn)
    }.unsafeRunSync()
  }
  
  val tests = Tests {
    
    test("generate invoice number with correct format") {
      withService { (service, repo, conn) =>
        for {
          _ <- repo.initializeCounter(testConfig.invoiceCounterStartingNumber)
          invoiceNumber <- service.generateInvoiceNumber()
        } yield {
          assert(invoiceNumber == "TEST-001")
        }
      }
    }
    
    test("generate multiple invoice numbers in sequence") {
      withService { (service, repo, conn) =>
        for {
          _ <- repo.initializeCounter(testConfig.invoiceCounterStartingNumber)
          first <- service.generateInvoiceNumber()
          second <- service.generateInvoiceNumber()
          third <- service.generateInvoiceNumber()
        } yield {
          assert(first == "TEST-001")
          assert(second == "TEST-002")
          assert(third == "TEST-003")
        }
      }
    }
    
    test("fails when counter not initialized") {
      withService { (service, repo, conn) =>
        for {
          result <- service.generateInvoiceNumber().attempt
        } yield {
          assert(result.isLeft)
          result.left.foreach { error =>
            assert(error.getMessage.contains("Invoice counter not initialized"))
          }
        }
      }
    }
    
    test("retry logic works on repeated failures") {
      withService { (service, repo, conn) =>
        for {
          // Try multiple times without initializing to test retry logic
          result1 <- service.generateInvoiceNumber().attempt
          result2 <- service.generateInvoiceNumber().attempt
        } yield {
          assert(result1.isLeft)
          assert(result2.isLeft)
          result1.left.foreach { error =>
            assert(error.getMessage.contains("Invoice counter not initialized"))
          }
          result2.left.foreach { error =>
            assert(error.getMessage.contains("Invoice counter not initialized"))
          }
        }
      }
    }
  }
}
