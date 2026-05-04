/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices

import java.io.File
import java.time.LocalDate

import afpma.firecalc.invoices.config.EnvironmentConfigLoader
import afpma.firecalc.invoices.models.Address
import afpma.firecalc.invoices.models.Company
import afpma.firecalc.invoices.models.CompanyConfig
import afpma.firecalc.invoices.models.InvoiceConfig
import afpma.firecalc.invoices.models.InvoiceLineItem
import afpma.firecalc.invoices.models.InvoiceParams
import afpma.firecalc.invoices.models.InvoiceSenderConfig
import afpma.firecalc.invoices.models.InvoiceStatus
import afpma.firecalc.invoices.models.PaymentMethod
import afpma.firecalc.invoices.models.PaymentTerms
import afpma.firecalc.invoices.models.TemplateConfig
import afpma.firecalc.invoices.templates.DefaultInvoiceTemplate

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.taig.babel.Locale
import io.taig.babel.Locales

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
        println("=" * 60                                            )

        val configDir = if (args.nonEmpty) new File(args(0)) else new File("modules/invoices/configs")
        val outputDir = new File("modules/invoices/samples")

        // Ensure directories exist
        if (!configDir.exists()) {
            println         (s"📁 Creating config directory: ${configDir.getAbsolutePath}")
            configDir.mkdirs(                                                             )
        }

        if (!outputDir.exists()) {
            println         (s"📁 Creating output directory: ${outputDir.getAbsolutePath}")
            outputDir.mkdirs(                                                             )
        }

        // Load and process all YAML configurations
        val yamlFiles = Option(configDir.listFiles())
            .map(_.filter(f => f.getName.endsWith(".yaml") || f.getName.endsWith(".yml")).toList)
            .getOrElse(List.empty)

        if (yamlFiles.isEmpty) {
            println            (s"⚠️  No YAML files found in ${configDir.getAbsolutePath}")
            println            ("📝 Creating sample configuration files..."               )
            createSampleConfigs(configDir                                                 )
            println            ("✅ Created sample configuration files"                    )
            return
        }

        println(s"📂 Found ${yamlFiles.length} configuration file(s)")
        println(s"📥 Config directory: ${configDir.getAbsolutePath}" )
        println(s"📤 Output directory: ${outputDir.getAbsolutePath}" )
        println(                                                     )

        var successCount = 0
        var errorCount   = 0

        yamlFiles.zipWithIndex.foreach { case (file, index) =>
            println(s"📄 Processing ${file.getName} (${index + 1}/${yamlFiles.length})")

            loadConfigFromYaml(file) match {
                case Success(config) =>
                    val baseName = file.getName.stripSuffix(".yaml").stripSuffix(".yml")
                    generateInvoiceFromConfig(config, outputDir, baseName) match {
                        case Success(_)     =>
                            successCount += 1
                            println("  ✅ Generated successfully")
                        case Failure(error) =>
                            errorCount += 1
                            println              (s"  ❌ Generation failed: ${error.getMessage}")
                            error.printStackTrace(                                             )
                    }
                case Failure(error)  =>
                    errorCount += 1
                    println              (s"  ❌ Config parsing failed: ${error.getMessage}")
                    error.printStackTrace(                                                 )
            }
        }

        println(                                                           )
        println(s"📊 Results: $successCount successful, $errorCount failed")
        if (successCount > 0) {
            println(s"📁 Output files in: ${outputDir.getAbsolutePath}")
        }

    /** Load invoice configuration from YAML file with environment variable substitution support. */
    def loadConfigFromYaml(file: File): Try[InvoiceConfig] = {
        // Use EnvironmentConfigLoader for environment variable substitution
        EnvironmentConfigLoader.loadFromFile(file)
    }

    /** Generate invoice from configuration in both supported languages. */
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

    /** Generate files for a specific locale. */
    private def generateForLocale(
        config   : InvoiceConfig,
        outputDir: File,
        baseName : String,
        locale   : Locale,
        suffix   : String
    ): Unit = {
        println(s"    🌐 Generating files for locale: $suffix")

        // Use the default template for now
        val template = DefaultInvoiceTemplate()

        // Demo runtime data (hardcoded in Scala as per plan)
        val demoRecipient = Company(
            name    = "Demo Customer Ltd",
            address = Address(
                street     = "456 Client Boulevard",
                city       = "Lyon",
                postalCode = "69001",
                region     = "Auvergne-Rhône-Alpes",
                country    = "France"
            ),
            email   = "accounts@client-solutions.com"
        )

        val demoLineItems = List(
            InvoiceLineItem          (
                description = "Professional Software License",
                quantity    = BigDecimal("1"),
                unitPrice   = BigDecimal("2500.00"),
                taxRate     = BigDecimal("20.0"),
                unit        = Some("license")
            ),
            InvoiceLineItem          (
                description = "Implementation Services",
                quantity    = BigDecimal("40"),
                unitPrice   = BigDecimal("150.00"),
                taxRate     = BigDecimal("20.0"),
                unit        = Some("hours")
            ),
            InvoiceLineItem.taxExempt(
                description = "Non-profit Educational Material",
                quantity    = BigDecimal("5"),
                unitPrice   = BigDecimal("120.00"),
                unit        = Some("units")
            )
        )

        val invoiceParams = InvoiceParams(
            invoiceNumber      = s"DEMO-${suffix.toUpperCase}-001",
            recipient          = demoRecipient,
            lineItems          = demoLineItems,
            paymentTerms       = config.invoice.paymentTerms,
            invoiceDate        = LocalDate.now(),
            dueDate            = None,
            reference          = Some(s"REF-$baseName"),
            currency           = config.invoice.currency,
            notes              = config.invoice.notes,
            discountPercentage = None,
            status             = InvoiceStatus.Sent
        )

        val companyConfig = CompanyConfig(
            sender         = config.invoice.sender,
            templateConfig = config.template
        )

        // Generate Typst file for inspection
        val typstContent = {
            given Locale    = locale
            val invoiceData = invoiceParams.toInvoiceData(companyConfig)
            template.render(invoiceData)
        }
        val typstFile    = new File(outputDir, s"$baseName-$suffix.typ")
        val typstWriter  = new java.io.FileWriter(typstFile)
        try {
            typstWriter.write(typstContent                          )
            println          (s"      ✅ Typst: ${typstFile.getName}")
        } finally {
            typstWriter.close()
        }

        // Generate PDF file with proper locale using new API
        val pdfResult = {
            val factory = FireCalcInvoiceFactory.fromTemplate(companyConfig)
            factory
                .generateInvoiceWithTemplate(invoiceParams, template, locale)
                .flatMap(_.savePdfToFile(s"${outputDir.getAbsolutePath}/$baseName-$suffix.pdf"))
        }

        pdfResult match {
            case Right(pdfFile) =>
                println(s"      ✅ PDF: ${pdfFile.getName}")
            case Left(error)    =>
                throw new RuntimeException(s"PDF generation failed for locale $suffix: $error")
        }
    }

    /** Create sample configuration files for first-time users. */
    def createSampleConfigs(configDir: File): Unit = {
        // Create a simple test config using the new InvoiceConfig structure
        val testConfig = InvoiceConfig(
            invoice  = createSimpleInvoice(),
            template = TemplateConfig()
        )

        // Use the proven encodeToYaml method from CustomYAMLEncoderDecoder
        InvoiceConfig.encodeToYaml(testConfig) match {
            case Success(yamlContent) =>
                val configFile = new File(configDir, "test-simple.yaml")
                val writer     = new java.io.FileWriter(configFile)
                try {
                    writer.write(yamlContent                            )
                    println     (s"    ✅ Created: ${configFile.getName}")
                } finally {
                    writer.close()
                }
            case Failure(error)       =>
                println(s"    ❌ Failed to create config: ${error.getMessage}")
        }
    }

    /** Create a simple invoice for testing. */
    def createSimpleInvoice(): InvoiceSenderConfig = {
        val senderAddress = Address(
            street      = "123 Business Street",
            streetLine2 = Some("Suite 100"),
            city        = "Paris",
            region      = "Île-de-France",
            postalCode  = "75001",
            country     = "France"
        )

        val sender = Company(
            name               = "ACME Development Corp",
            displayName        = Some("ACME Dev"),
            address            = senderAddress,
            vatNumber          = Some("FR12345678901"),
            registrationNumber = Some("RCS Paris 123 456 789"),
            email              = "billing@acme-dev.com",
            phone              = Some("+33 1 23 45 67 89"),
            website            = Some("https://www.acme-dev.com"),
            logo               = None,
            taxExemptNotice    = Some(
                Map     (
                    "fr"      -> "TVA non applicable, art. 293 B du CGI",
                    "en"      -> "Non-profit organization - Tax exempt",
                    "default" -> "Tax exempt"
                )
            )
        )

        val paymentTerms = PaymentTerms(
            description        = "Payment due within 30 days of invoice date",
            dueDays            = 30,
            methods            = List(
                PaymentMethod.BankTransfer(
                    iban = Some("FR14 2004 1010 0505 0001 3M02 606"),
                    bic  = Some("PSSTFRPPXXX")
                )
            ),
            lateFeePercentage  = Some(BigDecimal("1.5")),
            discountPercentage = Some(BigDecimal("2.0")),
            discountDays       = Some(10),
            notes              = Some("Early payment discount: 2% if paid within 10 days")
        )

        InvoiceSenderConfig      (
            sender       = sender,
            paymentTerms = paymentTerms,
            currency     = "EUR",
            notes        = Some("Thank you for your business! This is a test invoice configuration.")
        )
    }
