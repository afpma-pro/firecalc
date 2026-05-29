/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.FireboxAvailabilityExtensions.allows
import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.payments.shared.api.CreatePurchaseIntentResponse

import afpma.firecalc.ui.*
import afpma.firecalc.ui.config.UIConfig
import afpma.firecalc.ui.daisyui.*
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.i18n.implicits.I18N_UI

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*

import io.taig.babel.Locale

sealed trait OrderFlowError
case class OrderFlowBackendDisallowed(typeName: String) extends OrderFlowError
case class OrderFlowGenericError(message: String)       extends OrderFlowError

case class OrderButtonSection(
    currentFireboxSig                  : Signal[Firebox_V4],
    create_purchase_intent_response_var: Var[Option[Either[OrderFlowError, CreatePurchaseIntentResponse]]],
    send_validation_code_btn_shown_sig : Signal[Boolean],
    send_validation_code_btn_active_sig: Signal[Boolean],
    validation_code_sent_sig           : Signal[Boolean],
    billing_email_sig                  : Signal[String],
    onSendCode                         : () => EventStream[Either[OrderFlowError, CreatePurchaseIntentResponse]],
    sendCodeResponseObserver           : Observer[Either[OrderFlowError, CreatePurchaseIntentResponse]]
)                            (using Locale)
    extends Component:

    private val backend_allows_firebox_sig: Signal[Boolean] =
        currentFireboxSig.map(UIConfig.backendAvailability.allows)

    val disabledAttr: HtmlAttr[Boolean] = htmlAttr("disabled", BooleanAsAttrPresenceCodec)

    private val isButtonDisabled: Signal[Boolean] =
        send_validation_code_btn_active_sig
            .combineWith(create_purchase_intent_response_var.signal)
            .combineWith(backend_allows_firebox_sig)
            .map: (btn_active, response, backend_allows) =>
                if !backend_allows then true
                else
                    response match
                        case Some(Left(_)) => false
                        case _             => !btn_active

    private def hiddenUnless(bool_sig: Signal[Boolean]) = bool_sig.map(if (_) "" else "hidden")

    lazy val node =
        DaisyUITooltip (
            ttContent  = div(
                child <-- backend_allows_firebox_sig.map:
                    case false => span(I18N_UI.firebox.order_disabled.tooltip)
                    case true  => emptyNode
            ),
            ttPosition = "tooltip-bottom",
            element    = button(
                cls := "btn btn-block h-24 mt-6",
                cls <-- hiddenUnless(send_validation_code_btn_shown_sig),
                disabledAttr <-- isButtonDisabled,
                onClick.flatMap(_ => onSendCode()) --> sendCodeResponseObserver,
                div(
                    cls := "flex flex-row items-center w-full gap-3",
                    div(cls := "flex-none w-14", ""),
                    div(
                        cls := "flex-none w-14",
                        child <-- create_purchase_intent_response_var.signal
                            .combineWith(validation_code_sent_sig)
                            .map:
                                case (Some(Left(_)), _) =>
                                    lucide.`square-x`(w = 32, h = 32, stroke_width = 2)
                                case (_, true         ) =>
                                    lucide.`square-check`(w = 32, h = 32, stroke_width = 2)
                                case _ =>
                                    lucide.square(w = 32, h = 32, stroke_width = 2)
                    ),
                    div(
                        cls := "flex flex-initial",
                        child <-- create_purchase_intent_response_var.signal
                            .combineWith(validation_code_sent_sig)
                            .combineWith(send_validation_code_btn_active_sig)
                            .combineWith(billing_email_sig)
                            .map: (response, code_sent, btn_active, billing_email) =>
                                response match
                                    case Some(Left(OrderFlowBackendDisallowed(_))) =>
                                        p(
                                            cls := "text-xl",
                                            I18N_UI.firebox.order_disabled.error_message
                                        )
                                    case Some(Left(OrderFlowGenericError(msg)))    =>
                                        p(
                                            cls := "text-xl",
                                            I18N_UI.pdf_ordering.modal.validation.error_prefix
                                                .apply(msg)
                                        )
                                    case _                                         =>
                                        p(
                                            cls := "text-xl",
                                            (code_sent, btn_active) match
                                                case (true, _     ) =>
                                                    I18N_UI.pdf_ordering.modal.validation.code_sent_to
                                                        .apply(billing_email)
                                                case (false, true ) =>
                                                    I18N_UI.pdf_ordering.modal.validation.send_code_to
                                                        .apply(billing_email)
                                                case (false, false) =>
                                                    I18N_UI.pdf_ordering.modal.validation.invalid_email
                                                        .apply(billing_email)
                                        )
                    ),
                    div(cls := "flex-1", ""        )
                )
            )
        )
