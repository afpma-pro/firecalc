<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

## High-Level Architecture Overview

The payments module implements a __passwordless authentication + payment system__ using the following architecture:

### Core Architecture Pattern

- __Layered Architecture__: Repository → Service → HTTP Routes
- __Functional Programming__: Uses Cats Effect for pure functional programming with IO monads
- __Type Safety__: Strong typing with domain models and value classes
- __Database__: SQLite with Molecule ORM for type-safe database operations

### Key Components

1. __Domain Layer__ (`models_v2.scala`)

   - Core entities: Product, ProductOrder, PurchaseIntent, Customer, ProductMetadata
   - Value types: OrderId, ProductId, PurchaseToken, CustomerId
   - Enums: OrderStatus (Pending, Processing, Confirmed, PaidOut, Failed, Cancelled), CustomerType (Individual, Business), BackendCompatibleLanguage, PaymentProvider (GoCardless, Unknown)
   - Request/Response models with Circe JSON codecs

2. __Repository Layer__

   - Database abstraction using Molecule ORM
   - Repositories: UserRepository, ProductRepository, OrderRepository, PurchaseIntentRepository, CustomerRepository

3. __Service Layer__

   - __PurchaseService__: Orchestrates the purchase flow
   - __AuthenticationService__: Handles 6-digit codes and JWT tokens
   - __PaymentService__: GoCardless integration
   - __OrderService__: Order management
   - __EmailService__: Email notifications (currently mocked)

4. __HTTP Layer__

   - __PurchaseRoutes__: `/purchase/create-intent`, `/purchase/verify-and-process`
   - __WebhookRoutes__: `/webhooks/gocardless` for payment confirmations
   - __StaticPageRoutes__: Payment completion pages

5. __External Integrations__

   - __GoCardless__: Payment processing with billing requests and hosted payment pages
   - __Email Service__: Authentication codes, payment links, invoices (mocked implementation)

### Purchase Flow Implementation

The system implements the simplified flow from your requirements:

1. __Create Purchase Intent__ → Generates 6-digit code → Stores optional ProductMetadata → Sends email
2. __Verify & Process__ → Validates code → Creates/authenticates user → Creates order → Associates ProductMetadata → Generates GoCardless payment link
3. __Payment Processing__ → User pays via GoCardless → Webhook confirms payment → Invoice sent

### ProductMetadata Support

The system now supports optional product metadata that can be attached to purchases:

- __ProductMetadata Types__: Currently supports `FileDescriptionWithContent` for file uploads, extensible for future metadata types
- __JSON Storage__: ProductMetadata is JSON-encoded and stored in the `ProductMetadata` table with a `jsonString` field
- __Association__: Both `ProductOrder` and `PurchaseIntent` entities reference ProductMetadata via foreign key
- __Repository Layer__: `ProductMetadataRepository` handles CRUD operations for metadata
- __Service Integration__: `PurchaseServiceImpl` automatically stores ProductMetadata when present in `CreatePurchaseIntentRequest`
- __File Handling__: `FileDescriptionWithContent` includes filename, MIME type, and base64-encoded content, with utility method `toFile` for conversion

### Customer Management Flow

The system implements payment provider agnostic customer management:

1. __Check Existing Customer__ → Look up customer by email in local database
2. __Reuse GoCardless ID__ → If customer exists with GoCardless provider info, pass existing customer ID to billing request
3. __Auto-Create via GoCardless__ → If no existing GoCardless customer, GoCardless automatically creates one when `links[customer]` is empty
4. __Store Provider Info__ → Extract GoCardless customer ID from billing request response and store/update in local Customer table
5. __Future Purchases__ → Subsequent purchases by same customer reuse existing GoCardless customer to avoid duplicates

### Invoice Number Generation System

The system includes a comprehensive invoice number generation system with the following components:

- __InvoiceCounter__: Single-row table with atomic counter increments for thread-safe invoice number generation
- __InvoiceNumberService__: Service layer with retry logic and exponential backoff for reliable invoice generation
- __InvoiceCounterRepository__: Repository for atomic SQLite-based counter operations
- __Configuration-based__: Environment-specific invoice prefixes and formats (dev: `FCALC-DEV-001`, prod: `FCALC-0001`)

Key features:
- **Thread-safe**: Uses SQLite `UPDATE ... RETURNING` for atomic increments
- **Retry logic**: Exponential backoff for handling temporary failures
- **Environment isolation**: Separate counters and formats per environment
- **Retroactive generation**: Automatic invoice number assignment for existing confirmed orders

### Database Configuration & Setup

The system uses SQLite with Single Writer architecture optimized for production use:

#### **Configuration System**
- __PaymentsConfig__: Centralized configuration with environment-specific settings
- __DatabaseConfig__: Database file paths and connection settings
- __ConfigLoader__: HOCON-based configuration loading with environment variable support

#### **Environment-Specific Databases**
- **Development**: `./firecalc-payments-dev.db`
- **Production**: `./firecalc-payments-prod.db`
- **Testing**: `:memory:` (in-memory SQLite)

#### **SQLite Optimization**
- **WAL Mode**: Write-Ahead Logging for better concurrency
- **Foreign Keys**: Enabled for referential integrity
- **Busy Timeout**: 5-second timeout for lock handling
- **Synchronous Mode**: NORMAL for optimal performance with WAL

### Testing Architecture

Comprehensive test suite using **utest** framework with the following structure:

#### **Test Setup**
- __TestDatabaseSetup__: Shared in-memory SQLite setup for all tests
- __Consistent Configuration__: Same SQLite PRAGMAs as production for accurate testing
- __Resource Management__: Proper connection lifecycle management with Cats Effect Resource

#### **Test Coverage**
1. **InvoiceCounterRepositoryTest**: Repository layer testing
   - Counter initialization and increments
   - Error handling and validation
   - Concurrent access behavior
   
2. **InvoiceNumberServiceTest**: Service layer testing
   - Invoice number format validation
   - Sequential generation testing
   - Retry logic and error handling

#### **Test Results**
- **11/11 tests passing**
- **Fast execution**: In-memory database for quick feedback
- **Isolated**: Each test runs with fresh database state

### Database Schema (SQLite)

- __Product__: id, productId(UUID), name, description, price, active
- __ProductOrder__: id, orderId(UUID), customerId(UUID), productId(UUID), amount, status, paymentProvider, paymentId, language, productMetadata(FK), invoiceNumber, timestamps
- __Customer__: id, customerId(UUID), email, customerType, language, givenName, familyName, companyName, address fields, phoneNumber, paymentProviderId, paymentProvider, timestamps
- __PurchaseIntent__: id, token(UUID), productId(UUID), amount, authCode, customer(FK), processed, productMetadata(FK), expiresAt, createdAt
- __ProductMetadata__: id, jsonString (stores JSON-encoded ProductMetadata from domain), createdAt
- __InvoiceCounter__: id, currentNumber, startingNumber, createdAt, updatedAt (single-row table for atomic increments)
- __PaymentProvider__: GoCardless, Unknown (enum table)
- __OrderStatus__: Pending, Processing, Confirmed, PaidOut, Failed, Cancelled (enum table)
- __CustomerType__: Individual, Business (enum table)
