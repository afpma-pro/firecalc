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

class MoleculePurchaseIntentRepository[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext) extends PurchaseIntentRepository[F]:

  val logger = Logger[F]

  def create(
    productId: api.ProductId, 
    amount: BigDecimal, 
    currency: domain.Currency,
    authCode: String,
    customerId: domain.CustomerId,
    productMetadataId: Option[Long]
  ): F[domain.PurchaseIntent] =
    for
      _ <- logger.info(s"Creating purchase intent for customer UUID: $customerId")
      // First, get the Customer's internal id by querying with customerId
      customerInternalIdOpt <- future2AsyncF {
        Customer.id.customerId_(customerId.value).query.get.map(_.headOption)
      }
      customerInternalId <- customerInternalIdOpt match
        case Some(id) => Async[F].pure(id)
        case None => 
          val errorMsg = s"Customer not found with ID: $customerId"
          logger.error(errorMsg) *> Async[F].raiseError(new RuntimeException(errorMsg))
      // Use the internal method
      intent <- createWithInternalCustomerId(productId, amount, currency, authCode, customerInternalId, productMetadataId)
    yield intent

  def createWithInternalCustomerId(
    productId: api.ProductId, 
    amount: BigDecimal, 
    currency: domain.Currency,
    authCode: String,
    customerInternalId: Long,
    productMetadataId: Option[Long]
  ): F[domain.PurchaseIntent] =
    for
      _ <- logger.info(s"Creating purchase intent for customer internal ID: $customerInternalId")
      now <- Async[F].delay(Instant.now())
      expiresAt = now.plusSeconds(600) // 10 minutes
      token = UUID.randomUUID()
      _ <- future2AsyncF {
        PurchaseIntent.token.productId.amount.currency.authCode.customer.processed.expiresAt.createdAt.productMetadata_?
          .insert(
            (token, productId.value, amount, currency.toString, authCode, customerInternalId, false, expiresAt, now, productMetadataId)
          )
          .transact
      }
      // Get the customer UUID back to create the domain object
      customerUuidOpt <- future2AsyncF {
        Customer.id_(customerInternalId).customerId.query.get.map(_.headOption)
      }
      customerUuid <- customerUuidOpt match
        case Some(uuid) => Async[F].pure(uuid)
        case None => 
          val errorMsg = s"Customer not found with internal ID: $customerInternalId"
          logger.error(errorMsg) *> Async[F].raiseError(new RuntimeException(errorMsg))
      intent = domain.PurchaseIntent(
        api.PurchaseToken(token), 
        productId, 
        amount, 
        currency,
        authCode, 
        domain.CustomerId(customerUuid),
        false, 
        productMetadataId,
        expiresAt, 
        now
      )
      _ <- logger.info(s"Created purchase intent: ${intent.token}")
    yield intent

  def findByToken(token: api.PurchaseToken): F[Option[domain.PurchaseIntent]] =
    for
      _ <- logger.debug(s"Finding purchase intent by token: ${token}")
      result <- future2AsyncF {
        PurchaseIntent.token(token.value).productId.amount.currency.authCode.processed.expiresAt.createdAt
            .ProductMetadata.?(ProductMetadata.id)
            .Customer.customerId
            .query
            .get
            .map(_
                .headOption
                .map { case (token, productId, amount, currency, authCode, processed, expiresAt, createdAt, productMetadataIdOpt, customerId) =>
                domain.PurchaseIntent(
                    api.PurchaseToken(token), 
                    api.ProductId(productId), 
                    amount, 
                    domain.Currency.valueOf(currency.toString),
                    authCode, 
                    domain.CustomerId(customerId), // customerId is now UUID in DB
                    processed, 
                    productMetadataIdOpt,
                    expiresAt, 
                    createdAt,
                )
                }
            )
      }
    yield result

  def findByTokenAndCode(token: api.PurchaseToken, code: String): F[Option[domain.PurchaseIntent]] =
    for
      _ <- logger.debug(s"Finding purchase intent by token and code: ${token}")
      result <- future2AsyncF {
        // doc: https://www.scalamolecule.org/database/query/relationships.html#mix
        PurchaseIntent.token(token.value).productId.amount.currency.authCode(code).processed.expiresAt.createdAt
            .Customer.customerId
            ._PurchaseIntent.ProductMetadata.?(ProductMetadata.id)
            .query
            .get
            .map(_
              .headOption
              .map { case (token, productId, amount, currency, authCode, processed, expiresAt, createdAt, customerId, productMetadataIdOpt) =>
                domain.PurchaseIntent(
                  api.PurchaseToken(token), 
                  api.ProductId(productId), 
                  amount, 
                  domain.Currency.valueOf(currency.toString),
                  authCode, 
                  domain.CustomerId(customerId), // customerId is now UUID in DB
                  processed, 
                  productMetadataIdOpt,
                  expiresAt, 
                  createdAt
                )
              }
            )
      }
    yield result

  def markAsProcessed(token: api.PurchaseToken): F[Boolean] =
    for
      _ <- logger.info(s"Marking purchase intent as processed: ${token}")
      internalId <- future2AsyncF {
        PurchaseIntent.id.token_(token.value).query.get.map(_.head)
      }
      result <- future2AsyncF {
        PurchaseIntent(internalId).token(token.value).processed(true).update.transact
      }
    yield true

  def deleteExpired(): F[Int] =
    import scala.math.Ordered.orderingToOrdered
    for
      _ <- logger.info("Deleting expired purchase intents")
      now <- Async[F].delay(Instant.now())
      idsToDelete <- future2AsyncF {
        PurchaseIntent.id.token.expiresAt
          .query
          .get
          .map(_
            .filter(_._3 < now)
            .map(_._1)
          )
      }
      _ <- future2AsyncF { PurchaseIntent(idsToDelete).delete.transact }
      _ <- logger.info(s"Deleted ${idsToDelete.length} expired purchase intents")
    yield idsToDelete.length
