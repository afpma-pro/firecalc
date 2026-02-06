/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository.impl
import java.time.Instant

import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.*
import afpma.firecalc.payments.shared.api
import afpma.firecalc.payments.utils.*

import cats.effect.kernel.Async
import cats.syntax.all.*

import scala.concurrent.ExecutionContext

import molecule.db.common.spi.Conn
import molecule.db.sqlite.async.*
import org.typelevel.log4cats.Logger

class MoleculeProductMetadataRepository[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext)
    extends ProductMetadataRepository[F]:

    val logger = Logger[F]

    def create(productMetadata: api.ProductMetadata): F[Long] =
        for
            _ <- logger.info("Creating product metadata")
            now        = Instant.now()
            jsonString = api.ProductMetadata.serialize(productMetadata)
            txReport <- future2AsyncF {
                ProductMetadata.jsonString(jsonString).createdAt(now).save.transact
            }
            id = txReport.id
            _        <- logger.info(s"Created product metadata with id: $id")
        yield id

    def findById(id: Long): F[Option[api.ProductMetadata]] =
        for
            _      <- logger.debug(s"Finding product metadata by id: $id")
            result <- future2AsyncF {
                ProductMetadata(id).jsonString.query.get.map(_.headOption)
            }
            productMetadata = result.flatMap(api.ProductMetadata.deserialize)
        yield productMetadata
