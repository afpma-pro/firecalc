/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.v1

import afpma.firecalc.payments.shared.i18n.*
import afpma.firecalc.payments.shared.i18n.implicits.I18N_PaymentsShared
import afpma.firecalc.ui.models.schema.common.BillingInfo_Version
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import magnolia1.Transl
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto

// Import types from parent models package
import afpma.firecalc.ui.models.BillableCustomerType
import afpma.firecalc.ui.models.BillableCountry
import afpma.firecalc.ui.models.BillingLanguage

/**
 * Billing information for UI forms (Version 1).
 * Persisted in localStorage via AppStateSchema.
 *
 * This data is stored client-side and transforms to CustomerInfo_V1 when calling payment APIs.
 * Not sent to backend for PDF generation, but IS sent to payment backend after transformation.
 */
@Transl(I(_.billing_info.form_title))
case class BillingInfo_V1(
  version: BillingInfo_Version = BillingInfo_Version(1),
  @Transl(I(_.billing_info.customer_type))
  customer_type: BillableCustomerType,
  @Transl(I(_.billing_info.given_name))
  given_name: Option[GivenName] = None,
  @Transl(I(_.billing_info.family_name))
  family_name: Option[FamilyName] = None,
  @Transl(I(_.billing_info.company_name))
  company_name: Option[CompanyName] = None,
  @Transl(I(_.billing_info.address_line1))
  address_line1: String,
  @Transl(I(_.billing_info.address_line2))
  address_line2: Option[String] = None,
  @Transl(I(_.billing_info.address_line3))
  address_line3: Option[String] = None,
  @Transl(I(_.billing_info.city))
  city: String,
  @Transl(I(_.billing_info.region))
  region: Option[String] = None,
  @Transl(I(_.billing_info.postal_code))
  postal_code: String,
  @Transl(I(_.billing_info.country_code))
  country_code: BillableCountry,
  @Transl(I(_.billing_info.email))
  email: String,
  @Transl(I(_.billing_info.phone_number))
  phone_number: Option[String] = None,
)

object BillingInfo_V1:
    
    import afpma.firecalc.ui.instances.circe.{given_Decoder_BillingInfo, given_Encoder_BillingInfo}
    
    // Re-export circe instances
    export afpma.firecalc.ui.instances.circe.given_Decoder_BillingInfo
    export afpma.firecalc.ui.instances.circe.given_Encoder_BillingInfo

case class BillingInfoWithLanguage(
    version: BillingInfo_Version = BillingInfo_Version(1),
    language: BillingLanguage,
    customer_type: BillableCustomerType,
    given_name: Option[GivenName] = None,
    family_name: Option[FamilyName] = None,
    company_name: Option[CompanyName] = None,
    address_line1: String,
    address_line2: Option[String] = None,
    address_line3: Option[String] = None,
    city: String,
    region: Option[String] = None,
    postal_code: String,
    country_code: BillableCountry,
    email: String,
    phone_number: Option[String] = None,
)

object BillingInfoWithLanguage:
    
    def fromBillingInfoAndLanguage(
        billing_info: BillingInfo_V1,
        language: BillingLanguage,
    ): BillingInfoWithLanguage = 
        billing_info
            .into[BillingInfoWithLanguage]
            .withFieldConst(_.language, language)
            .transform

opaque type GivenName = String
object GivenName:
    def apply(s: String): GivenName = s
    extension (x: GivenName)
        def unwrap: String = x
    given Conversion[GivenName, String] = identity

opaque type FamilyName = String
object FamilyName:
    def apply(s: String): FamilyName = s
    extension (x: FamilyName)
        def unwrap: String = x
    given Conversion[FamilyName, String] = identity
        
opaque type CompanyName = String
object CompanyName:
    
    def apply(s: String): CompanyName = s
    extension (x: CompanyName)
        def unwrap: String = x
    given Conversion[CompanyName, String] = identity