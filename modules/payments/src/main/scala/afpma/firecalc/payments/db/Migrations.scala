/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.db

import cats.effect.Sync
import cats.implicits.*

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.typelevel.log4cats.Logger

object Migrations {

    def migrate[F[_]: Sync: Logger](dbUrl: String): F[Unit] =
        for {
            _      <- Logger[F].info("Running database migrations...")
            flyway <- Sync[F].delay {
                Flyway
                    .configure()
                    .dataSource(dbUrl, null, null)
                    .locations("classpath:db/migration/afpma/firecalc/payments/repository/impl/MoleculeDomain/sqlite")
                    .load()
            }
            _      <- Sync[F].delay(flyway.migrate()).handleErrorWith { case e: FlywayException =>
                Logger[F].error(s"Flyway migration failed: ${e.getMessage}") *> Sync[F].raiseError(e)
            }
            _      <- Logger[F].info("Database migration successful.")
        } yield ()
}
