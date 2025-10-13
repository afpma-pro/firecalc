/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository.impl

import cats.effect.IO
import cats.syntax.all.*
import afpma.firecalc.payments.domain
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.repository.impl.CustomerSyntax.*
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.*
import afpma.firecalc.payments.repository.CustomerRepository
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

class MoleculeCustomerRepository[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext) extends CustomerRepository[F]:

  val logger = Logger[F]

  def findByEmail(email: String): F[Option[domain.Customer]] =
    for
      _ <- logger.debug(s"Finding customer by email: $email")
      result <- future2AsyncF {
        Customer.customerId.email(email).customerType.language.givenName.familyName.companyName.addressLine1.addressLine2.addressLine3.city.region.postalCode.countryCode.phoneNumber.paymentProviderId.paymentProvider.createdAt.updatedAt
          .query
          .get
          .map(_
            .headOption
            .map(_.toDomainCustomer)
          )
      }
    yield result

  def findById(id: domain.CustomerId): F[Option[domain.Customer]] =
    for
      _ <- logger.debug(s"Finding customer by id: $id")
      result <- future2AsyncF {
        Customer.customerId(id.value).email.customerType.language.givenName.familyName.companyName.addressLine1.addressLine2.addressLine3.city.region.postalCode.countryCode.phoneNumber.paymentProviderId.paymentProvider.createdAt.updatedAt
          .query
          .get
          .map(_
            .headOption
            .map(_.toDomainCustomer)
          )
      }
    yield result

  def create(customerInfo: CustomerInfo): F[domain.Customer] =
    for
      _ <- logger.info(s"Creating customer with email: ${customerInfo.email}")
      now <- Async[F].delay(Instant.now())
      customerId = UUID.randomUUID()
      _ <- future2AsyncF {
        Customer.customerId.email.customerType.language.givenName.familyName.companyName.addressLine1.addressLine2.addressLine3.city.region.postalCode.countryCode.phoneNumber.paymentProviderId.paymentProvider.createdAt.updatedAt
          .insert {
            import customerInfo.*
            (customerId, email, customerType.toString, language.code, givenName.getOrElse(""), familyName.getOrElse(""), companyName.getOrElse(""), addressLine1.getOrElse(""), addressLine2.getOrElse(""), addressLine3.getOrElse(""), city.getOrElse(""), region.getOrElse(""), postalCode.getOrElse(""), countryCode.map(_.code).getOrElse(""), phoneNumber.getOrElse(""), "", PaymentProvider.Unknown.toString, now, now)
          }
          .transact
      }
      customer = customerInfo.toDomainCustomer(customerId, now)
      _ <- logger.info(s"Created customer: ${customer.id.value}")
    yield customer

  def createFull(customer: domain.Customer): F[Boolean] =
    for
        _ <- logger.info(s"Creating customer with email: ${customer.email}")
        now <- Async[F].delay(Instant.now())
        customerId = UUID.randomUUID()
        _ <- future2AsyncF {
            Customer.customerId.email.customerType.language.givenName.familyName.companyName.addressLine1.addressLine2.addressLine3.city.region.postalCode.countryCode.phoneNumber.paymentProviderId.paymentProvider.createdAt.updatedAt
            .insert {
                import customer.*
                val unsafePaymentProvider = paymentProvider.map(_.asMoleculePaymentProviderUnsafe.toString)
                (customerId, email, customerType.toString, language.code, givenName.getOrElse(""), familyName.getOrElse(""), companyName.getOrElse(""), addressLine1.getOrElse(""), addressLine2.getOrElse(""), addressLine3.getOrElse(""), city.getOrElse(""), region.getOrElse(""), postalCode.getOrElse(""), countryCode.map(_.code).getOrElse(""), phoneNumber.getOrElse(""), paymentProviderId.getOrElse(""), unsafePaymentProvider.getOrElse(""), now, now)
            }
            .transact
        }
        _ <- logger.info(s"Created customer: ${customer.id.value}")
    yield true

  def updatePaymentProvider(customerId: domain.CustomerId, paymentProviderId: String, paymentProvider: domain.PaymentProvider): F[Boolean] =
    for
      _ <- logger.info(s"Updating customer ${customerId.value} with payment provider: $paymentProvider, ID: $paymentProviderId")
      now <- Async[F].delay(Instant.now())
      customerInternalId <- future2AsyncF { Customer.id.customerId_(customerId.value).query.get.map(_.head) }
      result <- future2AsyncF {
        Customer(customerInternalId).paymentProviderId(paymentProviderId).paymentProvider(PaymentProvider.valueOf(paymentProvider.toString)).updatedAt(now)
          .update
          .transact
      }
      _ <- logger.info(s"Successfully updated customer ${customerId.value} payment provider")
    yield true

  def findByEmailAndUpdate(email: String, newCustomerInfo: CustomerInfo): F[Option[domain.Customer]] =
    for
      _ <- logger.info(s"Finding and updating customer with email: $email")
      existingCustomer <- findByEmail(email)
      result <- existingCustomer match
        case Some(customer) =>
          for
            now <- Async[F].delay(Instant.now())
            customerInternalId <- future2AsyncF { Customer.id.customerId_(customer.id.value).query.get.map(_.head) }
            _ <- future2AsyncF {
              Customer(customerInternalId)
                .customerType(afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.CustomerType.valueOf(newCustomerInfo.customerType.toString))
                .language(newCustomerInfo.language.code)
                .givenName(newCustomerInfo.givenName.getOrElse(""))
                .familyName(newCustomerInfo.familyName.getOrElse(""))
                .companyName(newCustomerInfo.companyName.getOrElse(""))
                .addressLine1(newCustomerInfo.addressLine1.getOrElse(""))
                .addressLine2(newCustomerInfo.addressLine2.getOrElse(""))
                .addressLine3(newCustomerInfo.addressLine3.getOrElse(""))
                .city(newCustomerInfo.city.getOrElse(""))
                .region(newCustomerInfo.region.getOrElse(""))
                .postalCode(newCustomerInfo.postalCode.getOrElse(""))
                .countryCode(newCustomerInfo.countryCode.map(_.code).getOrElse(""))
                .phoneNumber(newCustomerInfo.phoneNumber.getOrElse(""))
                .updatedAt(now)
                .update
                .transact
            }
            updatedCustomer = domain.Customer(
              id = customer.id,
              email = email,
              customerType = newCustomerInfo.customerType,
              language = newCustomerInfo.language,
              givenName = newCustomerInfo.givenName,
              familyName = newCustomerInfo.familyName,
              companyName = newCustomerInfo.companyName,
              addressLine1 = newCustomerInfo.addressLine1,
              addressLine2 = newCustomerInfo.addressLine2,
              addressLine3 = newCustomerInfo.addressLine3,
              city = newCustomerInfo.city,
              region = newCustomerInfo.region,
              postalCode = newCustomerInfo.postalCode,
              countryCode = newCustomerInfo.countryCode,
              phoneNumber = newCustomerInfo.phoneNumber,
              paymentProviderId = customer.paymentProviderId,
              paymentProvider = customer.paymentProvider,
              createdAt = customer.createdAt,
              updatedAt = now
            )
            _ <- logger.info(s"Successfully updated customer with email: $email")
          yield Some(updatedCustomer)
        case None =>
          logger.info(s"Customer with email $email not found") *> Async[F].pure(None)
    yield result
