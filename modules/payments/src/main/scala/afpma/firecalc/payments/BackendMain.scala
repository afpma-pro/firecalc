/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.{Logger, CORS}
import org.http4s.headers.Origin
import org.typelevel.log4cats
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.slf4j.Slf4jLogger

import cats.effect.{IO, IOApp, ExitCode}
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{Logger, CORS}
import org.typelevel.log4cats.Logger as Log4CatsLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import afpma.firecalc.payments.shared.api.ProductCatalogSelector
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.email.*
import afpma.firecalc.payments.http.*
import afpma.firecalc.payments.config.ConfigLoader
import afpma.firecalc.payments.db.Migrations
import afpma.firecalc.payments.repository.impl.MoleculeInvoiceCounterRepository
import afpma.firecalc.payments.service.{InvoiceNumberService, InvoiceNumberServiceImpl}
import afpma.firecalc.payments.domain

import scala.concurrent.ExecutionContext
import afpma.firecalc.payments.i18n.implicits.I18N_Payments
import io.taig.babel.Locales
import afpma.firecalc.payments.shared.api.*
import io.taig.babel.Locale
import java.util.UUID
import afpma.firecalc.reports.FireCalcReportFactory_15544_Strict
import afpma.firecalc.reports.{FireCalcReportError, YAMLFileReadException, YAMLDecodingException, EN15544ValidationException, TypstRenderingException, PDFFileCreationException}
import scala.io.Source
import java.io.File
import java.nio.file.Files
import afpma.firecalc.payments.exceptions.*
import afpma.firecalc.payments.domain.{OrderStatus}

