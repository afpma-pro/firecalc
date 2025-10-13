/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository.impl

import molecule.DomainStructure
import afpma.firecalc.payments.domain
import afpma.firecalc.payments.shared.api.{CustomerInfo, CustomerType, BackendCompatibleLanguage, CountryCode_ISO_3166_1_ALPHA_2}
import java.time.Instant
import java.util.UUID
import afpma.firecalc.payments.repository.impl.MoleculeDomain.PaymentProvider

object MoleculeDomain extends DomainStructure:
    enum PaymentProvider:
        case GoCardless, Unknown

    enum Currency:
        case EUR, USD

    trait Product {
        val productId    = oneUUID
        val name         = oneString
        val description  = oneString
        val price        = oneBigDecimal
        val currency     = oneEnum[Currency]
        val active       = oneBoolean
    }

    trait ProductOrder {
        
        enum OrderStatus:
            case Pending, Processing, Confirmed, PaidOut, Failed, Cancelled

        val orderId              = oneUUID
        val customerId           = oneUUID
        val productId            = oneUUID
        val amount               = oneBigDecimal
        val currency             = oneEnum[Currency]
        val status               = oneEnum[OrderStatus]
        val paymentProvider      = oneEnum[PaymentProvider]
        val paymentId            = oneString
        val language             = oneString
        val invoiceNumber        = oneString  // Optional invoice number
        val productMetadata      = one[ProductMetadata]
        val createdAt            = oneInstant
        val updatedAt            = oneInstant
    }

    trait Customer {
        
        enum CustomerType:
            case Individual, Business

        // Mandatory fields
        val customerId   = oneUUID.unique    // External customer UUID
        val email        = oneString.unique  // Unique by email
        val customerType = oneEnum[CustomerType]
        val language     = oneString
        
        // Optional personal info (mandatory for Individual, optional for Business)
        val givenName    = oneString
        val familyName   = oneString
        
        // Optional company info (mandatory for Business, optional for Individual)  
        val companyName  = oneString
        
        // Optional address fields (all optional)
        val addressLine1 = oneString
        val addressLine2 = oneString
        val addressLine3 = oneString
        val city         = oneString
        val region       = oneString
        val postalCode   = oneString
        val countryCode  = oneString // ISO 3166-1 alpha-2 code
        
        // Optional contact info
        val phoneNumber  = oneString
        
        // Payment provider fields (optional) - for payment provider agnostic approach
        val paymentProviderId = oneString  // GoCardless customer ID, Stripe customer ID, etc.
        val paymentProvider   = oneEnum[PaymentProvider]  // "GoCardless", "Stripe", etc.
        
        // Metadata for extensibility (as Map)
        // val metadata     = mapString
        
        // Audit fields
        val createdAt    = oneInstant
        val updatedAt    = oneInstant
    }
        
    trait PurchaseIntent {
        val token           = oneUUID
        val productId       = oneUUID
        val amount          = oneBigDecimal
        val currency        = oneEnum[Currency]
        val authCode        = oneString
        val customer        = one[Customer]
        val processed       = oneBoolean 
        val productMetadata = one[ProductMetadata]
        val expiresAt       = oneInstant
        val createdAt       = oneInstant
    }

    trait ProductMetadata {
        val jsonString = oneString
        val createdAt  = oneInstant
    }

    trait InvoiceCounter {
        val currentNumber = oneLong  
        val startingNumber = oneLong
        val updatedAt = oneInstant
        val createdAt = oneInstant
    }

    
