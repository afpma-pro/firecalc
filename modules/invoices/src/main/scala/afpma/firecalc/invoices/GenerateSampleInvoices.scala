/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices

import afpma.firecalc.invoices.models.{InvoiceConfig, InvoiceData, TemplateConfig, Company, Address, PaymentTerms, PaymentMethod, InvoiceLineItem, InvoiceStatus, CompanyConfig, InvoiceParams}
import afpma.firecalc.invoices.templates.DefaultInvoiceTemplate
import afpma.firecalc.invoices.typst.*
import afpma.firecalc.invoices.examples.ExampleInvoiceGeneration.given
import afpma.firecalc.invoices.config.EnvironmentConfigLoader
import io.taig.babel.{Locale, Locales}

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}
import java.time.LocalDate
import scala.io.Source
import scala.util.{Try, Success, Failure}

/**
 * Fast iteration tool for invoice PDF/Typst design development using YAML configs.
 * 
 * Usage:
 * - Run: sbt "invoices/runMain afpma.firecalc.invoices.GenerateSampleInvoices"
 * - Auto-reload: sbt "~invoices/runMain afpma.firecalc.invoices.GenerateSampleInvoices"
 * - Custom config dir: sbt "invoices/runMain afpma.firecalc.invoices.GenerateSampleInvoices /path/to/configs"
 * 
 * This tool:
 * 1. Scans for YAML files in the configs directory (default: modules/invoices/configs/)
 * 2. Loads invoice configurations using the proven CustomYAMLEncoderDecoder pattern
 * 3. Generates both PDF and Typst files for each configuration
 * 4. Supports custom template configurations per invoice
 */
