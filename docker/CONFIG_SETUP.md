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

- [ ] Deployment mode chosen (standalone or behind-proxy)
- [ ] Server with Docker and Docker Compose installed
- [ ] GoCardless account (sandbox for staging, live for production)
- [ ] SMTP service account (Mailtrap.io for staging, production SMTP for production)
- [ ] JWT secret generated (`openssl rand -base64 32`) and added to `.env`
- [ ] Company information and legal details ready

**Standalone mode additionally requires:**
- [ ] Domain names registered and DNS configured (UI_DOMAIN and API_DOMAIN)
- [ ] Ports 80 and 443 accessible from the internet (for Let's Encrypt)

**Behind-proxy mode additionally requires:**
- [ ] External reverse proxy (e.g., nginx-proxy-manager) configured

## Quick Start

For staging deployment:

```bash
# 1. Navigate to project root
cd /path/to/firecalc

# 2. Install UI dependencies (first time only)
make ui-setup

# 3. Copy environment templates (first time only)
cp docker/.env.defaults.example docker/.env.defaults
cp docker/.env.staging.example docker/.env.staging

# 4. Edit the real files with your values
nano docker/.env.defaults
# Update company info, shared config, secrets
nano docker/.env.staging
# Update staging-specific overrides (domains, tokens)
# 5. Choose deployment mode
make docker-setup-standalone     # Self-contained (nginx handles TLS)
# OR
make docker-setup-behind-proxy   # External proxy handles TLS

# 6. Create staging configuration files
cd docker/configs/staging/payments
cp payments-config.conf.example payments-config.conf
cp email-config.conf.example email-config.conf
cp gocardless-config.conf.example gocardless-config.conf

# 7. Create staging invoice configuration
cd ../invoices
cp invoice-config.yaml.example invoice-config.yaml

# 8. Edit all .conf and .yaml files with your actual values
nano payments-config.conf
nano email-config.conf
nano gocardless-config.conf
nano invoice-config.yaml

# 9. Add company logo for invoices and reports
# Copy your logo to BOTH directories (REQUIRED for PDF generation):
cp /path/to/your/logo.png docker/configs/staging/invoices/logo.png
cp /path/to/your/logo.jpg docker/configs/staging/reports/logo.jpg

# 10. Deploy to Docker (builds backend JAR + UI, then deploys)
cd /path/to/firecalc
make staging-docker-deploy-up

# 11. Monitor logs
cd docker
docker compose logs -f
```

- Merges environment files (`.env.defaults` + `.env.staging` → `.env`)
- Builds the backend JAR (`sbt payments/assembly`)
- Builds the UI static files (Scala.js compilation + Vite build)
- Rebuilds Docker images with `--build` flag
- Deploys containers with `docker compose up -d`

## Configuration Structure

```
docker/
├── .env.defaults.example          # Generic defaults template
├── .env.staging.example           # Staging overrides template
├── .env.production.example        # Production overrides template
├── setup-env.sh                   # Merge script (generates .env)
├── .env.defaults                  # Real defaults (git-ignored)
├── .env.staging                   # Real staging values (git-ignored)
├── .env                           # Generated at deploy time (never committed)
├── docker-compose.yml            # SYMLINK → chosen mode (created by make docker-setup-*)
├── docker-compose.standalone.yml   # Standalone mode (nginx handles TLS)
├── docker-compose.behind-proxy.yml # Behind-proxy mode (external proxy handles TLS)
├── Dockerfile                    # Application container definition
├── Dockerfile.nginx              # Nginx container (extends nginx:1.27-alpine + curl)
├── entrypoint.sh                 # Entrypoint script (fixes database permissions on startup)
├── CONFIG_SETUP.md               # This file
├── DUAL-MODE-DEPLOYMENT.md       # Dual-mode architecture spec
├── README.md                     # Quick reference
├── nginx-ui-server.conf          # UI static file server configuration (shared)
├── init-letsencrypt.sh           # Initial certificate provisioning script (standalone)
│
├── nginx-standalone/             # Nginx config for standalone mode
│   ├── nginx.conf                # Global nginx configuration (with SSL)
│   ├── proxy.conf.template       # Domain proxy config (envsubst template)
│   └── default.conf              # Empty — neutralizes nginx image default server block
│
├── nginx-behind-proxy/           # Nginx config for behind-proxy mode
│   ├── nginx.conf                # Global nginx configuration (HTTP only)
│   ├── proxy.conf                # Unified server block (UI + API via location matching)
│   └── default.conf              # Empty — neutralizes nginx image default server block
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
│       │   └── logo.png                          # Company logo for invoices (REQUIRED)
│       └── reports/
│           └── logo.jpg                          # Company logo for reports (REQUIRED)
│
└── databases/
    └── staging/                  # Staging database (git-ignored, auto-created)
        └── firecalc-payments-staging.db
```

**Nginx Architecture (Standalone Mode):** Four-container architecture:
1. **backend** - Scala payments application (port 8181)
2. **ui-server** - nginx:alpine serving static UI files (port 80, internal)
3. **nginx** - Official nginx:1.27-alpine reverse proxy handling both UI and API domains with HTTPS termination (ports 443/80)
4. **certbot** - certbot/certbot:v2.11.0 sidecar for automated Let's Encrypt certificate renewal (every 12 hours)

**Nginx Architecture (Behind-Proxy Mode):** Three-container architecture (no certbot):
1. **backend** - Scala payments application (port 8181)
2. **ui-server** - nginx:alpine serving static UI files (port 80, internal)
3. **nginx** - Official nginx:1.27-alpine internal gateway (HTTP only, no host ports exposed)

The outer proxy (e.g., nginx-proxy-manager) handles TLS termination and forwards traffic to the inner nginx container via the shared `proxy` Docker network.

## Step-by-Step Setup

### Step 1: Configure Domains and DNS

**Standalone mode only:**

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

**Behind-proxy mode:** Domain routing is handled by the outer proxy (e.g., nginx-proxy-manager). Configure proxy hosts in your outer proxy pointing to the inner nginx container on the `proxy` Docker network.

### Step 2: Configure Docker Environment

The environment configuration uses a **base + override** pattern:
- `.env.defaults` — all variables with generic defaults (company info, shared config)
- `.env.staging` — staging-specific overrides (domains, tokens, secrets)
- `.env` — generated at deploy time by merging defaults + overrides (last value wins)

1. **Copy templates to create real env files** (first time only)
   ```bash
   cp docker/.env.defaults.example docker/.env.defaults
   cp docker/.env.staging.example docker/.env.staging
   ```

2. **Edit `.env.defaults`** with your shared values
   ```bash
   nano docker/.env.defaults
   ```
   Set:
   - Company information (legal name, address, bank details)
   - Shared configuration (SMTP host/port, GoCardless base URL)
   - Template styling and feature flags

3. **Edit `.env.staging`** with staging-specific overrides
   ```bash
   nano docker/.env.staging
   ```
   Set:
   - `UI_DOMAIN` and `API_DOMAIN` (standalone mode)
   - `COMPOSE_PROJECT_NAME=firecalc-staging`
   - `FIRECALC_ENV=staging`
   - SMTP credentials (USERNAME, PASSWORD)
   - GoCardless credentials (ACCESS_TOKEN, WEBHOOK_SECRET, redirect URIs)
   - JWT secret

4. **Deploy** — the `make staging-docker-deploy-up` target automatically merges the files:
   ```bash
   make staging-docker-deploy-up
   # → setup-env.sh merges .env.defaults + .env.staging → .env
   # → compose up with correct config
   ```

**Or generate manually:**
   ```bash
   make docker-env-setup FIRECALC_ENV=staging
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
   - `jwt.secret`: Requires `JWT_SECRET` environment variable in `.env` (generate with `openssl rand -base64 32`, must be at least 32 characters)
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

3. **Add company logo (REQUIRED for PDF generation)**
   ```bash
   # Copy your company logo to BOTH directories
   # These logos will be embedded in the JAR during build and used by Typst for PDF generation
   # NOTE: The invoices module requires PNG format; the reports module requires JPG format
   cp /path/to/your/logo.png docker/configs/staging/invoices/logo.png
   cp /path/to/your/logo.jpg docker/configs/staging/reports/logo.jpg

   # Recommended size: 200x200 pixels or similar aspect ratio
   ```

4. **Secure the file**
   ```bash
   chmod 600 docker/configs/staging/invoices/invoice-config.yaml
   ```

### Step 7: Prepare Database Directory

The database directory is mounted into the container at `/app/databases`. The
backend runs as non-root user `appuser` (UID 999), which needs write access.

**Automatic fix on startup:** The container's entrypoint script (`docker/entrypoint.sh`)
runs as root before starting the application. It checks and fixes the database
directory ownership automatically. This means you can use `sudo docker compose up`
without worrying about permissions — the entrypoint handles it.

**Manual setup (optional):** If you prefer to set permissions on the host before
starting containers:

```bash
# Create staging database directory
mkdir -p docker/databases/staging

# Set ownership to match container user (UID 999)
sudo chown -R 999:999 docker/databases
# Directories: owner-only access; files: owner read/write
sudo find docker/databases -type d -exec chmod 700 {} \;
sudo find docker/databases -type f -exec chmod 600 {} \;

Or use the Makefile target:

```bash
make docker-fix-db-perms
```

**How it works:** On every container start, the entrypoint script:
1. Runs `chown -R appuser:appuser /app/databases` (recursive ownership fix)
2. Sets directories to `700` and files to `600` (owner-only access)
3. Drops privileges to `appuser` via `setpriv` before launching Java

This ensures SQLite can create its journal files
(without write access, SQLite fails with `SQLITE_READONLY_DIRECTORY`).

> **Security: Database Encryption at Rest**
>
> The SQLite database stores customer PII and payment data. The following hardening measures apply:
>
> - **Filesystem permissions**: The entrypoint script sets `chown -R appuser:appuser` on `/app/databases` on every container start, then secures directories to `700` and files to `600` (owner-only access). The host-side directory should match (see manual setup above or `make docker-fix-db-perms`).
> - **SQLCipher**: For production environments handling sensitive data, consider replacing the standard SQLite library with [SQLCipher](https://www.zetetic.net/sqlcipher/) to encrypt the database at rest. This requires a native dependency change and is not included by default.
> - **Backups**: Database backup files contain the same sensitive data and should be encrypted (e.g., `gpg --symmetric`) and stored with restrictive permissions.

### Step 8: Build and Deploy

1. **Validate configuration**
   ```bash
   # From project root
   cd /path/to/firecalc
   
   # Check docker compose configuration
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
   docker compose logs -f nginx
   docker compose logs -f certbot

   # Check service status
   docker compose ps
   ```

4. **Provision SSL certificates (standalone mode, first time only)**
   - Before the first deployment, run the certificate provisioning script:
     ```bash
     cd docker
     ./init-letsencrypt.sh
     ```
   - This generates self-signed bootstrap certs, starts nginx, obtains real Let's Encrypt
     certificates via certbot, and reloads nginx.
   - Subsequent certificate renewals are automatic (certbot sidecar checks every 12 hours).
   - Monitor certificate status:
     ```bash
     docker compose logs -f certbot
     ```

5. **Access the services**

   **Standalone mode:**
   - **UI**: https://firecalc.staging.example.com (port 443)
   - **API**: https://api.staging.example.com (port 443)

   **Behind-proxy mode:**
   - Services are accessed through the outer proxy (e.g., nginx-proxy-manager)
   - Configure proxy hosts in your outer proxy pointing to the inner nginx container

6. **Verify deployment**
   ```bash
   # Standalone mode:
   curl -s https://api.staging.example.com/v1/healthcheck
   
   # Behind-proxy mode:
   curl -s http://firecalc-nginx/v1/healthcheck
   
   # Check certificate includes both domains (standalone mode)
   docker compose exec nginx openssl x509 -in /etc/letsencrypt/live/${UI_DOMAIN}/fullchain.pem -noout -text | grep DNS
   ```
   
   Expected healthcheck output:
   ```json
   {"info":{"engine_version":"0.3.0-b4","payments_base_version":"0.9.0-b4",...}}
   ```
   
   Expected certificate output (standalone mode):
   ```
   DNS:firecalc.staging.example.com, DNS:api.staging.example.com
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
- Firebox availability switches (optional — omitting the block defaults all types to enabled, safe for upgrades)

**Location**: [`configs/staging/payments/payments-config.conf`](../configs/staging/payments/payments-config.conf.example)

**Firebox Availability Switches**: The optional `firebox-availability` block (inside the environment section) controls which firebox types are available for purchase. Valid keys (kebab-case): `traditional`, `ecolabeled`, `afpma-prse`, `single-tested`, `door15a-catalog`. Each key defaults to `true` when omitted; omitting the entire block defaults to all-enabled. This is the safe choice for upgrades — existing behaviour is preserved with no configuration change.

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