object CustomerSyntax:
    
    /** Convert empty strings to None for optional fields */
    private def stringToOption(s: String): Option[String] =
        if s.nonEmpty then Some(s) else None

    /** Safely parse CustomerType from string with fallback */
    private def parseCustomerType(customerTypeStr: String): CustomerType =
        CustomerType.values.find(_.toString == customerTypeStr)
            .getOrElse(CustomerType.Individual)

    /** Safely parse PaymentProvdier from string with fallback */
    extension (dpp: domain.PaymentProvider)
        def asMoleculePaymentProviderUnsafe: PaymentProvider =
            val mpp = dpp.toString
            PaymentProvider.values.find(_.toString == mpp)
                .getOrElse(throw new IllegalStateException(s"invalid molecule payment provider : $mpp"))

    /** Extension method to convert Molecule query result tuple to domain.Customer */
    extension (tuple: (UUID, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, Instant, Instant))
        def toDomainCustomer: domain.Customer = 
            val (customerId, email, customerType, language, givenName, familyName, companyName, 
                    addressLine1, addressLine2, addressLine3, city, region, postalCode, countryCode, 
                    phoneNumber, paymentProviderId, paymentProvider, createdAt, updatedAt) = tuple

            domain.Customer(
                id = domain.CustomerId(customerId),
                email = email,
                customerType = parseCustomerType(customerType),
                language = BackendCompatibleLanguage.fromCodeWithFallback(language),
                givenName = stringToOption(givenName),
                familyName = stringToOption(familyName),
                companyName = stringToOption(companyName),
                addressLine1 = stringToOption(addressLine1),
                addressLine2 = stringToOption(addressLine2),
                addressLine3 = stringToOption(addressLine3),
                city = stringToOption(city),
                region = stringToOption(region),
                postalCode = stringToOption(postalCode),
                countryCode = CountryCode_ISO_3166_1_ALPHA_2.fromString(countryCode),
                phoneNumber = stringToOption(phoneNumber),
                paymentProviderId = stringToOption(paymentProviderId),
                paymentProvider = stringToOption(paymentProvider).flatMap(domain.PaymentProvider.fromString),
                createdAt = createdAt,
                updatedAt = updatedAt
            )

    /** Extension method for tuples with Option[String] fields (for create method) */
    extension (tuple: (UUID, String, CustomerType, BackendCompatibleLanguage, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[CountryCode_ISO_3166_1_ALPHA_2], Option[String], Option[String], Option[domain.PaymentProvider], Instant))
        def toDomainCustomer: domain.Customer = 
            val (customerId, email, customerType, language, givenName, familyName, companyName, 
                    addressLine1, addressLine2, addressLine3, city, region, postalCode, countryCode, 
                    phoneNumber, paymentProviderId, paymentProvider, now) = tuple

            domain.Customer(
                id = domain.CustomerId(customerId),
                email = email,
                customerType = customerType,
                language = language,
                givenName = givenName,
                familyName = familyName,
                companyName = companyName,
                addressLine1 = addressLine1,
                addressLine2 = addressLine2,
                addressLine3 = addressLine3,
                city = city,
                region = region,
                postalCode = postalCode,
                countryCode = countryCode,
                phoneNumber = phoneNumber,
                paymentProviderId = paymentProviderId,
                paymentProvider = paymentProvider,
                createdAt = now,
                updatedAt = now
            )

    /** Extension method for CustomerInfo to create database insertion tuple and domain Customer */
    extension (customerInfo: CustomerInfo)
        def toDomainCustomer(customerId: UUID, now: Instant): domain.Customer =
            domain.Customer(
                id = domain.CustomerId(customerId),
                email = customerInfo.email,
                customerType = customerInfo.customerType,
                language = customerInfo.language,
                givenName = customerInfo.givenName,
                familyName = customerInfo.familyName,
                companyName = customerInfo.companyName,
                addressLine1 = customerInfo.addressLine1,
                addressLine2 = customerInfo.addressLine2,
                addressLine3 = customerInfo.addressLine3,
                city = customerInfo.city,
                region = customerInfo.region,
                postalCode = customerInfo.postalCode,
                countryCode = customerInfo.countryCode,
                phoneNumber = customerInfo.phoneNumber,
                paymentProviderId = None,
                paymentProvider = None,
                createdAt = now,
                updatedAt = now
            )
