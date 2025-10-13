<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# UI Configuration Guide

This document explains how to configure the FireCalc UI module for different environments.

## Overview

The UI module uses **Vite environment variables** to configure backend API endpoints. This approach follows frontend best practices and works seamlessly in both browser and Electron environments.

## Configuration Files

### Development Configuration

**File:** `modules/ui/.env.development`

Used automatically when running:
```bash
npm run dev
```

Default configuration:
```env
VITE_BACKEND_PROTOCOL=http
VITE_BACKEND_HOST=localhost
VITE_BACKEND_PORT=8181
VITE_BACKEND_BASE_PATH=/v1
```

### Production Configuration

**File:** `modules/ui/.env.production`

Used automatically when building:
```bash
npm run build
```

Default configuration:
```env
VITE_BACKEND_PROTOCOL=https
VITE_BACKEND_HOST=api.firecalc.afpma.pro
VITE_BACKEND_PORT=443
VITE_BACKEND_BASE_PATH=/v1
```

## Setup Instructions

### First Time Setup

1. **Copy template files** (if `.env` files don't exist):
   ```bash
   cd modules/ui
   cp ../../configs/templates/ui/.env.development.template .env.development
   cp ../../configs/templates/ui/.env.production.template .env.production
   ```

2. **Edit configuration files** to match your environment:
   ```bash
   # Edit development config
   nano .env.development
   
   # Edit production config
   nano .env.production
   ```

### Configuration Variables

All variables must be prefixed with `VITE_` to be accessible in the client:

- **VITE_BACKEND_PROTOCOL**: `http` or `https`
- **VITE_BACKEND_HOST**: Domain or IP address (e.g., `localhost`, `api.example.com`)
- **VITE_BACKEND_PORT**: Port number (e.g., `8181`, `443`)
- **VITE_BACKEND_BASE_PATH**: API base path (e.g., `/v1`)

## Usage in Code

### Accessing Configuration

```scala
import afpma.firecalc.ui.config.UIConfig

// Get full endpoint URLs
val healthcheckUrl = UIConfig.Endpoints.healthcheck
val createIntentUrl = UIConfig.Endpoints.createPurchaseIntent

// Get base URL
val baseUrl = UIConfig.backendBaseUrl

// Debug information
println(UIConfig.debugInfo())
```

### Low-Level Access (Advanced)

```scala
import afpma.firecalc.ui.config.ViteEnv

// Direct access to environment variables
val protocol = ViteEnv.backendProtocol
val host = ViteEnv.backendHost
val port = ViteEnv.backendPort

// Environment information
val isDev = ViteEnv.isDev
val isProd = ViteEnv.isProd
```

## How It Works

1. **Build Time**: Vite reads `.env.development` or `.env.production` based on the mode
2. **Injection**: Environment variables are injected into the JavaScript bundle
3. **Type Safety**: ScalaJS facade (`ViteEnv.scala`) provides type-safe access
4. **Convenience**: `UIConfig.scala` constructs full URLs from the configuration

## Generated URLs

The configuration generates these endpoint URLs:

```scala
// Development (http://localhost:8181/v1)
UIConfig.Endpoints.healthcheck          // http://localhost:8181/v1/healthcheck
UIConfig.Endpoints.createPurchaseIntent // http://localhost:8181/v1/purchase/create-intent
UIConfig.Endpoints.verifyAndProcess     // http://localhost:8181/v1/purchase/verify-and-process

// Production (https://api.firecalc.afpma.pro/v1)
UIConfig.Endpoints.healthcheck          // https://api.firecalc.afpma.pro/v1/healthcheck
UIConfig.Endpoints.createPurchaseIntent // https://api.firecalc.afpma.pro/v1/purchase/create-intent
UIConfig.Endpoints.verifyAndProcess     // https://api.firecalc.afpma.pro/v1/purchase/verify-and-process
```

## Port Handling

The configuration automatically handles default ports:
- Port `80` for `http://` is omitted from URLs
- Port `443` for `https://` is omitted from URLs
- Other ports are included explicitly

Examples:
```scala
// http://localhost:8181/v1 - port included
// https://api.example.com/v1 - port 443 omitted
// http://api.example.com/v1 - port 80 omitted
```

## Security

### ⚠️ Important Security Notes

1. **Never commit `.env` files** - They're in `.gitignore`
2. **Use templates** - Template files (`*.template`) are safe to commit
3. **No secrets in UI config** - UI runs in browser, all config is public
4. **HTTPS in production** - Always use HTTPS for production

### Environment File Priority

Vite uses a priority system for environment files. The `.gitignore` protects local override files:

- **`.env.local`** - Personal local overrides for any environment (ignored)
  - Takes precedence over `.env`, `.env.development`, `.env.production`
  - Never committed (developer-specific settings)

- **`.env.development.local`** - Personal local overrides for development only (ignored)
  - Takes precedence over `.env.development`
  - Never committed (e.g., test against different localhost port)

- **`.env.production.local`** - Personal local overrides for production builds (ignored)
  - Takes precedence over `.env.production`
  - Never committed (e.g., test prod build with different backend)

**Priority order (highest to lowest):**
1. `.env.[mode].local` ← **ignored** (personal overrides)
2. `.env.[mode]` ← **committed** (team configuration)
3. `.env.local` ← **ignored** (personal base overrides)
4. `.env` ← **committed** (base configuration)

This allows developers to have personal overrides without affecting the team's shared configuration files.

### What's Safe to Put in UI Config

✅ **Safe:**
- API endpoints (public)
- Domain names (public)
- Port numbers (public)
- Environment modes (public)

❌ **Never put in UI config:**
- API keys
- Passwords
- Secret tokens
- Private credentials

## Troubleshooting

### Config not loading

1. Ensure `.env` files exist in `modules/ui/` directory
2. Restart the dev server after changing `.env` files
3. Check that variables are prefixed with `VITE_`

### Wrong URLs being used

```scala
// Add debug output to check current configuration
import afpma.firecalc.ui.config.UIConfig
dom.console.log(UIConfig.debugInfo())
```

### Building for different environment

```bash
# Development build
npm run dev

# Production build
npm run build
```

## Migration from Hardcoded URLs

Before (hardcoded):
```scala
FetchStream.post(
  url = "http://localhost:8181/v1/purchase/create-intent",
  // ...
)
```

After (configured):
```scala
import afpma.firecalc.ui.config.UIConfig

FetchStream.post(
  url = UIConfig.Endpoints.createPurchaseIntent,
  // ...
)
```

## Related Files

- `modules/ui/src/main/scala/afpma/firecalc/ui/config/ViteEnv.scala` - Environment variable facade
- `modules/ui/src/main/scala/afpma/firecalc/ui/config/UIConfig.scala` - Configuration object
- `modules/ui/.env.development` - Development configuration
- `modules/ui/.env.production` - Production configuration
- `configs/templates/ui/.env.*.template` - Template files

## Further Reading

- [Vite Environment Variables](https://vite.dev/guide/env-and-mode.html)
- [ScalaJS Facades](https://www.scala-js.org/doc/interoperability/facade-types.html)
