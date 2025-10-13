<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# FireCalc Invoices Module

A comprehensive invoice generation system using Typst for PDF output. This module provides a flexible, type-safe way to generate professional invoices with customizable templates.

## Features

- **Type-Safe Models**: Comprehensive domain models for invoices, companies, payments, etc.
- **Template System**: Extensible template system using Scala typeclasses
- **Multiple Input Formats**: Support for JSON, YAML, and direct Scala objects
- **Typst Integration**: Uses Typst for high-quality PDF generation
- **Internationalization**: Built-in support for multiple locales
- **Validation**: Comprehensive validation of invoice data
- **Fluent API**: Easy-to-use fluent interface for common operations

## Quick Start

### Basic Usage

```scala
import afpma.firecalc.invoices.*
import afpma.firecalc.invoices.models.*
import io.taig.babel.Locale
import java.time.LocalDate

given Locale = Locale.en

// Create invoice data
val invoice = InvoiceData(
  invoiceNumber = "INV-2025-001",
  invoiceDate = LocalDate.now(),
  dueDate = Some(LocalDate.now().plusDays(30)),
  sender = /* your company data */,
  billTo = /* customer data */,
  lineItems = List(/* invoice items */),
  currency = "EUR",
  // ... other fields
)

// Generate PDF
InvoiceGenerator.generatePdf(invoice, "invoice.pdf") match
  case Right(file) => println(s"Invoice generated: ${file.getAbsolutePath}")
  case Left(error) => println(s"Error: $error")
```

### Loading from Files

```scala
// From JSON file
InvoiceGenerator.generatePdfFromJson(
  new File("invoice.json"), 
  "output.pdf"
)

// From YAML file  
InvoiceGenerator.generatePdfFromYaml(
  new File("invoice.yaml"),
  "output.pdf"
)
```

### Using the Factory API

```scala
val result = for {
  factory <- FireCalcInvoiceFactory.init().loadFromJsonFile(new File("invoice.json"))
  file <- factory.generatePdfFile(Some("custom_invoice.pdf"))
} yield file
```

## Domain Models

### Core Models

- **InvoiceData**: Main invoice container with all invoice information
- **Company**: Represents sender and bill-to entities
- **Address**: Physical address information
- **InvoiceLineItem**: Individual line items with pricing and tax
- **PaymentTerms**: Payment conditions and methods
- **PaymentMethod**: Various payment options (bank transfer, PayPal, etc.)

### Example Invoice Structure

```scala
val invoice = InvoiceData(
  invoiceNumber = "INV-2025-001",
  invoiceDate = LocalDate.now(),
  dueDate = Some(LocalDate.now().plusDays(30)),
  sender = Company(
    name = "ACME Corp",
    address = Address("123 Street", None, "Paris", "IDF", "75001", "France"),
    email = "contact@acme.com",
    vatNumber = Some("FR12345678901")
  ),
  billTo = Company(/* customer info */),
  lineItems = List(
    InvoiceLineItem(
      description = "Professional Services",
      quantity = BigDecimal("10"),
      unit = Some("hours"),
      unitPrice = BigDecimal("150.00"),
      taxRate = BigDecimal("20.0")
    )
  ),
  currency = "EUR",
  paymentTerms = PaymentTerms(
    description = "Payment due within 30 days",
    dueDays = 30,
    methods = List(PaymentMethod.BankTransfer(Some("FR76..."), Some("BNPAFR...")))
  ),
  status = InvoiceStatus.Draft
)
```

## Template System

### Using Default Template

The default template provides a professional invoice layout:

```scala
given Locale = Locale.en
val template = DefaultInvoiceTemplate() // Uses default configuration
```

### Custom Template Configuration

```scala
val customConfig = TemplateConfig(
  logoPosition = LogoPosition.TopRight,
  logoWidth = "3cm",
  primaryColor = "#2563eb",
  showTaxBreakdown = true,
  showPaymentQRCode = false
)

val template = DefaultInvoiceTemplate.withConfig(customConfig)

FireCalcInvoiceFactory.init()
  .loadFromInvoice(invoice)
  .map(_.withTemplate(template))
  .flatMap(_.generatePdfFile(Some("custom_invoice.pdf")))
```

### Creating Custom Templates

Implement the `InvoiceTemplate` trait for full customization:

```scala
class MyCustomTemplate extends InvoiceTemplate[InvoiceData]:
  def render(invoice: InvoiceData)(using Locale): String =
    // Your custom Typst markup generation
    s"""
    |#set document(title: "Invoice ${invoice.invoiceNumber}")
    |
    |= Invoice
    |
    |// Your custom layout here
    |""".stripMargin
  
  def validate(invoice: InvoiceData): Option[String] =
    // Your custom validation logic
    None
```

## JSON/YAML Schema

### JSON Example

