/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments

import molecule.db.common.spi.Conn
import cats.effect.{IO, Resource}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.ExecutionContext

trait TestDatabaseSetup {
  
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  
  /**
   * Creates an in-memory SQLite database connection for testing.
   * Uses the same SQLite configuration as production for consistency.
   * 
   * This ensures test environment matches production in terms of:
   * - WAL mode
   * - Foreign key enforcement
   * - Busy timeout settings
   * - Synchronous mode
   */
  def getTestConnection: IO[Conn] = 
    Database.createTestConnection[IO].allocated.map(_._1)
  
  /**
   * Creates a test database connection as a Resource for automatic cleanup.
   * Uses the production-grade SQLite configuration for consistent testing.
   */
  def testConnectionResource: Resource[IO, Conn] = 
    Database.createTestConnection[IO]
  
  /**
   * Sets up the basic schema and returns a connection.
   * Note: Molecule generates the schema automatically, so we don't need to run migrations manually.
   * The connection will have all production SQLite PRAGMAs applied.
   */
  def setupTestDatabase: IO[Conn] = getTestConnection
}

object TestDatabaseSetup extends TestDatabaseSetup
