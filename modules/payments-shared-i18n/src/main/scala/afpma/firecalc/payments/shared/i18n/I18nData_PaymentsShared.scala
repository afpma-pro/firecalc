/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.shared.i18n

import afpma.firecalc.payments.shared.i18n.I18nData_PaymentsShared.*

import io.taig.babel.StringFormat1

final case class I18nData_PaymentsShared(
    billing_info: BillingInfo,
    products: Products
)

object I18nData_PaymentsShared:
    
    case class BillingInfo(
        form_title: String,
        email: String,
        customer_type: String,
        customer_type_individual: String,
        customer_type_business: String,
        language: String,
        given_name: String,
        family_name: String,
        company_name: String,
        address_line1: String,
        address_line2: String,
        address_line3: String,
        city: String,
        region: String,
        postal_code: String,
        country_code: String,
        phone_number: String,
    )
    
    case class Products(
        pdf_report_EN_15544_2023: ProductTranslation,
        test: ProductTranslation
    )
    
    case class ProductTranslation(
        name: String,
        description: String
    )
