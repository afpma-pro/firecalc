/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models
import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.instances.*

import com.raquo.laminar.api.L.*

import afpma.laminar.form.*
import afpma.laminar.form.Form.*
import afpma.laminar.form.daisyui.DaisyUIVertical
import io.taig.babel.Locale

object ProjectDescrUI:

    def make()(using Locale, DisplayUnits): ProjectDescrUI = ProjectDescrUI()

    given Defaultable[ProjectDescr]:
        def default = ProjectDescr.empty

case class ProjectDescrUI()(using Locale, DisplayUnits):

    lazy val node = div(
        cls := "flex flex-row flex-wrap items-start justify-start",
        div(cls := "flex-auto flex justify-start", div(cls := "flex-none", form_customer)       ),
        div(cls := "flex-auto flex justify-start", div(cls := "flex-none", form_project_descr)  ),
        div(cls := "flex-auto flex justify-start", div(cls := "flex-none", form_billing_address)),
        div(cls := "flex-auto flex justify-start", div(cls := "flex-none", form_project_address))
    )

    // Customer and address data now come from clientProjectDataVar (client-side only)
    lazy val customer_var        = clientProjectDataVar.zoomLazy(_.customer)((cpd, c) => cpd.copy(customer = c))
    lazy val billing_address_var =
        clientProjectDataVar.zoomLazy(_.billing_address)((cpd, a) => cpd.copy(billing_address = a))
    lazy val project_address_var =
        clientProjectDataVar.zoomLazy(_.project_address)((cpd, a) => cpd.copy(project_address = a))

    private val vertical_form = new VerticalFormCommonInstances()
    import vertical_form.given
    given FormRenderer = DaisyUIVertical

    lazy val form_customer        = customer_var.as_HtmlElement
    lazy val form_project_descr   = project_descr_var.as_HtmlElement
    lazy val form_billing_address =
        given Form[Address] = vertical_form.given_Address.withFieldName(I18N_UI.client_project_data.billing_address)
        billing_address_var.as_HtmlElement
    lazy val form_project_address =
        given Form[Address] = vertical_form.given_Address.withFieldName(I18N_UI.client_project_data.project_address)
        project_address_var.as_HtmlElement

extension (x: ProjectDescr) def nonEmpty: Boolean = !(x == ProjectDescr.empty)
