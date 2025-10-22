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
import org.http4s.headers.{`Content-Type`, `WWW-Authenticate`}
import org.http4s.Challenge
import io.circe.syntax.*
import afpma.firecalc.payments.shared.api
import afpma.firecalc.payments.shared.api.ErrorResponseEnvelope
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.domain.Codecs.given
import afpma.firecalc.payments.exceptions.*
import org.typelevel.log4cats.Logger
import org.typelevel.ci.CIStringSyntax

class PurchaseRoutes[F[_]: Async](
    purchaseService: PurchaseService[F]
)(implicit logger: Logger[F])
    extends Http4sDsl[F]:

    val routes_V1: HttpRoutes[F] = HttpRoutes.of[F] {

        case req @ POST -> Root / "v1" / "purchase" / "create-intent" =>
            (
                for
                    createRequest <- req.asJsonDecode[api.v1.CreatePurchaseIntentRequest]
                    _ <- logger.info(
                        s"Received create purchase intent request for: ${createRequest.customer.email}"
                    )
                    token <- purchaseService.createPurchaseIntent(createRequest)
                    response <- Ok(api.v1.CreatePurchaseIntentResponse(token.value.toString).asJson)
                yield response
           ).handleErrorWith(handlePurchaseServiceError)

        case req @ POST -> Root / "v1" / "purchase" / "verify-and-process" =>
            (
                for
                    verifyRequest <- req.asJsonDecode[api.v1.VerifyAndProcessRequest]
                    _             <- logger.info(
                        s"Received verify and process request for token: ${verifyRequest.purchaseToken}"
                    )
                    response      <- purchaseService.verifyAndProcess(verifyRequest)
                    result        <- Ok(response.asJson)
                yield result
            ).handleErrorWith(handlePurchaseServiceError)
    }
            
    // Structured error handling for typed purchase service exceptions
    private def handlePurchaseServiceError(error: Throwable): F[Response[F]] =
        error match
            // Authentication errors - 401 Unauthorized
            case ex: InvalidOrExpiredCodeException =>
                for
                    _ <- logger.warn(s"Authentication failed: ${ex.getMessage}")
                    response <- Unauthorized(`WWW-Authenticate`(Challenge.apply("Bearer", "Purchase API")), createErrorResponse(ex))
                yield response
                
            case ex: AuthenticationFailedException =>
                for
                    _ <- logger.warn(s"Authentication failed: ${ex.getMessage}")
                    response <- Unauthorized(`WWW-Authenticate`(Challenge.apply("Bearer", "Purchase API")), createErrorResponse(ex))
                yield response
            
            // Resource not found errors - 404 Not Found  
            case ex: PurchaseIntentNotFoundException =>
                for
                    _ <- logger.warn(s"Purchase intent not found: ${ex.getMessage}")
                    response <- NotFound(createErrorResponse(ex))
                yield response
                
            case ex: CustomerNotFoundException =>
                for
                    _ <- logger.error(s"Customer not found: ${ex.getMessage}")
                    response <- NotFound(createErrorResponse(ex))
                yield response
                
            case ex: ProductNotFoundException =>
                for
                    _ <- logger.error(s"Product not found: ${ex.getMessage}")
                    response <- NotFound(createErrorResponse(ex))
                yield response
            
            // Business logic errors - 422 Unprocessable Entity
            case ex: CustomerValidationException =>
                for
                    _ <- logger.warn(s"Customer validation failed: ${ex.getMessage}")
                    response <- UnprocessableEntity(createErrorResponse(ex))
                yield response
            
            // Service errors - 503 Service Unavailable
            case ex: OrderCreationFailedException =>
                for
                    _ <- logger.error(s"Order creation failed: ${ex.getMessage}")
                    response <- ServiceUnavailable(createErrorResponse(ex))
                yield response
                
            case ex: PaymentLinkCreationFailedException =>
                for
                    _ <- logger.error(s"Payment link creation failed: ${ex.getMessage}")
                    response <- ServiceUnavailable(createErrorResponse(ex))
                yield response
                
            case ex: JWTGenerationFailedException =>
                for
                    _ <- logger.error(s"JWT generation failed: ${ex.getMessage}")
                    response <- ServiceUnavailable(createErrorResponse(ex))
                yield response
                
            case ex: EmailSendingFailedException =>
                for
                    _ <- logger.error(s"Email sending failed: ${ex.getMessage}")
                    response <- ServiceUnavailable(createErrorResponse(ex))
                yield response
            
            // General purchase processing errors - 422 Unprocessable Entity
            case ex: PurchaseIntentProcessingException =>
                for
                    _ <- logger.error(s"Purchase processing failed: ${ex.getMessage}")
                    response <- UnprocessableEntity(createErrorResponse(ex))
                yield response
            
            // JSON decoding errors - 400 Bad Request
            case e @ InvalidMessageBodyFailure(details, cause) =>
                for
                    _ <- logger.error(s"JSON decoding failed: $details")
                    _ <- cause match
                        case None => logger.error("No underlying throwable")
                        case Some(ex) => logger.error(ex)(s"Underlying error: ${ex.getMessage}")
                    response <- BadRequest(ErrorResponseEnvelope(
                        error = "invalid_json",
                        message = s"Invalid JSON in request body: $details"
                    ).asJson)
                yield response
            
            // Unexpected errors - 500 Internal Server Error
            case other =>
                for
                    _ <- logger.error(other)(s"Unexpected error: ${other.getMessage}")
                    response <- InternalServerError(ErrorResponseEnvelope(
                        error = "internal_server_error",
                        message = "An unexpected error occurred"
                    ).asJson)
                yield response

    private def createErrorResponse(ex: PurchaseServiceError) =
        ErrorResponseEnvelope(
            error = ex.errorCode,
            message = ex.getMessage
        ).asJson

object PurchaseRoutes:
    def create[F[_]: Async](
        purchaseService: PurchaseService[F]
    )(implicit logger: Logger[F]): PurchaseRoutes[F] =
        new PurchaseRoutes[F](purchaseService)
