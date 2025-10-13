/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices

import afpma.firecalc.invoices.models.*
import afpma.firecalc.invoices.templates.{InvoiceTemplate, DefaultInvoiceTemplate}
import io.taig.babel.Locale
import io.github.fatihcatalkaya.javatypst.JavaTypst
import java.io.{File, FileOutputStream}
import java.nio.file.Files
import io.circe.parser.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.syntax.*
import scala.util.{Try, Success, Failure}
import io.taig.babel.Locales

/**
 * Main factory for generating invoice PDFs using dynamic parameters.
 * 
 * This factory provides a template-based API for:
 * - Loading company configuration from YAML
 * - Generating invoices with dynamic parameters (invoice number, recipient, line items)
 * - Customizing templates and locales
 * - Generating PDF files or byte arrays
 */
trait FireCalcInvoiceFactory:
  
  /**
   * Generates an invoice from dynamic parameters.
   */
  def generateInvoice(params: InvoiceParams, locale: Locale): Either[String, InvoiceResult]
  
  /**
   * Generates an invoice with a custom template.
   */
  def generateInvoiceWithTemplate[T](params: InvoiceParams, template: InvoiceTemplate[T], locale: Locale): Either[String, InvoiceResult]

/**
 * Result of invoice generation containing both Typst source and PDF bytes.
 */
case class InvoiceResult(
  typstContent: String,
  pdfBytes: Array[Byte]
):
  /**
   * Saves the PDF to a file.
   */
  def savePdfToFile(outputPath: String): Either[String, File] =
    Try {
      val outputFile = new File(outputPath)
      val fos = new FileOutputStream(outputFile)
      try {
        fos.write(pdfBytes)
        outputFile
      } finally {
        fos.close()
      }
    } match
      case Success(file) => Right(file)
      case Failure(error) => Left(s"Failed to write PDF file: ${error.getMessage}")

  /**
   * Saves the Typst content to a file.
   */
  def saveTypToFile(outputPath: String): Either[String, File] =
    Try {
      val outputFile = new File(outputPath)
      val fos = new FileOutputStream(outputFile)
      try {
        fos.write(typstContent.getBytes("UTF-8"))
        outputFile
      } finally {
        fos.close()
      }
    } match
      case Success(file) => Right(file)
      case Failure(error) => Left(s"Failed to write Typst file: ${error.getMessage}")

