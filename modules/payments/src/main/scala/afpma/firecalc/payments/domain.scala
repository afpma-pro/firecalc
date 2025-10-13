/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.domain

import java.time.Instant
import java.util.UUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.util.Base64

// Core domain models
case class CustomerId(value: UUID) extends AnyVal

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import afpma.firecalc.payments.shared.api.{OrderId, CustomerType, BackendCompatibleLanguage, CountryCode_ISO_3166_1_ALPHA_2, CustomerInfo, ProductId, PurchaseToken}

case class Product(
  id: ProductId,
  name: String,
  description: String,
  price: BigDecimal,
  currency: Currency,
  active: Boolean
)

case class Customer(
  id: CustomerId,
  email: String,
  customerType: CustomerType,
  language: BackendCompatibleLanguage,
  givenName: Option[String] = None,
  familyName: Option[String] = None,
  companyName: Option[String] = None,
  addressLine1: Option[String] = None,
  addressLine2: Option[String] = None,
  addressLine3: Option[String] = None,
  city: Option[String] = None,
  region: Option[String] = None,
  postalCode: Option[String] = None,
  countryCode: Option[CountryCode_ISO_3166_1_ALPHA_2] = None,
  phoneNumber: Option[String] = None,
  paymentProviderId: Option[String] = None,
  paymentProvider: Option[PaymentProvider] = None,
  createdAt: Instant,
  updatedAt: Instant
)

object Customer:
  extension (customer: Customer)
    def toCustomerInfo: CustomerInfo = CustomerInfo(
      email = customer.email,
      customerType = customer.customerType,
      language = customer.language,
      givenName = customer.givenName,
      familyName = customer.familyName,
      companyName = customer.companyName,
      addressLine1 = customer.addressLine1,
      addressLine2 = customer.addressLine2,
      addressLine3 = customer.addressLine3,
      city = customer.city,
      region = customer.region,
      postalCode = customer.postalCode,
      countryCode = customer.countryCode,
      phoneNumber = customer.phoneNumber,
    )

    def individualNameOrCompanyName: String = 
      customer.customerType match {
        case CustomerType.Individual =>
            (customer.givenName, customer.familyName) match {
            case (Some(givenName), Some(familyName)) => s"$givenName $familyName"
            case (Some(givenName), None) => givenName
            case (None, Some(familyName)) => familyName
            case (None, None) => customer.email // Fallback to email
            }
        case CustomerType.Business =>
            customer.companyName.getOrElse(customer.email) // Fallback to email
      }

case class ProductOrder(
  id: OrderId,
  customerId: CustomerId,
  productId: ProductId,
  amount: BigDecimal,
  currency: Currency,
  status: OrderStatus,
  paymentProvider: Option[PaymentProvider] = None,
  paymentId: Option[String] = None,
  language: BackendCompatibleLanguage,
  invoiceNumber: Option[String] = None,
  productMetadataId: Option[Long] = None,
  createdAt: Instant,
  updatedAt: Instant
)

case class InvoiceCounter(
  id: Long,
  currentNumber: Long,
  startingNumber: Long,
  updatedAt: Instant,
  createdAt: Instant
)

enum OrderStatus:
  case Pending, Processing, Confirmed, PaidOut, Failed, Cancelled

enum PaymentProvider:
  case GoCardless, Unknown

object PaymentProvider:
  def fromString(s: String): Option[PaymentProvider] =
    PaymentProvider.values.find(_.toString == s)

enum Currency:
  case EUR, USD

object Currency:
  def fromString(s: String): Option[Currency] =
    Currency.values.find(_.toString == s)

case class PurchaseIntent(
  token: PurchaseToken,
  productId: ProductId,
  amount: BigDecimal,
  currency: Currency,
  authCode: String,
  customerId: CustomerId,
  processed: Boolean = false,
  productMetadataId: Option[Long] = None,
  expiresAt: Instant,
  createdAt: Instant
)

case class AuthenticationCode(
  email: String,
  code: String,
  isNewUser: Boolean,
  productName: String,
  amount: BigDecimal
)

case class EmailResult(success: Boolean, message: String)

// GoCardless webhook models
case class GoCardlessWebhookPayload(
  events: List[GoCardlessWebhookEvent]
)

case class GoCardlessWebhookEvent(
  id: String,
  created_at: Instant,
  action: String,
  resource_type: String,
  links: Map[String, String],
  details: Option[Map[String, String]] = None,
  customer_notifications: Option[List[GoCardlessCustomerNotification]] = None
)

case class GoCardlessCustomerNotification(
  id: String,
  `type`: String,
  deadline: Instant,
  mandatory: Boolean
)

// Circe encoders/decoders
object Codecs:
    

  given Encoder[CustomerId] = Encoder[UUID].contramap(_.value)
  given Decoder[CustomerId] = Decoder[UUID].map(CustomerId.apply)

  given Encoder[OrderStatus] = Encoder[String].contramap(_.toString)
  given Decoder[OrderStatus] = Decoder[String].emap(s => 
    OrderStatus.values.find(_.toString == s).toRight(s"Invalid status: $s")
  )

  given Encoder[PaymentProvider] = Encoder[String].contramap(_.toString)
  given Decoder[PaymentProvider] = Decoder[String].emap(s => 
    PaymentProvider.values.find(_.toString == s).toRight(s"Invalid payment provider: $s")
  )

  given Encoder[Currency] = Encoder[String].contramap(_.toString)
  given Decoder[Currency] = Decoder[String].emap(s => 
    Currency.values.find(_.toString == s).toRight(s"Invalid currency: $s")
  )

  given Encoder[PurchaseIntent] = deriveEncoder
  given Decoder[PurchaseIntent] = deriveDecoder

  given Encoder[Customer] = deriveEncoder
  given Decoder[Customer] = deriveDecoder

  // GoCardless webhook codecs
  given Encoder[GoCardlessWebhookPayload] = deriveEncoder
  given Decoder[GoCardlessWebhookPayload] = deriveDecoder

  given Encoder[GoCardlessWebhookEvent] = deriveEncoder
  given Decoder[GoCardlessWebhookEvent] = deriveDecoder

  given Encoder[GoCardlessCustomerNotification] = deriveEncoder
  given Decoder[GoCardlessCustomerNotification] = deriveDecoder
