/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import scala.scalajs.js

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.FireCalcProjet
import afpma.firecalc.ui.config.{UIConfig, ViteEnv, BuildMode}
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.utils.PaymentsBackendApiConnectivity
import afpma.firecalc.ui.instances.transformers.given

import com.raquo.laminar.api.L.*
import io.taig.babel.Locale
import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.FireCalcYAML
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*
import com.raquo.airstream.web.*
import io.taig.babel.Language
import org.scalajs.dom
import org.scalajs.dom.HTMLDialogElement
import afpma.firecalc.ui.daisyui.*
import afpma.firecalc.payments.shared.api.*

case class OrderPDFReportModalComponent()(using Locale) extends Component:

    private val modal_id = "order_pdf_modal"

    protected final case class PDFReportOrderingState(
        cgv_accepted: Boolean = false,
        // send_validation_code_btn_shown: Boolean = false,
        // send_validation_code_btn_active: Boolean = false,
        validation_code_sent: Boolean = false,
        six_digits_code: Option[String] = None,
        validate_six_digit_code_btn_active: Boolean = false,
        validation_code_valid: Boolean = false,
        purchase_token: Option[String] = None,
        payment_link: Option[String] = None,
    )

    object PDFReportOrderingState:
        val init = PDFReportOrderingState(cgv_accepted = false)

    private val pdf_report_ordering_var = Var[PDFReportOrderingState](PDFReportOrderingState.init)
    
    private val cgv_accepted_sig = pdf_report_ordering_var.signal.map(_.cgv_accepted)

    // private val send_validation_code_btn_shown_var = pdf_report_ordering_var.zoomLazy(_.send_validation_code_btn_shown)((x, a) => x.copy(send_validation_code_btn_shown = a))
    // private val send_validation_code_btn_shown_sig = send_validation_code_btn_shown_var.signal

    // private val send_validation_code_btn_active_var = pdf_report_ordering_var.zoomLazy(_.send_validation_code_btn_active)((x, a) => x.copy(send_validation_code_btn_active = a))
    // private val send_validation_code_btn_active_sig = send_validation_code_btn_active_var.signal


    private val validation_code_sent_sig = pdf_report_ordering_var.signal.map(_.validation_code_sent)

    private val verify_and_process_response_var = 
        Var[Option[Either[String, VerifyAndProcessResponse]]](None)

    private val six_digits_code_var = pdf_report_ordering_var.zoomLazy(_.six_digits_code)((x, c) => x.copy(six_digits_code = c))

    private val pdf_report_ordering_sig = pdf_report_ordering_var.signal

    private val backend_polling_active_sig = cgv_accepted_sig

    // Connectivity checking - only poll when CGV is accepted
    private val connectivity_details_sig = PaymentsBackendApiConnectivity.createDetailedConnectivitySignal(
        backend_polling_active_sig
    )
    private val connectivity_sig = connectivity_details_sig.map:
        case PaymentsBackendApiConnectivity.FullConnection(_, _) => true
        case _ => false

    val billing_email_sig = billingInfoVar.signal.map(_.email)
    val billing_email_valid_sig = billing_email_sig.map { email => 
        if (email != "" && email.contains("@")) true else false 
    }

    val send_validation_code_btn_shown_sig = connectivity_sig

    val send_validation_code_btn_active_sig =
        cgv_accepted_sig
        .combineWith(connectivity_sig)
        .combineWith(validation_code_sent_sig)
        .combineWith(billing_email_valid_sig)
        .map { (cgv_accepted, conn, validation_code_sent, email_valid) => 
            cgv_accepted && conn && !validation_code_sent && email_valid
        }

    val send_validation_code_btn_clicked_bus = new EventBus[Unit]
    val send_validation_code_btn_clicked_stream = send_validation_code_btn_clicked_bus.events

    // GLOBAL BINDERS
    private val binders: Seq[Binder.Base] = Seq(
        // when clicked, send VerifyAndProcessRequest to backend, and handle response.
        // Retrieve VerifyAndProcessResponse and put it into verify_and_process_response_var
        send_validation_code_btn_clicked_bus.events.flatMapSwitch(_ => makeVerifyAndProcessRequest()) --> handleVerifyAndProcessResponse()
    )

    val disabledAttr: HtmlAttr[Boolean]   = htmlAttr("disabled"     , BooleanAsAttrPresenceCodec)

    private def hiddenUnless(bool_sig: Signal[Boolean]) = bool_sig.map(if (_) "" else "hidden")

    // Imports for API requests
    import io.circe.*
    import io.circe.syntax.*
    import io.circe.parser.*
    import io.scalaland.chimney.dsl.*
    import java.util.UUID
    import scala.scalajs.js
    import scala.util.{Try, Success, Failure}

    /** Convert AppState (FireCalcYAML) to base64-encoded YAML string using UTF-8 safe encoding.
      * Uses TextEncoder to properly handle Unicode characters (e.g., Greek letters like ζ)
      * that are outside the Latin1 range, which standard btoa() cannot handle.
      */
    private def convertProjectToBase64(fireCalcYaml: FireCalcYAML): Try[String] =
        // Use CustomYAMLEncoderDecoder to convert to YAML string
        AppState.encodeToYaml(fireCalcYaml).flatMap { yamlString =>
            Try {
                // Use TextEncoder to convert UTF-8 string to bytes
                val textEncoder = js.Dynamic.newInstance(js.Dynamic.global.TextEncoder)()
                val utf8Bytes = textEncoder.encode(yamlString)
                
                // Convert Uint8Array bytes to binary string
                val byteArray = utf8Bytes.asInstanceOf[js.typedarray.Uint8Array]
                val binString = (0 until byteArray.length).map { i =>
                    js.Dynamic.global.String.fromCodePoint(byteArray(i)).asInstanceOf[String]
                }.mkString("")
                
                // Encode binary string to base64
                js.Dynamic.global.btoa(binString).asInstanceOf[String]
            }
        }

    def makePurchaseCreateIntentRequest()(using locale: Locale): EventStream[Either[String, CreatePurchaseIntentResponse]] =
        val billingInfo = billingInfoVar.now()
        
        // Get language from current locale
        val language: BillingLanguage = locale.transformInto[BillingLanguage]
        
        // Select product ID based on build mode
        val productId = ViteEnv.buildMode match
            case BuildMode.Development  => v1.DevelopmentProductCatalog.PDF_REPORT_EN_15544_2023.id
            case BuildMode.Staging      => v1.StagingProductCatalog.PDF_REPORT_EN_15544_2023.id
            case BuildMode.Production   => v1.ProductionProductCatalog.PDF_REPORT_EN_15544_2023.id
        
        // Use transformers to convert BillingInfo + language to CustomerInfo
        val billingInfoWithLanguage = BillingInfoWithLanguage.fromBillingInfoAndLanguage(
            billing_info = billingInfo,
            language = language
        )
        
        val customerInfo: CustomerInfo = billingInfoWithLanguage.transformInto[CustomerInfo]
        
        // Convert current project state to base64-encoded YAML
        val currentProject = appStateVar.now()
        val productMetadataResult: Either[String, FileDescriptionWithContent] = convertProjectToBase64(currentProject) match
            case Success(base64Content) =>
                Right(FileDescriptionWithContent(
                    filename = "project.firecalc.yaml",
                    mimeType = "application/yaml",
                    content = base64Content
                ))
            case Failure(error) =>
                val errorMsg = s"Failed to convert project to base64: ${error.getMessage}"
                dom.console.error(errorMsg)
                // dom.console.error(error.getStackTrace().toList.mkString("\n"))
                Left(errorMsg)
        
        // If conversion failed, return error immediately
        productMetadataResult match
            case Left(errorMsg) =>
                return EventStream.fromValue(Left(errorMsg), emitOnce = true)
            case Right(_) =>
                () // Continue with request
        
        val createPurchaseIntentRequest = CreatePurchaseIntentRequest(
            productId = productId,
            productMetadata = productMetadataResult.toOption,
            customer = customerInfo
        )
        
        // Create the request body as JSON string
        val requestBody = createPurchaseIntentRequest.asJson.noSpaces
        
        // Make the POST request with proper headers and handle both success and error responses
        FetchStream
            .post(
                url = UIConfig.Endpoints.createPurchaseIntent,
                init => {
                    init.body(requestBody)
                    init.headers("Content-Type" -> "application/json")
                }
            )
            .recoverToTry
            .map { tryResponse =>
                tryResponse match
                    case scala.util.Success(responseText) =>
                        // Try to decode as success response first
                        decode[CreatePurchaseIntentResponse](responseText) match
                            case Right(response) => Right(response)
                            case Left(_) =>
                                // If that fails, try to decode as error response
                                decode[ErrorResponseEnvelope](responseText) match
                                    case Right(errorEnvelope) => 
                                        Left(s"${errorEnvelope.error}: ${errorEnvelope.message}")
                                    case Left(decodeError) => 
                                        Left(s"Failed to decode response : ${decodeError.getMessage}\n=> Response:\n'${responseText}'")
                    case scala.util.Failure(fetchError) =>
                        Left(s"Network error: ${fetchError.getMessage}")
            }

    def handlePurchaseCreateIntentResponse(): Observer[Either[String, CreatePurchaseIntentResponse]] = 
        Observer[Either[String, CreatePurchaseIntentResponse]] { 
            case Right(response) =>
                // Store the purchase token in the state on success
                pdf_report_ordering_var.update(_.copy(
                    purchase_token = Some(response.purchase_token),
                    validation_code_sent = true
                ))
            case Left(errorMessage) =>
                // Log error and keep validation_code_sent as false
                dom.console.error(s"Purchase intent creation failed: $errorMessage")
                // Reset state to allow retry
                pdf_report_ordering_var.update(_.copy(
                    validation_code_sent = false,
                    purchase_token = None
                ))
        }

    def makeVerifyAndProcessRequest(): EventStream[Either[String, VerifyAndProcessResponse]] =
        // Get purchase token, verification code, and email from state
        val purchaseTokenStr = pdf_report_ordering_var.now().purchase_token
        val verificationCode = six_digits_code_var.now()
        val billingEmail = billingInfoVar.now().email
        
        // Validate that we have all required fields
        (purchaseTokenStr, verificationCode) match
            case (None, _) =>
                val errorMsg = "No purchase token available. Please send validation code first."
                dom.console.error(errorMsg)
                return EventStream.fromValue(Left(errorMsg), emitOnce = true)
            case (_, None) =>
                val errorMsg = "No verification code entered. Please enter the 6-digit code."
                dom.console.error(errorMsg)
                return EventStream.fromValue(Left(errorMsg), emitOnce = true)
            case (Some(tokenStr), Some(code)) if code.length != 6 =>
                val errorMsg = s"Invalid verification code length: ${code.length}. Must be 6 digits."
                dom.console.error(errorMsg)
                return EventStream.fromValue(Left(errorMsg), emitOnce = true)
            case (Some(tokenStr), Some(code)) =>
                // Parse token string to UUID and create PurchaseToken
                val purchaseToken = Try(PurchaseToken(UUID.fromString(tokenStr))) match
                    case Success(pt) => pt
                    case Failure(error) =>
                        val errorMsg = s"Invalid purchase token format: ${error.getMessage}"
                        dom.console.error(errorMsg)
                        return EventStream.fromValue(Left(errorMsg), emitOnce = true)
                
                // Valid inputs, continue with request
                val verifyRequest = VerifyAndProcessRequest(
                    purchaseToken = purchaseToken,
                    email = billingEmail,
                    code = code
                )
                
                // Create the request body as JSON string
                val requestBody = verifyRequest.asJson.noSpaces
                
                // Make the POST request with proper headers and handle both success and error responses
                FetchStream
                    .post(
                        url = UIConfig.Endpoints.verifyAndProcess,
                        init => {
                            init.body(requestBody)
                            init.headers("Content-Type" -> "application/json")
                        }
                    )
                    .recoverToTry
                    .map { tryResponse =>
                        tryResponse match
                            case scala.util.Success(responseText) =>
                                // Try to decode as success response first
                                decode[VerifyAndProcessResponse](responseText) match
                                    case Right(response) => Right(response)
                                    case Left(_) =>
                                        // If that fails, try to decode as error response
                                        decode[ErrorResponseEnvelope](responseText) match
                                            case Right(errorEnvelope) => 
                                                Left(s"${errorEnvelope.error}: ${errorEnvelope.message}")
                                            case Left(decodeError) => 
                                                Left(s"Failed to decode response: ${decodeError.getMessage}\n=> Response:\n'${responseText}'")
                            case scala.util.Failure(fetchError) =>
                                Left(s"Network error: ${fetchError.getMessage}")
                    }

    def handleVerifyAndProcessResponse(): Observer[Either[String, VerifyAndProcessResponse]] = 
        Observer[Either[String, VerifyAndProcessResponse]] { 
            case Right(response) =>
                // Store the response in the state on success
                verify_and_process_response_var.set(Some(Right(response)))
                // dom.console.log(s"Verification successful. Payment URL: ${response.paymentUrl}")
            case Left(errorMessage) =>
                // Log error and store in state for display
                // dom.console.error(s"Verification failed: $errorMessage")
                verify_and_process_response_var.set(Some(Left(errorMessage)))
        }

    
    lazy val node =
        div(
            div(
                cls    := "flex items-stretch gap-2",
                DaisyUITooltip(
                    ttContent = div(
                         text <-- all_conditions_and_results_satisfied_sig.map(
                            if (_) I18N_UI.tooltips.order_pdf_report else I18N_UI.tooltips.order_pdf_report_not_possible
                        )
                    ),
                    element = div(
                        tabIndex := 0,
                        role     := "button",
                        disabledAttr <-- all_conditions_and_results_not_satisfied_sig,
                        cls      := "btn btn-outline hover:btn-secondary rounded-field",
                        cls(
                            "text-base-content"
                        ) <-- all_conditions_and_results_satisfied_sig,
                        cls(
                            "text-base-content/40 hover:text-base-content"
                        ) <-- all_conditions_and_results_not_satisfied_sig,
                        div(
                            cls := "h-4 flex items-center justify-center",
                            I18N_UI.buttons.order_pdf_report
                        ),
                        div(
                            cls := "w-4 h-4 flex items-center justify-center",
                            lucide.`tag`(stroke_width = 1.5)
                        ),
                        onClick --> { _ =>
                            dom.document
                                .getElementById(modal_id)
                                .asInstanceOf[HTMLDialogElement]
                                .showModal()
                        }
                    ),
                    ttPosition = "tooltip-bottom"
                )
            ),
            dialogTag(
                idAttr := modal_id,
                cls    := "modal",
                onMountUnmountCallbackWithState[HtmlElement, js.Function1[dom.Event, Unit]](
                    mount = ctx => {
                        val dialog = ctx.thisNode.ref.asInstanceOf[HTMLDialogElement]
                        val closeHandler: js.Function1[dom.Event, Unit] = _ => {
                            pdf_report_ordering_var.set(PDFReportOrderingState.init)
                        }
                        dialog.addEventListener("close", closeHandler)
                        closeHandler
                    },
                    unmount = (thisNode, maybeHandler) => {
                        maybeHandler.foreach { handler =>
                            val dialog = thisNode.ref.asInstanceOf[HTMLDialogElement]
                            dialog.removeEventListener("close", handler)
                        }
                    }
                ),
                div(
                    cls := "modal-box w-8/12 max-w-5xl max-h-10/12",
                    h3(
                        cls := "text-lg font-bold",
                        I18N_UI.pdf_ordering.modal.title
                    ),
                    div(
                        cls := "py-4",
                        p(I18N_UI.pdf_ordering.modal.report_compliant_with_standard + " ", b("EN 15544:2023")),
                        br(),
                        p(I18N_UI.pdf_ordering.modal.report_will_be_sent_to_email),
                        br(),
                        p(b(I18N_UI.pdf_ordering.modal.report_price)),
                        br(),
                        ul(
                            I18N_UI.pdf_ordering.modal.order_steps_title,
                            li(
                                cls := "mt-4",
                                I18N_UI.pdf_ordering.modal.order_step_1
                            ),
                            li(I18N_UI.pdf_ordering.modal.order_step_2),
                            li(I18N_UI.pdf_ordering.modal.order_step_3),
                            li(I18N_UI.pdf_ordering.modal.order_step_4)
                        ),
                        div(cls := "divider"),
                        BillingInfoUI()._form,

                        // CGV

                        button(
                            cls := "btn btn-block h-24 mt-6",
                            onClick
                                .compose(
                                    _.withCurrentValueOf(cgv_accepted_sig).collect { case (_, curr) => curr }
                                ) --> { curr =>
                                    if (curr == false)
                                        pdf_report_ordering_var.update(_.copy(cgv_accepted = true))
                                    else
                                        pdf_report_ordering_var.update(_.copy(cgv_accepted = false))
                                },
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(cls := "flex-none w-14",
                                    child <-- cgv_accepted_sig.map: cgv_accepted =>
                                        if (cgv_accepted)
                                            lucide.`square-check`(w = 32, h = 32, stroke_width = 2)
                                        else
                                            lucide.square(w = 32, h = 32, stroke_width = 2)
                                ),
                                div(cls := "flex flex-initial", p(cls := "text-xl", I18N_UI.pdf_ordering.modal.accept_terms_and_conditions)),
                                div(cls := "flex-1", ""),
                            )
                        ),
                        
                        // Connectivity : Internet + Backend

                        button(
                            cls := "btn btn-block h-24 mt-6",
                            disabledAttr := true,
                            cls <-- hiddenUnless(backend_polling_active_sig),
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(cls := "flex-none w-14",
                                    child <-- connectivity_details_sig.map:
                                        case PaymentsBackendApiConnectivity.FullConnection(_, _) =>
                                            lucide.`square-check`(w = 32, h = 32, stroke_width = 2)
                                        case _ =>
                                            lucide.square(w = 32, h = 32, stroke_width = 2)
                                ),
                                div(cls := "flex flex-initial",
                                    p(
                                        cls := "text-xl",
                                        child <-- backend_polling_active_sig.combineWith(connectivity_details_sig).map:
                                            case (false, _) =>
                                                p(I18N_UI.pdf_ordering.modal.no_connection_attempt)
                                            case (true, PaymentsBackendApiConnectivity.CheckDisabled) =>
                                                p(I18N_UI.pdf_ordering.modal.internet_check_disabled)
                                            case (true, PaymentsBackendApiConnectivity.InternetCheckInProgress(_)) =>
                                                p(I18N_UI.pdf_ordering.modal.internet_checking, span(cls := "ml-4 loading loading-dots loading-lg"))
                                            case (true, PaymentsBackendApiConnectivity.InternetOnly(_)) =>
                                                p(I18N_UI.pdf_ordering.modal.internet_disconnected, span(cls := "ml-4 loading loading-dots loading-lg"))
                                            case (true, PaymentsBackendApiConnectivity.BackendCheckInProgress(_, _)) =>
                                                p(I18N_UI.pdf_ordering.modal.internet_ok_backend_checking, span(cls := "ml-4 loading loading-dots loading-lg"))
                                            case (true, PaymentsBackendApiConnectivity.PartialConnection(_, PaymentsBackendApiConnectivity.BackendDisconnected(PaymentsBackendApiConnectivity.JsonValidationError(msg)))) =>
                                                p(I18N_UI.pdf_ordering.modal.internet_ok_backend_error.apply(msg))
                                            case (true, PaymentsBackendApiConnectivity.PartialConnection(_, PaymentsBackendApiConnectivity.BackendDisconnected(PaymentsBackendApiConnectivity.NetworkError(msg)))) =>
                                                p(I18N_UI.pdf_ordering.modal.internet_ok_backend_error.apply(msg))
                                            case (true, PaymentsBackendApiConnectivity.FullConnection(_, _)) =>
                                                p(I18N_UI.pdf_ordering.modal.internet_ok_backend_ok)
                                    )
                                ),
                                div(cls := "flex-1", ""),
                            ),
                        ),

                        // SEND 6-DIGIT VALIDATION CODE

                        button(
                            cls := "btn btn-block h-24 mt-6",
                            cls <-- hiddenUnless(send_validation_code_btn_shown_sig),
                            disabledAttr <-- send_validation_code_btn_active_sig.map(!_),
                            onClick.flatMap(_ => makePurchaseCreateIntentRequest()) --> handlePurchaseCreateIntentResponse(),
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(cls := "flex-none w-14",
                                    child <-- validation_code_sent_sig.map: code_sent =>
                                        if (code_sent)
                                            lucide.`square-check`(w = 32, h = 32, stroke_width = 2)
                                        else
                                            lucide.square(w = 32, h = 32, stroke_width = 2)
                                ),
                                div(cls := "flex flex-initial", 
                                    p(
                                        cls := "text-xl", 
                                        text <-- validation_code_sent_sig
                                            .combineWith(send_validation_code_btn_active_sig)
                                            .combineWith(billing_email_sig)
                                            .map: (code_sent, btn_active, billing_email) =>
                                                (code_sent, btn_active) match
                                                    case (true, _)      => I18N_UI.pdf_ordering.modal.validation_code_sent_to.apply(billing_email)
                                                    case (false, true)  => I18N_UI.pdf_ordering.modal.send_validation_code_to.apply(billing_email)
                                                    case (false, false) => I18N_UI.pdf_ordering.modal.invalid_email.apply(billing_email)
                                    )),
                                div(cls := "flex-1", ""),
                            )
                        ),

                        // Button + 6-digit code validation Input form

                        button(
                            cls := "btn btn-block h-24 mt-6 hover:bg-base-200 focus:bg-base-200 hover:border-base-200 focus:border-base-200",
                            cls <-- hiddenUnless(validation_code_sent_sig),
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(cls := "flex-none w-14",
                                    child <-- verify_and_process_response_var.signal.map:
                                        case None =>
                                            lucide.square(w = 32, h = 32, stroke_width = 2)
                                        case Some(Left(msg)) =>
                                            lucide.`square-x`(w = 32, h = 32, stroke_width = 2)
                                        case Some(Right(vap_resp)) =>
                                            lucide.`square-check`(w = 32, h = 32, stroke_width = 2)
                                ),
                                div(
                                    cls := "flex flex-initial", 
                                    child <-- 
                                        verify_and_process_response_var.signal
                                        .map:
                                        case None =>
                                            DaisyUIInputs.SixDigitCodeInputWithPrefixAndButton(
                                                six_digits_code_var,
                                                inputId = "six_digit_validation_code",
                                                prefix = I18N_UI.pdf_ordering.modal.six_digit_code_label,
                                                buttonTxt = I18N_UI.pdf_ordering.modal.validate_button,
                                                buttonClickedObs = send_validation_code_btn_clicked_bus.writer,
                                            )
                                        case Some(Left(msg)) =>
                                            p(cls := "text-xl", I18N_UI.pdf_ordering.modal.error_prefix.apply(msg))
                                        case Some(Right(vap_resp)) =>
                                            p(cls := "text-xl", I18N_UI.pdf_ordering.modal.email_validated)
                                ),
                                div(cls := "flex-1", ""),
                            )
                        ),

                        // GO TO PAYMENT URL

                        button(
                            cls := "btn btn-block h-24 mt-6",
                            cls <-- hiddenUnless(verify_and_process_response_var.signal.map:
                                case Some(Right(_)) => true
                                case _ => false
                            ),
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(cls := "flex-none w-14", 
                                    lucide.square(w = 32, h = 32, stroke_width = 2)
                                ),
                                div(
                                    cls := "flex flex-initial", 
                                    child <-- 
                                        verify_and_process_response_var.signal
                                        .map:
                                            case Some(Right(vap_resp)) => 
                                                a(
                                                    cls := "btn btn-xl btn-outline btn-accent",
                                                    href := s"${vap_resp.paymentUrl}",
                                                    target := "blank",
                                                    I18N_UI.pdf_ordering.modal.go_to_payment_page
                                                )
                                            case _ =>
                                                p("hidden")
                                ),
                                div(cls := "flex-1", ""),
                            )
                        ),

                        // CONFIRMATION NOTICE

                        button(
                            cls := "btn btn-block h-24 mt-6",
                            cls <-- hiddenUnless(verify_and_process_response_var.signal.map:
                                case Some(Right(_)) => true
                                case _ => false
                            ),
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(cls := "flex-none w-14", 
                                    lucide.square(w = 32, h = 32, stroke_width = 2)
                                ),
                                div(
                                    cls := "flex flex-initial", 
                                    child <-- 
                                        verify_and_process_response_var.signal
                                        .map:
                                            case Some(Right(vap_resp)) => 
                                                p(
                                                    cls := "text-xl text-left",
                                                    I18N_UI.pdf_ordering.modal.confirmation_notice
                                                )
                                            case _ =>
                                                p("hidden")
                                ),
                                div(cls := "flex-1", ""),
                            )
                        ),  
                    ),
                    div(
                        cls := "modal-action",
                        form(
                            method := "dialog",
                            button(
                                cls := "btn btn-outline btn-error",
                                I18N_UI.pdf_ordering.modal.button_cancel
                            )
                        ),
                    )

                )
            )
        )
        .amend(binders)

