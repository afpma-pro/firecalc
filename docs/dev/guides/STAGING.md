<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Staging Environment Guide

## Overview

The staging environment is a production-like testing environment used for final validation before deploying to production. It mirrors the production setup while using test credentials and isolated data.

## Purpose of Staging

- **Pre-production Testing**: Validate features in a production-like environment
- **Integration Testing**: Test with external services (GoCardless sandbox, test email)
- **User Acceptance Testing (UAT)**: Allow stakeholders to review before production
- **Performance Testing**: Test with realistic data volumes
- **Training**: Demo environment for training and documentation

## Quick Start

### 1. Environment Setup

The staging environment is already configured with:
- ✅ Configuration directory: `configs/staging/`
- ✅ Database directory: `databases/staging/`
- ✅ UI environment file: `modules/ui/.env.staging`

### 2. Customize Configuration

Edit the following files with your staging credentials:

```bash
# Payments configuration
configs/staging/payments/payments-config.conf
configs/staging/payments/gocardless-config.conf
configs/staging/payments/email-config.conf

# Invoices configuration
configs/staging/invoices/company-invoice.yaml

# UI configuration
modules/ui/.env.staging
```

**IMPORTANT**: Replace all placeholder values with actual staging credentials.

### 3. Running in Staging

#### Backend (Payments Module)
```bash
FIRECALC_ENV=staging sbt "payments/run"
```

#### UI (Frontend)
```bash
cd modules/ui
npm run dev -- --mode staging
```

Or build for staging deployment:
```bash
cd modules/ui
npm run build -- --mode staging
```

## Configuration Details

### Database Configuration

**File**: `configs/staging/payments/payments-config.conf`

Key settings:
```hocon
staging {
  database {
    filename = "firecalc-payments-staging.db"
    path = "databases/staging/firecalc-payments-staging.db"
  }
  
  invoice {
    number-prefix = "FCALC-STG-[YYYY]-"
    number-digits = 4
    counter-starting-number = 1
  }
}
```

**Database Location**: `databases/staging/firecalc-payments-staging.db`
- Created automatically on first run
- Separate from dev/prod databases
- Should be backed up regularly

### GoCardless Configuration

**File**: `configs/staging/payments/gocardless-config.conf`

**CRITICAL**: Staging MUST use GoCardless **SANDBOX** environment:

```hocon
staging {
  access-token = "sandbox_YOUR_ACCESS_TOKEN_HERE"
  base-url = "https://api-sandbox.gocardless.com"
  environment = "sandbox"
  webhook-secret = "YOUR_WEBHOOK_SECRET_HERE"
  
  # Update with your staging domain
  redirect-uri = "https://staging.firecalc.afpma.pro/v1/payment_complete"
  exit-uri = "https://staging.firecalc.afpma.pro/v1/payment_cancelled"
}
```

**Setup GoCardless Sandbox**:
1. Create sandbox account: https://manage-sandbox.gocardless.com/
2. Generate sandbox API token
3. Configure webhook endpoint for staging server
4. Update redirect URIs with your staging domain

### Email Configuration

**File**: `configs/staging/payments/email-config.conf`

**Recommended**: Use a test email service like Mailtrap.io to avoid sending real emails:

```hocon
staging {
  smtp {
    host = "sandbox.smtp.mailtrap.io"
    port = 2525
    username = "your-mailtrap-username-staging"
    password = "your-mailtrap-password-staging"
    use-tls = true
  }
  
  from {
    address = "noreply-staging@firecalc.example.com"
    name = "FireCalc Staging"
  }
}
```

**Alternative Email Services**:
- **Mailtrap.io** (recommended for staging)
- AWS SES with sandbox mode
- Mailgun with test credentials

### UI Configuration

**File**: `modules/ui/.env.staging`

Configure to point to staging backend:

```env
VITE_BACKEND_PROTOCOL=https
VITE_BACKEND_HOST=staging.firecalc.afpma.pro
VITE_BACKEND_PORT=443
VITE_BACKEND_BASE_PATH=/v1
```

## Staging vs Dev vs Prod

| Aspect | Development | Staging | Production |
|--------|-------------|---------|------------|
| **Purpose** | Active development | Pre-production testing | Live users |
| **Database** | `firecalc-payments-dev.db` | `firecalc-payments-staging.db` | `firecalc-payments-prod.db` |
| **Invoice Prefix** | `FCALC-DEV-` | `FCALC-STG-` | `FCALC-` |
| **GoCardless** | Sandbox | **Sandbox** | Live |
| **Email** | Mock/Mailtrap | Mailtrap | Production SMTP |
| **Domain** | localhost:8181 | staging.domain.com | domain.com |
| **Data** | Test data | Realistic test data | Real data |
| **Access** | Developers only | Dev + QA + Stakeholders | Public |

## Testing in Staging

### 1. Backend Testing

Start the payments backend:
```bash
FIRECALC_ENV=staging sbt "payments/run"
```

Test endpoints:
```bash
# Health check
curl https://staging.firecalc.afpma.pro/v1/health

# Create purchase intent (example)
curl -X POST https://staging.firecalc.afpma.pro/v1/purchase/create-intent \
  -H "Content-Type: application/json" \
  -d '{"productId": "...", "amount": 89.00}'
```

### 2. UI Testing

Build and serve UI:
```bash
cd modules/ui
npm run build -- --mode staging
npm run preview
```

Or run in dev mode with staging backend:
```bash
cd modules/ui
npm run dev -- --mode staging
```

### 3. Payment Flow Testing

