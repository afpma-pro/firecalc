<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Invoice Module Architecture

## Overview

The invoice module provides a template-based PDF invoice generation system with internationalization support. It separates static company configuration from dynamic invoice parameters, enabling flexible invoice creation while maintaining type safety and validation.

## Core Components

### 1. Factory Pattern (`FireCalcInvoiceFactory`)
- **Entry Point**: Main interface for invoice generation
- **Template Support**: Customizable invoice layouts via `InvoiceTemplate[T]`
- **Multi-format**: Generates both Typst markup and PDF output
- **Error Handling**: Comprehensive validation with `Either` types

### 2. Data Models (`models/`)
- **CompanyConfig**: Static company information (logo, address, payment terms)
- **InvoiceParams**: Dynamic invoice data (number, recipient, line items)
- **InvoiceData**: Combined model for template rendering
- **Supporting Models**: Address, PaymentTerms, InvoiceLineItem, etc.

### 3. Template System (`templates/`)
- **InvoiceTemplate[T]**: Generic template interface with validation
- **DefaultInvoiceTemplate**: Standard invoice layout
- **Typst Generation**: Markup-based PDF generation via JavaTypst
- **Locale-aware**: Multi-language support through i18n

### 4. Configuration Management (`config/`)
- **Environment-based**: Load configurations from YAML files
- **Template Configs**: Deployment-ready configuration templates
- **Logo Management**: Automatic fallback logic for logo resolution
- **Validation**: Type-safe configuration parsing with error reporting

## Architecture Patterns

### Configuration Separation
```
CompanyConfig (Static)     InvoiceParams (Dynamic)
       ↓                          ↓
       └─────── InvoiceData ──────┘
                    ↓
              Template Rendering
                    ↓
              Typst → PDF
```

### Template-Based Generation
- **Pluggable Templates**: Custom layouts via `InvoiceTemplate[T]`
- **Type Safety**: Compile-time template validation
- **Locale Support**: Multi-language rendering with Babel i18n

### Error Handling Strategy
- **Validation Chain**: CompanyConfig → InvoiceParams → Template → PDF
- **Early Failure**: Fail fast with descriptive error messages
- **Resource Safety**: Proper file handling and cleanup

## Directory Structure

```
modules/invoices/
├── src/main/scala/afpma/firecalc/invoices/
│   ├── FireCalcInvoiceFactory.scala    # Main factory interface
│   ├── models/                         # Data models
│   ├── templates/                      # Invoice templates
│   ├── config/                         # Configuration loading
│   ├── typst/                          # Typst markup utilities
│   └── examples/                       # Usage examples
├── config-templates/                   # Deployment templates
├── configs/                           # Runtime configurations
└── samples/                           # Generated examples
```

## Key Features

### Dynamic Generation
- **Template-based API**: `FireCalcInvoiceFactory.fromTemplate()`
- **Parameter Injection**: Dynamic invoice numbers, recipients, line items
- **Validation**: Type-safe parameter validation
- **Output Flexibility**: Typst source + PDF bytes

### Internationalization
- **Babel Integration**: HOCON-based translations
- **Locale Support**: English/French with parameterized strings
- **Template Localization**: Locale-aware template rendering

### Logo Management
- **Flexible Paths**: Company-specific or default logo fallback
- **Environment Configs**: Logo placement in `configs/` directory
- **Resolution Logic**: `companyConfig.resolveLogoPath`

## Usage Patterns

### Standard Invoice Generation
```scala
val factory = FireCalcInvoiceFactory.fromConfigFile(configFile)
val result = factory.generateInvoice(params, Locales.en)
result.savePdfToFile("invoice.pdf")
```

### Custom Template Usage
```scala
val factory = FireCalcInvoiceFactory.fromTemplate(companyConfig)
val result = factory.generateInvoiceWithTemplate(params, customTemplate, locale)
```

## Dependencies

- **Babel**: Internationalization (io.taig.babel)
- **JavaTypst**: PDF generation (io.github.fatihcatalkaya.javatypst)
- **Circe**: JSON/YAML parsing (io.circe)
- **OS-Lib**: File operations (com.lihaoyi.os-lib)

## Related Modules

- **invoices-i18n**: Internationalization data and loading
- **engine**: Core calculation engine (if needed)
- **dto**: Data transfer objects and encoding
