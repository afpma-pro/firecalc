<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# FireCalc Payments Module

Backend payment processing system with GoCardless integration, passwordless authentication, and invoice generation.

## 📚 Documentation

### Architecture & Design
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Complete system architecture and implementation details
- **[AGPLV3_NETWORK_COMPLIANCE.md](AGPLV3_NETWORK_COMPLIANCE.md)** - AGPLv3 compliance for network services

### Product Management
- **[PRODUCT_CATALOG_TESTING.md](PRODUCT_CATALOG_TESTING.md)** - Product catalog testing guide
- **[PRODUCT_CATALOG_UI_INTEGRATION.md](PRODUCT_CATALOG_UI_INTEGRATION.md)** - Frontend integration guide

## 🏗️ Architecture Overview

**Layered Architecture:**
```
HTTP Routes → Services → Repositories → Database (SQLite)
```

**Core Components:**
- **Domain Layer**: Type-safe models and value objects
- **Repository Layer**: Database abstraction (Molecule ORM)
- **Service Layer**: Business logic (Purchase, Auth, Payment, Email)
- **HTTP Layer**: API endpoints and webhooks
- **External Integrations**: GoCardless, Email

## 🚀 Quick Start

### Running the Backend

```bash
# Development mode
make dev-backend-run

# Staging mode
make staging-backend-run

# Production mode
make prod-backend-run
```

### Running Tests

```bash
# All tests
sbt "payments/test"

# Specific test suite
sbt "payments/test:testOnly afpma.firecalc.payments.repository.InvoiceNumberServiceTest"
sbt "payments/test:testOnly afpma.firecalc.payments.repository.ProductCatalogIntegrationTest"
```

## 🗄️ Database

**Technology:** SQLite with Molecule ORM

**Schema Management:**
- Flyway for migrations
- See [Database Migration Guide](../../docs/dev/guides/DB_SQLITE3_MIGRATION_GUIDE.md)

**Environment-Specific Databases:**
- Development: `./firecalc-payments-dev.db`
- Staging: `./firecalc-payments-staging.db`
- Production: `./firecalc-payments-prod.db`

**Key Features:**
- WAL mode for better concurrency
- Foreign keys enabled
- Atomic invoice number generation
- Automatic migrations on startup

## 💳 Payment Flow

### 1. Create Purchase Intent
```
User → POST /api/v1/purchase/intent
     → Generates 6-digit auth code
     → Sends email with code
     → Returns purchase token
```

### 2. Verify & Process
```
User → POST /api/v1/purchase/verify
     → Validates auth code
     → Creates/authenticates user
     → Creates order
     → Generates GoCardless payment link
     → Returns payment URL
```

### 3. Payment Confirmation
```
GoCardless → Webhook /api/v1/webhooks/gocardless
          → Updates order status
          → Generates invoice
          → Sends invoice email
```

## 🔧 Configuration

**Configuration Files:**
```
configs/
├── dev/payments/
│   ├── payments-config.conf
│   ├── gocardless-config.conf
│   └── email-config.conf
├── staging/payments/
│   └── ... (same structure)
└── prod/payments/
    └── ... (same structure)
```

**Environment Detection:**
Set `FIRECALC_ENV` environment variable:
- `dev` (default)
- `staging`
- `prod`

## 📦 Product Catalog

The system supports multiple product catalogs:
- **Development**: Test products with minimal prices
- **Staging**: Production-like products at minimal prices
- **Production**: Real products with actual prices

See [PRODUCT_CATALOG_TESTING.md](PRODUCT_CATALOG_TESTING.md) for details.

## 🔐 Security

### AGPLv3 Compliance
This module provides network services and must comply with AGPLv3 Section 13.

See [AGPLV3_NETWORK_COMPLIANCE.md](AGPLV3_NETWORK_COMPLIANCE.md) for:
- Implementation requirements
- Source code availability
- User rights
- Modified version obligations

### Authentication
- Passwordless authentication via 6-digit codes
- JWT tokens for session management
- Email-based verification

## 🧪 Testing

**Test Coverage:**
- Repository layer tests
- Service layer tests
- Integration tests
- Invoice generation tests
- Product catalog tests

**Test Database:**
- Uses in-memory SQLite (`:memory:`)
- Consistent configuration with production
- Fast test execution
- Isolated test runs

## 📖 API Documentation

### Purchase Endpoints
- `POST /api/v1/purchase/intent` - Create purchase intent
- `POST /api/v1/purchase/verify` - Verify code and process payment

### Webhook Endpoints
- `POST /api/v1/webhooks/gocardless` - Payment status updates

### Utility Endpoints
- `GET /health` - Health check
- `GET /source` - Source code information (AGPLv3 compliance)

## 🔗 Module Dependencies

```
payments depends on:
├── engine           # Calculation engine
├── reports          # PDF generation
├── invoices         # Invoice templates
├── payments-i18n    # Backend translations
├── invoices-i18n    # Invoice translations
└── payments-shared  # API contracts (shared with UI)
```

## 📋 Key Features

### Invoice Management
- Automatic invoice number generation
- Thread-safe counter with retry logic
- Environment-specific prefixes
- Retroactive assignment for existing orders

### Customer Management
- Payment provider agnostic
- GoCardless customer reuse
- Support for Individual and Business customers
- Multiple language support (EN, FR)

### Email Integration
- Authentication codes
- Payment links
- Invoice delivery
- Status notifications

## 🔍 Troubleshooting

### Common Issues

**Database locked:**
- SQLite uses single-writer model
- Check for long-running transactions
- Verify busy timeout settings

**Migration fails:**
- Backup database before migrations
- Check SQLite version compatibility
- Review [migration guide](../../docs/dev/guides/DB_SQLITE3_MIGRATION_GUIDE.md)

**GoCardless webhook issues:**
- Verify webhook signature
- Check endpoint accessibility
- Review webhook logs

## 📝 Development

### Adding New Features

1. Update domain models in `models_v2.scala`
2. Update repository layer
3. Generate database schema: `sbt payments/moleculeGen`
4. Create migration script
5. Update service layer
6. Add/update HTTP routes
7. Write tests
8. Update documentation

### Code Style
Follow Scala best development practices:
- SOLID, DRY, KISS, YAGNI principles
- Pure functions where possible
- Explicit effect handling
- Immutable collections

## 📖 Related Documentation

### Project Documentation
- [Architecture Overview](../../ARCHITECTURE.md)
- [Developer Guide](../../docs/dev/README.md)
- [Database Migrations](../../docs/dev/guides/DB_SQLITE3_MIGRATION_GUIDE.md)
- [GoCardless Integration](../../docs/dev/guides/GOCARDLESS.md)

### Module Documentation
- [Invoices Module](../invoices/)
- [Reports Module](../reports/)
- [Engine Module](../engine/)

## 📄 License

See [LICENSE](../../LICENSE) - AGPL-3.0-or-later

**Important:** This module provides network services. Under AGPLv3, users interacting over a network must have access to source code. See [AGPLV3_NETWORK_COMPLIANCE.md](AGPLV3_NETWORK_COMPLIANCE.md).