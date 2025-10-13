/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import io.taig.babel.Locale
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.models.{BillingInfoWithLanguage, GivenName, FamilyName, CompanyName}
import afpma.firecalc.ui.models.schema.v1.BillingInfo_V1
import afpma.firecalc.payments.shared.api.*

object transformers:

    /** Transform BillableCustomerType to CustomerType */
    given Transformer[BillableCustomerType, CustomerType] = {
        case BillableCustomerType.Individual => CustomerType.Individual
        case BillableCustomerType.Business => CustomerType.Business
    }

    /** Transform BillableCountry to CountryCode_ISO_3166_1_ALPHA_2 */
    given Transformer[BillableCountry, CountryCode_ISO_3166_1_ALPHA_2] = {
            case BillableCountry.France => CountryCode_ISO_3166_1_ALPHA_2.FR
        }

    /** Transform BillingLanguage to BackendCompatibleLanguage */
    given Transformer[BillingLanguage, BackendCompatibleLanguage] =  {
            case BillingLanguage.English => BackendCompatibleLanguage.English
            case BillingLanguage.French => BackendCompatibleLanguage.French
        }

    /** Transform Locale to BillableCountry */
    given Transformer[Locale, BillableCountry] = { locale =>
            import io.taig.babel.Locales
            locale match
                case Locales.fr => BillableCountry.France
                // Default case: when other BillableCountry values are added 
                // (e.g., US, GB, DE, etc.), add corresponding locale matches here
                case _ => BillableCountry.France // Default to France for unhandled locales
        }
    
    /** Transform Locale to BillingLanguage (defaults to French) */
    given Transformer[Locale, BillingLanguage] = { locale =>
            // Extract language code from locale
            locale.toString.take(2).toLowerCase match
                case "en" => BillingLanguage.English
                case "fr" => BillingLanguage.French
                case _ => BillingLanguage.French // Default to French
        }

    /** Transform BillingInfo to CustomerInfo */
    given billingInfoToCustomerInfo: Transformer[BillingInfo, CustomerInfo] = 
        Transformer.define[BillingInfo, CustomerInfo]
            .withFieldRenamed(_.customer_type, _.customerType)
            .withFieldComputed(_.language, _ => BackendCompatibleLanguage.French) // Default language
            .withFieldComputed(_.givenName, _.given_name.map(_.unwrap))
            .withFieldComputed(_.familyName, _.family_name.map(_.unwrap))
            .withFieldComputed(_.companyName, _.company_name.map(_.unwrap))
            .withFieldComputed(_.addressLine1, x => if (x.address_line1.isEmpty) None else Some(x.address_line1))
            .withFieldComputed(_.addressLine2, _.address_line2)
            .withFieldComputed(_.addressLine3, _.address_line3)
            .withFieldComputed(_.city, x => if (x.city.isEmpty) None else Some(x.city))
            .withFieldComputed(_.region, _.region)
            .withFieldComputed(_.postalCode, x => if (x.postal_code.isEmpty) None else Some(x.postal_code))
            .withFieldComputed(_.countryCode, x => Some(x.country_code.transformInto[CountryCode_ISO_3166_1_ALPHA_2]))
            .withFieldRenamed(_.phone_number, _.phoneNumber)
            .buildTransformer

    /** Transform BillingInfoWithLanguage to CustomerInfo - directly without intermediate BillingInfo */
    given Transformer[BillingInfoWithLanguage, CustomerInfo] = { withLang =>
            // Create BillingInfo_V1 manually from BillingInfoWithLanguage (dropping language field)
            val billingInfo = new afpma.firecalc.ui.models.schema.v1.BillingInfo_V1(
                version = withLang.version,
                customer_type = withLang.customer_type,
                given_name = withLang.given_name,
                family_name = withLang.family_name,
                company_name = withLang.company_name,
                address_line1 = withLang.address_line1,
                address_line2 = withLang.address_line2,
                address_line3 = withLang.address_line3,
                city = withLang.city,
                region = withLang.region,
                postal_code = withLang.postal_code,
                country_code = withLang.country_code,
                email = withLang.email,
                phone_number = withLang.phone_number
            )
            // Transform to CustomerInfo and override language
            billingInfo.transformInto[CustomerInfo]
                .copy(language = withLang.language.transformInto[BackendCompatibleLanguage])
        }
