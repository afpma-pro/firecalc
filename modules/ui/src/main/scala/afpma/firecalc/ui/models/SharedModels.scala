/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import magnolia1.Transl
import io.taig.babel.Locale
import cats.Show
import afpma.firecalc.payments.shared.i18n.implicits.I18N_PaymentsShared
import afpma.firecalc.utils.circe.{*, given}

// Customer type enum
enum BillableCustomerType:
  case Individual, Business

object BillableCustomerType:
    given show: Locale => Show[BillableCustomerType] = Show.show:
        case BillableCustomerType.Individual => I18N_PaymentsShared.billing_info.customer_type_individual
        case BillableCustomerType.Business   => I18N_PaymentsShared.billing_info.customer_type_business

enum BillableCountry(val countryCode_ISO_3166_1_ALPHA_2: String):
    // case US extends CountryCode_ISO_3166_1_ALPHA_2("US")
    // case GB extends CountryCode_ISO_3166_1_ALPHA_2("GB")
    case France extends BillableCountry("FR")
    // case DE extends CountryCode_ISO_3166_1_ALPHA_2("DE")
    // case IT extends CountryCode_ISO_3166_1_ALPHA_2("IT")
    // case ES extends CountryCode_ISO_3166_1_ALPHA_2("ES")
    // case PT extends CountryCode_ISO_3166_1_ALPHA_2("PT")
    // case NL extends CountryCode_ISO_3166_1_ALPHA_2("NL")
    // case BE extends CountryCode_ISO_3166_1_ALPHA_2("BE")
    // case AT extends CountryCode_ISO_3166_1_ALPHA_2("AT")
    // case CH extends CountryCode_ISO_3166_1_ALPHA_2("CH")
    // case LU extends CountryCode_ISO_3166_1_ALPHA_2("LU")

object BillableCountry:

    def fromCountryCode_ISO_3166_1_ALPHA_2(code: String): Option[BillableCountry] =
        BillableCountry.values.find(_.countryCode_ISO_3166_1_ALPHA_2 == code)

    given show: Locale => Show[BillableCountry] = Show.show:
        // case x: CountryCode_ISO_3166_1_ALPHA_2.US.type => x.code
        // case x: CountryCode_ISO_3166_1_ALPHA_2.GB.type => x.code
        case x: BillableCountry.France.type => x.countryCode_ISO_3166_1_ALPHA_2
        // case x: CountryCode_ISO_3166_1_ALPHA_2.DE.type => x.code
        // case x: CountryCode_ISO_3166_1_ALPHA_2.IT.type => x.code
        // case x: CountryCode_ISO_3166_1_ALPHA_2.ES.type => x.code
        // case x: CountryCode_ISO_3166_1_ALPHA_2.PT.type => x.code
        // case x: CountryCode_ISO_3166_1_ALPHA_2.NL.type => x.code
        // case x: CountryCode_ISO_3166_1_ALPHA_2.BE.type => x.code
        // case x: CountryCode_ISO_3166_1_ALPHA_2.AT.type => x.code
        // case x: CountryCode_ISO_3166_1_ALPHA_2.CH.type => x.code
        // case x: CountryCode_ISO_3166_1_ALPHA_2.LU.type => x.code

enum BillingLanguage(val code: String):
    case English extends BillingLanguage("en")
    case French extends BillingLanguage("fr")
    // case German extends BillingLanguage("de")
    // case Portuguese extends BillingLanguage("pt")
    // case Spanish extends BillingLanguage("es")
    // case Italian extends BillingLanguage("it")
    // case Dutch extends BillingLanguage("nl")
    // case Danish extends BillingLanguage("da")
    // case Norwegian extends BillingLanguage("nb")
    // case Slovenian extends BillingLanguage("sl")
    // case Swedish extends BillingLanguage("sv")

object BillingLanguage:
    import io.taig.babel.{Locale, Locales}

    given show: Locale => Show[BillingLanguage] = Show.show:
        case x: English.type     => x.toString      
        case x: French.type      => x.toString      
        // case x: German.type      => x.toString      
        // case x: Portuguese.type  => x.toString          
        // case x: Spanish.type     => x.toString      
        // case x: Italian.type     => x.toString      
        // case x: Dutch.type       => x.toString  
        // case x: Danish.type      => x.toString      
        // case x: Norwegian.type   => x.toString      
        // case x: Slovenian.type   => x.toString      
        // case x: Swedish.type     => x.toString      
    
    /** Default language used as fallback throughout the application */
    val DefaultLanguage: BillingLanguage = French
    
    /** Convert BillableLanguage to Babel Locale for i18n translations */
    extension (lang: BillingLanguage)
        def toLocale: Locale = lang match
            case English    => Locales.en
            case French     => Locales.fr
            // case _ => Locales.en // Default fallback to English for unsupported languages

    implicit def billingLanguageToLocale(implicit bcl: BillingLanguage): Locale = bcl.toLocale
    
    /** Parse language code string to BillingLanguage */
    def fromCode(code: String): Option[BillingLanguage] =
        BillingLanguage.values.find(_.code == code)
    
    /** Parse language code string with fallback to default language */
    def fromCodeWithFallback(code: String): BillingLanguage =
        fromCode(code).getOrElse(DefaultLanguage)