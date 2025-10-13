<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Invoice Configuration Deployment Guide

This directory contains templates for configuring the invoice system for your company. The templates use environment variable substitution to keep sensitive company information out of the open-source repository.

## 🔧 Quick Setup

### 1. Copy Template to Working Directory

```bash
# Copy the template to your configs directory
cp config-templates/basic-invoice.yaml.template configs/company-invoice.yaml
```

### 2. Set Environment Variables

Create a `.env` file in your project root or set system environment variables:

```bash
# Company Information
COMPANY_LEGAL_NAME="Your Company Legal Name Inc"
COMPANY_DISPLAY_NAME="Your Company"
COMPANY_EMAIL="billing@yourcompany.com"
COMPANY_PHONE="+1 555 123 4567"
COMPANY_WEBSITE="https://www.yourcompany.com"

# Company Address
COMPANY_STREET="123 Your Business Street"
COMPANY_STREET_LINE2="Suite 100"
COMPANY_CITY="Your City"
COMPANY_POSTAL_CODE="12345"
COMPANY_REGION="Your Region"
COMPANY_COUNTRY="Your Country"

# Legal/Financial Information
COMPANY_VAT_NUMBER="YOUR_VAT_NUMBER_HERE"
COMPANY_REGISTRATION="YOUR_REGISTRATION_NUMBER_HERE"
COMPANY_IBAN="YOUR_IBAN_NUMBER_HERE"
COMPANY_BIC="YOUR_BIC_CODE_HERE"
COMPANY_CURRENCY="EUR"

# Payment Terms
PAYMENT_DUE_DAYS=30
LATE_FEE_PERCENTAGE=1.5
EARLY_DISCOUNT_PERCENTAGE=2.0
EARLY_DISCOUNT_DAYS=10

# Template Styling
TEMPLATE_LOGO_POSITION="top-left"
TEMPLATE_PRIMARY_COLOR="#2563eb"
TEMPLATE_FONT_FAMILY="Liberation Sans"
```

### 3. Alternative: Direct File Editing

Instead of environment variables, you can directly edit the copied YAML file by replacing all `${VARIABLE_NAME:-default}` placeholders with your actual values.

## 📁 File Structure

After setup, your structure should look like:

```
modules/invoices/
├── config-templates/           # Templates (shipped with open-source)
│   ├── basic-invoice.yaml.template
│   └── README-DEPLOYMENT.md
├── configs/                    # Your actual configs (git-ignored)
│   ├── company-invoice.yaml    # Your configured file
│   └── .gitkeep
└── samples/                    # Generated samples (git-ignored)
    ├── *.pdf
    └── *.typ
```

## 🔒 Security Notes

- **Never commit actual company configs**: The `.gitignore` is configured to exclude:
  - `modules/*/configs/*.yaml`
  - `modules/*/samples/*`
  - `.env*` files

- **Environment Variables**: Use your deployment system's secure environment variable management

- **File Permissions**: Ensure config files with sensitive data have appropriate permissions:
  ```bash
  chmod 600 configs/company-invoice.yaml
  chmod 600 .env
  ```

## 🏢 Multi-Environment Setup

For different environments (development, staging, production):

```bash
# Development
cp config-templates/basic-invoice.yaml.template configs/dev-invoice.yaml

# Staging  
cp config-templates/basic-invoice.yaml.template configs/staging-invoice.yaml

# Production
cp config-templates/basic-invoice.yaml.template configs/production-invoice.yaml
```

Use different environment variable sets or separate `.env` files:
- `.env.development`
- `.env.staging` 
- `.env.production`

## 🧪 Testing Configuration

To test your configuration:

```bash
# Generate sample invoices
sbt "invoices/run"

# Check generated files in samples/ directory
ls -la modules/invoices/samples/
```

## 🚀 Production Deployment

1. **Set environment variables** in your deployment system
2. **Copy template** to deployment location
3. **Verify configuration** by generating test invoices
4. **Backup configuration** securely
5. **Monitor for sensitive data leaks** in logs

## ❓ Troubleshooting

**Issue**: Variables not substituted
- **Solution**: Check environment variable names match exactly
- **Debug**: Print environment variables: `env | grep COMPANY`

**Issue**: Permission denied
- **Solution**: Check file permissions and ownership

**Issue**: Missing configuration
- **Solution**: Ensure all required variables are set (see template for `${VAR:-default}` patterns)

## 📞 Support

For configuration questions, refer to the main project documentation or create an issue in the project repository.
