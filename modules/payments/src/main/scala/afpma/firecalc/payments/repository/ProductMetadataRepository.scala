/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import afpma.firecalc.payments.repository.impl.*
import afpma.firecalc.payments.shared.api.*

import cats.effect.Async

import scala.concurrent.ExecutionContext

import molecule.db.common.spi.Conn
import org.typelevel.log4cats.Logger

trait ProductMetadataRepository[F[_]]:
    def create  (productMetadata: ProductMetadata): F[Long]
    def findById(id             : Long           ): F[Option[ProductMetadata]]

object ProductMetadataRepository:
    def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[ProductMetadataRepository[F]] =
        Async[F].pure(new MoleculeProductMetadataRepository[F])
