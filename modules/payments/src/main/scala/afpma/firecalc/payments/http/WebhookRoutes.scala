/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.http

import cats.effect.Async
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.*
import org.http4s.syntax.literals.*
import org.http4s.headers.`Content-Type`
import io.circe.syntax.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.domain.Codecs.given
import afpma.firecalc.payments.exceptions.*
import org.typelevel.log4cats.Logger
import org.typelevel.ci.CIStringSyntax
import afpma.firecalc.payments.service.impl.WebhookEventStatus
import afpma.firecalc.payments.shared.api.ErrorResponseEnvelope

class WebhookRoutes[F[_]: Async](
    paymentService: PaymentService[F]
)(implicit logger: Logger[F])
    extends Http4sDsl[F]:

    val routes_V1: HttpRoutes[F] = HttpRoutes.of[F] {

        case req @ POST -> Root / "v1" / "webhooks" / "gocardless" =>
            req.headers.get(ci"Webhook-Signature") match
                case None =>
                    handleWebhookError(WebhookSignatureMissingException())
                
                case Some(signatureHeader) =>
                    val signature = signatureHeader.head.value
                    (
                        for
                            _ <- logger.info("Received GoCardless webhook")
                            
                            // Validate signature format
                            _ <- if (signature.trim.isEmpty)
                                then
                                    Async[F].raiseError(WebhookSignatureInvalidException(signature))
                                else 
                                    Async[F].unit
                            
                            // Get the raw request body
                            requestBody <- req.bodyText.compile.string
                            _ <- logger.debug(s"Webhook body length: ${requestBody.length}")
                            _ <- logger.debug(s"Webhook signature length: ${signature.length}")
                            
                            // Process the webhook
                            result <- paymentService.processWebhook(requestBody, signature)
                            
                            response <- result match
                                case Right(webhookEventStatus) =>
                                    for
                                        _ <- logger.info("Webhook processed successfully")
                                        response <- webhookEventStatus match 
                                            case WebhookEventStatus.Processed => Ok()
                                            case WebhookEventStatus.Unknown => NoContent()
                                            case WebhookEventStatus.InvalidSignature => 
                                                handleWebhookError(WebhookSignatureInvalidException(signature))
                                    yield response
                                case Left(error) =>
                                    Async[F].raiseError(WebhookProcessingException(error))
    
                        yield response
                    ).handleErrorWith(handleWebhookError)
    }

    // Structured error handling for webhook exceptions
    private def handleWebhookError(error: Throwable): F[Response[F]] =
        error match
            // Missing signature header - 400 Bad Request
            case ex: WebhookSignatureMissingException =>
                for
                    _ <- logger.warn(s"Webhook signature missing: ${ex.getMessage}")
                    response <- BadRequest(createErrorResponse(ex))
                yield response
                
            // Invalid signature - 401 Unauthorized  
            case ex: WebhookSignatureInvalidException =>
                for
                    _ <- logger.warn(s"Invalid webhook signature: ${ex.getMessage}")
                    response <- Response[F](Status.Unauthorized)
                        .withEntity(createErrorResponse(ex))
                        .pure[F]
                yield response
                
            // Webhook processing errors - 422 Unprocessable Entity
            case ex: WebhookProcessingException =>
                for
                    _ <- logger.error(s"Webhook processing failed: ${ex.getMessage}")
                    response <- UnprocessableEntity(createErrorResponse(ex))
                yield response
                
            // Other purchase service errors
            case ex: PurchaseServiceError =>
                for
                    _ <- logger.error(s"Purchase service error during webhook processing: ${ex.getMessage}")
                    response <- InternalServerError(createErrorResponse(ex))
                yield response
                
            // Unexpected errors - 500 Internal Server Error
            case other =>
                for
                    _ <- logger.error(other)(s"Unexpected webhook error: ${other.getMessage}")
                    response <- InternalServerError(ErrorResponseEnvelope(
                        error = "internal_server_error",
                        message = "An unexpected error occurred processing the webhook"
                    ).asJson)
                yield response

    private def createErrorResponse(ex: PurchaseServiceError) =
        ErrorResponseEnvelope(
            error = ex.errorCode,
            message = ex.getMessage
        ).asJson

object WebhookRoutes:
    def create[F[_]: Async](
        paymentService: PaymentService[F]
    )(implicit logger: Logger[F]): WebhookRoutes[F] =
        new WebhookRoutes[F](paymentService)
