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

# 2. Copy environment templates (first time only)
cp docker/.env.defaults.example docker/.env.defaults
cp docker/.env.staging.example docker/.env.staging

# 3. Edit the real files with your values
nano docker/.env.defaults
# Update company info, shared config, secrets
nano docker/.env.staging
# Update staging-specific overrides (domains, tokens)
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
├── .env.defaults.example          # Generic defaults template
├── .env.staging.example           # Staging overrides template
├── .env.production.example        # Production overrides template
├── setup-env.sh                   # Merge script (generates .env)
├── .env.defaults                  # Real defaults (git-ignored, operator copies from .example)
├── .env.staging                   # Real staging values (git-ignored)
├── .env                           # Generated at deploy time (never committed)
├── docker-compose.yml        # SYMLINK → chosen mode (created by make docker-setup-*)
├── docker-compose.standalone.yml   # Standalone mode (nginx handles TLS)
├── docker-compose.behind-proxy.yml # Behind-proxy mode (external proxy handles TLS)
├── Dockerfile                # Application container definition
├── Dockerfile.nginx          # Nginx container (extends nginx:1.27-alpine + curl)
├── entrypoint.sh             # Entrypoint script (fixes database permissions on startup)
├── nginx-ui-server.conf      # UI static file server configuration (shared)
├── init-letsencrypt.sh       # Initial certificate provisioning script (standalone)
├── CONFIG_SETUP.md           # Comprehensive setup guide
├── DUAL-MODE-DEPLOYMENT.md   # Dual-mode architecture spec
├── README.md                 # This file
│
├── nginx-standalone/         # Nginx config for standalone mode
│   ├── nginx.conf            # Global nginx configuration (with SSL)
│   ├── proxy.conf.template   # Domain proxy config (envsubst template)
│   └── default.conf          # Empty — neutralizes nginx image default server block
│
├── nginx-behind-proxy/       # Nginx config for behind-proxy mode
│   ├── nginx.conf            # Global nginx configuration (HTTP only)
│   ├── proxy.conf            # Unified server block (UI + API via location matching)
│   └── default.conf          # Empty — neutralizes nginx image default server block
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
- **[`.env.defaults.example`](./.env.defaults.example)** - Generic defaults template
- **[`.env.staging.example`](./.env.staging.example)** - Staging overrides template
- **[`.env.production.example`](./.env.production.example)** - Production overrides template
- **[`setup-env.sh`](./setup-env.sh)** - Merge script (generates .env from defaults + overrides)
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

- [ ] Environment templates copied and edited: `docker/.env.defaults` and `docker/.env.{env}`
- [ ] `.env` generated by deploy target (automatic via `setup-env.sh`)
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

1. Copy `docker/.env.production.example` to `docker/.env.production`
2. Edit `docker/.env.production` with production values (domains, tokens, secrets)
3. Edit `docker/.env.defaults` with company info and shared config
4. Copy `configs/staging/` to `configs/production/`
5. Update all configs with production values
6. Deploy: `make prod-docker-deploy-up` (merges env automatically)

## Multi-Environment Deployment

You can run staging and production on the same host by keeping each environment
in a separate project directory (e.g., `firecalc-staging/` and `firecalc-prod/`).

Container names and image tags are prefixed with `firecalc-${FIRECALC_ENV}-payments` to
prevent collisions:

| Environment | Container Example | Image Tag |
|-------------|-------------------|-----------|
| staging     | `firecalc-staging-nginx` | `firecalc-staging-payments:latest` |
| production  | `firecalc-production-nginx` | `firecalc-production-payments:latest` |

**Standalone mode:** Set different `NGINX_HTTP_PORT` / `NGINX_HTTPS_PORT` in each
`.env` file to avoid host port conflicts (defaults are 80/443).
