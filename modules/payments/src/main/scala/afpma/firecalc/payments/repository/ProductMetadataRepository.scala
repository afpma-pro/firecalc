/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import afpma.firecalc.payments.shared.api.*
import cats.effect.{Async, Resource}
import molecule.db.common.spi.Conn
import scala.concurrent.ExecutionContext
import org.typelevel.log4cats.Logger
import afpma.firecalc.payments.repository.impl.*

trait ProductMetadataRepository[F[_]]:
  def create(productMetadata: ProductMetadata): F[Long]
  def findById(id: Long): F[Option[ProductMetadata]]

object ProductMetadataRepository:
  def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[ProductMetadataRepository[F]] =
    Async[F].pure(new MoleculeProductMetadataRepository[F])