1. Create purchase intent
2. Receive email with authentication code (check Mailtrap)
3. Verify code and create GoCardless payment
4. Complete payment in GoCardless sandbox
5. Verify webhook received
6. Check invoice generated and emailed

### 4. Invoice Generation Testing

```bash
# Test invoice generation with staging configs
FIRECALC_ENV=staging sbt "invoices/run"
```

### 5. Database Verification

```bash
# Check staging database
sqlite3 databases/staging/firecalc-payments-staging.db

# List tables
.tables

# Check invoice counter
SELECT * FROM InvoiceCounter;

# List recent orders
SELECT * FROM ProductOrder ORDER BY createdAt DESC LIMIT 10;
```

## Deployment

### Local Staging

Run staging environment on your local machine:

```bash
# Terminal 1: Backend
FIRECALC_ENV=staging sbt "payments/run"

# Terminal 2: UI
cd modules/ui && npm run dev -- --mode staging
```

### Server Deployment

1. **Build Backend**:
```bash
sbt "payments/assembly"
```

2. **Deploy to Staging Server**:
```bash
# Copy JAR to staging server
scp modules/payments/target/scala-*/firecalc-payments-assembly.jar \
    staging-server:/opt/firecalc/

# Copy configs (securely!)
scp -r configs/staging/* staging-server:/opt/firecalc/configs/staging/

# Copy databases directory
ssh staging-server "mkdir -p /opt/firecalc/databases/staging"
```

3. **Run on Server**:
```bash
ssh staging-server
cd /opt/firecalc
FIRECALC_ENV=staging java -jar firecalc-payments-assembly.jar
```

4. **Build and Deploy UI**:
```bash
cd modules/ui
npm run build -- --mode staging

# Deploy dist/ to staging web server
rsync -avz dist/ staging-server:/var/www/staging.firecalc.afpma.pro/
```

## Maintenance

### Database Backups

```bash
# Backup staging database
cp databases/staging/firecalc-payments-staging.db \
   databases/staging/backups/firecalc-payments-staging-$(date +%Y%m%d).db

# Restore from backup
cp databases/staging/backups/firecalc-payments-staging-20250510.db \
   databases/staging/firecalc-payments-staging.db
```

### Data Refresh

Periodically refresh staging with anonymized production data:

```bash
# 1. Backup current staging DB
cp databases/staging/firecalc-payments-staging.db \
   databases/staging/firecalc-payments-staging.backup

# 2. Copy and anonymize production data
# (Create script to anonymize sensitive data)
./scripts/anonymize-db.sh databases/prod/firecalc-payments-prod.db \
   databases/staging/firecalc-payments-staging.db

# 3. Verify data
sqlite3 databases/staging/firecalc-payments-staging.db \
   "SELECT COUNT(*) FROM Customer;"
```

### Log Monitoring

```bash
# View staging logs
tail -f /var/log/firecalc/staging-backend.log

# Check for errors
grep ERROR /var/log/firecalc/staging-backend.log
```

## Troubleshooting

### Environment Not Detected

**Problem**: Wrong environment configs loaded

**Solution**: Verify environment variable
```bash
echo $FIRECALC_ENV
# Should output: staging

# Or check in code
FIRECALC_ENV=staging sbt console
scala> import afpma.firecalc.config.ConfigPathResolver
scala> println(ConfigPathResolver.debugInfo())
```

### Configuration Not Found

**Problem**: `ConfigurationNotFoundException`

**Solution**: Ensure staging configs exist
```bash
ls -la configs/staging/payments/
# Should list: payments-config.conf, gocardless-config.conf, email-config.conf
```

### GoCardless Errors

**Problem**: Payment creation fails

**Solution**: Verify sandbox credentials
1. Check access token is for sandbox
2. Verify base URL is `https://api-sandbox.gocardless.com`
3. Check webhook secret matches GoCardless dashboard
4. Ensure redirect URIs are correct

### Email Not Sending

**Problem**: Emails not received

**Solution**:
1. Check Mailtrap inbox (not real email)
2. Verify SMTP credentials in `email-config.conf`
3. Check email service logs

### Database Locked

**Problem**: `database is locked` error

**Solution**:
```bash
# Check for other processes using the database
lsof databases/staging/firecalc-payments-staging.db

# Kill other processes if needed
# Then restart application
```

## Best Practices

1. **Use Test Credentials**: Never use production API keys in staging
2. **Sandbox Only**: Always use GoCardless sandbox for staging
3. **Regular Testing**: Test in staging before every production deployment
4. **Data Privacy**: Anonymize any production data used in staging
5. **Access Control**: Limit staging access to authorized users only
6. **Monitoring**: Set up logging and monitoring for staging
7. **Backups**: Regularly backup staging database
8. **Documentation**: Document any staging-specific configurations

## Security Checklist

- [ ] GoCardless sandbox credentials configured (not live)
- [ ] Test email service configured (not production SMTP)
- [ ] Staging configs excluded from git (`.gitignore`)
- [ ] No production API keys in staging configs
- [ ] Staging domain uses HTTPS
- [ ] Access logs enabled
- [ ] Regular security reviews of staging configs

## Next Steps

After verifying staging works correctly:

1. Test complete purchase flow end-to-end
2. Verify invoice generation with staging configs
3. Test report generation with staging logo
4. Run integration tests against staging backend
5. Conduct UAT with stakeholders
6. Document any staging-specific issues
7. Prepare for production deployment

## Support

For issues with staging environment:
1. Check logs in `/var/log/firecalc/` (server) or console (local)
2. Verify configuration files are correctly set up
3. Consult `configs/README.md` for configuration details
4. Review `STAGING_SETUP_PLAN.md` for implementation checklist
