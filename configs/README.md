# Configuration Setup

## Overview

This directory contains centralized configuration for all modules, organized by environment. This replaces the previous approach of embedding configuration files in JAR resources, providing better flexibility and security.

## Quick Start

1. **Copy template files to your environment directory:**
   ```bash
   cp -r configs/templates/* configs/dev/
   cd configs/dev && find . -name "*.template" -exec bash -c 'mv "$1" "${1%.template}"' _ {} \;
   ```

2. **Edit configuration files with your actual values:**
   - Remove `.template` extensions
   - Replace placeholder values with real configuration
   - Add sensitive information (API keys, passwords, etc.)

3. **Run application with environment:**
   ```bash
   FIRECALC_ENV=dev sbt "payments/run"
   # or
   java -Dfirecalc.env=dev -jar modules/payments/target/scala-*/firecalc-payments-assembly.jar
   ```

## Directory Structure

```
configs/
├── .gitignore                    # Exclude environment configs from git
├── README.md                     # This file
├── templates/                    # Template files (committed to git)
│   ├── payments/
│   │   ├── payments-config.conf.template
│   │   ├── gocardless-config.conf.template
│   │   └── email-config.conf.template
│   ├── reports/
│   │   └── logo.jpg.template
│   ├── invoices/
│   │   └── company-invoice.yaml.template
│   └── ui/
│       ├── .env.development.template
│       ├── .env.staging.template
│       └── .env.production.template
├── dev/                         # Development environment (not in git)
│   ├── payments/
│   │   ├── payments-config.conf
│   │   ├── gocardless-config.conf
│   │   └── email-config.conf
│   ├── reports/
│   │   └── logo.jpg
│   └── invoices/
│       ├── company-invoice.yaml
│       └── logo.png (optional)
├── staging/                     # Staging environment (not in git)
│   ├── payments/
│   │   ├── payments-config.conf
│   │   ├── gocardless-config.conf
│   │   └── email-config.conf
│   ├── reports/
│   │   └── logo.jpg
│   └── invoices/
│       ├── company-invoice.yaml
│       └── logo.png (optional)
└── prod/                        # Production environment (not in git)
    ├── payments/
    │   ├── payments-config.conf
    │   ├── gocardless-config.conf
    │   └── email-config.conf
    ├── reports/
    │   └── logo.png
    └── invoices/
        ├── company-invoice.yaml
        └── logo.png
```

## Environment Configuration

### Environment Detection

The system determines which environment to use through this priority order:

1. `FIRECALC_ENV` environment variable
2. `firecalc.env` system property (`-Dfirecalc.env=dev`)
3. Default to `dev`

### Required Files by Module

#### Payments Module
- `payments/payments-config.conf` - Main payments configuration
- `payments/gocardless-config.conf` - GoCardless payment provider settings
- `payments/email-config.conf` - Email/SMTP configuration

#### UI Module
- `modules/ui/.env.development` - Development environment variables
- `modules/ui/.env.staging` - Staging environment variables
- `modules/ui/.env.production` - Production environment variables

#### Reports Module
- `reports/logo.jpg` or `reports/logo.png` - Logo for PDF reports (optional)

#### Invoices Module
- `invoices/company-invoice.yaml` - Company information for invoices
- `invoices/logo.png` - Company logo for invoices (optional)

## Development Workflow

### Initial Setup
```bash
# 1. Copy templates to dev environment
cp -r configs/templates/* configs/dev/

# 2. Remove .template extensions
cd configs/dev
find . -name "*.template" -exec bash -c 'mv "$1" "${1%.template}"' _ {} \;

# 3. Edit configuration files with your development values
# Edit configs/dev/payments/payments-config.conf
# Edit configs/dev/payments/gocardless-config.conf
# Edit configs/dev/invoices/company-invoice.yaml
# Add your logos to configs/dev/reports/ and configs/dev/invoices/
```

### Running in Development
```bash
# Using sbt (environment defaults to dev)
sbt "payments/run"

# Explicitly set environment
FIRECALC_ENV=dev sbt "payments/run"
```

## Staging Environment