object FireCalcInvoiceFactory:
  
  /**
   * Creates a new factory instance from company configuration.
   */
  def fromTemplate(companyConfig: CompanyConfig): FireCalcInvoiceFactory =
    new FireCalcInvoiceFactoryImpl(companyConfig)
  
  /**
   * Creates a factory from a company configuration YAML file.
   */
  def fromConfigFile(configFile: File): Either[String, FireCalcInvoiceFactory] =
    for {
      companyConfig <- loadCompanyConfigFromFile(configFile)
    } yield new FireCalcInvoiceFactoryImpl(companyConfig)
  
  /**
   * Creates a factory from a company configuration YAML string.
   */
  def fromConfigYaml(yamlContent: String): Either[String, FireCalcInvoiceFactory] =
    for {
      companyConfig <- parseCompanyConfig(yamlContent)
    } yield new FireCalcInvoiceFactoryImpl(companyConfig)

  /**
   * Loads company configuration from a YAML file.
   */
  def loadCompanyConfigFromFile(file: File): Either[String, CompanyConfig] =
    Try {
      val path = os.Path(file.getAbsoluteFile())
      val content = os.read(path)
      content
    } match
      case Success(content) => parseCompanyConfig(content)
      case Failure(error) => Left(s"Failed to read config file: ${error.getMessage}")
  
  /**
   * Parses company configuration from YAML string.
   * Extracts CompanyConfig from template structure where:
   * - invoice.sender becomes CompanyConfig.sender
   * - template becomes CompanyConfig.templateConfig
   * 
   * First applies environment variable substitution to resolve template variables.
   */
  def parseCompanyConfig(yamlContent: String): Either[String, CompanyConfig] =
    // First substitute environment variables
    substituteEnvironmentVariables(yamlContent) match
      case Right(substitutedYaml) =>
        yamlParser.parse(substitutedYaml) match
          case Right(json) => 
            // Extract sender from invoice.sender
            val senderResult = json.hcursor.downField("invoice").downField("sender").as[Company]
            // Extract template config from template
            val templateConfigResult = json.hcursor.downField("template").as[TemplateConfig]
            
            (senderResult, templateConfigResult) match
              case (Right(sender), Right(templateConfig)) =>
                val companyConfig = CompanyConfig(
                  sender = sender,
                  templateConfig = templateConfig,
                  defaultLogoPath = Some("modules/invoices/configs/logo.png") // default value
                )
                val errors = companyConfig.validate
                if (errors.nonEmpty)
                  Left(s"Invalid company configuration: ${errors.mkString(", ")}")
                else
                  Right(companyConfig)
              case (Left(senderError), _) =>
                Left(s"Failed to extract sender from invoice.sender: ${senderError.getMessage}")
              case (_, Left(templateError)) =>
                Left(s"Failed to extract template config from template: ${templateError.getMessage}")
          case Left(error) => Left(s"Failed to parse YAML: ${error.getMessage}")
      case Left(error) => Left(s"Failed to substitute environment variables: $error")

  /**
   * Substitute environment variables in YAML content using the same logic as EnvironmentConfigLoader.
   */
  private def substituteEnvironmentVariables(yamlContent: String): Either[String, String] =
    Try {
      import scala.util.matching.Regex
      
      // Regex to match environment variable patterns: ${VAR_NAME} or ${VAR_NAME:-default}
      val envVarPattern: Regex = """\$\{([A-Z_][A-Z0-9_]*)(?::-(.*?))?\}""".r
      
      var result = yamlContent
      var missingVars = List.empty[String]

      // Find all environment variable references
      val matches = envVarPattern.findAllMatchIn(yamlContent).toList

      for (matchResult <- matches) {
        val fullMatch = matchResult.group(0) // ${VAR_NAME} or ${VAR_NAME:-default}
        val varName = matchResult.group(1)   // VAR_NAME
        val defaultValue = Option(matchResult.group(2)) // default value or null

        // Get environment variable value
        sys.env.get(varName) match {
          case Some(envValue) =>
            // Replace with environment variable value
            result = result.replace(fullMatch, envValue)
          
          case None =>
            defaultValue match {
              case Some(default) =>
                // Use default value
                result = result.replace(fullMatch, default)
              case None =>
                // Required variable is missing
                missingVars = varName :: missingVars
            }
        }
      }

      // Check if any required variables are missing
      if (missingVars.nonEmpty) {
        throw new IllegalStateException(
          s"Missing required environment variables: ${missingVars.mkString(", ")}"
        )
      }

      result
    } match {
      case Success(substituted) => Right(substituted)
      case Failure(error) => Left(error.getMessage)
    }

  /**
   * Internal implementation of the factory.
   */
  private class FireCalcInvoiceFactoryImpl(
    private val companyConfig: CompanyConfig
  ) extends FireCalcInvoiceFactory:

    override def generateInvoice(params: InvoiceParams, locale: Locale): Either[String, InvoiceResult] =
      // Use default template
      val template = {
        given Locale = locale
        DefaultInvoiceTemplate.defaultInvoiceTemplate
      }
      generateInvoiceWithTemplate(params, template, locale)

    override def generateInvoiceWithTemplate[T](params: InvoiceParams, template: InvoiceTemplate[T], locale: Locale): Either[String, InvoiceResult] =
      for {
        _ <- validateParams(params)
        // Resolve logo path with fallback logic
        resolvedConfig = companyConfig.withResolvedLogo
        invoiceData = params.toInvoiceData(resolvedConfig)
        typstContent <- renderTemplate(invoiceData, template, locale)
        pdfBytes <- generatePdfFromTypst(typstContent)
      } yield InvoiceResult(typstContent, pdfBytes)

    private def validateParams(params: InvoiceParams): Either[String, Unit] =
      val errors = params.validate
      if (errors.nonEmpty)
        Left(s"Invalid invoice parameters: ${errors.mkString(", ")}")
      else
        Right(())

    private def renderTemplate[T](invoice: InvoiceData, template: InvoiceTemplate[T], locale: Locale): Either[String, String] =
      // Cast template to work with InvoiceData
      val invoiceTemplate = template.asInstanceOf[InvoiceTemplate[InvoiceData]]
      
      // Validate data with template
      val validationResult = invoiceTemplate.validate(invoice)
      validationResult match {
        case Some(validationError) => Left(s"Template validation failed: $validationError")
        case None =>
          val renderResult = Try {
            given Locale = locale
            invoiceTemplate.render(invoice)
          }
          renderResult match {
            case Success(rendered) => Right(rendered)
            case Failure(error) => Left(s"Failed to render template: ${error.getMessage}")
          }
      }

    private def generatePdfFromTypst(typstContent: String): Either[String, Array[Byte]] =
      Try(JavaTypst.render(typstContent)) match
        case Success(bytes) => Right(bytes)
        case Failure(error) => Left(s"Failed to generate PDF: ${error.getMessage}")

/**
 * Convenience object for simplified invoice generation.
 */
object InvoiceGenerator:
  
  /**
   * Generates an invoice PDF from company config and invoice parameters.
   */
  def generatePdf(companyConfig: CompanyConfig, params: InvoiceParams, locale: Locale, outputPath: String): Either[String, File] =
    for {
      factory <- Right(FireCalcInvoiceFactory.fromTemplate(companyConfig))
      result <- factory.generateInvoice(params, locale)
      file <- result.savePdfToFile(outputPath)
    } yield file

  /**
   * Generates an invoice PDF from company config file and invoice parameters.
   */
  def generatePdfFromConfigFile(configFile: File, params: InvoiceParams, locale: Locale, outputPath: String): Either[String, File] =
    for {
      factory <- FireCalcInvoiceFactory.fromConfigFile(configFile)
      result <- factory.generateInvoice(params, locale)
      file <- result.savePdfToFile(outputPath)
    } yield file

  /**
   * Generates Typst string from company config and invoice parameters for inspection/debugging.
   */
  def generateTypst(companyConfig: CompanyConfig, params: InvoiceParams, locale: Locale): Either[String, String] =
    for {
      factory <- Right(FireCalcInvoiceFactory.fromTemplate(companyConfig))
      result <- factory.generateInvoice(params, locale)
    } yield result.typstContent
