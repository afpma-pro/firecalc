/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.dto.common.DisplayUnits

import afpma.firecalc.engine.utils.VNelString

import afpma.firecalc.payments.shared.i18n.implicits.I18N_PaymentsShared

import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.BillingInfo

import com.raquo.laminar.api.L.*

import afpma.laminar.form.*
import afpma.laminar.form.Form
import afpma.laminar.form.Form.*
import afpma.laminar.form.daisyui.DaisyUIVertical
import afpma.laminar.form.derivation.FormDerivation
import io.taig.babel.Locale

case class BillingInfoUI()(using DisplayUnits, Locale):

    private val vertical_form = new VerticalFormCommonInstances()
    given FormRenderer        = DaisyUIVertical

    import afpma.laminar.form.i18n.FormI18nExtensions.autoOverwriteFieldNames
    import FormDerivation.given
    import hastranslations.forModule_PaymentsShared.given

    type DF[A] = Form[A]

    given conditionalFor_GivenName : ConditionalFor[BillingInfo, GivenName]  =
        ConditionalFor(_.customer_type == BillableCustomerType.Individual)
    given conditionalFor_FamilyName: ConditionalFor[BillingInfo, FamilyName] =
        ConditionalFor(_.customer_type == BillableCustomerType.Individual)

    given conditionalFor_CompanyName: ConditionalFor[BillingInfo, CompanyName] =
        ConditionalFor(_.customer_type == BillableCustomerType.Business)

    given form_option_GivenName: Form[Option[GivenName]] =
        given DF[String]                    = vertical_form.string_emptyAsDefault_alwaysValid
        given Conversion[String, GivenName] = GivenName.apply
        given DF[GivenName]                 = Form.formConversionOpaque[GivenName, String]
        given Defaultable[GivenName]        = defaultable.string.empty.map(identity)
        FormDerivation
            .conditionalOn[BillingInfo, GivenName](billingInfoVar)
            .withFieldName(I18N_PaymentsShared.billing_info.given_name)

    given form_option_FamilyName: Form[Option[FamilyName]] =
        given DF[String]                     = vertical_form.string_emptyAsDefault_alwaysValid
        given Conversion[String, FamilyName] = FamilyName.apply
        given DF[FamilyName]                 = Form.formConversionOpaque[FamilyName, String]
        given Defaultable[FamilyName]        = defaultable.string.empty.map(identity)
        FormDerivation
            .conditionalOn[BillingInfo, FamilyName](billingInfoVar)
            .withFieldName(I18N_PaymentsShared.billing_info.family_name)

    given form_option_CompanyName: Form[Option[CompanyName]] =
        given DF[String]                      = vertical_form.string_emptyAsDefault_alwaysValid
        given Conversion[String, CompanyName] = CompanyName.apply
        given DF[CompanyName]                 = Form.formConversionOpaque[CompanyName, String]
        given Defaultable[CompanyName]        = defaultable.string.empty.map(identity)
        FormDerivation
            .conditionalOn[BillingInfo, CompanyName](billingInfoVar)
            .withFieldName(I18N_PaymentsShared.billing_info.company_name)

    // Hidden form for version field (user shouldn't see or edit this)
    import afpma.firecalc.ui.models.schema.common.BillingInfo_Version
    given Defaultable[BillingInfo_Version] = Defaultable(BillingInfo_Version(1))
    given ValidateVar[BillingInfo_Version] = ValidateVar.make(_ => VNelString.validUnit)
    given Form[BillingInfo_Version]        =
        Form.makeFor[BillingInfo_Version](summon[Defaultable[BillingInfo_Version]]): (variable, formConfig) =>
            // Hidden input - version is automatic and should not be visible/editable by user
            input(tpe := "hidden", value <-- variable.signal.map(_.toInt.toString))

    given Form[BillingInfo] =
        import ValidateVarCommonInstances.string.validOption_Always
        given DF[String]         = vertical_form.string_emptyAsDefault_alwaysValid
        given DF[Option[String]] = FormDerivation.forOptionString

        FormDerivation.derived[BillingInfo].autoOverwriteFieldNames

    lazy val _form = billingInfoVar.as_HtmlElement