object GenerateSampleInvoices:

  def main(args: Array[String]): Unit =
    println("🧾 FireCalc Invoice Sample Generator (YAML-driven)")
    println("=" * 60)
    
    val configDir = if (args.nonEmpty) new File(args(0)) else new File("modules/invoices/configs")
    val outputDir = new File("modules/invoices/samples")
    
    // Ensure directories exist
    if (!configDir.exists()) {
      println(s"📁 Creating config directory: ${configDir.getAbsolutePath}")
      configDir.mkdirs()
    }
    
    if (!outputDir.exists()) {
      println(s"📁 Creating output directory: ${outputDir.getAbsolutePath}")
      outputDir.mkdirs()
    }
    
    // Load and process all YAML configurations
    val yamlFiles = Option(configDir.listFiles())
      .map(_
        .filter(f => f.getName.endsWith(".yaml") || f.getName.endsWith(".yml")).toList
      )
      .getOrElse(List.empty)
    
    if (yamlFiles.isEmpty) {
      println(s"⚠️  No YAML files found in ${configDir.getAbsolutePath}")
      println("📝 Creating sample configuration files...")
      createSampleConfigs(configDir)
      println("✅ Created sample configuration files")
      return
    }
    
    println(s"📂 Found ${yamlFiles.length} configuration file(s)")
    println(s"📥 Config directory: ${configDir.getAbsolutePath}")
    println(s"📤 Output directory: ${outputDir.getAbsolutePath}")
    println()
    
    var successCount = 0
    var errorCount = 0
    
    yamlFiles.zipWithIndex.foreach { case (file, index) =>
      println(s"📄 Processing ${file.getName} (${index + 1}/${yamlFiles.length})")
      
      loadConfigFromYaml(file) match {
        case Success(config) =>
          val baseName = file.getName.stripSuffix(".yaml").stripSuffix(".yml")
          generateInvoiceFromConfig(config, outputDir, baseName) match {
            case Success(_) =>
              successCount += 1
              println(s"  ✅ Generated successfully")
            case Failure(error) =>
              errorCount += 1
              println(s"  ❌ Generation failed: ${error.getMessage}")
              error.printStackTrace()
          }
        case Failure(error) =>
          errorCount += 1
          println(s"  ❌ Config parsing failed: ${error.getMessage}")
          error.printStackTrace()
      }
    }
    
    println()
    println(s"📊 Results: $successCount successful, $errorCount failed")
    if (successCount > 0) {
      println(s"📁 Output files in: ${outputDir.getAbsolutePath}")
    }

  /**
   * Load invoice configuration from YAML file with environment variable substitution support.
   */
  def loadConfigFromYaml(file: File): Try[InvoiceConfig] = {
    // Use EnvironmentConfigLoader for environment variable substitution
    EnvironmentConfigLoader.loadFromFile(file)
  }

  /**
   * Generate invoice from configuration in both supported languages.
   */
  def generateInvoiceFromConfig(config: InvoiceConfig, outputDir: File, baseName: String): Try[Unit] = {
    Try {
      // List of supported locales with their suffixes
      val locales = List(
        (Locales.en, "en"),
        (Locales.fr, "fr")
      )

      // Generate files for each locale
      locales.foreach { case (locale, suffix) =>
        generateForLocale(config, outputDir, baseName, locale, suffix)
      }
    }
  }

  /**
   * Generate files for a specific locale.
   */
  private def generateForLocale(config: InvoiceConfig, outputDir: File, baseName: String, locale: Locale, suffix: String): Unit = {
    println(s"    🌐 Generating files for locale: $suffix")
    
    // Use the default template for now
    val template = DefaultInvoiceTemplate()

    // Generate Typst file for inspection
    val typstContent = {
      given Locale = locale
      template.render(config.invoice)
    }
    val typstFile = new File(outputDir, s"$baseName-$suffix.typ")
    val typstWriter = new java.io.FileWriter(typstFile)
    try {
      typstWriter.write(typstContent)
      println(s"      ✅ Typst: ${typstFile.getName}")
    } finally {
      typstWriter.close()
    }

    // Generate PDF file with proper locale using new API
    val pdfResult = {
      given Locale = locale
      
      // Convert InvoiceConfig to CompanyConfig + InvoiceParams structure
      val companyConfig = CompanyConfig(
        sender = config.invoice.sender,
        templateConfig = config.template
      )
      
      val invoiceParams = InvoiceParams(
        invoiceNumber = config.invoice.invoiceNumber,
        recipient = config.invoice.recipient,
        lineItems = config.invoice.lineItems,
        paymentTerms = config.invoice.paymentTerms,
        invoiceDate = config.invoice.invoiceDate,
        dueDate = config.invoice.dueDate,
        reference = config.invoice.reference,
        billTo = config.invoice.billTo,
        currency = config.invoice.currency,
        notes = config.invoice.notes,
        discountPercentage = config.invoice.discountPercentage,
        status = config.invoice.status
      )
      
      val factory = FireCalcInvoiceFactory.fromTemplate(companyConfig)
      factory.generateInvoiceWithTemplate(invoiceParams, template, locale)
        .flatMap(_.savePdfToFile(s"${outputDir.getAbsolutePath}/$baseName-$suffix.pdf"))
    }
    
    pdfResult match {
      case Right(pdfFile) => 
        println(s"      ✅ PDF: ${pdfFile.getName}")
      case Left(error) => 
        throw new RuntimeException(s"PDF generation failed for locale $suffix: $error")
    }
  }

  /**
   * Create sample configuration files for first-time users.
   */
  def createSampleConfigs(configDir: File): Unit = {
    // Create a simple test config using the new InvoiceConfig structure
    val testConfig = InvoiceConfig(
      invoice = createSimpleInvoice(),
      template = TemplateConfig()
    )
    
    // Use the proven encodeToYaml method from CustomYAMLEncoderDecoder
    InvoiceConfig.encodeToYaml(testConfig) match {
      case Success(yamlContent) =>
        val configFile = new File(configDir, "test-simple.yaml")
        val writer = new java.io.FileWriter(configFile)
        try {
          writer.write(yamlContent)
          println(s"    ✅ Created: ${configFile.getName}")
        } finally {
          writer.close()
        }
      case Failure(error) =>
        println(s"    ❌ Failed to create config: ${error.getMessage}")
    }
  }

  /**
   * Create a simple invoice for testing.
   */
  def createSimpleInvoice(): InvoiceData = {
    val senderAddress = Address(
      street = "123 Business Street",
      streetLine2 = Some("Suite 100"),
      city = "Paris",
      region = "Île-de-France",
      postalCode = "75001",
      country = "France"
    )

    val sender = Company(
      name = "ACME Development Corp",
      displayName = Some("ACME Dev"),
      address = senderAddress,
      vatNumber = Some("FR12345678901"),
      registrationNumber = Some("RCS Paris 123 456 789"),
      email = "billing@acme-dev.com",
      phone = Some("+33 1 23 45 67 89"),
      website = Some("https://www.acme-dev.com"),
      logo = None
    )

    val customerAddress = Address(
      street = "456 Client Boulevard",
      streetLine2 = None,
      city = "Lyon",
      region = "Auvergne-Rhône-Alpes",
      postalCode = "69001",
      country = "France"
    )

    val customer = Company(
      name = "Client Solutions Ltd",
      displayName = None,
      address = customerAddress,
      vatNumber = Some("FR98765432101"),
      registrationNumber = Some("RCS Lyon 987 654 321"),
      email = "accounts@client-solutions.com",
      phone = Some("+33 4 78 90 12 34"),
      website = Some("https://www.client-solutions.com"),
      logo = None
    )

    val paymentTerms = PaymentTerms(
      description = "Payment due within 30 days of invoice date",
      dueDays = 30,
      methods = List(
        PaymentMethod.BankTransfer(
          iban = Some("FR14 2004 1010 0505 0001 3M02 606"),
          bic = Some("PSSTFRPPXXX")
        )
      ),
      lateFeePercentage = Some(BigDecimal("1.5")),
      discountPercentage = Some(BigDecimal("2.0")),
      discountDays = Some(10),
      notes = Some("Early payment discount: 2% if paid within 10 days")
    )

    val lineItems = List(
      InvoiceLineItem(
        description = "Professional Software License",
        quantity = BigDecimal("1"),
        unitPrice = BigDecimal("2500.00"),
        taxRate = BigDecimal("20.0"),
        unit = Some("license")
      ),
      InvoiceLineItem(
        description = "Implementation Services",
        quantity = BigDecimal("40"),
        unitPrice = BigDecimal("150.00"),
        taxRate = BigDecimal("20.0"),
        unit = Some("hours")
      )
    )

    InvoiceData(
      invoiceNumber = "INV-TEST-001",
      invoiceDate = LocalDate.now(),
      dueDate = Some(LocalDate.now().plusDays(30)),
      reference = Some("Test Invoice"),
      sender = sender,
      recipient = customer,
      billTo = None,
      lineItems = lineItems,
      paymentTerms = paymentTerms,
      currency = "EUR",
      notes = Some("Thank you for your business! This is a test invoice."),
      discountPercentage = None,
      status = InvoiceStatus.Draft
    )
  }
