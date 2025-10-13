/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.transformers

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import afpma.firecalc.ui.models.schema.v1.BillingInfo_V1
import afpma.firecalc.payments.shared.api.v1.{CustomerInfo_V1, CustomerType, BackendCompatibleLanguage, CountryCode_ISO_3166_1_ALPHA_2}
import afpma.firecalc.ui.models.{BillableCustomerType, BillableCountry}

/**
 * Transformers between UI billing data and payment API contracts.
 * 
 * BillingInfo_V1 and CustomerInfo_V1 are independently versioned:
 * - BillingInfo_V1 updates when UI form fields change
 * - CustomerInfo_V1 updates when payment API requirements change
 * 
 * This transformer must be updated when either version changes.
 */
object PaymentTransformers:

    /**
     * Transform UI billing form (V1) to payment API contract (V1).
     * 
     * This transformation happens on-demand when creating purchase intents,
     * not during normal UI state updates to localStorage.
     */
    given billingInfo_V1_to_customerInfo_V1: Transformer[BillingInfo_V1, CustomerInfo_V1] =
        Transformer.define[BillingInfo_V1, CustomerInfo_V1]
            .withFieldComputed(_.email, _.email)
            .withFieldComputed(_.customerType, bi =>
                bi.customer_type match
                    case BillableCustomerType.Individual => CustomerType.Individual
                    case BillableCustomerType.Business => CustomerType.Business
            )
            .withFieldComputed(_.language, _ => 
                BackendCompatibleLanguage.French) // Default, should come from locale
            .withFieldComputed(_.givenName, _.given_name.map(_.unwrap))
            .withFieldComputed(_.familyName, _.family_name.map(_.unwrap))
            .withFieldComputed(_.companyName, _.company_name.map(_.unwrap))
            .withFieldComputed(_.addressLine1, bi => 
                if (bi.address_line1.isEmpty) None else Some(bi.address_line1))
            .withFieldComputed(_.addressLine2, _.address_line2)
            .withFieldComputed(_.addressLine3, _.address_line3)
            .withFieldComputed(_.city, bi => 
                if (bi.city.isEmpty) None else Some(bi.city))
            .withFieldComputed(_.region, _.region)
            .withFieldComputed(_.postalCode, bi => 
                if (bi.postal_code.isEmpty) None else Some(bi.postal_code))
            .withFieldComputed(_.countryCode, bi =>
                bi.country_code match
                    case BillableCountry.France => Some(CountryCode_ISO_3166_1_ALPHA_2.FR)
            )
            .withFieldComputed(_.phoneNumber, _.phone_number)
            .buildTransformer