/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import utest.*
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import afpma.firecalc.payments.TestDatabaseSetup
import afpma.firecalc.payments.repository.impl.MoleculeInvoiceCounterRepository
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import molecule.db.common.spi.Conn
import scala.concurrent.ExecutionContext

object InvoiceCounterRepositoryTest extends TestSuite with TestDatabaseSetup {
  
  def withRepository(test: (InvoiceCounterRepository[IO], Conn) => IO[Unit]): Unit = {
    testConnectionResource.use { implicit conn =>
      val repo = new MoleculeInvoiceCounterRepository[IO]()
      test(repo, conn)
    }.unsafeRunSync()
  }
  
  val tests = Tests {
    
    test("initialize counter with starting number") {
      withRepository { (repo, conn) =>
        for {
          // Initialize counter starting at 100
          initialized <- repo.initializeCounter(100)
          currentValue <- repo.getCurrentCounter()
        } yield {
          assert(initialized == true)
          assert(currentValue == Some(99)) // Should be startingNumber - 1
        }
      }
    }
    
    test("prevent double initialization") {
      withRepository { (repo, conn) =>
        for {
          // First initialization
          first <- repo.initializeCounter(100)
          // Second initialization should fail
          second <- repo.initializeCounter(200)
          currentValue <- repo.getCurrentCounter()
        } yield {
          assert(first == true)
          assert(second == false) // Should not reinitialize
          assert(currentValue == Some(99)) // Should remain at original value
        }
      }
    }
    
    test("get next invoice number increments counter") {
      withRepository { (repo, conn) =>
        for {
          // Initialize counter
          _ <- repo.initializeCounter(1)
          
          // Get first invoice number
          first <- repo.getNextInvoiceNumber()
          
          // Get second invoice number  
          second <- repo.getNextInvoiceNumber()
          
          // Get third invoice number
          third <- repo.getNextInvoiceNumber()
          
          // Check current counter value
          currentValue <- repo.getCurrentCounter()
        } yield {
          assert(first == 1)
          assert(second == 2) 
          assert(third == 3)
          assert(currentValue == Some(3))
        }
      }
    }
    
    test("get next invoice number with custom starting number") {
      withRepository { (repo, conn) =>
        for {
          // Initialize counter starting at 1000
          _ <- repo.initializeCounter(1000)
          
          // Get first invoice number
          first <- repo.getNextInvoiceNumber()
          
          // Get second invoice number
          second <- repo.getNextInvoiceNumber()
          
          currentValue <- repo.getCurrentCounter()
        } yield {
          assert(first == 1000)
          assert(second == 1001)
          assert(currentValue == Some(1001))
        }
      }
    }
    
    test("get next invoice number fails when counter not initialized") {
      withRepository { (repo, conn) =>
        for {
          // Try to get invoice number without initializing
          result <- repo.getNextInvoiceNumber().attempt
        } yield {
          assert(result.isLeft)
          result.left.foreach { error =>
            assert(error.getMessage.contains("Invoice counter not initialized"))
          }
        }
      }
    }
    
    test("get current counter returns None when not initialized") {
      withRepository { (repo, conn) =>
        for {
          currentValue <- repo.getCurrentCounter()
        } yield {
          assert(currentValue == None)
        }
      }
    }
    
    test("concurrent counter increments fail with SQLite limitations") {
      withRepository { (repo, conn) =>
        for {
          // Initialize counter
          _ <- repo.initializeCounter(1)
          
          // SQLite in-memory has transaction limitations for concurrent access
          // This test documents the known limitation by expecting the exception
          result <- IO.parTraverseN(10)((1 to 10).toList)(_ => repo.getNextInvoiceNumber()).attempt
        } yield {
          // Expect the operation to fail due to SQLite transaction limitations
          assert(result.isLeft)
          result.left.foreach { error =>
            assert(error.getMessage.contains("database in auto-commit mode") || 
                   error.getMessage.contains("cannot start a transaction within a transaction"))
          }
        }
      }
    }
  }
}
