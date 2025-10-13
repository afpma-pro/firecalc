/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.typelevel.log4cats.Logger
import cats.effect.Sync
import cats.implicits.*
import java.nio.file.{Files, Paths}

object Migrations {

  def migrate[F[_]: Sync: Logger](dbUrl: String): F[Unit] =
    for {
      _ <- Logger[F].info("Running database migrations...")
      _ <- ensureDatabaseFileExists(dbUrl)
      flyway <- Sync[F].delay {
        Flyway.configure()
          .dataSource(dbUrl, null, null)
          .locations("classpath:db/migration")
          .load()
      }
      _ <- Sync[F].delay(flyway.migrate()).handleErrorWith {
        case e: FlywayException => Logger[F].error(s"Flyway migration failed: ${e.getMessage}") *> Sync[F].raiseError(e)
      }
      _ <- Logger[F].info("Database migration successful.")
    } yield ()

  private def ensureDatabaseFileExists[F[_]: Sync: Logger](dbUrl: String): F[Unit] = {
    val path = Paths.get(dbUrl.stripPrefix("jdbc:sqlite:"))
    Sync[F].delay(Files.exists(path)).flatMap {
      case true => Logger[F].info(s"Database file already exists at $path.")
      case false =>
        for {
          _ <- Logger[F].info(s"Database file not found at $path. Creating it...")
          _ <- Sync[F].delay(Files.createDirectories(path.getParent))
          _ <- Sync[F].delay(Files.createFile(path))
          _ <- Logger[F].info(s"Database file created at $path.")
        } yield ()
    }
  }
}
