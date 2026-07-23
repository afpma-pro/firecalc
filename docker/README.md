# FireCalc Docker Deployment

This directory contains everything needed to deploy the FireCalc payments backend using Docker.

## Quick Start

For a complete staging deployment (recommended):

```bash
# From project root
cd /path/to/firecalc

# 1. Install UI + landing page dependencies (first time only)
make ui-setup
make landing-setup

# 2. Copy environment template
cp docker/.env.example docker/.env

# 3. Edit .env with your values
nano docker/.env
# Update UI_DOMAIN, API_DOMAIN, FIRECALC_ENV

# 3b. Choose deployment mode
make docker-setup-standalone     # Self-contained (nginx handles TLS)
# OR
make docker-setup-behind-proxy   # External proxy handles TLS

# 4. Copy configuration templates
cd docker/configs/staging/payments
cp payments-config.conf.example payments-config.conf
cp email-config.conf.example email-config.conf
cp gocardless-config.conf.example gocardless-config.conf

cd ../invoices
cp invoice-config.yaml.example invoice-config.yaml

# 5. Edit all configuration files
# Update with your actual credentials and company information

# 6. Build and deploy (builds backend JAR + UI, then deploys)
cd /path/to/firecalc
make staging-docker-deploy-up

# 7. Monitor logs
cd docker
docker compose logs -f
```

**Note:** `make staging-docker-deploy-up` automatically builds both the backend JAR and UI, then deploys to Docker with image rebuild.

## Directory Structure

```
docker/
├── .env.example              # Environment variables template
├── .env                      # Your actual environment variables (git-ignored)
├── docker-compose.yml        # SYMLINK → chosen mode (created by make docker-setup-*)
├── docker-compose.standalone.yml   # Standalone mode (nginx handles TLS)
├── docker-compose.behind-proxy.yml # Behind-proxy mode (external proxy handles TLS)
├── Dockerfile                # Application container definition
├── nginx-ui-server.conf      # UI static file server configuration (shared)
├── init-letsencrypt.sh       # Initial certificate provisioning script (standalone)
├── CONFIG_SETUP.md           # Comprehensive setup guide
├── DUAL-MODE-DEPLOYMENT.md   # Dual-mode architecture spec
├── README.md                 # This file
│
├── nginx-standalone/         # Nginx config for standalone mode
│   ├── nginx.conf            # Global nginx configuration (with SSL)
│   └── proxy.conf.template   # Domain proxy config (envsubst template)
│
├── nginx-behind-proxy/       # Nginx config for behind-proxy mode
│   ├── nginx.conf            # Global nginx configuration (HTTP only)
│   └── proxy.conf            # Unified server block (UI + API via location matching)
│
├── configs/                  # Configuration files
│   └── staging/              # Staging environment configs (git-ignored)
│       ├── payments/         # Payment backend configuration
│       ├── invoices/         # Invoice generation configuration
│       └── reports/          # Report configuration
│
└── databases/                # Database files (git-ignored)
    └── staging/              # Staging database
```

## Files

### Essential Files

- **[`CONFIG_SETUP.md`](./CONFIG_SETUP.md)** - Comprehensive deployment guide (start here!)
- **[`DUAL-MODE-DEPLOYMENT.md`](./DUAL-MODE-DEPLOYMENT.md)** - Dual-mode deployment architecture
- **[`.env.example`](./.env.example)** - Template for environment variables
- **[`docker-compose.yml`](./docker-compose.yml)** - Service orchestration (symlink)
- **[`Dockerfile`](./Dockerfile)** - Application container image

### Configuration Templates

All templates are in `configs/staging/` with `.example` extension:

- **Payment Backend:**
  - `payments/payments-config.conf.example` - Main application settings
  - `payments/email-config.conf.example` - SMTP configuration
  - `payments/gocardless-config.conf.example` - Payment gateway settings

- **Invoice Generation:**
  - `invoices/invoice-config.yaml.example` - Company info and invoice templates

## Documentation

Read [`CONFIG_SETUP.md`](./CONFIG_SETUP.md) for detailed setup instructions.
Read [`DUAL-MODE-DEPLOYMENT.md`](./DUAL-MODE-DEPLOYMENT.md) for dual-mode deployment architecture.

## Common Commands

```bash
# Start services
docker compose up -d

# Stop services
docker compose down

# View logs
docker compose logs -f

# View specific service logs
docker compose logs -f backend
docker compose logs -f nginx
docker compose logs -f certbot

# Restart services
docker compose restart

# Rebuild and restart (REQUIRED after JAR/UI changes)
docker compose up -d --build

# Check service status
docker compose ps

# Validate configuration
docker compose config
```

## Pre-Deployment Checklist

Before deploying:

- [ ] Deployment mode chosen: `make docker-setup-standalone` or `make docker-setup-behind-proxy`
- [ ] `.env` file created and configured (UI_DOMAIN/API_DOMAIN for standalone; FIRECALC_ENV for both)
- [ ] All config files copied from `.example` templates
- [ ] Company information updated in all configs
- [ ] GoCardless credentials obtained (sandbox for staging)
- [ ] SMTP service configured (Mailtrap.io recommended for staging)
- [ ] UI dependencies installed: `make ui-setup` (first time only)
- [ ] Landing page dependencies installed: `make landing-setup` (first time only)

**Standalone mode only:**
- [ ] Both UI and API domains DNS configured and propagated
- [ ] Ports 80 and 443 open in firewall
- [ ] Initial certificates provisioned: `./init-letsencrypt.sh`

**Behind-proxy mode only:**
- [ ] Outer proxy (e.g., NPM) configured with proxy hosts pointing to inner nginx
- [ ] Outer proxy handles TLS termination

## Deployment Environments

### Staging (Current Configuration)

- UI Domain: `firecalc.staging.example.com` (replace with your actual domain)
- API Domain: `api.staging.example.com` (replace with your actual domain)
- Environment: `FIRECALC_ENV=staging`
- GoCardless: **SANDBOX only** (use `sandbox` config section)
- Email: Test service (Mailtrap.io recommended)
- Database: `databases/staging/firecalc-payments-staging.db`

### Production Setup

For production deployment:
1. Copy `configs/staging/` to `configs/production/`
2. Update all configs with production values
3. Set `FIRECALC_ENV=production` in `.env`
4. Use GoCardless **LIVE** credentials
5. Use production SMTP service
6. Update domain to production URL
