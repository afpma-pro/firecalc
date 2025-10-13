/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments

import cats.effect.{Async, Resource}
import afpma.firecalc.payments.repository.impl.*
import afpma.firecalc.payments.config.PaymentsConfig
import org.typelevel.log4cats.Logger
import java.sql.DriverManager
import molecule.db.common.spi.Conn
import scala.concurrent.ExecutionContext
import molecule.db.common.facade.JdbcConn_JVM
import molecule.db.common.marshalling.JdbcProxy
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.*
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.metadb.MoleculeDomain_sqlite
import molecule.db.sqlite.facade.JdbcHandlerSQlite_JVM

// Database setup with SQLite Single Writer best practices
object Database:
  
  /**
   * Creates a properly configured SQLite connection for Single Writer architecture.
   * 
   * Configuration includes:
   * - WAL mode for better concurrency
   * - Foreign key enforcement
   * - Appropriate busy timeout
   * - NORMAL synchronous mode for WAL
   */
  def createConnection[F[_]: Async: Logger](config: PaymentsConfig): Resource[F, Conn] =
    Resource.make(
      Async[F].delay {
        
        val SQLITE_URL = s"jdbc:sqlite:${config.databaseConfig.path}"
        
        // Initialize SQLite database with raw JDBC connection first
        val sqlConn = DriverManager.getConnection(SQLITE_URL)
        
        // Configure SQLite for optimal Single Writer performance and safety
        configureSQLitePragmas(sqlConn)
        
        val metaDb = MoleculeDomain_sqlite()
        val proxy = JdbcProxy(SQLITE_URL, metaDb)

        // Create Molecule connection 
        implicit val conn: JdbcConn_JVM = JdbcHandlerSQlite_JVM.recreateDb(proxy, sqlConn)

        conn
      }
    )(conn => 
        Async[F].delay(conn.close())
    )

  /**
   * Configures essential SQLite PRAGMAs for Single Writer architecture and production use.
   * 
   * Settings applied:
   * - journal_mode = WAL: Write-Ahead Logging for better concurrency
   * - synchronous = NORMAL: Good balance of safety and performance with WAL
   * - foreign_keys = ON: Enforce referential integrity
   * - busy_timeout = 5000: Wait up to 5 seconds on database locks
   */
  private def configureSQLitePragmas(sqlConn: java.sql.Connection): Unit = {
    val statement = sqlConn.createStatement()
    
    try {
      // Enable Write-Ahead Logging (WAL) mode
      // This allows readers to continue while a write is in progress
      statement.execute("PRAGMA journal_mode = WAL;")
      
      // Set synchronous mode to NORMAL (safe with WAL mode)
      // FULL would be slower, OFF would be risky
      statement.execute("PRAGMA synchronous = NORMAL;")
      
      // Enable foreign key constraints enforcement
      // Disabled by default for backward compatibility
      statement.execute("PRAGMA foreign_keys = ON;")
      
      // Set busy timeout to 5 seconds
      // SQLite will wait this long before returning SQLITE_BUSY
      statement.execute("PRAGMA busy_timeout = 5000;")
      
      println("SQLite configured with Single Writer best practices:")
      println("- WAL mode enabled for better concurrency")
      println("- Foreign keys enforcement enabled")
      println("- Busy timeout set to 5 seconds")
      println("- Synchronous mode set to NORMAL")
      
    } finally {
      statement.close()
    }
  }

  /**
   * Creates a test connection with in-memory database for testing.
   * Uses the same PRAGMA configuration as production for consistency.
   */
  def createTestConnection[F[_]: Async: Logger]: Resource[F, Conn] =
    Resource.make(
      Async[F].delay {
        
        val SQLITE_URL = "jdbc:sqlite::memory:"
        
        // Initialize in-memory SQLite database
        val sqlConn = DriverManager.getConnection(SQLITE_URL)
        
        // Apply same configuration as production
        configureSQLitePragmas(sqlConn)
        
        val metaDb = MoleculeDomain_sqlite()
        val proxy = JdbcProxy(SQLITE_URL, metaDb)

        // Create Molecule connection
        implicit val conn: JdbcConn_JVM = JdbcHandlerSQlite_JVM.recreateDb(proxy, sqlConn, true)

        conn
      }
    )(conn => 
        Async[F].delay(conn.close())
    )
