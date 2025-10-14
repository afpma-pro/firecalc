
# FireCalc Docker Deployment

This directory contains everything needed to deploy the FireCalc payments backend using Docker.

## Quick Start

```bash
# 1. Copy environment template
cp .env.example .env

# 2. Edit .env with your values
nano .env

# 3. Copy configuration templates
cd configs/staging/payments
cp payments-config.conf.example payments-config.conf
cp email-config.conf.example email-config.conf
cp gocardless-config.conf.example gocardless-config.conf

cd ../invoices
cp invoice-config.yaml.example invoice-config.yaml

# 4. Edit all configuration files
# Update with your actual credentials and company information

# 5. Build and deploy
cd ../../..
docker-compose up -d

# 6. Monitor logs
docker-compose logs -f
```

## Directory Structure

```
docker/
├── .env.example              # Environment variables template
├── .env                      # Your actual environment variables (git-ignored)
├── docker-compose.yml        # Docker orchestration
├── Dockerfile                # Application container definition
├── CONFIG_SETUP.md           # Comprehensive setup guide
├── README.md                 # This file
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
- **[`.env.example`](./.env.example)** - Template for environment variables
- **[`docker-compose.yml`](./docker-compose.yml)** - Service orchestration
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

📖 **Read [`CONFIG_SETUP.md`](./CONFIG_SETUP.md) for detailed setup instructions**

## Common Commands

```bash
# Start services
docker-compose up -d

# Stop services
docker-compose down

# View logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f backend
docker-compose logs -f nginx-ssl-proxy

# Restart services
docker-compose restart

# Rebuild and restart
docker-compose up -d --build

# Check service status
docker-compose ps

# Validate configuration
docker-compose config
```

## Pre-Deployment Checklist

Before deploying:

- [ ] Domain DNS configured and propagated
- [ ] Ports 80 and 443 open in firewall
- [ ] `.env` file created and configured
- [ ] All config files copied from `.example` templates
- [ ] Company information updated in all configs
- [ ] GoCardless credentials obtained (sandbox for staging)
- [ ] SMTP service configured (Mailtrap.io recommended for staging)
- [ ] Application JAR built with fixed merge strategy: `sbt "payments/assembly"`
- [ ] JAR exists at: `../modules/payments/target/scala-*/firecalc-payments-assembly.jar`
- [ ] Database directory has proper ownership: `sudo chown -R 999:999 docker/databases && sudo chmod -R 755 docker/databases`

## Deployment Environments

### Staging (Current Configuration)

- Domain: `api.staging.example.com` (replace with your actual domain)
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