object Main extends IOApp:

  given Log4CatsLogger[IO] = Slf4jLogger.getLogger[IO]
  given ExecutionContext = ExecutionContext.global

  /**
   * Helper method to generate a PDF report from a project file
   * @param fileDesc The project file containing YAML content
   * @param locale The locale for internationalization
   * @return IO[File] The generated PDF file
   */
  def generatePdfReportBlocking[F[_]: Async](fileDesc: FileDescriptionWithContent, locale: Locale, asDraft: Boolean): F[File] =
    Async[F].blocking {
      // Decode base64 content directly to UTF-8 string, avoiding file I/O issues
      val yamlContent = try {
        val decodedBytes = java.util.Base64.getDecoder.decode(fileDesc.content)
        new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8)
      } catch {
        case ex: IllegalArgumentException =>
          throw ReportFileAccessException(fileDesc.filename, s"base64_decoding (msg: ${ex.getMessage()})", Some(ex))
        case ex: Exception =>
          throw ReportFileAccessException(fileDesc.filename, s"content_decoding (msg: ${ex.getMessage()})", Some(ex))
      }

      given Locale = locale

      val reportFactory = FireCalcReportFactory_15544_Strict.init()
      
      // Propagate typed errors directly from the report factory
      reportFactory.loadYAMLString(yamlContent) match {
        case Left(err) => throw err  // Re-throw FireCalcReportError as-is
        case Right(loadedFactory) =>
          loadedFactory.makePDF(isDraft = asDraft) match {
            case Left(err) => throw err  // Re-throw FireCalcReportError as-is
            case Right(pdfFile) => pdfFile
          }
      }
    }

  def generatePdfReportAndInvoiceNumber[F[_]: Log4CatsLogger: Async](
    fileDesc: FileDescriptionWithContent,
    context: OrderCompletionContext,
    invoiceNumberService: InvoiceNumberService[F],
    locale: Locale,
    asDraft: Boolean
  ): F[(File, String)] =
    val logger = Log4CatsLogger[F]
    generatePdfReportBlocking(fileDesc, locale, asDraft)
    .flatMap { f =>
        for
            // PDF report generation successful
            _ <- Log4CatsLogger[F].info(s"[REPORT-GENERATION] Generated PDF report: ${f.getAbsolutePath}")
            // Generate invoice number and update order with it
            invoiceNumber <- if (context.order.invoiceNumber.isEmpty) {
                invoiceNumberService.generateInvoiceNumberAndUpdateOrder(context.order.id)
                    .handleErrorWith { error =>
                        val msg = s"Invoice number generation failed for order ${context.order.id}: ${error.getMessage}"
                        logger.error(msg) *>
                        Async[F].raiseError(InvoiceNumberGenerationFailedException(msg))
                    }
                } else Async[F].pure(context.order.invoiceNumber.get)
        yield (f, invoiceNumber)
    }
    // Propagate typed FireCalcReportErrors without wrapping them
  
  import BackendCompatibleLanguage.given

  // force to load I18NData_Payments for all BackendCompatibleLanguage so we fail early if missing keys
  val translationsDummy = List(
    BackendCompatibleLanguage.English,
    BackendCompatibleLanguage.French
  ).foreach(implicit bl => 
    print(s"Loading translations for ${bl.code}...")
    val translations = I18N_Payments(using summon[Locale])
    println(" => OK.")
  )

  /**
   * Helper method to build InvoiceEmail from order completion context
   * @param context Order completion context containing customer, order, and product info
   * @param email Email address to send to
   * @param invoiceNumber Invoice number for the email
   * @param pdfBytes PDF bytes to attach (can be empty for notifications)
   * @return InvoiceEmail Constructed invoice email object
   */
  def buildInvoiceEmail(
    context: OrderCompletionContext,
    email: String,
    invoiceNumber: String,
    pdfBytes: Array[Byte]
  ): InvoiceEmail = {
    InvoiceEmail(
      email = EmailAddress.unsafeFromString(email),
      orderId = context.order.id.value.toString,
      invoiceNumber = invoiceNumber,
      productName = context.product.name,
      customerName = context.customer.individualNameOrCompanyName,
      amount = context.order.amount,
      currency = context.order.currency.toString,
      pdfBytes = pdfBytes,
      language = context.customer.language
    )
  }

  /**
   * Helper method to send invoice with PDF report email
   * @param context Order completion context containing customer, order, and product info
   * @param fileDesc File description for the PDF report
   * @param pdfReportFile Generated PDF report file
   * @param pdfInvoiceAsBytes PDF invoice bytes for attachment
   * @param emailService Email service to send the email
   * @return IO[Unit] Effect that sends the email or throws an exception on failure
   */
  def sendInvoiceWithReportToUser(
    context: OrderCompletionContext,
    fileDesc: FileDescriptionWithContent,
    pdfReportFile: File,
    pdfInvoiceAsBytes: Array[Byte],
    emailService: EmailService[IO]
  ): IO[Unit] = {
    for {
      invoiceNumber <- IO.fromOption(context.order.invoiceNumber)(
        InvoiceNumberMissingException(context.order.id.value)
      )
      
      // Use helper method to build invoice email
      invoiceEmail = buildInvoiceEmail(context, context.customer.email, invoiceNumber, pdfInvoiceAsBytes)

      pdfReportEmail = PdfReportEmail(
        email = EmailAddress.unsafeFromString(context.customer.email),
        reportName = fileDesc.filename,
        customerName = context.customer.individualNameOrCompanyName,
        orderId = Some(context.order.id.value.toString),
        pdfBytes = Files.readAllBytes(pdfReportFile.toPath),
        language = context.customer.language
      )

      _ <- {
        given BackendCompatibleLanguage = context.customer.language
        emailService.sendUserInvoiceWithReport(invoiceEmail, pdfReportEmail).flatMap {
          case EmailFailed(error) => 
            IO.raiseError(EmailSendingFailedException(
              orderId = context.order.id.value, 
              recipient = context.customer.email,
              reason = error
            ))
          case EmailSent => IO.unit
        }
      }
    } yield ()
  }

  /**
   * Helper method to send admin invoice notification
   * @param context Order completion context containing customer, order, and product info
   * @param adminEmail Admin email address from configuration
   * @param pdfInvoiceAsBytes PDF invoice bytes for attachment (empty for admin notifications)
   * @param emailService Email service to send the email
   * @return IO[Unit] Effect that sends the email or throws an exception on failure
   */
  def sendAdminInvoiceNotification(
    context: OrderCompletionContext,
    adminEmail: String,
    pdfInvoiceAsBytes: Array[Byte],
    emailService: EmailService[IO]
  ): IO[Unit] = {
    context.order.invoiceNumber.traverse_ { invoiceNumber =>
      // Use helper method to build admin invoice email
      val adminInvoiceEmail = buildInvoiceEmail(context, adminEmail, invoiceNumber, pdfInvoiceAsBytes)
      
      given BackendCompatibleLanguage = context.customer.language
      emailService.sendAdminInvoice(adminInvoiceEmail).flatMap:
        handleEmailResult(s"admin notification for $invoiceNumber", context, adminEmail)
    }
  }

  private def handleEmailResult(descr: String, context: OrderCompletionContext, recipient: String)(emailResult: EmailResult): IO[Unit] = 
    emailResult match
        case EmailSent => 
            IO.println(s"[EMAIL-SERVICE] Email successfully sent to ${recipient}")
        case EmailFailed(error) => 
            IO.println(s"[EMAIL-SERVICE] Failed to send email to ${recipient}: $error") *>
            IO.raiseError(EmailSendingFailedException(
                orderId = context.order.id.value,
                recipient = recipient,
                reason = s"Error: $error"
            ))
    

  def run(args: List[String]): IO[ExitCode] =
    // Create HTTP client resource
    EmberClientBuilder.default[IO].build.use { httpClient =>
      
      val logger = summon[org.typelevel.log4cats.Logger[IO]]
      
      for {
        // Load payments configuration first
        paymentsConfig <- ConfigLoader.loadPaymentsConfig[IO]()
        _ <- logger.info(s"Loaded payments config for environment: ${paymentsConfig.environment}")
        _ <- logger.info(s"Using database: ${paymentsConfig.databaseConfig.filename}")
        
        // Run migrations with configured database
        _ <- Migrations.migrate[IO](s"jdbc:sqlite:${paymentsConfig.databaseConfig.path}")
        
        exitCode <- Database.createConnection[IO](paymentsConfig).use { implicit conn =>
            for
                _ <- logger.info("Initializing repositories...")

                // Initialize repositories
                productRepo         <- ProductRepository.create[IO]
                customerRepo        <- CustomerRepository.create[IO]
                orderRepo           <- OrderRepository.create[IO]
                purchaseIntentRepo  <- PurchaseIntentRepository.create[IO]
                productMetadataRepo <- ProductMetadataRepository.create[IO]

                // Get product catalog based on configuration
                productCatalog = ProductCatalogSelector.getCatalog(paymentsConfig.productCatalog)
                _ <- logger.info(s"Using ${paymentsConfig.productCatalog} product catalog")
                
                // Sync product catalog to database
                _ <- logger.info("Syncing product catalog to database...")
                _ <- productCatalog.allProducts.traverse { productInfo =>
                  productRepo.upsert(productInfo)
                }
                _ <- logger.info(s"Synced ${productCatalog.allProducts.size} product(s) from catalog")

                _ <- logger.info("Initializing services...")

                // Initialize services
                authService     <- AuthenticationService.create[IO](purchaseIntentRepo)
                orderService    <- OrderService.create[IO](orderRepo, productRepo, customerRepo, productMetadataRepo)

                // Load email configuration
                emailConfig     <- ConfigLoader.loadEmailConfig[IO]()
                _ <- logger.info(s"Loaded email configuration: ${emailConfig.smtpHost}:${emailConfig.smtpPort}")
                
                emailService    <- EmailService.create[IO](emailConfig)
                // Alternative: Use mocked email service for testing
                // emailService <- EmailService.createMocked[IO]

                _ <- logger.info("Initializing invoice number generation system...")

                // Initialize invoice number services
                invoiceCounterRepo  <- InvoiceCounterRepository.create[IO]
                _                   <- invoiceCounterRepo.initializeCounter(paymentsConfig.invoiceCounterStartingNumber)

                invoiceNumberService <- InvoiceNumberService.create[IO](
                    paymentsConfig,
                    invoiceCounterRepo,
                    orderRepo
                )

                // Initialize invoice pdf generation service
                invoicePdfGenerationService <- InvoicePdfGenerationService.create[IO](paymentsConfig.invoiceConfig.configFilePath)

                // Register invoice generation callback
                // _ <- orderService.registerInvoiceNumberGenerationCallback(invoiceNumberService)
                // _ <- logger.info("Registered invoice number generation callback")

                // Generate invoice numbers retroactively for existing confirmed orders
                retroactiveCount <- invoiceNumberService.generateInvoiceNumbersRetroactively()
                _ <- logger.info(s"Generated $retroactiveCount invoice numbers retroactively during startup")

                // Register order completion callbacks for 'Processing' state
                _ <- orderService.registerCallbackForFinalStatus("project-to-report", domain.OrderStatus.Processing, { context =>
                        (
                            for
                                _                   <- logger.info(s"[REPORT-GENERATION] Order ${context.order.id} completed for customer ${context.customer.email}")
                                
                                // Check ProductMetadata exists
                                fileDesc            <- context.productMetadata
                                    .map { case fd: FileDescriptionWithContent => IO.pure(fd) }
                                    .getOrElse(IO.raiseError(new ProductMetadataMissingException(context.order.id.value)))

                                locale              = context.customer.language.toLocale 

                                // Try to process project file and generate PDF report
                                _                              <- logger.info(s"[REPORT-GENERATION] Processing project file: ${fileDesc.filename} (${fileDesc.mimeType})")
                                (pdfReportFile, invoiceNumber) <- generatePdfReportAndInvoiceNumber[IO](fileDesc, context, invoiceNumberService, locale, paymentsConfig.reportAsDraft)

                                newContext = context.copy(order = context.order.copy(invoiceNumber = Some(invoiceNumber)))

                                // Generate PDF invoice
                                pdfInvoiceAsBytes   <- invoicePdfGenerationService.generateInvoicePdf(newContext, locale)
                                
                                // Send email to user containing PDF invoice and PDF report
                                _                   <- logger.info(s"[EMAIL-NOTIFIER] Sending email to ${newContext.customer.email}")
                                _                   <- logger.info(s"[EMAIL-NOTIFIER] Product: ${newContext.product.name} - Amount: ${newContext.order.amount} ${newContext.order.currency}")
                                _                   <- sendInvoiceWithReportToUser(newContext, fileDesc, pdfReportFile, pdfInvoiceAsBytes, emailService)
                                
                                // Send notification email to admin containing PDF invoice only
                                _                   <- logger.info(s"[ADMIN-INVOICE] Sending admin invoice notification for order ${newContext.order.id}")
                                _                   <- sendAdminInvoiceNotification(newContext, paymentsConfig.adminConfig.email, pdfInvoiceAsBytes, emailService)
                            yield ()
                        ).handleErrorWith { err =>
                            logger.error(s"[REPORT-GENERATION] Error : ${err.getMessage}") *>
                            (err match
                                // Handle EN15544 validation errors - notify customer and admin about invalid project
                                case ex: EN15544ValidationException =>
                                    {
                                        val userNotif = UserNotification(
                                            email = EmailAddress(context.customer.email),
                                            error = ex,
                                            orderId = Some(context.order.id.value.toString),
                                            language = context.customer.language
                                        )
                                        val adminNotif = AdminNotification(
                                            adminEmail = EmailAddress(paymentsConfig.adminConfig.email),
                                            subject = s"EN15544 Validation Failed for Order ${context.order.id.value}",
                                            message = s"Customer ${context.customer.individualNameOrCompanyName} (${context.customer.email}) uploaded an invalid FireCalc project.\n\nValidation errors:\n${ex.validationErrors.mkString("\n- ", "\n- ", "")}",
                                            orderId = Some(context.order.id.value.toString)
                                        )
                                        
                                        logger.info(s"[REPORT-GENERATION] EN15544 validation failed for order ${context.order.id}: ${ex.validationErrors.mkString(", ")}") *>
                                        logger.info(s"[REPORT-GENERATION] Update Order ${context.order.id} with status 'Failed' due to validation errors") *>
                                        orderService.updateOrderStatus(context.order.id, OrderStatus.Failed) *>
                                        logger.info(s"[REPORT-GENERATION] Sending validation error notifications to customer and admin") *>
                                        emailService.sendUserNotification(userNotif).handleErrorWith(e =>
                                            logger.error(s"[REPORT-GENERATION] Failed to send user notification: ${e.getMessage}") *> IO.unit
                                        ) *>
                                        emailService.sendAdminNotification(adminNotif).handleErrorWith(e =>
                                            logger.error(s"[REPORT-GENERATION] Failed to send admin notification: ${e.getMessage}") *> IO.unit
                                        ).void
                                    }
                                
                                // Handle other report errors - mark order as failed
                                case ex: (YAMLFileReadException | YAMLDecodingException | TypstRenderingException | PDFFileCreationException) =>
                                    logger.info(s"[REPORT-GENERATION] Report generation error for order ${context.order.id}: ${ex.getClass.getSimpleName}") *>
                                    logger.info(s"[REPORT-GENERATION] Update Order ${context.order.id} with status 'Failed' because of: ${err.getMessage}") *>
                                    orderService.updateOrderStatus(context.order.id, OrderStatus.Failed).void
                                
                                // Handle payment exceptions - mark order as failed
                                case ex: (ProductMetadataMissingException | PDFGenerationFailedException | InvoiceNumberGenerationFailedException) =>
                                    logger.info(s"[REPORT-GENERATION] Update Order ${context.order.id} with status 'Failed' because of: ${err.getMessage}") *>
                                    orderService.updateOrderStatus(context.order.id, OrderStatus.Failed).void
                                
                                case other =>
                                    IO.raiseError(other)
                            )
                        }
                  })
                _ <- IO.println("Registered PDF invoice + PDF Report generation and sending callback")

                // Register callback for notifying admin when order status becomes "Failed" or "Cancelled"
                _ <- orderService.registerCallbackForFinalStatuses("email-notifier-admin", 
                   Set(domain.OrderStatus.Failed, domain.OrderStatus.Cancelled),
                   { context =>
                      val adminEmail = paymentsConfig.adminConfig.email
                      IO.println(s"[EMAIL-NOTIFIER] Sending email to ${context.customer.email}") *>
                      IO.println(s"[EMAIL-NOTIFIER] Product: ${context.product.name} - Amount: ${context.order.amount}")
                      // Here you could send a completion email using the emailService
                      val adminNotif = AdminNotification(
                        adminEmail = EmailAddress.unsafeFromString(adminEmail),
                        subject = s"Order ${context.order.status} : ${context.order.id.value}",
                        message = s"PDF Report generation failed for customer '${context.customer.individualNameOrCompanyName}' (${context.customer.email}) ? Or payment failed (mandate cancelled ?) ? Make sure the customer will pay its bill by other payment methods.",
                        orderId = Some(context.order.id.value.toString)
                      )
                      emailService.sendAdminNotification(adminNotif).flatMap:
                        handleEmailResult(
                            s"admin notification with PDF invoice ${context.order.invoiceNumber} for customer ${context.customer.email}", 
                            context,
                            adminEmail
                        )
                  })

                // GoCardless configuration - load from config file
                goCardlessConfig <- ConfigLoader.loadGoCardlessConfig[IO]()

                paymentService  <- PaymentService.create[IO](httpClient, goCardlessConfig, emailService, orderService, customerRepo)

                purchaseService <- PurchaseService.create[IO](
                    productRepo,
                    customerRepo,
                    purchaseIntentRepo,
                    productMetadataRepo,
                    authService,
                    orderService,
                    paymentService,
                    emailService
                )

                _ <- IO.println("Setting up HTTP routes...")

                // Setup routes
                purchaseRoutes = PurchaseRoutes.create[IO](purchaseService)
                webhookRoutes  = WebhookRoutes.create[IO](paymentService)
                staticRoutes   = StaticPageRoutes.create[IO](emailConfig)
                healthRoutes   = HealthCheckRoutes.create[IO]
                sourceRoutes   = SourceRoutes.create[IO]

                // Apply selective logging middleware to each route group
                purchaseRoutes_V1_WithLogging = Logger.httpRoutes(
                    logHeaders = true, 
                    logBody = true
                )(purchaseRoutes.routes_V1)
                
                webhookRoutes_V1_WithLogging = Logger.httpRoutes(
                    logHeaders = true, 
                    logBody = true
                )(webhookRoutes.routes_V1)
                
                staticRoutes_V1_WithLogging = Logger.httpRoutes(
                    logHeaders = true, 
                    logBody = false  // Only headers for static routes
                )(staticRoutes.routes_V1)
                
                healthRoutes_V1_WithLogging = Logger.httpRoutes(
                    logHeaders = true,
                    logBody = false  // Only headers for health check routes
                )(healthRoutes.routes_V1)
                
                sourceRoutes_WithLogging = Logger.httpRoutes(
                    logHeaders = true,
                    logBody = false  // Only headers for source routes
                )(sourceRoutes.routes)
                
                // Combine all logged routes
                allRoutes_V1 = purchaseRoutes_V1_WithLogging <+> webhookRoutes_V1_WithLogging <+> staticRoutes_V1_WithLogging <+> healthRoutes_V1_WithLogging <+> sourceRoutes_WithLogging

                // Apply CORS middleware to allow cross-origin requests from frontend
                corsRoutes = CORS.policy.withAllowOriginAll.withAllowCredentials(false).apply(allRoutes_V1)

                // Create HttpApp
                httpApp = corsRoutes.orNotFound

                _ <- IO.println("Starting HTTP server...")

                // Start server
                _ <- EmberServerBuilder
                    .default[IO]
                    .withHost(ipv4"0.0.0.0")
                    .withPort(port"8181")
                    .withHttpApp(httpApp)
                    .build
                    .use(_ =>
                        IO.println("Server started at http://localhost:8181") *>
                        IO.println("Available endpoints:") *>
                        IO.println("  POST /v1/purchase/create-intent") *>
                        IO.println("  POST /v1/purchase/verify-and-process") *>
                        IO.println("  ") *>
                        IO.println("  POST /v1/webhooks/gocardless") *>
                        IO.println("  ") *>
                        IO.println("  GET  /v1/payment_complete") *>
                        IO.println("  GET  /v1/payment_cancelled") *>
                        IO.println("  ") *>
                        IO.println("  GET  /v1/healthcheck") *>
                        IO.println("  GET  /source (AGPLv3 compliance)") *>
                        IO.println("  ") *>
                        IO.println("GoCardless Configuration:") *>
                        IO.println(s"  Environment: ${goCardlessConfig.environment}") *>
                        IO.println(s"  Base URL: ${goCardlessConfig.baseUrl}") *>
                        IO.never
                    )
            yield ExitCode.Success
        }
      } yield ExitCode.Success
    }
