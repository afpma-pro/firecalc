/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.ui.daisyui.DaisyUIVerticalForm
import io.taig.babel.Locale
import afpma.firecalc.ui.formgen.*
import afpma.firecalc.ui.instances.*
import afpma.firecalc.i18n.implicits.I18N

import magnolia1.Transl
import afpma.firecalc.payments.shared.api.*

import afpma.firecalc.ui.models.BillingInfo
import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.ui.instances.vertical_form.string_emptyAsDefault_alwaysValid
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto
import afpma.firecalc.payments.shared.i18n.implicits.I18N_PaymentsShared
import com.raquo.laminar.api.L.{*, given}
import afpma.firecalc.engine.utils.VNelString

case class BillingInfoUI()(using Locale):
    
    import vertical_form.given
    import BillingInfo.*
    import DaisyUIVerticalForm.autoOverwriteFieldNames
    import hastranslations.forModule_PaymentsShared.given

    type DF[A] = DaisyUIVerticalForm[A]

    given conditionalFor_GivenName: ConditionalFor[BillingInfo, GivenName] =
        ConditionalFor(_.customer_type == BillableCustomerType.Individual)
    given conditionalFor_FamilyName: ConditionalFor[BillingInfo, FamilyName] =
        ConditionalFor(_.customer_type == BillableCustomerType.Individual)

    given conditionalFor_CompanyName: ConditionalFor[BillingInfo, CompanyName] =
        ConditionalFor(_.customer_type == BillableCustomerType.Business)

    given form_option_GivenName: DaisyUIVerticalForm[Option[GivenName]] =
        given DF[String] = vertical_form.string_emptyAsDefault_alwaysValid
        given Conversion[String, GivenName] = GivenName.apply
        given DF[GivenName] = DaisyUIVerticalForm.formConversionOpaque[GivenName, String]
        given Defaultable[GivenName] = defaultable.string.empty.map(identity)
        DaisyUIVerticalForm
        .conditionalOn[BillingInfo, GivenName](billingInfoVar)
        .withFieldName(I18N_PaymentsShared.billing_info.given_name)

    given form_option_FamilyName: DaisyUIVerticalForm[Option[FamilyName]] =
        given DF[String] = vertical_form.string_emptyAsDefault_alwaysValid
        given Conversion[String, FamilyName] = FamilyName.apply
        given DF[FamilyName] = DaisyUIVerticalForm.formConversionOpaque[FamilyName, String]
        given Defaultable[FamilyName] = defaultable.string.empty.map(identity)
        DaisyUIVerticalForm
        .conditionalOn[BillingInfo, FamilyName](billingInfoVar)
        .withFieldName(I18N_PaymentsShared.billing_info.family_name)

    given form_option_CompanyName: DaisyUIVerticalForm[Option[CompanyName]] =
        given DF[String] = vertical_form.string_emptyAsDefault_alwaysValid
        given Conversion[String, CompanyName] = CompanyName.apply
        given DF[CompanyName] = DaisyUIVerticalForm.formConversionOpaque[CompanyName, String]
        given Defaultable[CompanyName] = defaultable.string.empty.map(identity)
        DaisyUIVerticalForm
        .conditionalOn[BillingInfo, CompanyName](billingInfoVar)
        .withFieldName(I18N_PaymentsShared.billing_info.company_name)

    // Hidden form for version field (user shouldn't see or edit this)
    import afpma.firecalc.ui.models.schema.common.BillingInfo_Version
    given Defaultable[BillingInfo_Version] = Defaultable(BillingInfo_Version(1))
    given ValidateVar[BillingInfo_Version] = ValidateVar.make(_ => VNelString.validUnit)
    given DaisyUIVerticalForm[BillingInfo_Version] =
        DaisyUIVerticalForm.makeFor[BillingInfo_Version](summon[Defaultable[BillingInfo_Version]]): (variable, formConfig) =>
            // Hidden input - version is automatic and should not be visible/editable by user
            input(tpe := "hidden", value <-- variable.signal.map(_.toInt.toString))

    given DaisyUIVerticalForm[BillingInfo] =
        import validatevar.string.validOption_Always
        given DF[String] = vertical_form.string_emptyAsDefault_alwaysValid
        given DF[Option[String]] = DaisyUIVerticalForm.forOptionString_default
        
        DaisyUIVerticalForm.autoDerived[BillingInfo]
        .autoOverwriteFieldNames

    lazy val _form = billingInfoVar.as_HtmlElement