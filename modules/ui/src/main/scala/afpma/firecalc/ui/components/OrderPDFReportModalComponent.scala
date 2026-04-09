/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components
import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.common.DisplayUnits

import afpma.firecalc.payments.shared.Constants.FIRECALC_FILE_EXTENSION
import afpma.firecalc.payments.shared.api.*

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.config.BuildMode
import afpma.firecalc.ui.config.UIConfig
import afpma.firecalc.ui.config.ViteEnv
import afpma.firecalc.ui.daisyui.*
import afpma.laminar.form.daisyui.DaisyUIInputs
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.instances.transformers.given
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.utils.PaymentsBackendApiConnectivity

import cats.syntax.show.toShow

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*

import io.taig.babel.Locale
import org.scalajs.dom
import org.scalajs.dom.HTMLDialogElement

case class OrderPDFReportModalComponent()(using DisplayUnits, Locale) extends Component:


    protected final case class PDFReportOrderingState(
        cgv_accepted                      : Boolean        = false,
        // send_validation_code_btn_shown: Boolean = false,
        // send_validation_code_btn_active: Boolean = false,
        validation_code_sent              : Boolean        = false,
        six_digits_code                   : Option[String] = None,
        validate_six_digit_code_btn_active: Boolean        = false,
        validation_code_valid             : Boolean        = false,
        purchase_token                    : Option[String] = None,
        payment_link                      : Option[String] = None
    )

    object PDFReportOrderingState:
        val init = PDFReportOrderingState(cgv_accepted = false)

    private val pdf_report_ordering_var = Var[PDFReportOrderingState](PDFReportOrderingState.init)

    private val emissions_warning_acknowledged_var = Var[Boolean](false)

    private val cgv_accepted_sig = pdf_report_ordering_var.signal.map(_.cgv_accepted)

    // private val send_validation_code_btn_shown_var = pdf_report_ordering_var.zoomLazy(_.send_validation_code_btn_shown)((x, a) => x.copy(send_validation_code_btn_shown = a))
    // private val send_validation_code_btn_shown_sig = send_validation_code_btn_shown_var.signal

    // private val send_validation_code_btn_active_var = pdf_report_ordering_var.zoomLazy(_.send_validation_code_btn_active)((x, a) => x.copy(send_validation_code_btn_active = a))
    // private val send_validation_code_btn_active_sig = send_validation_code_btn_active_var.signal

    private val validation_code_sent_sig = pdf_report_ordering_var.signal.map(_.validation_code_sent)

    private val verify_and_process_response_var =
        Var[Option[Either[String, VerifyAndProcessResponse]]](None)

    private val create_purchase_intent_response_var =
        Var[Option[Either[String, CreatePurchaseIntentResponse]]](None)

    private val six_digits_code_var =
        pdf_report_ordering_var.zoomLazy(_.six_digits_code)((x, c) => x.copy(six_digits_code = c))

    private val backend_polling_active_sig = cgv_accepted_sig

    // Connectivity checking - only poll when CGV is accepted
    private val connectivity_details_sig = PaymentsBackendApiConnectivity.createDetailedConnectivitySignal(
        backend_polling_active_sig
    )
    private val connectivity_sig         = connectivity_details_sig.map:
        case PaymentsBackendApiConnectivity.FullConnection(_, _) => true
        case _                                                   => false

    val billing_email_sig       = billingInfoVar.signal.map(_.email)
    val billing_email_valid_sig = billing_email_sig.map { email =>
        validateEmail(email)
    }

    private def validateEmail(email: String): Boolean =
        if email.trim.isEmpty then false
        else if email.contains("@@") then false
        else
            val EMAIL_REGEX = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
            EMAIL_REGEX.matches(email.trim)

    val send_validation_code_btn_shown_sig = connectivity_sig

    val send_validation_code_btn_active_sig =
        cgv_accepted_sig
            .combineWith(connectivity_sig)
            .combineWith(validation_code_sent_sig)
            .combineWith(billing_email_valid_sig)
            .map { (cgv_accepted, conn, validation_code_sent, email_valid) =>
                cgv_accepted && conn && !validation_code_sent && email_valid
            }

    val send_validation_code_btn_clicked_bus    = new EventBus[Unit]
    val send_validation_code_btn_clicked_stream = send_validation_code_btn_clicked_bus.events

    // GLOBAL BINDERS
    private val binders: Seq[Binder.Base] = Seq(
        // when clicked, send VerifyAndProcessRequest to backend, and handle response.
        // Retrieve VerifyAndProcessResponse and put it into verify_and_process_response_var
        send_validation_code_btn_clicked_bus.events.flatMapSwitch(_ =>
            makeVerifyAndProcessRequest()
        ) --> handleVerifyAndProcessResponse()
    )

    val disabledAttr: HtmlAttr[Boolean] = htmlAttr("disabled", BooleanAsAttrPresenceCodec)

    private def hiddenUnless(bool_sig: Signal[Boolean]) = bool_sig.map(if (_) "" else "hidden")

    // Imports for API requests
    import io.circe.*
    import io.circe.syntax.*
    import io.circe.parser.*
    import io.scalaland.chimney.dsl.*
    import java.util.UUID
    import scala.scalajs.js
    import scala.util.{Try, Success, Failure}

    /**
     * Convert AppState (FireCalcYAML) to base64-encoded YAML string using UTF-8 safe encoding.
     * Uses TextEncoder to properly handle Unicode characters (e.g., Greek letters like ζ)
     * that are outside the Latin1 range, which standard btoa() cannot handle.
     */
    private def convertProjectToBase64(fireCalcYaml: FireCalcYAML): Try[String] =
        import afpma.firecalc.dto.FireCalcYAMLMigrations
        // Use dto migrations module to convert to YAML string
        FireCalcYAMLMigrations.encodeToYamlTry(fireCalcYaml).flatMap { yamlString =>
            Try {
                // Use TextEncoder to convert UTF-8 string to bytes
                val textEncoder = js.Dynamic.newInstance(js.Dynamic.global.TextEncoder)()
                val utf8Bytes   = textEncoder.encode(yamlString)

                // Convert Uint8Array bytes to binary string
                val byteArray = utf8Bytes.asInstanceOf[js.typedarray.Uint8Array]
                val binString = (0 until byteArray.length)
                    .map { i =>
                        js.Dynamic.global.String.fromCodePoint(byteArray(i)).asInstanceOf[String]
                    }
                    .mkString("")

                // Encode binary string to base64
                js.Dynamic.global.btoa(binString).asInstanceOf[String]
            }
        }

    def makePurchaseCreateIntentRequest()(using
        locale: Locale
    ): EventStream[Either[String, CreatePurchaseIntentResponse]] =
        val billingInfo = billingInfoVar.now()

        // Get language from current locale
        val language: BillingLanguage = locale.transformInto[BillingLanguage]

        // Select product ID based on build mode
        val productId = ViteEnv.buildMode match
            case BuildMode.Development => v1.DevelopmentProductCatalog.PDF_REPORT_EN_15544_2023.id
            case BuildMode.Staging     => v1.StagingProductCatalog.PDF_REPORT_EN_15544_2023.id
            case BuildMode.Production  => v1.ProductionProductCatalog.PDF_REPORT_EN_15544_2023.id

        // Use transformers to convert BillingInfo + language to CustomerInfo
        val billingInfoWithLanguage = BillingInfoWithLanguage.fromBillingInfoAndLanguage(
            billing_info = billingInfo,
            language     = language
        )

        val customerInfo: CustomerInfo = billingInfoWithLanguage.transformInto[CustomerInfo]

        // Convert current project state to base64-encoded YAML
        val currentProject = engineStateVar.now()
        val productMetadataResult: Either[String, FileDescriptionWithContent] = convertProjectToBase64(
            currentProject
        ) match
            case Success(base64Content) =>
                Right(
                    FileDescriptionWithContent(
                        filename = s"project${FIRECALC_FILE_EXTENSION}",
                        mimeType = "application/yaml",
                        content  = base64Content
                    )
                )
            case Failure(error)         =>
                val errorMsg = s"Failed to convert project to base64: ${error.getMessage}"
                dom.console.error(errorMsg)
                // dom.console.error(error.getStackTrace().toList.mkString("\n"))
                Left             (errorMsg)

        // If conversion failed, return error immediately
        productMetadataResult match
            case Left(errorMsg) =>
                return EventStream.fromValue(Left(errorMsg), emitOnce = true)
            case Right(_)       =>
                () // Continue with request

        val createPurchaseIntentRequest = CreatePurchaseIntentRequest(
            productId       = productId,
            productMetadata = productMetadataResult.toOption,
            customer        = customerInfo
        )

        // Create the request body as JSON string
        val requestBody = createPurchaseIntentRequest.asJson.noSpaces

        // Make the POST request with proper headers and handle both success and error responses
        FetchStream
            .post(
                url = UIConfig.Endpoints.createPurchaseIntent,
                init => {
                    init.body   (requestBody                         )
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
                            case Left(_)         =>
                                // If that fails, try to decode as error response
                                decode[ErrorResponseEnvelope](responseText) match
                                    case Right(errorEnvelope) =>
                                        Left(s"${errorEnvelope.error}: ${errorEnvelope.message}")
                                    case Left(decodeError)    =>
                                        Left(
                                            s"Failed to decode response : ${decodeError.getMessage}\n=> Response:\n'${responseText}'"
                                        )
                    case scala.util.Failure(fetchError)   =>
                        Left(s"Network error: ${fetchError.getMessage}")
            }

    def handlePurchaseCreateIntentResponse(): Observer[Either[String, CreatePurchaseIntentResponse]] =
        Observer[Either[String, CreatePurchaseIntentResponse]] {
            case Right(response)    =>
                // Store the purchase token in the state on success
                pdf_report_ordering_var.update         (
                    _.copy      (
                        purchase_token       = Some(response.purchase_token),
                        validation_code_sent = true
                    )
                )
                // Clear any previous errors
                create_purchase_intent_response_var.set(Some(Right(response)))
            case Left(errorMessage) =>
                // Store error in state for display
                create_purchase_intent_response_var.set(Some(Left(errorMessage)))
                // Reset state to allow retry
                pdf_report_ordering_var.update         (
                    _.copy(
                        validation_code_sent = false,
                        purchase_token       = None
                    )
                )
        }

    def makeVerifyAndProcessRequest(): EventStream[Either[String, VerifyAndProcessResponse]] =
        // Get purchase token, verification code, and email from state
        val purchaseTokenStr = pdf_report_ordering_var.now().purchase_token
        val verificationCode = six_digits_code_var.now()
        val billingEmail     = billingInfoVar.now().email

        // Validate that we have all required fields
        (purchaseTokenStr, verificationCode) match
            case (None, _                   )                     =>
                val errorMsg = "No purchase token available. Please send validation code first."
                dom.console.error(errorMsg)
                return EventStream.fromValue(Left(errorMsg), emitOnce = true)
            case (_, None                   )                     =>
                val errorMsg = "No verification code entered. Please enter the 6-digit code."
                dom.console.error(errorMsg)
                return EventStream.fromValue(Left(errorMsg), emitOnce = true)
            case (Some(tokenStr), Some(code)) if code.length != 6 =>
                val errorMsg = s"Invalid verification code length: ${code.length}. Must be 6 digits."
                dom.console.error(errorMsg)
                return EventStream.fromValue(Left(errorMsg), emitOnce = true)
            case (Some(tokenStr), Some(code))                     =>
                // Parse token string to UUID and create PurchaseToken
                val purchaseToken = Try(PurchaseToken(UUID.fromString(tokenStr))) match
                    case Success(pt)    => pt
                    case Failure(error) =>
                        val errorMsg = s"Invalid purchase token format: ${error.getMessage}"
                        dom.console.error(errorMsg)
                        return EventStream.fromValue(Left(errorMsg), emitOnce = true)

                // Valid inputs, continue with request
                val verifyRequest = VerifyAndProcessRequest(
                    purchaseToken = purchaseToken,
                    email         = billingEmail,
                    code          = code
                )

                // Create the request body as JSON string
                val requestBody = verifyRequest.asJson.noSpaces

                // Make the POST request with proper headers and handle both success and error responses
                FetchStream
                    .post(
                        url = UIConfig.Endpoints.verifyAndProcess,
                        init => {
                            init.body   (requestBody                         )
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
                                    case Left(_)         =>
                                        // If that fails, try to decode as error response
                                        decode[ErrorResponseEnvelope](responseText) match
                                            case Right(errorEnvelope) =>
                                                Left(s"${errorEnvelope.error}: ${errorEnvelope.message}")
                                            case Left(decodeError)    =>
                                                Left(
                                                    s"Failed to decode response: ${decodeError.getMessage}\n=> Response:\n'${responseText}'"
                                                )
                            case scala.util.Failure(fetchError)   =>
                                Left(s"Network error: ${fetchError.getMessage}")
                    }

    def handleVerifyAndProcessResponse(): Observer[Either[String, VerifyAndProcessResponse]] =
        Observer[Either[String, VerifyAndProcessResponse]] {
            case Right(response)    =>
                // Store the response in the state on success
                verify_and_process_response_var.set(Some(Right(response))                           )
                // Store payment_link - this acts as both data and transition flag
                // Its presence tells the order modal close handler not to reset state
                pdf_report_ordering_var.update     (_.copy(payment_link = Some(response.paymentUrl)))
                // Close the order modal and open the payment success modal
                mainModal.ref.asInstanceOf[HTMLDialogElement].close()
                successModal.ref.asInstanceOf[HTMLDialogElement].showModal()
            case Left(errorMessage) =>
                // Log error and store in state for display
                verify_and_process_response_var.set(Some(Left(errorMessage)))
        }

    lazy val node =
        div(
            div         (
                cls    := "flex items-stretch gap-2",
                DaisyUITooltip (
                    ttContent  = div(
                        text <-- conditions_and_results_satisfied_except_emissions_sig
                            .combineWith(emissions_and_efficiency_values_satisfied_sig)
                            .map { case (conditions_ok, emissions_ok) =>
                                if      (!conditions_ok) I18N_UI.tooltips.order_pdf_report_not_possible
                                else if (!emissions_ok ) I18N_UI.tooltips.order_pdf_report_emissions_warning
                                else I18N_UI.tooltips.order_pdf_report
                            }
                    ),
                    element    = div(
                        tabIndex := 0,
                        role     := "button",
                        disabledAttr <-- conditions_and_results_satisfied_except_emissions_not_sig,
                        cls      := "btn btn-outline hover:btn-secondary rounded-field",
                        cls(
                            "text-base-content"
                        ) <-- conditions_and_results_satisfied_except_emissions_sig,
                        cls(
                            "text-base-content/40 hover:text-base-content"
                        ) <-- conditions_and_results_satisfied_except_emissions_not_sig,
                        div(
                            cls := "h-4 flex items-center justify-center",
                            I18N_UI.buttons.order_pdf_report
                        ),
                        div(
                            cls := "w-4 h-4 flex items-center justify-center",
                            lucide.`tag`(stroke_width = 1.5)
                        ),
                        onClick
                            .compose(
                                _.withCurrentValueOf(emissions_and_efficiency_values_satisfied_sig)
                            ) --> { (_, emissionsOk) =>
                            if (emissionsOk) {
                                // Direct flow to main modal
                                mainModal.ref.asInstanceOf[HTMLDialogElement].showModal()
                            } else {
                                // Show warning modal first
                                emissions_warning_acknowledged_var.set(false) // Reset checkbox
                                warningModal.ref.asInstanceOf[HTMLDialogElement].showModal()
                            }
                        }
                    ),
                    ttPosition = "tooltip-bottom"
                )
            ),
            mainModal,
            warningModal,
            successModal
        )
            .amend(binders)

    lazy val mainModal: HtmlElement =
        dialogTag(
            cls := "modal",
                onMountUnmountCallbackWithState[HtmlElement, js.Function1[dom.Event, Unit]]  (
                    mount   = ctx => {
                        val dialog = ctx.thisNode.ref.asInstanceOf[HTMLDialogElement]
                        val closeHandler: js.Function1[dom.Event, Unit] = _ => {
                            // Only reset state if payment_link is empty (user cancelled)
                            // If payment_link is set, we're transitioning to the success modal
                            if (pdf_report_ordering_var.now().payment_link.isEmpty) {
                                pdf_report_ordering_var.set            (PDFReportOrderingState.init)
                                create_purchase_intent_response_var.set(None                       )
                                verify_and_process_response_var.set    (None                       )
                            }
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
                div                                                                          (
                    cls := "modal-box w-8/12 max-w-5xl max-h-10/12",
                    h3 (
                        cls := "text-lg font-bold",
                        I18N_UI.pdf_ordering.modal.title
                    ),
                    div(
                        cls := "py-4",
                        p              (I18N_UI.pdf_ordering.modal.report.compliant_with_standard + " ", b("EN 15544:2023")),
                        br             (                                                                                   ),
                        p              (I18N_UI.pdf_ordering.modal.report.will_be_sent_to_email                            ),
                        br             (                                                                                   ),
                        p(b(I18N_UI.pdf_ordering.modal.report.price)),
                        br             (                                                                                   ),
                        ul             (
                            I18N_UI.pdf_ordering.modal.order_steps.title,
                            li(
                                cls := "mt-4",
                                I18N_UI.pdf_ordering.modal.order_steps.step_1
                            ),
                            li(I18N_UI.pdf_ordering.modal.order_steps.step_2),
                            li(I18N_UI.pdf_ordering.modal.order_steps.step_3),
                            li(I18N_UI.pdf_ordering.modal.order_steps.step_4)
                        ),
                        div            (cls := "divider"                                                                   ),
                        BillingInfoUI()._form,

                        // CGV

                        button         (
                            cls          := "btn btn-block h-24 mt-6",
                            onClick
                                .compose(
                                    _.withCurrentValueOf(cgv_accepted_sig).collect { case (_, curr) => curr }
                                ) --> { curr =>
                                if (curr == false)
                                    pdf_report_ordering_var.update(_.copy(cgv_accepted = true) )
                                else
                                    pdf_report_ordering_var.update(_.copy(cgv_accepted = false))
                            },
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(
                                    cls := "flex-none w-14",
                                    child <-- cgv_accepted_sig.map: cgv_accepted =>
                                        if (cgv_accepted)
                                            lucide.`square-check`(w = 32, h = 32, stroke_width = 2)
                                        else
                                            lucide.square        (w = 32, h = 32, stroke_width = 2)
                                ),
                                div(
                                    cls := "flex flex-initial",
                                    p(cls := "text-xl", I18N_UI.pdf_ordering.modal.accept_terms_and_conditions)
                                ),
                                div(cls := "flex-1", ""        )
                            )
                        ),

                        // Connectivity : Internet + Backend

                        button         (
                            cls          := "btn btn-block h-24 mt-6",
                            disabledAttr := true,
                            cls <-- hiddenUnless(backend_polling_active_sig),
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(
                                    cls := "flex-none w-14",
                                    child <-- connectivity_details_sig.map:
                                        case PaymentsBackendApiConnectivity.FullConnection(_, _) =>
                                            lucide.`square-check`(w = 32, h = 32, stroke_width = 2)
                                        case _                                                   =>
                                            lucide.square(w = 32, h = 32, stroke_width = 2)
                                ),
                                div(
                                    cls := "flex flex-initial",
                                    p(
                                        cls := "text-xl",
                                        child <-- backend_polling_active_sig
                                            .combineWith(connectivity_details_sig)
                                            .map:
                                                case (false, _                                                 ) =>
                                                    p(I18N_UI.pdf_ordering.modal.connection.no_connection_attempt)
                                                case (true, PaymentsBackendApiConnectivity.CheckDisabled       ) =>
                                                    p(I18N_UI.pdf_ordering.modal.connection.internet_check_disabled)
                                                case (
                                                        true,
                                                        PaymentsBackendApiConnectivity.InternetCheckInProgress(_)
                                                    ) =>
                                                    p(
                                                        I18N_UI.pdf_ordering.modal.connection.internet_checking,
                                                        span(cls := "ml-4 loading loading-dots loading-lg")
                                                    )
                                                case (true, PaymentsBackendApiConnectivity.InternetOnly(_)     ) =>
                                                    p(
                                                        I18N_UI.pdf_ordering.modal.connection.internet_disconnected,
                                                        span(cls := "ml-4 loading loading-dots loading-lg")
                                                    )
                                                case (
                                                        true,
                                                        PaymentsBackendApiConnectivity.BackendCheckInProgress(_, _)
                                                    ) =>
                                                    p(
                                                        I18N_UI.pdf_ordering.modal.connection.internet_ok_backend_checking,
                                                        span(cls := "ml-4 loading loading-dots loading-lg")
                                                    )
                                                case (
                                                        true,
                                                        PaymentsBackendApiConnectivity.PartialConnection(
                                                            _,
                                                            PaymentsBackendApiConnectivity.BackendDisconnected(
                                                                PaymentsBackendApiConnectivity.JsonValidationError(msg)
                                                            )
                                                        )
                                                    ) =>
                                                    p(
                                                        I18N_UI.pdf_ordering.modal.connection.internet_ok_backend_error
                                                            .apply(msg)
                                                    )
                                                case (
                                                        true,
                                                        PaymentsBackendApiConnectivity.PartialConnection(
                                                            _,
                                                            PaymentsBackendApiConnectivity.BackendDisconnected(
                                                                PaymentsBackendApiConnectivity.NetworkError(msg)
                                                            )
                                                        )
                                                    ) =>
                                                    p(
                                                        I18N_UI.pdf_ordering.modal.connection.internet_ok_backend_error
                                                            .apply(msg)
                                                    )
                                                case (true, PaymentsBackendApiConnectivity.FullConnection(_, _)) =>
                                                    p(I18N_UI.pdf_ordering.modal.connection.internet_ok_backend_ok)
                                    )
                                ),
                                div(cls := "flex-1", ""        )
                            )
                        ),

                        // SEND 6-DIGIT VALIDATION CODE

                        button         (
                            cls          := "btn btn-block h-24 mt-6",
                            cls <-- hiddenUnless(send_validation_code_btn_shown_sig),
                            disabledAttr <-- send_validation_code_btn_active_sig
                                .combineWith(create_purchase_intent_response_var.signal)
                                .map:
                                    case (btn_active, Some(Left(_))) => false // Allow retry after error
                                    case (btn_active, _            ) => !btn_active
                            ,
                            onClick.flatMap(_ =>
                                makePurchaseCreateIntentRequest()
                            ) --> handlePurchaseCreateIntentResponse(),
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
                                                case Some(Left(errorMsg)) =>
                                                    p(
                                                        cls := "text-xl",
                                                        I18N_UI.pdf_ordering.modal.validation.error_prefix
                                                            .apply(errorMsg)
                                                    )
                                                case _                    =>
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
                        ),

                        // Button + 6-digit code validation Input form

                        button         (
                            cls          := "btn btn-block h-24 mt-6 hover:bg-base-200 focus:bg-base-200 hover:border-base-200 focus:border-base-200",
                            cls <-- hiddenUnless(validation_code_sent_sig),
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(
                                    cls := "flex-none w-14",
                                    child <-- verify_and_process_response_var.signal.map:
                                        case None                  =>
                                            lucide.square(w = 32, h = 32, stroke_width = 2)
                                        case Some(Left(msg))       =>
                                            lucide.`square-x`(w = 32, h = 32, stroke_width = 2)
                                        case Some(Right(vap_resp)) =>
                                            lucide.`square-check`(w = 32, h = 32, stroke_width = 2)
                                ),
                                div(
                                    cls := "flex flex-initial",
                                    child <--
                                        verify_and_process_response_var.signal
                                            .map:
                                                case None                  =>
                                                    DaisyUIInputs.SixDigitCodeInputWithPrefixAndButton         (
                                                        six_digits_code_var,
                                                        inputId          = "six_digit_validation_code",
                                                        prefix           =
                                                            I18N_UI.pdf_ordering.modal.validation.six_digit_code_label,
                                                        buttonTxt        =
                                                            I18N_UI.pdf_ordering.modal.validation.validate_button,
                                                        buttonClickedObs = send_validation_code_btn_clicked_bus.writer
                                                    )
                                                case Some(Left(msg))       =>
                                                    p(
                                                        cls := "text-xl",
                                                        I18N_UI.pdf_ordering.modal.validation.error_prefix.apply(msg)
                                                    )
                                                case Some(Right(vap_resp)) =>
                                                    p(
                                                        cls := "text-xl",
                                                        I18N_UI.pdf_ordering.modal.validation.email_validated
                                                    )
                                ),
                                div(cls := "flex-1", ""        )
                            )
                        )
                    ),
                    div(
                        cls := "modal-action",
                        form(
                            method := "dialog",
                            button(
                                cls := "btn btn-outline btn-error",
                                I18N_UI.pdf_ordering.modal.button_cancel
                            )
                        )
                    )
                )
        )

    // Emissions Warning Modal - shown when user tries to order with unmet emissions criteria
    lazy val warningModal: HtmlElement =
        dialogTag(
            cls := "modal",
                onMountUnmountCallbackWithState[HtmlElement, js.Function1[dom.Event, Unit]]  (
                    mount   = ctx => {
                        val dialog = ctx.thisNode.ref.asInstanceOf[HTMLDialogElement]
                        val closeHandler: js.Function1[dom.Event, Unit] = _ => {
                            // Reset acknowledgment state when modal is closed
                            emissions_warning_acknowledged_var.set(false)
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
                div                                                                          (
                    cls := "modal-box w-10/12 max-w-4xl",
                    h3 (
                        cls := "text-lg font-bold text-warning",
                        I18N_UI.pdf_ordering.modal.emissions_warning.title
                    ),
                    div(
                        cls := "py-4",
                        // Warning message
                        div(
                            cls := "alert alert-warning mb-4",
                            lucide.`triangle-alert`(w = 24, h = 24                                      ),
                            span                   (I18N_UI.pdf_ordering.modal.emissions_warning.message)
                        ),
                        // Unmet criteria table
                        div(
                            cls := "overflow-x-auto",
                            child <-- en15544_strict_local_regulations_and_check_results.map {
                                case (lregOpt, checkResults) =>
                                    val unmetCriterias = checkResults.unmetCriterias
                                    div(
                                        if (unmetCriterias.isEmpty) {
                                            p(cls := "text-success", "All criteria met")
                                        } else {
                                            table(
                                                cls := "table table-zebra w-full",
                                                thead(
                                                    tr(
                                                        th(
                                                            I18N_UI.pdf_ordering.modal.emissions_warning.table.parameter
                                                        ),
                                                        th(
                                                            I18N_UI.pdf_ordering.modal.emissions_warning.table.current_value
                                                        ),
                                                        th(
                                                            I18N_UI.pdf_ordering.modal.emissions_warning.table.required + "*"
                                                        ),
                                                        th(I18N_UI.pdf_ordering.modal.emissions_warning.table.status)
                                                    )
                                                ),
                                                tbody(
                                                    unmetCriterias.map { res =>
                                                        tr(
                                                            td(res.showDetailedParamDescription),
                                                            td(res.showValue.getOrElse("-")),
                                                            td(res.showCriteria                ),
                                                            td(
                                                                cls := "text-warning font-bold",
                                                                I18N_UI.pdf_ordering.modal.emissions_warning.not_met
                                                            )
                                                        )
                                                    }
                                                )
                                            )
                                        },
                                        // Local regulation citation
                                        lregOpt.map { lreg =>
                                            p(
                                                cls := "mt-4 text-sm italic text-base-content/70", {
                                                    val local_ref_and_country =
                                                        s"${lreg.regulation_ref} (${lreg.country.show})"
                                                    val full_ref_sentence     = I18N_UI.pdf_ordering.modal.emissions_warning
                                                        .according_to(local_ref_and_country)
                                                    s"*$full_ref_sentence" // prefix with an asterisk
                                                }
                                            )
                                        }
                                    )
                            }
                        ),
                        // Acknowledgment checkbox
                        div(
                            cls := "form-control mt-6",
                            label(
                                cls := "label cursor-pointer justify-start gap-4",
                                input(
                                    typ := "checkbox",
                                    cls := "checkbox checkbox-warning",
                                    checked <-- emissions_warning_acknowledged_var.signal,
                                    onChange.mapToChecked --> emissions_warning_acknowledged_var.writer
                                ),
                                span (
                                    cls := "label-text text-base",
                                    I18N_UI.pdf_ordering.modal.emissions_warning.acknowledge_checkbox
                                )
                            )
                        )
                    ),
                    div(
                        cls := "modal-action flex gap-2",
                        // Cancel button
                        form  (
                            method := "dialog",
                            button(
                                cls := "btn btn-outline",
                                I18N_UI.pdf_ordering.modal.emissions_warning.button_cancel
                            )
                        ),
                        // Confirm button - disabled until checkbox is checked
                        button(
                            cls    := "btn btn-warning",
                            disabledAttr <-- emissions_warning_acknowledged_var.signal.map(!_),
                            onClick --> { _ =>
                                // Close warning modal
                                warningModal.ref.asInstanceOf[HTMLDialogElement].close()
                                // Open main PDF order modal
                                mainModal.ref.asInstanceOf[HTMLDialogElement].showModal()
                            },
                            I18N_UI.pdf_ordering.modal.emissions_warning.button_confirm
                        )
                    )
                )
        )

    // Payment Success Modal - shown after 6-digit code validation
    lazy val successModal: HtmlElement =
        dialogTag(
            cls := "modal",
                onMountUnmountCallbackWithState[HtmlElement, js.Function1[dom.Event, Unit]]  (
                    mount   = ctx => {
                        val dialog = ctx.thisNode.ref.asInstanceOf[HTMLDialogElement]
                        val closeHandler: js.Function1[dom.Event, Unit] = _ => {
                            // Reset all state when success modal is closed
                            pdf_report_ordering_var.set            (PDFReportOrderingState.init)
                            create_purchase_intent_response_var.set(None                       )
                            verify_and_process_response_var.set    (None                       )
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
                div                                                                          (
                    cls := "modal-box w-8/12 max-w-2xl",
                    h3 (
                        cls := "text-lg font-bold",
                        I18N_UI.pdf_ordering.modal.payment.required_title
                    ),
                    div(
                        cls := "py-4",
                        // Payment instruction text
                        p     (
                            cls := "text-center text-xl mt-4",
                            I18N_UI.pdf_ordering.modal.payment.instruction
                        ),
                        // Payment link button - styled like other modal buttons
                        button(
                            cls := "btn btn-block h-24 mt-6",
                            div(
                                cls := "flex flex-row items-center w-full gap-3",
                                div(cls := "flex-none w-14", ""),
                                div(
                                    cls := "flex-none w-14",
                                    lucide.square(w = 32, h = 32, stroke_width = 2)
                                ),
                                div(
                                    cls := "flex flex-initial",
                                    // Read from payment_link (single source of truth)
                                    child <-- pdf_report_ordering_var.signal
                                        .map(_.payment_link)
                                        .map:
                                            case Some(paymentUrl) =>
                                                a   (
                                                    cls    := "btn btn-xl btn-outline btn-accent",
                                                    href   := paymentUrl,
                                                    target := "blank",
                                                    I18N_UI.pdf_ordering.modal.payment.go_to_payment_page
                                                )
                                            case None             =>
                                                emptyNode
                                ),
                                div(cls := "flex-1", ""        )
                            )
                        ),
                        // Confirmation notice
                        p     (
                            cls := "text-center mt-6",
                            I18N_UI.pdf_ordering.modal.payment.confirmation_notice
                        ),
                        // Close window hint
                        p     (
                            cls := "text-center mt-4 font-bold",
                            I18N_UI.pdf_ordering.modal.payment.close_window_hint
                        )
                    ),
                    div(
                        cls := "modal-action",
                        form(
                            method := "dialog",
                            button(
                                cls := "btn btn-outline",
                                I18N_UI.pdf_ordering.modal.payment.button_close
                            )
                        )
                    )
                )
            )
