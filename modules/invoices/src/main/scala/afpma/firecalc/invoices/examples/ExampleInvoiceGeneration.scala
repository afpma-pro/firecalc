/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.examples

import afpma.firecalc.invoices.models.{InvoiceData, Company, Address, InvoiceLineItem, PaymentTerms, PaymentMethod, InvoiceStatus, CompanyConfig, InvoiceParams, TemplateConfig}
import afpma.firecalc.invoices.templates.{DefaultInvoiceTemplate, LogoPosition}
import afpma.firecalc.invoices.templates.{TemplateConfig => TemplateTemplateConfig}
import afpma.firecalc.invoices.{FireCalcInvoiceFactory, InvoiceGenerator}
import io.taig.babel.Locale
import java.time.LocalDate
import java.io.File
import io.taig.babel.Locales

/**
 * Example demonstrating how to use the new dynamic invoice generation system.
 * This shows how to separate static company configuration from dynamic invoice parameters.
 */
object ExampleInvoiceGeneration:

  given Locale = Locales.en

  def main(args: Array[String]): Unit =
    println("FireCalc Dynamic Invoice Generation Examples")
    println("===========================================")
    
    // Example 1: Create invoices with dynamic parameters
    example1_DynamicInvoices()
    
    // Example 2: Create invoices with custom template
    example2_CustomTemplate()
    
    // Example 3: Generate Typst markup only (for debugging)
    example3_TypstOnly()
    
    // Example 4: Generate multiple invoices with same company config
    example4_MultipleInvoices()
    
    // Example 5: Generate tax-exempt invoice for non-profit organization
    example5_TaxExemptInvoice()

  /**
   * Example 1: Create invoices with dynamic parameters
   */
  def example1_DynamicInvoices(): Unit =
    println("\n1. Dynamic Invoice Generation")
    println("-" * 30)
    
    val (companyConfig, client1Params) = createSampleData()
    
    InvoiceGenerator.generatePdf(companyConfig, client1Params, Locales.en, "modules/invoices/samples/simple_invoice.pdf") match
      case Right(file) => 
        println(s"✅ Invoice generated successfully: ${file.getAbsolutePath}")
      case Left(error) => 
        println(s"❌ Failed to generate invoice: $error")

  /**
   * Example 2: Create invoices with custom template configuration
   */
  def example2_CustomTemplate(): Unit =
    println("\n2. Custom Template Configuration")
    println("-" * 35)
    
    val (companyConfig, invoiceParams) = createSampleData()
    
    val customTemplateConfig = TemplateTemplateConfig(
      logoPosition = LogoPosition.TopRight,
      primaryColor = "#2563eb"
    )
    
    val customModelConfig = TemplateConfig()
    val customCompanyConfig = companyConfig.copy(templateConfig = customModelConfig)
    val customTemplate = DefaultInvoiceTemplate.withConfig(customTemplateConfig)
    
    val factory = FireCalcInvoiceFactory.fromTemplate(customCompanyConfig)
    factory.generateInvoiceWithTemplate(invoiceParams, customTemplate, Locales.en)
      .flatMap(_.savePdfToFile("modules/invoices/samples/custom_invoice.pdf")) match
        case Right(file) => 
          println(s"✅ Custom invoice generated successfully: ${file.getAbsolutePath}")
        case Left(error) => 
          println(s"❌ Failed to generate custom invoice: $error")

  /**
   * Example 3: Generate only Typst markup for debugging
   */
  def example3_TypstOnly(): Unit =
    println("\n3. Typst Markup Generation")
    println("-" * 27)
    
    val (companyConfig, invoiceParams) = createSampleData()
    
    InvoiceGenerator.generateTypst(companyConfig, invoiceParams, Locales.en) match
      case Right(typstMarkup) => 
        println("✅ Typst markup generated successfully")
        println("First 200 characters:")
        println(typstMarkup.take(200) + "...")
        
        // Optionally save to file for inspection
        val file = new File("modules/invoices/samples/invoice.typ")
        val writer = new java.io.FileWriter(file)
        try {
          writer.write(typstMarkup)
          println(s"✅ Typst markup saved to: ${file.getAbsolutePath}")
        } finally {
          writer.close()
        }
        
      case Left(error) => 
        println(s"❌ Failed to generate Typst markup: $error")

  /**
   * Example 4: Generate multiple invoices with same company config
   */
  def example4_MultipleInvoices(): Unit =
    println("\n4. Multiple Invoice Generation")
    println("-" * 31)
    
    val (companyConfig, _) = createSampleData()
    val factory = FireCalcInvoiceFactory.fromTemplate(companyConfig)
    
    // Create different clients and invoice parameters
    val clients = List(
      ("Client A", "INV-2025-001", BigDecimal("1500.00")),
      ("Client B", "INV-2025-002", BigDecimal("2800.00")),
      ("Client C", "INV-2025-003", BigDecimal("950.00"))
    )
    
    clients.foreach { case (clientName, invoiceNumber, amount) =>
      val invoiceParams = createInvoiceParamsForClient(clientName, invoiceNumber, amount)
      
      factory.generateInvoice(invoiceParams, Locales.en)
        .map: invoiceResult =>
          invoiceResult.savePdfToFile(s"modules/invoices/samples/invoice_${clientName.replace(" ", "_").toLowerCase}.pdf") match
            case Right(file) => 
              println(s"✅ Invoice for $clientName: ${file.getName}")
              invoiceResult
            case Left(error) => 
              println(s"❌ Failed to generate invoice for $clientName: $error")
              invoiceResult
        .map: invoiceResult =>
          invoiceResult.saveTypToFile(s"modules/invoices/samples/invoice_${clientName.replace(" ", "_").toLowerCase}.typ") match
            case Right(file) => 
              println(s"✅ Invoice for $clientName: ${file.getName}")
            case Left(error) => 
              println(s"❌ Failed to generate invoice for $clientName: $error")
    }

  /**
   * Example 5: Generate tax-exempt invoice for non-profit organization
   */
  def example5_TaxExemptInvoice(): Unit =
    println("\n5. Tax-Exempt Invoice Generation")
    println("-" * 33)
    
    val (companyConfig, _) = createSampleData()
    
    // Create a non-profit organization client
    val nonProfitAddress = Address(
      street = "15 Association Boulevard",
      streetLine2 = Some("Bureau 201"),
      city = "Marseille",
      region = "Provence-Alpes-Côte d'Azur",
      postalCode = "13001",
      country = "France"
    )

    val nonProfitClient = Company(
      name = "Association Aide Humanitaire",
      displayName = Some("AAH - Association Loi 1901"),
      address = nonProfitAddress,
      vatNumber = None, // Non-profits often don't have VAT numbers
      registrationNumber = Some("W751234567"), // Association registration number
      email = "compta@aide-humanitaire.org",
      phone = Some("+33 4 91 12 34 56"),
      website = Some("https://www.aide-humanitaire.org"),
      logo = None
    )

    val taxExemptPaymentTerms = PaymentTerms(
      description = "Payment due within 30 days",
      dueDays = 30,
      methods = List(
        PaymentMethod.BankTransfer(
          iban = Some("FR14 2004 1010 0505 0001 3M02 606"),
          bic = Some("PSST FR PP XXX")
        ),
        PaymentMethod.SepaMandate(
          mandateReference = Some("MANDATE-ASSO-001"),
          mandateDate = Some(LocalDate.of(2025, 1, 10)),
          iban = Some("FR14 2004 1010 0505 0001 3M02 606")
        ),
        PaymentMethod.Check
      ),
      lateFeePercentage = None, // Often no late fees for non-profits
      discountPercentage = Some(BigDecimal("5.0")), // Special non-profit discount
      discountDays = Some(15),
      notes = Some("Special conditions for registered non-profit organizations")
    )

    // Create tax-exempt line items using the new convenience methods
    val taxExemptLineItems = List(
      InvoiceLineItem.taxExemptService(
        description = "Formation professionnelle pour bénévoles",
        hours = BigDecimal("16"),
        hourlyRate = BigDecimal("75.00"),
      ),
      InvoiceLineItem.taxExempt(
        description = "Matériel pédagogique spécialisé",
        quantity = BigDecimal("5"),
        unitPrice = BigDecimal("120.00"),
        unit = Some("unités")
      ),
    )

    val taxExemptInvoiceParams = InvoiceParams(
      invoiceNumber = "INV-ASSO-2025-001",
      recipient = nonProfitClient,
      lineItems = taxExemptLineItems,
      paymentTerms = taxExemptPaymentTerms,
      invoiceDate = LocalDate.now(),
      dueDate = Some(LocalDate.now().plusDays(30)),
      reference = Some("FORMATION-BENEVOLES-2025"),
      currency = "EUR",
      notes = Some("Facture avec exonération de TVA conformément au statut d'association Loi 1901. Certains services restent soumis à la TVA selon la réglementation en vigueur."),
      discountPercentage = None,
      status = InvoiceStatus.Draft
    )

    val factory = FireCalcInvoiceFactory.fromTemplate(companyConfig)
    factory.generateInvoice(taxExemptInvoiceParams, Locales.fr) // Generate in French for this example
      .flatMap { invoiceResult =>
        invoiceResult
        .saveTypToFile("modules/invoices/samples/tax_exempt_nonprofit_invoice.typ")   
        .flatMap: _ =>
            invoiceResult
            .savePdfToFile("modules/invoices/samples/tax_exempt_nonprofit_invoice.pdf")
      } match
        case Right(file) => 
          println(s"✅ Tax-exempt invoice generated successfully: ${file.getName}")
          println("   📋 Features demonstrated:")
          println("   • SEPA Mandate payment method with mandate details")
          println("   • Tax-exempt line items for non-profit organizations")
          println("   • Mixed tax/tax-exempt items in same invoice")
          println("   • Special non-profit payment terms")
          println("   • French localization")
        case Left(error) => 
          println(s"❌ Failed to generate tax-exempt invoice: $error")

  /**
   * Creates a comprehensive sample invoice for testing.
   */
  def createSampleInvoice(): InvoiceData =
    val senderAddress = Address(
      street = "123 Business Street",
      streetLine2 = Some("Suite 100"),
      city = "Paris",
      region = "Île-de-France",
      postalCode = "75001",
      country = "France"
    )

    val sender = Company(
      name = "ACME Corp",
      displayName = Some("ACME Solutions"),
      address = senderAddress,
      vatNumber = Some("FR12345678901"),
      registrationNumber = Some("RCS Paris 123 456 789"),
      email = "contact@acme-corp.com",
      phone = Some("+33 1 23 45 67 89"),
      website = Some("https://www.acme-corp.com"),
      logo = None // Let CompanyConfig handle logo resolution with fallback
    )

    val customerAddress = Address(
      street = "456 Client Avenue",
      streetLine2 = None,
      city = "Lyon",
      region = "Auvergne-Rhône-Alpes",
      postalCode = "69001",
      country = "France"
    )

    val customer = Company(
      name = "Client Industries",
      displayName = None,
      address = customerAddress,
      vatNumber = Some("FR98765432101"),
      registrationNumber = Some("RCS Lyon 987 654 321"),
      email = "finance@client-industries.com",
      phone = Some("+33 4 78 90 12 34"),
      website = Some("https://www.client-industries.com"),
      logo = None
    )

    val paymentTerms = PaymentTerms(
      description = "Payment due within 30 days",
      dueDays = 30,
      methods = List(
        PaymentMethod.BankTransfer(
          iban = Some("FR14 2004 1010 0505 0001 3M02 606"),
          bic = Some("PSST FR PP XXX")
        ),
        PaymentMethod.SepaMandate(
          mandateReference = Some("MANDATE-2025-001"),
          mandateDate = Some(LocalDate.of(2025, 1, 15)),
          iban = Some("FR14 2004 1010 0505 0001 3M02 606")
        ),
        PaymentMethod.PayPal(email = Some("payments@acme-corp.com"))
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
        discountPercentage = Some(BigDecimal("0")),
        unit = Some("license")
      ),
      InvoiceLineItem(
        description = "Implementation and Training",
        quantity = BigDecimal("40"),
        unitPrice = BigDecimal("150.00"),
        taxRate = BigDecimal("20.0"),
        discountPercentage = Some(BigDecimal("10")),
        unit = Some("hours")
      ),
      InvoiceLineItem(
        description = "Annual Support Contract",
        quantity = BigDecimal("1"),
        unitPrice = BigDecimal("800.00"),
        taxRate = BigDecimal("20.0"),
        discountPercentage = Some(BigDecimal("0")),
        unit = Some("year")
      )
    )

    InvoiceData(
      invoiceNumber = "INV-2025-001",
      invoiceDate = LocalDate.now(),
      dueDate = Some(LocalDate.now().plusDays(30)),
      reference = Some("CONTRACT-2025-ABC"),
      sender = sender,
      recipient = customer,
      billTo = None,
      lineItems = lineItems,
      paymentTerms = paymentTerms,
      currency = "EUR",
      notes = Some("Thank you for your business! For any questions regarding this invoice, please contact our accounting department."),
      discountPercentage = Some(BigDecimal("0")),
      status = InvoiceStatus.Draft
    )

  /**
   * Example of how to handle different invoice scenarios.
   */
  def additionalExamples(): Unit =
    println("\n4. Additional Examples")
    println("-" * 21)
    
    // Example with international customer
    val usAddress = Address(
      street = "123 Broadway",
      streetLine2 = None,
      city = "New York",
      region = "NY",
      postalCode = "10001",
      country = "United States"
    )
    
    val usCustomer = Company(
      name = "US Client Corp",
      displayName = None,
      address = usAddress,
      vatNumber = None,
      registrationNumber = Some("EIN 12-3456789"),
      email = "billing@usclient.com",
      phone = Some("+1 212 555 0123"),
      website = Some("https://www.usclient.com"),
      logo = None
    )
    
    val internationalInvoice = createSampleInvoice().copy(
      currency = "USD",
      recipient = usCustomer
    )
    
    // Example with multiple tax rates
    val multiTaxInvoice = createSampleInvoice().copy(
      lineItems = List(
        InvoiceLineItem(
          description = "Standard Rate Service",
          quantity = BigDecimal("1"),
          unitPrice = BigDecimal("1000"),
          taxRate = BigDecimal("20.0")
        ),
        InvoiceLineItem(
          description = "Reduced Rate Goods",
          quantity = BigDecimal("5"),
          unitPrice = BigDecimal("50"),
          taxRate = BigDecimal("5.5"),
          unit = Some("units")
        ),
        InvoiceLineItem(
          description = "Zero Rate Export",
          quantity = BigDecimal("1"),
          unitPrice = BigDecimal("500"),
          taxRate = BigDecimal("0.0")
        )
      )
    )
    
    println("Additional invoice scenarios created for testing different tax rates and international billing.")

  /**
   * Creates sample company configuration and invoice parameters for testing.
   */
  def createSampleData(): (CompanyConfig, InvoiceParams) =
    // Company configuration (static)
    val senderAddress = Address(
      street = "123 Business Street",
      streetLine2 = Some("Suite 100"),
      city = "Paris",
      region = "Île-de-France",
      postalCode = "75001",
      country = "France"
    )

    val sender = Company(
      name = "ACME Corp",
      displayName = Some("ACME Solutions"),
      address = senderAddress,
      vatNumber = Some("FR12345678901"),
      registrationNumber = Some("RCS Paris 123 456 789"),
      email = "contact@acme-corp.com",
      phone = Some("+33 1 23 45 67 89"),
      website = Some("https://www.acme-corp.com"),
      logo = None // Let CompanyConfig handle logo resolution with fallback
    )

    val companyConfig = CompanyConfig(
      sender = sender,
      templateConfig = TemplateConfig()
    )

    // Client and invoice parameters (dynamic)
    val customerAddress = Address(
      street = "456 Client Avenue",
      streetLine2 = None,
      city = "Lyon",
      region = "Auvergne-Rhône-Alpes",
      postalCode = "69001",
      country = "France"
    )

    val customer = Company(
      name = "Client Industries",
      displayName = None,
      address = customerAddress,
      vatNumber = Some("FR98765432101"),
      registrationNumber = Some("RCS Lyon 987 654 321"),
      email = "finance@client-industries.com",
      phone = Some("+33 4 78 90 12 34"),
      website = Some("https://www.client-industries.com"),
      logo = None
    )

    val paymentTerms = PaymentTerms(
      description = "Payment due within 30 days",
      dueDays = 30,
      methods = List(
        PaymentMethod.BankTransfer(
          iban = Some("FR14 2004 1010 0505 0001 3M02 606"),
          bic = Some("PSST FR PP XXX")
        ),
        PaymentMethod.PayPal(email = Some("payments@acme-corp.com"))
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
        discountPercentage = Some(BigDecimal("0")),
        unit = Some("license")
      ),
      InvoiceLineItem(
        description = "Implementation and Training",
        quantity = BigDecimal("40"),
        unitPrice = BigDecimal("150.00"),
        taxRate = BigDecimal("20.0"),
        discountPercentage = Some(BigDecimal("10")),
        unit = Some("hours")
      )
    )

    val invoiceParams = InvoiceParams(
      invoiceNumber = "INV-2025-001",
      recipient = customer,
      lineItems = lineItems,
      paymentTerms = paymentTerms,
      invoiceDate = LocalDate.now(),
      dueDate = Some(LocalDate.now().plusDays(30)),
      reference = Some("CONTRACT-2025-ABC"),
      currency = "EUR",
      notes = Some("Thank you for your business! For any questions regarding this invoice, please contact our accounting department."),
      discountPercentage = Some(BigDecimal("0")),
      status = InvoiceStatus.Draft
    )

    (companyConfig, invoiceParams)

  /**
   * Creates invoice parameters for a specific client.
   */
  def createInvoiceParamsForClient(clientName: String, invoiceNumber: String, amount: BigDecimal): InvoiceParams =
    val customerAddress = Address(
      street = s"${clientName} Street",
      streetLine2 = None,
      city = "Business City",
      region = "Business Region",
      postalCode = "12345",
      country = "France"
    )

    val customer = Company(
      name = s"$clientName Ltd",
      displayName = None,
      address = customerAddress,
      vatNumber = Some("FR11111111111"),
      registrationNumber = Some("RCS Business 111 111 111"),
      email = s"billing@${clientName.toLowerCase.replace(" ", "")}.com",
      phone = Some("+33 1 11 11 11 11"),
      website = Some(s"https://www.${clientName.toLowerCase.replace(" ", "")}.com"),
      logo = None
    )

    val paymentTerms = PaymentTerms(
      description = "Payment due within 30 days",
      dueDays = 30,
      methods = List(
        PaymentMethod.BankTransfer(
          iban = Some("FR14 2004 1010 0505 0001 3M02 606"),
          bic = Some("PSST FR PP XXX")
        )
      ),
      lateFeePercentage = Some(BigDecimal("1.5")),
      discountPercentage = None,
      discountDays = None,
      notes = None
    )

    val lineItems = List(
      InvoiceLineItem(
        description = "Professional Services",
        quantity = BigDecimal("1"),
        unitPrice = amount,
        taxRate = BigDecimal("20.0"),
        unit = Some("service")
      )
    )

    InvoiceParams(
      invoiceNumber = invoiceNumber,
      recipient = customer,
      lineItems = lineItems,
      paymentTerms = paymentTerms,
      invoiceDate = LocalDate.now(),
      dueDate = Some(LocalDate.now().plusDays(30)),
      reference = Some(s"REF-$clientName"),
      currency = "EUR",
      notes = Some(s"Invoice for $clientName - Professional Services"),
      status = InvoiceStatus.Draft
    )