```json
{
  "invoiceNumber": "INV-2025-001",
  "invoiceDate": "2025-01-09",
  "dueDate": "2025-02-08",
  "currency": "EUR",
  "status": "Draft",
  "sender": {
    "name": "ACME Corp",
    "address": {
      "street": "123 Business Street",
      "city": "Paris",
      "postalCode": "75001",
      "country": "France"
    },
    "email": "contact@acme.com",
    "vatNumber": "FR12345678901"
  },
  "billTo": {
    "name": "Client Corp",
    "address": { /* address info */ },
    "email": "billing@client.com"
  },
  "lineItems": [
    {
      "description": "Professional Services",
      "quantity": 10,
      "unit": "hours",
      "unitPrice": 150.00,
      "taxRate": 20.0,
      "discountPercentage": 0
    }
  ],
  "paymentTerms": {
    "description": "Payment due within 30 days",
    "dueDays": 30,
    "methods": [
      {
        "type": "BankTransfer",
        "iban": "FR14 2004 1010 0505 0001 3M02 606",
        "bic": "PSSTFRPPXXX"
      }
    ]
  }
}
```

### YAML Example

```yaml
invoiceNumber: INV-2025-001
invoiceDate: 2025-01-09
currency: EUR
status: Draft
sender:
  name: ACME Corp
  address:
    street: 123 Business Street
    city: Paris
    postalCode: "75001"
    country: France
  email: contact@acme.com
lineItems:
  - description: Professional Services
    quantity: 10
    unitPrice: 150.00
    taxRate: 20.0
```

## Advanced Features

### Multiple Currencies

```scala
val usdInvoice = invoice.copy(currency = "USD")
val gbpInvoice = invoice.copy(currency = "GBP")
```

### Tax Calculations

The system automatically calculates:
- Line item taxes
- Tax breakdowns by rate
- Subtotals and totals
- Discount applications

### Validation

```scala
val invoice = createInvoice()
val errors = invoice.validate
if (errors.nonEmpty) {
  println(s"Validation errors: ${errors.mkString(", ")}")
}
```

### Debugging with Typst Output

```scala
// Generate Typst markup for inspection
InvoiceGenerator.generateTypst(invoice) match
  case Right(typstMarkup) => 
    println(typstMarkup)
    // Save to .typ file for debugging
  case Left(error) => 
    println(s"Error: $error")
```

## Payment Methods

Supported payment methods:

- **Bank Transfer**: With IBAN and BIC
- **PayPal**: With email
- **Credit Card**: Basic credit card info
- **Cash**: Cash payments
- **Check**: Check payments
- **Other**: Custom payment methods

```scala
val paymentTerms = PaymentTerms(
  description = "Payment due within 30 days",
  dueDays = 30,
  methods = List(
    PaymentMethod.BankTransfer(
      iban = Some("FR14 2004 1010 0505 0001 3M02 606"),
      bic = Some("PSSTFRPPXXX")
    ),
    PaymentMethod.PayPal(email = Some("payments@acme.com")),
    PaymentMethod.CreditCard
  ),
  lateFeePercentage = Some(BigDecimal("1.5")),
  discountPercentage = Some(BigDecimal("2.0")),
  discountDays = Some(10)
)
```

## Error Handling

All operations return `Either[String, T]` for comprehensive error handling:

```scala
FireCalcInvoiceFactory.init()
  .loadFromJsonFile(jsonFile)
  .flatMap(_.generatePdfFile()) match
    case Right(file) => 
      println(s"Success: ${file.getAbsolutePath}")
    case Left(error) => 
      println(s"Error: $error")
      // Handle error appropriately
```

## Examples

See `ExampleInvoiceGeneration.scala` for comprehensive examples including:

- Simple invoice generation
- Custom template usage
- International invoices
- Multiple tax rates
- Various payment methods

Run examples:

```bash
sbt "invoices/runMain afpma.firecalc.invoices.examples.ExampleInvoiceGeneration"
```

## Building and Dependencies

This module depends on:

- **Typst**: For PDF generation (via java-typst)
- **Circe**: For JSON/YAML parsing
- **Babel**: For internationalization
- **OS-Lib**: For file operations

Build the module:

```bash
sbt invoices/compile
sbt invoices/test
```

Generate fat JAR:

```bash
sbt invoices/assembly
```

## Architecture

The module follows the same architectural patterns as the reports module:

```
invoices/
├── models/           # Domain models (InvoiceData, Company, etc.)
├── templates/        # Template system (InvoiceTemplate trait, implementations)
├── typst/           # Typst rendering (TypstShow typeclass)
├── examples/        # Usage examples
└── FireCalcInvoiceFactory.scala  # Main factory API
```

The design emphasizes:

- **Type Safety**: Comprehensive Scala types for all domain concepts
- **Extensibility**: Easy to add new templates and payment methods
- **Testability**: Pure functions and dependency injection
- **Performance**: Efficient rendering and minimal allocations

## Contributing

When adding new features:

1. Add models to `models/` package
2. Extend templates as needed
3. Add TypstShow instances for new types
4. Update examples and documentation
5. Add tests for new functionality

## License

This module is part of the FireCalc project and follows the same licensing terms.
