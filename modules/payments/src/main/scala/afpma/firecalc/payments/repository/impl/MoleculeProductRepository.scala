/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository.impl

import cats.effect.IO
import cats.syntax.all.*
import afpma.firecalc.payments.domain
import afpma.firecalc.payments.shared.api
import afpma.firecalc.payments.shared.api.{CustomerInfo, CustomerType, BackendCompatibleLanguage, CountryCode_ISO_3166_1_ALPHA_2}
import afpma.firecalc.payments.repository.*
import CustomerSyntax.{toDomainCustomer}
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.*
import afpma.firecalc.payments.utils.*
import java.time.Instant
import java.util.UUID
import org.typelevel.log4cats.Logger
import molecule.db.sqlite.async.*
import cats.effect.kernel.Async
import molecule.db.common.spi.Conn
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.caps.internal
import cats.syntax.flatMap

class MoleculeProductRepository[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext) extends ProductRepository[F]:

  val logger = Logger[F]

  def findById(id: api.ProductId): F[Option[domain.Product]] =
    for
      _ <- logger.debug(s"Finding product by id: ${id}")
      result <- future2AsyncF {
        Product.productId(id.value).name.description.price.currency.active
          .query
          .get
          .map(_
            .headOption
            .map:
                case (id, name, description, price, currency, active) =>
                    domain.Product(api.ProductId(id), name, description, price, domain.Currency.valueOf(currency.toString), active)
                        
          ) 
      }
      _ <- logger.debug(s"Found product: ${result.isDefined}")
    yield result

  def findOrCreate(name: String, description: String, price: BigDecimal, currency: domain.Currency): F[domain.Product] =
    for
      _ <- logger.info(s"Finding or Creating new product: $name")
      
      internalId <- future2AsyncF {
        Product.id.name_(name).description_(description).price_(price).currency_(Currency.valueOf(currency.toString)).active_(true).query.get.map(_.headOption)
      }

      productId = UUID.randomUUID()
      product = domain.Product(api.ProductId(productId), name, description, price, currency, active = true)

      _ <- internalId match
        case Some(internalId) =>
            future2AsyncF {
                Product(internalId).productId.query.get.map(_.head)
            } flatMap { productId =>
                logger.info(s"Found product: ${productId}")
            }
        case None =>
            future2AsyncF {
                Product.productId.name.description.price.currency.active
                .insert((productId, name, description, price, currency.toString, true))
                .transact
            } flatMap { _ =>
                logger.info(s"Created product: ${product.id.value}")
            }
      
    yield product

  def create(
    id: api.ProductId,
    name: String,
    description: String,
    price: BigDecimal,
    currency: domain.Currency,
    active: Boolean
  ): F[domain.Product] =
    for
      _ <- logger.info(s"Creating product: $name (id: ${id.value})")
      _ <- future2AsyncF {
        Product.productId.name.description.price.currency.active
          .insert((id.value, name, description, price, currency.toString, active))
          .transact
      }
      product = domain.Product(id, name, description, price, currency, active)
      _ <- logger.info(s"Created product: ${id.value}")
    yield product

  def update(
    id: api.ProductId,
    name: String,
    description: String,
    price: BigDecimal,
    currency: domain.Currency,
    active: Boolean
  ): F[domain.Product] =
    for
      _ <- logger.info(s"Updating product: ${id.value}")
      
      // First, get the internal ID
      internalId <- future2AsyncF {
        Product.id.productId_(id.value).query.get.map(_.headOption)
      }.flatMap {
        case Some(internalId) => Async[F].pure(internalId)
        case None => Async[F].raiseError(new RuntimeException(s"Product not found: ${id.value}"))
      }
      
      // Update using internal ID
      _ <- future2AsyncF {
        Product(internalId)
          .name(name)
          .description(description)
          .price(price)
          .currency(Currency.valueOf(currency.toString))
          .active(active)
          .update
          .transact
      }
      
      product = domain.Product(id, name, description, price, currency, active)
      _ <- logger.info(s"Updated product: ${id.value}")
    yield product

  def upsert(productInfo: api.v1.ProductInfo): F[domain.Product] =
    for
      _ <- logger.info(s"Upserting product: ${productInfo.nameKey} (id: ${productInfo.id.value})")
      
      // Check if product exists
      existingProduct <- findById(productInfo.id)
      
      // Parse currency from string
      currency <- Async[F].fromOption(
        domain.Currency.fromString(productInfo.currency),
        new IllegalArgumentException(s"Invalid currency: ${productInfo.currency}")
      )
      
      // Create or update based on existence
      product <- existingProduct match
        case Some(_) =>
          update(
            productInfo.id,
            productInfo.nameKey,
            productInfo.descriptionKey,
            productInfo.price,
            currency,
            productInfo.active
          )
        case None =>
          create(
            productInfo.id,
            productInfo.nameKey,
            productInfo.descriptionKey,
            productInfo.price,
            currency,
            productInfo.active
          )
      
      _ <- logger.info(s"Upserted product: ${productInfo.id.value}")
    yield product
