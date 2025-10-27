# FireCalc Docker Configuration Setup Guide

This guide explains how to configure the FireCalc payments backend for Docker deployment in staging and production environments.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration Structure](#configuration-structure)
- [Step-by-Step Setup](#step-by-step-setup)
- [Configuration Files](#configuration-files)

## Overview

The FireCalc payments backend requires configuration at multiple levels:

1. **Docker environment variables** (`.env`) - Container orchestration and basic settings
2. **Payment configuration** (`payments-config.conf`) - Core application settings
3. **Email configuration** (`email-config.conf`) - SMTP and email delivery
4. **Payment gateway configuration** (`gocardless-config.conf`) - GoCardless integration
5. **Invoice configuration** (`invoice-config.yaml`) - Company details and invoice templates

All configuration is environment-specific (staging, production) and stored outside the Docker image for security and flexibility.

## Prerequisites

Before starting configuration:

- [ ] Domain name registered and DNS configured (e.g., `api.staging.example.com`)
- [ ] Server with Docker and Docker Compose installed
- [ ] Ports 80 and 443 accessible from the internet (for Let's Encrypt)
- [ ] GoCardless account (sandbox for staging, live for production)
- [ ] SMTP service account (Mailtrap.io for staging, production SMTP for production)
- [ ] Company information and legal details ready

## Quick Start

For staging deployment:

```bash
# 1. Navigate to project root
cd /path/to/firecalc

# 2. Install UI dependencies (first time only)
make ui-setup

# 3. Copy environment template
cp docker/.env.example docker/.env

# 4. Edit .env with your staging values
nano docker/.env
# Update UI_DOMAIN, API_DOMAIN, FIRECALC_ENV, and other variables

# 5. Create staging configuration files
cd docker/configs/staging/payments
cp payments-config.conf.example payments-config.conf
cp email-config.conf.example email-config.conf
cp gocardless-config.conf.example gocardless-config.conf

# 6. Create staging invoice configuration
cd ../invoices
cp invoice-config.yaml.example invoice-config.yaml

# 7. Edit all .conf and .yaml files with your actual values
nano payments-config.conf
nano email-config.conf
nano gocardless-config.conf
nano invoice-config.yaml

# 8. (Optional) Add company logo
# Place logo file at: configs/staging/invoices/logo.png

# 9. Deploy to Docker (builds backend JAR + UI, then deploys)
cd /path/to/firecalc
make staging-docker-deploy-up

# 10. Monitor logs
cd docker
docker compose logs -f
```

**Note:** The `staging-docker-deploy-up` target automatically:
- Builds the backend JAR (`sbt payments/assembly`)
- Builds the UI static files (Scala.js compilation + Vite build)
- Rebuilds Docker images with `--build` flag
- Deploys containers with `docker compose up -d`

## Configuration Structure

```
docker/
├── .env.example                  # Template for environment variables
├── .env                          # Your actual environment variables (git-ignored)
├── docker-compose.yml            # Docker orchestration
├── Dockerfile                    # Application container definition
├── CONFIG_SETUP.md               # This file
├── nginx-ui-server.conf          # UI static file server configuration
├── nginx-proxy-custom.conf       # SSL proxy configuration (both domains)
│
├── configs/
│   └── staging/                  # Staging environment configs (git-ignored)
│       ├── payments/
│       │   ├── payments-config.conf.example      # Template
│       │   ├── payments-config.conf              # Your config
│       │   ├── email-config.conf.example         # Template
│       │   ├── email-config.conf                 # Your config
│       │   ├── gocardless-config.conf.example    # Template
│       │   └── gocardless-config.conf            # Your config
│       ├── invoices/
│       │   ├── invoice-config.yaml.example       # Template
│       │   ├── invoice-config.yaml               # Your config
│       │   └── logo.png                          # Your company logo (optional)
│       └── reports/
│           └── logo.png                          # Logo for PDF reports (optional)
│
└── databases/
    └── staging/                  # Staging database (git-ignored, auto-created)
        └── firecalc-payments-staging.db
```

**Nginx Architecture:** FireCalc uses a three-container architecture:
1. **backend** - Scala payments application (port 8181)
2. **ui-server** - nginx:alpine serving static UI files (port 80, internal)
3. **nginx-ssl-proxy** - Single SSL termination proxy handling both UI and API domains with automatic Let's Encrypt certificates (ports 443/80)

## Step-by-Step Setup

### Step 1: Configure Domains and DNS

FireCalc uses TWO separate domains for better security and separation of concerns:

1. **UI_DOMAIN** - Serves the web application frontend (e.g., `firecalc.staging.example.com`)
2. **API_DOMAIN** - Handles all backend API requests (e.g., `api.staging.example.com`)

**Register both domains**:
   - UI Domain example: `firecalc.staging.example.com`
   - API Domain example: `api.staging.example.com`

**Configure DNS A records** for BOTH domains:
   - Point both domains to your server's public IP address
   - Wait for DNS propagation (can take up to 48 hours, usually minutes)

**Verify DNS resolution**:
   ```bash
   # Check both domains resolve to your server
   nslookup firecalc.staging.example.com
   nslookup api.staging.example.com
   
   # Both should return your server's IP address
   ```

### Step 2: Configure Docker Environment

1. **Copy environment template**
   ```bash
   cp docker/.env.example docker/.env
   ```

2. **Edit `.env` file**
   ```bash
   nano docker/.env
   ```

3. **Set required variables**
   ```env
   # Minimum required for staging:
   UI_DOMAIN=firecalc.staging.example.com
   API_DOMAIN=api.staging.example.com
   FIRECALC_ENV=staging
   
   # Company information
   COMPANY_LEGAL_NAME="Your Company Name"
   COMPANY_EMAIL="billing@yourcompany.com"
   # ... (see .env.example for full list)
   ```

4. **Secure the file**
   ```bash
   chmod 600 docker/.env
   ```

### Step 3: Configure Payments Settings

1. **Copy template**
   ```bash
   cp docker/configs/staging/payments/payments-config.conf.example \
      docker/configs/staging/payments/payments-config.conf
   ```

2. **Edit configuration**
   ```bash
   nano docker/configs/staging/payments/payments-config.conf
   ```

3. **Key settings to update**:
   - `invoice.number-prefix`: Set to `"FCALC-STG-[YYYY]-"` for staging
   - `invoice-generation.config-file-path`: Verify path is correct
   - `admin.email`: Set to your admin email
   - `database.path`: Verify database path

4. **Secure the file**
   ```bash
   chmod 600 docker/configs/staging/payments/payments-config.conf
   ```

### Step 4: Configure Email/SMTP

1. **Sign up for test email service** (for staging)
   - Recommended: [Mailtrap.io](https://mailtrap.io) (free tier available)
   - Alternative: MailHog (self-hosted)

2. **Copy template**
   ```bash
   cp docker/configs/staging/payments/email-config.conf.example \
      docker/configs/staging/payments/email-config.conf
   ```

3. **Get SMTP credentials**
   - Log in to Mailtrap.io
   - Create inbox for staging
   - Copy SMTP credentials from inbox settings

4. **Update configuration**
   ```bash
   nano docker/configs/staging/payments/email-config.conf
   ```
   
   Update:
   ```hocon
   smtp {
     host = "sandbox.smtp.mailtrap.io"
     port = 2525
     username = "your-actual-username"
     password = "your-actual-password"
     use-tls = true
   }
   
   from {
     address = "noreply-staging@firecalc.example.com"
     name = "FireCalc Staging"
   }
   ```

5. **Secure the file**
   ```bash
   chmod 600 docker/configs/staging/payments/email-config.conf
   ```

### Step 5: Configure GoCardless Payment Gateway

1. **Create GoCardless sandbox account**
   - Visit: https://manage-sandbox.gocardless.com
   - Sign up for free sandbox account

2. **Generate API access token**
   - Navigate to: Developers > API tokens
   - Create token named "FireCalc Staging"
   - Copy token (starts with `sandbox_`)
   - **Save securely - shown only once!**

3. **Configure webhook**
   - Navigate to: Developers > Webhooks
   - Create endpoint using your API_DOMAIN: `https://api.staging.example.com/v1/webhooks/gocardless`
   - Copy webhook secret

4. **Register redirect URIs**
   - Navigate to: Developers > Redirect URIs
   - Add (using API_DOMAIN): `https://api.staging.example.com/v1/payment_complete`
   - Add (using API_DOMAIN): `https://api.staging.example.com/v1/payment_cancelled`

5. **Copy template**
   ```bash
   cp docker/configs/staging/payments/gocardless-config.conf.example \
      docker/configs/staging/payments/gocardless-config.conf
   ```

6. **Update configuration**
   ```bash
   nano docker/configs/staging/payments/gocardless-config.conf
   ```

7. **Secure the file**
   ```bash
   chmod 600 docker/configs/staging/payments/gocardless-config.conf
   ```

### Step 6: Configure Invoice Generation

1. **Copy template**
   ```bash
   cp docker/configs/staging/invoices/invoice-config.yaml.example \
      docker/configs/staging/invoices/invoice-config.yaml
   ```

2. **Update company information**
   ```bash
   nano docker/configs/staging/invoices/invoice-config.yaml
   ```
   
   Update all company details in the `sender` section:
   - Legal name and display name
   - Complete address
   - VAT number and registration number
   - Contact information (email, phone, website)
   - Bank details (IBAN, BIC)

3. **Add company logo (optional)**
   ```bash
   # Copy your logo to the invoices directory
   cp /path/to/your/logo.png docker/configs/staging/invoices/logo.png
   
   # Update invoice-config.yaml
   # Change: logo: null
   # To: logo: "logo.png"
   ```

4. **Secure the file**
   ```bash
   chmod 600 docker/configs/staging/invoices/invoice-config.yaml
   ```

### Step 7: Prepare Database Directory

The database directory requires proper ownership for the Docker container user (UID 999):

```bash
# Create staging database directory
mkdir -p docker/databases/staging

# Set ownership to match container user (UID 999) and secure permissions
sudo chown -R 999:999 docker/databases
sudo chmod -R 755 docker/databases
```

**Why UID 999?** The Docker container runs as non-root user `appuser` with UID 999 for security. The database directory must be owned by this user to allow write access.

### Step 8: Build and Deploy

1. **Validate configuration**
   ```bash
   # From project root
   cd /path/to/firecalc
   
   # Check docker-compose configuration
   cd docker
   docker compose config
   
   # This should show your configuration with .env variables substituted
   # Look for any errors or warnings
   ```

2. **Deploy using Makefile (recommended)**
   ```bash
   # From project root
   cd /path/to/firecalc
   
   # This single command builds backend JAR, builds UI, and deploys to Docker
   make staging-docker-deploy-up
   ```
   
   **OR manually (for more control):**
   ```bash
   # From project root
   cd /path/to/firecalc
   
   # Build backend JAR (also builds UI automatically)
   make staging-backend-build
   
   # Verify builds
   ls -lh modules/payments/target/scala-*/firecalc-payments-assembly.jar
   ls -lh web/dist-app/index.html
   
   # Deploy to Docker
   cd docker
   docker compose down
   docker compose up -d --build
   ```

3. **Monitor startup**
   ```bash
   # Watch logs from both services
   docker compose logs -f
   
   # Watch specific service
   docker compose logs -f backend
   docker compose logs -f nginx-ssl-proxy
   
   # Check service status
   docker compose ps
   ```

4. **Wait for SSL certificates**
   - First startup takes 2-5 minutes to obtain Let's Encrypt certificates for BOTH domains
   - Monitor the nginx-ssl-proxy logs:
     ```bash
     docker compose logs -f nginx-ssl-proxy
     ```
   - Once ready, you'll see "Certificate obtained successfully"
   - The nginx-ssl-proxy container automatically handles:
     - SSL certificate generation for both domains (single certificate with SANs)
     - HTTP to HTTPS redirects
     - Certificate renewal before expiration
     - Routing to appropriate upstream based on domain (UI → ui-server, API → backend)

5. **Access the services**
   - **UI**: https://staging.firecalc.example.com (port 443)
   - **API**: https://api.staging.firecalc.example.com (port 443)
   
   Both domains use standard HTTPS port 443, with routing handled by nginx based on the `server_name`.

6. **Verify deployment**
   ```bash
   # Check API version (should match build.sbt version)
   curl -s https://api.staging.example.com/v1/healthcheck
   
   # Check certificate includes both domains
   docker exec firecalc-ssl-proxy openssl x509 -in /etc/letsencrypt/fullchain-copy.pem -noout -text | grep DNS
   ```
   
   Expected healthcheck output:
   ```json
   {"info":{"engine_version":"0.3.0-b4","payments_base_version":"0.9.0-b4",...}}
   ```
   
   Expected certificate output:
   ```
   DNS:staging.firecalc.example.com, DNS:api.staging.firecalc.example.com
   ```

> **⚠️ IMPORTANT - Docker Image Rebuild**:
> - After rebuilding the JAR or UI, you **MUST** use `docker compose up -d --build` to rebuild the Docker image
> - Simply restarting with `docker compose restart` will **NOT** update the JAR or UI files inside containers
> - The `--build` flag ensures new files are copied into fresh Docker images
> - **Best practice**: Use `make staging-docker-deploy-up` which handles all build steps and Docker rebuild automatically

> **⚠️ IMPORTANT - UI Build Required**:
> - The UI must be built (`web/dist-app/` directory) before Docker deployment
> - If `web/dist-app/` is empty or contains root-owned files, you'll get a **403 Forbidden** error
> - Run `make ui-setup` once to install dependencies, then `make staging-web-ui-build` to build UI
> - Or use `make staging-backend-build` which builds both backend and UI
> - Fix permissions if needed: `sudo chown -R $USER:$USER web/dist-app`

## Configuration Files

### payments-config.conf

**Purpose**: Core application settings

**Key sections**:
- Database configuration
- Invoice numbering
- Retry settings
- Admin contact

**Location**: [`configs/staging/payments/payments-config.conf`](../configs/staging/payments/payments-config.conf.example)

### email-config.conf

**Purpose**: SMTP and email delivery

**Key sections**:
- SMTP server settings
- Authentication credentials
- Sender information

**Location**: [`configs/staging/payments/email-config.conf`](../configs/staging/payments/email-config.conf.example)

**Staging recommendation**: Use Mailtrap.io to avoid sending real emails

### gocardless-config.conf

**Purpose**: Payment gateway integration

**Key sections**:
- API access token
- Webhook secret
- Redirect URIs
- Environment (sandbox/live)

**Location**: [`configs/staging/payments/gocardless-config.conf`](../configs/staging/payments/gocardless-config.conf.example)

**Critical**: Always use `sandbox` environment for staging!

### invoice-config.yaml

**Purpose**: Invoice generation and company branding

**Key sections**:
- Sender (company) information
- Payment terms and methods
- Template styling
- Logo configuration

**Location**: [`configs/staging/invoices/invoice-config.yaml`](../configs/staging/invoices/invoice-config.yaml.example)

**Supports**: Environment variable substitution for sensitive data