### Setup Staging Configuration
```bash
# 1. Create staging directory
mkdir -p configs/staging/{payments,reports,invoices}

# 2. Copy and customize templates
cp configs/templates/payments/*.template configs/staging/payments/
cp configs/templates/reports/*.template configs/staging/reports/
cp configs/templates/invoices/*.template configs/staging/invoices/

# 3. Remove .template extensions and edit with staging values
cd configs/staging
find . -name "*.template" -exec bash -c 'mv "$1" "${1%.template}"' _ {} \;

# 4. Add staging logos and test credentials
# - Use GoCardless SANDBOX environment
# - Use test email service (e.g., Mailtrap.io)
# - Configure staging domain (e.g., staging.firecalc.afpma.pro)
```

### Running in Staging
```bash
# Backend
FIRECALC_ENV=staging sbt "payments/run"

# UI (with Vite)
cd modules/ui && npm run dev -- --mode staging
```

### Staging Best Practices
- **GoCardless**: Always use SANDBOX environment, never live
- **Email**: Use test email service (Mailtrap.io) to avoid sending real emails
- **Database**: Use separate staging database (`firecalc-payments-staging.db`)
- **Invoice Prefix**: Use `FCALC-STG-` to differentiate from dev/prod
- **Domain**: Use staging subdomain (e.g., `staging.firecalc.afpma.pro`)

## Production Deployment

### Setup Production Configuration
```bash
# 1. Create production directory
mkdir -p configs/prod/{payments,reports,invoices}

# 2. Copy and customize templates
cp configs/templates/payments/*.template configs/prod/payments/
cp configs/templates/reports/*.template configs/prod/reports/
cp configs/templates/invoices/*.template configs/prod/invoices/

# 3. Remove .template extensions and edit with production values
cd configs/prod
find . -name "*.template" -exec bash -c 'mv "$1" "${1%.template}"' _ {} \;

# 4. Add production logos and sensitive configuration
```

### Running in Production
```bash
# Build assembly JAR
sbt "payments/assembly"

# Run with production environment
java -Dfirecalc.env=prod -jar modules/payments/target/scala-*/firecalc-payments-assembly.jar

# Or using environment variable
FIRECALC_ENV=prod java -jar modules/payments/target/scala-*/firecalc-payments-assembly.jar
```

## Configuration File Details

### Payments Configuration
- **File**: `payments/payments-config.conf`
- **Format**: HOCON (Human-Optimized Config Object Notation)
- **Contains**: Database settings, invoice configuration, retry settings

### GoCardless Configuration
- **File**: `payments/gocardless-config.conf`
- **Format**: HOCON
- **Contains**: Payment provider API keys, webhook secrets, environment settings

### Email Configuration
- **File**: `payments/email-config.conf`
- **Format**: HOCON
- **Contains**: SMTP server settings, sender information

### Invoice Configuration
- **File**: `invoices/company-invoice.yaml`
- **Format**: YAML
- **Contains**: Company information, branding, invoice templates

### Logo Files
- **Reports**: `reports/logo.{jpg,png}` - Used in PDF reports
- **Invoices**: `invoices/logo.png` - Used in invoice generation
- **Optional**: System continues without logos if files are missing

## Security Considerations

1. **Environment configs are excluded from git** via `.gitignore`
2. **Only templates are committed** to version control
3. **Sensitive data** (API keys, passwords) should only exist in environment-specific configs
4. **Logo files** may contain sensitive branding information
5. **Production configs** should be deployed securely and separately from code

## Troubleshooting

### Configuration Not Found
```
Error: ConfigurationNotFoundException: configs/dev/payments/payments-config.conf
```
**Solution**: Ensure you've copied templates to the appropriate environment directory and removed `.template` extensions.

### Wrong Environment
Check current environment detection:
```scala
import afpma.firecalc.config.ConfigPathResolver
println(ConfigPathResolver.debugInfo())
```

### Logo Not Loading
- Check file exists: `configs/{env}/reports/logo.{jpg,png}`
- Verify file permissions are readable
- System will continue without logo if file is missing

## Migration from Old System

If migrating from the old `getClassLoader` approach:

1. **Copy existing configs** from `modules/*/src/main/resources/` to appropriate `configs/dev/` directories
2. **Update any hardcoded paths** in your code to use the new centralized approach
3. **Test thoroughly** in development environment before deploying to production
4. **Remove old resource files** once migration is complete and tested

## Template Maintenance

When adding new configuration:

1. **Add template** to `configs/templates/{module}/`
2. **Update this README** with new requirements
3. **Update environment setup scripts** if needed
4. **Document sensitive fields** that need customization

Templates should contain:
- Placeholder values (e.g., `your-api-key-here`)
- Comments explaining configuration options
- Safe default values where applicable
