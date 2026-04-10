/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.config

import java.nio.file.Files
import java.time.ZoneId

import afpma.firecalc.payments.email.EmailAddress
import afpma.firecalc.payments.email.EmailConfig
import afpma.firecalc.payments.exceptions.*
import afpma.firecalc.payments.invoice.InvoiceNumberValidator
import afpma.firecalc.payments.service.impl.GoCardlessConfig

import cats.effect.Async
import cats.syntax.all.*

import scala.util.Try

import afpma.firecalc.config.ConfigPathResolver
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object ConfigLoader:

    def loadGoCardlessConfig[F[_]: Async](configFilename: String = "gocardless-config.conf"): F[GoCardlessConfig] =
        for {
            configPath <- Async[F].delay {
                ConfigPathResolver.resolveConfigPath("payments", configFilename)
            }
            _          <- Async[F].delay(println(s"Loading GoCardless configuration from: $configPath"))

            // Load the configuration file
            config <- Async[F].delay {
                if (!Files.exists(configPath)) {
                    throw ConfigurationNotFoundException(configPath.toString)
                }
                try {
                    ConfigFactory.parseFile(configPath.toFile).resolve()
                } catch {
                    case ex: Exception =>
                        throw ConfigurationLoadException(configPath.toString, ex.getMessage, Some(ex))
                }
            }

            // Determine environment (default to sandbox)
            environment <- Async[F].delay {
                Try(config.getString("gocardless.environment")).getOrElse("sandbox")
            }

            // Load configuration based on environment
            goCardlessConfig <- environment match {
                case "live" => loadLiveConfig[F](config)
                case _      => loadSandboxConfig[F](config)
            }

            _ <- Async[F].delay(
                println(s"Loaded GoCardless configuration for environment: ${goCardlessConfig.environment}")
            )

        } yield goCardlessConfig

    private def loadSandboxConfig[F[_]: Async](config: Config): F[GoCardlessConfig] =
        Async[F].delay {
            val sandboxConfig = config.getConfig("gocardless.sandbox")

            // Extract domain from redirect-uri to determine protocol and domain
            val redirectUri = sandboxConfig.getString("redirect-uri")
            val protocol: "http" | "https" = if (redirectUri.startsWith("https://")) "https" else "http"
            val domain      = redirectUri.replace("https://", "").replace("http://", "").split("/")(0)
            val adminEmail  = sandboxConfig.getString("admin-email")
            val accessToken = sandboxConfig.getString("access-token")

            GoCardlessConfig
                .sandbox(accessToken, domain, protocol, adminEmail)
                .withWebhookSecret(sandboxConfig.getString("webhook-secret"))
        }

    private def loadLiveConfig[F[_]: Async](config: Config): F[GoCardlessConfig] =
        Async[F].delay {
            val liveConfig = config.getConfig("gocardless.live")

            // Extract domain from redirect-uri
            val redirectUri = liveConfig.getString("redirect-uri")
            val domain      = redirectUri.replace("https://", "").split("/")(0)
            val adminEmail  = liveConfig.getString("admin-email")

            GoCardlessConfig.live  (
                accessToken   = liveConfig.getString("access-token"),
                webhookSecret = liveConfig.getString("webhook-secret"),
                domain        = domain,
                adminEmail    = adminEmail
            )
        }

    def loadPaymentsConfig[F[_]: Async](
        configFilename: String = "payments-config.conf"
    ): F[PaymentsConfig] =
        for {
            configPath <- Async[F].delay {
                ConfigPathResolver.resolveConfigPath("payments", configFilename)
            }
            _          <- Async[F].delay(println(s"Loading payments configuration from: $configPath"))

            // Load the configuration file
            config <- Async[F].delay {
                if (!Files.exists(configPath)) {
                    throw ConfigurationNotFoundException(configPath.toString)
                }
                try {
                    ConfigFactory.parseFile(configPath.toFile).resolve()
                } catch {
                    case ex: Exception =>
                        throw ConfigurationLoadException(configPath.toString, ex.getMessage, Some(ex))
                }
            }

            // Determine environment from config (with env var override and fallback)
            environment <- Async[F].delay {
                sys.env
                    .get("PAYMENTS_ENVIRONMENT")
                    .orElse(Try(config.getString("payments.environment")).toOption)
                    .getOrElse("development")
            }

            // Load configuration based on environment
            paymentsConfig  <- loadPaymentsEnvironmentConfig[F](config, environment)
            validatedConfig <- validatePaymentsConfig[F](paymentsConfig)

            _ <- Async[F].delay(println(s"Loaded payments configuration for environment: $environment"))

        } yield validatedConfig

    private def loadPaymentsEnvironmentConfig[F[_]: Async](config: Config, environment: String): F[PaymentsConfig] =
        Async[F].delay {
            val envConfig = config.getConfig(s"payments.$environment")

            val databaseConfig          = envConfig.getConfig("database")
            val invoiceConfig           = envConfig.getConfig("invoice")
            val retryConfig             = envConfig.getConfig("retry")
            val invoiceGenerationConfig = envConfig.getConfig("invoice-generation")
            val adminConfig             = envConfig.getConfig("admin")
            val jwtSection              = envConfig.getConfig("jwt")

            PaymentsConfig                 (
                environment                  = environment,
                productCatalog               = Try(envConfig.getString("product-catalog")).getOrElse(environment),
                invoiceNumberPrefix          = invoiceConfig.getString("number-prefix"),
                invoiceNumberDigits          = invoiceConfig.getInt("number-digits"),
                invoiceCounterStartingNumber = invoiceConfig.getInt("counter-starting-number"),
                invoiceTimezone              = ZoneId.of(
                    Try(invoiceConfig.getString("timezone")).getOrElse("Europe/Paris")
                ),
                retryConfig                  = InvoiceRetryConfig(
                    maxRetries  = retryConfig.getInt("max-retries"),
                    baseDelayMs = retryConfig.getLong("base-delay-ms"),
                    maxDelayMs  = retryConfig.getLong("max-delay-ms")
                ),
                databaseConfig               = DatabaseConfig(
                    filename = databaseConfig.getString("filename"),
                    path     = databaseConfig.getString("path")
                ),
                invoiceConfig                = InvoiceConfig(
                    configFilePath = invoiceGenerationConfig.getString("config-file-path"),
                    enabled        = Try(invoiceGenerationConfig.getBoolean("enabled")).getOrElse(true)
                ),
                adminConfig                  = AdminConfig(
                    email = adminConfig.getString("email")
                ),
                reportAsDraft                = Try(envConfig.getBoolean("report-as-draft")).getOrElse(false),
                jwtConfig                    = JwtConfig(
                    secret            = jwtSection.getString("secret"),
                    expirationMinutes = Try(jwtSection.getInt("expiration-minutes")).getOrElse(60),
                    issuer            = Try(jwtSection.getString("issuer")).getOrElse("firecalc-payments")
                ),
                loggingConfig                = Try(envConfig.getConfig("logging")).map { loggingSection =>
                    import scala.jdk.CollectionConverters.*
                    LoggingConfig(
                        rootLevel        = Try(loggingSection.getString("root-level")).getOrElse("INFO"),
                        packageOverrides = Try(
                            loggingSection.getConfig("package-overrides")
                                .entrySet().asScala
                                .map(e => e.getKey -> e.getValue.unwrapped().toString)
                                .toMap
                        ).getOrElse(Map.empty)
                    )
                }.getOrElse(LoggingConfig()),
                corsAllowedOrigins           = Try {
                    import scala.jdk.CollectionConverters.*
                    envConfig.getStringList("cors-allowed-origins").asScala.toList
                }.getOrElse(List("*"))
            )
        }

    private[config] def validatePaymentsConfig[F[_]: Async](config: PaymentsConfig): F[PaymentsConfig] =
        InvoiceNumberValidator.validatePrefix(config.invoiceNumberPrefix) match {
            case Left(error) => Async[F].raiseError(new IllegalArgumentException(error))
            case Right(_) =>
                if (
                    config.environment != "development" &&
                    (config.corsAllowedOrigins.contains("*") || config.corsAllowedOrigins.isEmpty)
                ) then
                    Async[F].raiseError(
                        new IllegalStateException(
                            s"SEC-016: CORS wildcard or empty origin list is not allowed in '${config.environment}' environment. Configure explicit origins in cors-allowed-origins."
                        )
                    )
                else Async[F].pure(config)
        }

    def loadEmailConfig[F[_]: Async](
        configFilename: String = "email-config.conf"
    ): F[EmailConfig] =
        for {
            configPath <- Async[F].delay {
                ConfigPathResolver.resolveConfigPath("payments", configFilename)
            }
            _          <- Async[F].delay(println(s"Loading email configuration from: $configPath"))

            // Load the configuration file
            config <- Async[F].delay {
                if (!Files.exists(configPath)) {
                    throw ConfigurationNotFoundException(configPath.toString)
                }
                try {
                    ConfigFactory.parseFile(configPath.toFile).resolve()
                } catch {
                    case ex: Exception =>
                        throw ConfigurationLoadException(configPath.toString, ex.getMessage, Some(ex))
                }
            }

            // Determine environment from config (with env var override and fallback)
            environment <- Async[F].delay {
                sys.env
                    .get("PAYMENTS_ENVIRONMENT")
                    .orElse(Try(config.getString("email.environment")).toOption)
                    .getOrElse("development")
            }

            // Load configuration based on environment
            emailConfig <- loadEmailEnvironmentConfig[F](config, environment)

            _ <- Async[F].delay(println(s"Loaded email configuration for environment: $environment"))

        } yield emailConfig

    private def loadEmailEnvironmentConfig[F[_]: Async](config: Config, environment: String): F[EmailConfig] =
        Async[F].delay {
            val envConfig = config.getConfig(s"email.$environment")

            val smtpConfig    = envConfig.getConfig("smtp")
            val fromConfig    = envConfig.getConfig("from")
            val supportConfig = envConfig.getConfig("support")
            val websiteConfig = envConfig.getConfig("website")

            EmailConfig    (
                smtpHost     = smtpConfig.getString("host"),
                smtpPort     = smtpConfig.getInt("port"),
                username     = smtpConfig.getString("username"),
                password     = smtpConfig.getString("password"),
                fromAddress  = EmailAddress.unsafeFromString(fromConfig.getString("address")),
                fromName     = fromConfig.getString("name"),
                useTLS       = Try(smtpConfig.getBoolean("use-tls")).getOrElse(true),
                supportEmail = EmailAddress.unsafeFromString(supportConfig.getString("address")),
                websiteUrl   = websiteConfig.getString("url")
            )
        }
