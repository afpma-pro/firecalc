<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Open Source Privacy Setup Guide

This document explains how the project is structured to allow open-source distribution while protecting sensitive company information.

## 🎯 Overview

The invoice system is designed with a **layered configuration approach** that separates:
- **Generic framework code** (shipped with open-source)
- **Company-specific sensitive data** (deployment-only, git-ignored)

This enables companies to use the system without exposing financial details, personal information, or business data in the public repository.

## 🔒 What's Protected

### Automatically Git-Ignored Files:
- `modules/*/configs/*.yaml` - Invoice configurations with real company data
- `modules/*/samples/*` - Generated invoices with sensitive information  
- `deployment/` - Company-specific deployment configurations
- `.env*` files - Environment variables with sensitive data
- `**/company-*.conf` - Company-specific override files

### Safe for Open Source:
- Translation files (`en.conf`, `fr.conf`) - Only contain UI labels
- Code structure and business logic
- Template files with placeholder variables
- Build system and dependencies
- Documentation and examples

## 📁 Directory Structure

```
project-root/
├── modules/invoices/
│   ├── config-templates/          # 🟢 Shipped (templates only)
│   │   ├── basic-invoice.yaml.template
│   │   └── README-DEPLOYMENT.md
│   ├── configs/                   # 🔴 Git-ignored (real configs)
│   │   ├── .gitkeep
│   │   └── company-invoice.yaml   # Created by user
│   └── samples/                   # 🔴 Git-ignored (generated files)
│       ├── invoice-en.pdf
│       └── invoice-fr.pdf
├── modules/invoices-i18n/
│   └── src/main/resources/i18n/   # 🟢 Shipped (generic labels)
│       ├── en.conf
│       └── fr.conf
└── deployment/                    # 🔴 Git-ignored (company setup)
    ├── .env
    └── production-configs/
```

## 🚀 Quick Start for Companies

### 1. Clone and Set Up

```bash
git clone <repository>
cd <project>

# Copy template to working config
cp modules/invoices/config-templates/basic-invoice.yaml.template \
   modules/invoices/configs/company-invoice.yaml
```

### 2. Configure Environment Variables

Create a `.env` file in the project root:

```bash
# Company Information
COMPANY_LEGAL_NAME="Your Company Legal Name Inc"
COMPANY_DISPLAY_NAME="Your Company"
COMPANY_EMAIL="billing@yourcompany.com"
COMPANY_PHONE="+1 555 123 4567"
COMPANY_WEBSITE="https://www.yourcompany.com"

# Financial Information (SENSITIVE)
COMPANY_VAT_NUMBER="YOUR_VAT_NUMBER_HERE"
COMPANY_IBAN="YOUR_IBAN_NUMBER_HERE"
COMPANY_BIC="YOUR_BIC_CODE_HERE"

# Payment Terms
PAYMENT_DUE_DAYS=30
LATE_FEE_PERCENTAGE=1.5
EARLY_DISCOUNT_PERCENTAGE=2.0
```

### 3. Test Configuration

```bash
# Generate sample invoices to verify setup
sbt "invoices/run"

# Check generated files (git-ignored)
ls modules/invoices/samples/
```

## 🛠️ Technical Implementation

### Environment Variable Substitution

The system supports two patterns:
- `${VAR_NAME}` - Required variable (fails if not set)
- `${VAR_NAME:-default}` - Optional with default value

Example in template:
```yaml
sender:
  name: "${COMPANY_LEGAL_NAME:-Your Company Name}"
  email: "${COMPANY_EMAIL:-info@yourcompany.com}"
  vatNumber: "${COMPANY_VAT_NUMBER}"  # Required, no default
```

### Configuration Loading Process

1. **Template Loading**: Read YAML template file
2. **Environment Substitution**: Replace `${VAR}` patterns with actual values
3. **Validation**: Ensure all required variables are set
4. **Object Creation**: Parse into Scala case classes
5. **Invoice Generation**: Generate PDF/Typst with real data

### Security Features

- **No hardcoded secrets**: All sensitive data comes from environment variables
- **Fail-fast validation**: Missing required variables cause immediate errors
- **Git protection**: Comprehensive `.gitignore` prevents accidental commits
- **Template validation**: Tools to verify all variables are configured

## 🏢 Production Deployment

### Docker/Container Setup

```dockerfile
# Set environment variables securely
ENV COMPANY_LEGAL_NAME="Acme Corp"
ENV COMPANY_IBAN="DE89370400440532013000"
# ... other variables

# Copy only templates (not actual configs)
COPY modules/invoices/config-templates/ /app/templates/
```

### CI/CD Pipeline

```yaml
# Example GitHub Actions
- name: Setup Company Config
  env:
    COMPANY_IBAN: ${{ secrets.COMPANY_IBAN }}
    COMPANY_VAT: ${{ secrets.COMPANY_VAT }}
  run: |
    cp config-templates/basic-invoice.yaml.template configs/production.yaml
    # Environment variables automatically substituted at runtime
```

### Kubernetes Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: company-config
data:
  COMPANY_IBAN: <base64-encoded-iban>
  COMPANY_VAT: <base64-encoded-vat>
```

## 🔧 Development Workflow

### For Open Source Contributors

1. Clone repository
2. Use default template values for development
3. Focus on code improvements, not configuration
4. Never commit real company data

### For Company Users

1. Fork or clone repository
2. Set up environment variables for your company
3. Generate invoices with real data
4. Keep sensitive configs private

### For Maintainers

1. Ensure all examples use placeholder data
2. Review PRs for accidental sensitive data
3. Keep templates generic and configurable
4. Document new configuration options

## 📋 Validation and Troubleshooting

### Configuration Validation

```scala
// Check what environment variables are needed
val variables = EnvironmentConfigLoader.getReferencedVariables(templateContent)
variables.foreach { env =>
  println(s"${env.name}: ${if (env.isSet) "✅ Set" else "❌ Missing"}")
}
```

### Common Issues

**Issue**: `Missing required environment variables: COMPANY_IBAN`
**Solution**: Set the variable in your `.env` file or system environment

**Issue**: Generated invoices show placeholder values
**Solution**: Verify environment variables are properly set and substituted

**Issue**: Git trying to commit sensitive files
**Solution**: Check `.gitignore` is properly configured

## 🔄 Migration from Hardcoded Values

If migrating from hardcoded company information:

1. **Identify sensitive data** in existing configs
2. **Create environment variables** for each sensitive value
3. **Update templates** with `${VAR}` patterns
4. **Test substitution** works correctly
5. **Remove hardcoded values** from git history if needed

## 📞 Support

- **Configuration questions**: See `modules/invoices/config-templates/README-DEPLOYMENT.md`
- **Environment variables**: Check template files for required variables
- **Security concerns**: Review `.gitignore` and deployment practices
- **Bugs/Features**: Create issues in the project repository

## 🎉 Benefits

✅ **Open Source Friendly**: No sensitive data in public repository  
✅ **Deployment Flexible**: Works with any deployment system  
✅ **Security First**: Environment-based configuration  
✅ **Easy Setup**: Copy template, set variables, run  
✅ **Multi-Environment**: Support dev/staging/production configs  
✅ **Validation**: Automatic checks for missing configuration  

This architecture enables the project to be safely open-sourced while maintaining full functionality for companies with their private data.
