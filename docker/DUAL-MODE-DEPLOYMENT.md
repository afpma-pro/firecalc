# Dual-Mode Docker Deployment Plan

## Overview

Support two deployment modes for the FireCalc Docker stack:

- **Standalone** — inner nginx handles TLS termination and Let's Encrypt certificate management. Full self-contained deployment.
- **Behind-proxy** — external reverse proxy (e.g., nginx-proxy-manager) handles TLS termination. Inner nginx serves plain HTTP on the Docker network only.

The developer chooses a mode by creating a symlink. No build-time flags, no conditional logic in configs.

---

## Decision Log

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | Who manages certs in behind-proxy mode? | External proxy (NPM) | Inner nginx can't terminate TLS when NPM owns port 443 for domain routing |
| 2 | How is mode selected? | Two separate compose files | Explicit naming forces developer awareness |
| 3 | Nginx config organization? | Separate directories per mode | Zero overlap, each mode self-contained |
| 4 | Inner nginx role in behind-proxy? | Kept as internal gateway | Preserves rate limiting, security headers, routing logic |
| 5 | Developer picks compose file how? | Symlink `docker-compose.yml` → chosen file | One-time setup, all commands work after |
| 6 | Domain awareness in behind-proxy? | Two NPM proxy hosts, inner nginx uses `$http_host` | Preserves server-block separation from standalone |
| 7 | `.env` strategy? | Single `.env`, standalone vars optional | `UI_DOMAIN`/`API_DOMAIN` only needed for standalone |

---

## File Structure

### After Implementation

```
docker/
  docker-compose.standalone.yml      # NEW — standalone mode
  docker-compose.behind-proxy.yml    # NEW — behind-proxy mode
  docker-compose.yml                 # SYMLINK — developer creates pointing to chosen mode
  Dockerfile                         # UPDATED — entrypoint script for database permission fix
  Dockerfile.nginx                   # NEW — nginx:1.27-alpine + curl for healthcheck
  entrypoint.sh                      # NEW — fixes /app/databases ownership on startup
  .env.example                       # UPDATED — mode-specific variable documentation
  init-letsencrypt.sh                # UNCHANGED — standalone-only, documented
  nginx-ui-server.conf               # UNCHANGED — shared internal static file server
  nginx-standalone/                  # NEW — migrated from docker/ root
    nginx.conf                       # (from current docker/nginx.conf)
    proxy.conf.template              # (from current docker/nginx-proxy-custom.conf.template)
    default.conf                     # NEW — empty, neutralizes nginx image default server block
  nginx-behind-proxy/                # NEW — HTTP-only configs
    nginx.conf                       # HTTP-only global config
    proxy.conf                       # Unified server block (UI + API via location matching)
    default.conf                     # NEW — empty, neutralizes nginx image default server block
  configs/                           # UNCHANGED
  databases/                         # UNCHANGED
```

### Files Removed from `docker/` Root

- `docker/nginx.conf` → migrated to `docker/nginx-standalone/nginx.conf`
- `docker/nginx-proxy-custom.conf.template` → migrated to `docker/nginx-standalone/proxy.conf.template`

---

## Compose File Specifications

### `docker-compose.standalone.yml`

Identical to the current `docker-compose.yml` with nginx volume paths updated to reference `nginx-standalone/`.

**Services:**

| Service | Image | Ports | Key Volumes |
|---------|-------|-------|-------------|
| `backend` | `firecalc-${FIRECALC_ENV}-payments:latest` (built from Dockerfile) | `8181` (expose, internal) | `./configs:/app/configs:ro`, `./databases:/app/databases:rw` |
| `ui-server` | `nginx:alpine` | none (internal) | `../web/dist-app:/usr/share/nginx/html:ro`, `./nginx-ui-server.conf:/etc/nginx/conf.d/default.conf:ro` |
| `nginx` | built from `Dockerfile.nginx` | `80:80`, `443:443` | `./nginx-standalone/nginx.conf:/etc/nginx/nginx.conf:ro`, `./nginx-standalone/proxy.conf.template:/etc/nginx/templates/nginx-proxy-custom.conf.template:ro`, `./nginx-standalone/default.conf:/etc/nginx/conf.d/default.conf:ro`, `letsencrypt-certs:/etc/letsencrypt:ro`, `certbot-webroot:/var/www/certbot:ro` |
| `certbot` | `certbot/certbot:v2.11.0` | none | `letsencrypt-certs:/etc/letsencrypt`, `certbot-webroot:/var/www/certbot` |

**Container names:** `firecalc-${FIRECALC_ENV}-{service}` (e.g. `firecalc-staging-nginx`)

**Networks:** `firecalc` (bridge — Compose namespaces by project directory)

**Volumes:** `letsencrypt-certs` (local), `certbot-webroot` (local)

**Environment (nginx):** `UI_DOMAIN`, `API_DOMAIN`, `NGINX_ENVSUBST_FILTER=^(UI_DOMAIN|API_DOMAIN)$`

**Healthcheck (nginx):** `curl -f http://localhost/healthcheck` (proxied to backend `/v1/healthcheck`)

**depends_on:** `nginx` depends on `ui-server` (service_started) and `backend` (service_healthy)

### `docker-compose.behind-proxy.yml`

Same services except `certbot` is removed. Nginx has no host ports.

**Services:**

| Service | Image | Ports | Key Volumes |
|---------|-------|-------|-------------|
| `backend` | `firecalc-${FIRECALC_ENV}-payments:latest` (built from Dockerfile) | `8181` (expose, internal) | `./configs:/app/configs:ro`, `./databases:/app/databases:rw` |
| `ui-server` | `nginx:alpine` | none (internal) | `../web/dist-app:/usr/share/nginx/html:ro`, `./nginx-ui-server.conf:/etc/nginx/conf.d/default.conf:ro` |
| `nginx` | built from `Dockerfile.nginx` | none (Docker network only, exposes 80 internally) | `./nginx-behind-proxy/nginx.conf:/etc/nginx/nginx.conf:ro`, `./nginx-behind-proxy/proxy.conf:/etc/nginx/conf.d/proxy.conf:ro`, `./nginx-behind-proxy/default.conf:/etc/nginx/conf.d/default.conf:ro` |

**Container names:** `firecalc-${FIRECALC_ENV}-{service}` (e.g. `firecalc-staging-nginx`)

**Networks:** `firecalc` (bridge — Compose namespaces by project directory), `proxy` (external — shared with NPM)

**Volumes:** none (no letsencrypt volumes)

**Environment (nginx):** none (no envsubst, no domain variables)

**Healthcheck (nginx):** `curl -f http://localhost/healthcheck` (proxied to backend `/v1/healthcheck`)

**depends_on:** `nginx` depends on `ui-server` (service_started) and `backend` (service_healthy)

---

## Nginx Configuration Specifications

### `nginx-standalone/nginx.conf`

Migrated from current `docker/nginx.conf` with no changes. Contains:

- Worker processes, error log, PID
- Events block (1024 connections)
- HTTP block: mime types, logging, sendfile, tcp_nopush, keepalive, gzip
- SSL defaults: TLSv1.2+1.3, strong ciphers, session cache, OCSP stapling
- Rate-limit zones: `api_auth` (10r/s), `api_intent` (2r/s)
- `include /etc/nginx/conf.d/*.conf`

### `nginx-standalone/proxy.conf.template`

Migrated from current `docker/nginx-proxy-custom.conf.template` with no changes. Processed by nginx envsubst. Contains:

- Upstreams: `ui_upstream` → `ui-server:80`, `api_upstream` → `backend:8181`, `api_upstream_http` → `backend:8181` (for healthcheck bypass)
- UI server block: HTTPS on 443, SSL certs, HSTS, ACME challenge, proxy to `ui_upstream`
- API server block: HTTPS on 443, SSL certs, HSTS, ACME challenge, rate-limited auth endpoints, CSP, `client_max_body_size 50m`, proxy to `api_upstream`
- HTTP→HTTPS redirect server block on port 80 with `/healthcheck` location proxying to `api_upstream_http` (bypasses redirect for Docker healthcheck)

### `nginx-behind-proxy/nginx.conf`

HTTP-only global config. **No SSL defaults.**

```nginx
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    server_tokens off;

    # Logging
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent"';
    access_log /var/log/nginx/access.log main;

    # Performance
    sendfile on;
    tcp_nopush on;
    keepalive_timeout 65;
    gzip on;

    # Rate Limiting Zones
    limit_req_zone $binary_remote_addr zone=api_auth:10m rate=10r/s;
    limit_req_zone $binary_remote_addr zone=api_intent:10m rate=2r/s;

    # Include server blocks
    include /etc/nginx/conf.d/*.conf;
}
```

### `nginx-behind-proxy/proxy.conf`

Unified server block handling both UI and API traffic via location matching.
Single `server_name _` on `listen 80 default_server`. UI routes (`/`, `/fr/`,
`/en/`, `/app/`, `/_next/static/`, `/assets/`) proxy to `ui_upstream`. API
routes (`/v1/purchase/*`, default `/`) proxy to `api_upstream`. Dedicated
`/healthcheck` location proxies to `api_upstream` for Docker healthcheck.

**Note:** Separate server blocks for UI and API were rejected because nginx
only uses the first `server_name _` it encounters (alphabetically by conf.d/
filename). A single server block with location-based routing is the correct
approach.

### `nginx-standalone/default.conf` and `nginx-behind-proxy/default.conf`

Both files are intentionally empty (header comment only). They override the
nginx image's built-in `default.conf` which has a `server_name _` server block
serving static files from `/usr/share/nginx/html/`. Without the override, the
default server block catches `localhost` requests before our proxy config,
breaking the healthcheck.

```nginx
upstream ui_upstream {
    server ui-server:80;
}

upstream api_upstream {
    server backend:8181;
}

server {
    listen 80 default_server;
    server_name _;

    client_max_body_size 50m;

    # --- Landing page routes (UI) ---
    location = / {
        return 302 /fr/;
    }

    location ~ ^/(en|fr)/$ {
        # UI security headers + proxy to ui_upstream
        add_header X-Frame-Options "SAMEORIGIN" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-XSS-Protection "1; mode=block" always;
        add_header Referrer-Policy "no-referrer-when-downgrade" always;
        add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
        add_header Cache-Control "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0" always;
        proxy_pass http://ui_upstream;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # --- Next.js static assets (UI) ---
    location /_next/static/ {
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        proxy_pass http://ui_upstream;
        # ... proxy headers ...
    }

    location /assets/ {
        add_header Cache-Control "public, max-age=1209600, immutable" always;
        proxy_pass http://ui_upstream;
        # ... proxy headers ...
    }

    # --- SPA at /app (UI) ---
    location = /app { return 301 /app/; }
    location = /app/ {
        # UI security headers + proxy to ui_upstream
    }
    location ~* ^/app/.*\.(js|mjs)$ { /* proxy to ui_upstream */ }
    location ~* ^/app/.*\.(css|woff|woff2|ttf|otf|eot|svg|png|jpg|jpeg|gif|ico|webp)$ { /* proxy to ui_upstream */ }
    location ~* \.map$ { return 404; }
    location = /app/version.json { /* proxy to ui_upstream */ }

    # --- Rate-limited API auth endpoints ---
    location = /v1/purchase/verify-and-process {
        limit_req zone=api_auth burst=5 nodelay;
        limit_req_status 429;
        # API security headers + proxy to api_upstream
    }

    location = /v1/purchase/create-intent {
        limit_req zone=api_intent burst=3 nodelay;
        limit_req_status 429;
        # API security headers + proxy to api_upstream
    }

    # --- Default API proxy ---
    location / {
        # API security headers + proxy to api_upstream
    }
}
```

---

## `.env.example` Updates

Mark `UI_DOMAIN` and `API_DOMAIN` as standalone-only. Add mode documentation at the top:

```bash
# =============================================================================
# Deployment Mode
# =============================================================================
# Choose ONE mode by creating a symlink:
#   Standalone (self-contained, nginx handles TLS):
#     cd docker && ln -s docker-compose.standalone.yml docker-compose.yml
#   Behind-proxy (external reverse proxy handles TLS):
#     cd docker && ln -s docker-compose.behind-proxy.yml docker-compose.yml
# =============================================================================

# -----------------------------------------------------------------------------
# Standalone Mode (REQUIRED only for docker-compose.standalone.yml)
# -----------------------------------------------------------------------------
UI_DOMAIN=firecalc.example.com
API_DOMAIN=api.example.com
LETSENCRYPT_EMAIL=admin@example.com
LETSENCRYPT_STAGING=true

# -----------------------------------------------------------------------------
# Shared (REQUIRED for both modes)
# -----------------------------------------------------------------------------
FIRECALC_ENV=staging
# ... rest of shared variables unchanged ...
```

---

## Makefile Changes

### New Targets

```makefile
## ================================
## DOCKER DEPLOYMENT — MODE SETUP
## ================================

docker-setup-standalone:
	@echo "Setting up Docker for standalone mode..."
	@cd docker && rm -f docker-compose.yml && ln -s docker-compose.standalone.yml docker-compose.yml
	@echo "✅ Symlink: docker-compose.yml → docker-compose.standalone.yml"

docker-setup-behind-proxy:
	@echo "Setting up Docker for behind-proxy mode..."
	@cd docker && rm -f docker-compose.yml && ln -s docker-compose.behind-proxy.yml docker-compose.yml
	@echo "✅ Symlink: docker-compose.yml → docker-compose.behind-proxy.yml"
```

### Modified Targets

All existing deploy targets (`prod-docker-deploy-up`, `staging-docker-deploy-up`, `dev-docker-deploy-up`, `docker-deploy-down`, `docker-deploy-restart`, `docker-deploy-logs`) gain a symlink validation step:

```makefile
# Example for prod-docker-deploy-up:
prod-docker-deploy-up:
	@echo "Deploying to Docker (production)..."
	@test -L docker/docker-compose.yml || (echo "ERROR: docker-compose.yml symlink not found. Run 'make docker-setup-standalone' or 'make docker-setup-behind-proxy' first." && exit 1)
	@make prod-backend-build
	@cd docker && docker compose down && docker compose up -d --build
	@echo "✅ Deployment complete!"
```

---

## NPM Configuration (Behind-Proxy Mode)

Developer configures manually in nginx-proxy-manager:

### Proxy Host 1 — UI

- **Domain:** `UI_DOMAIN` (e.g., `firecalc.example.com`)
- **Forward Port:** `80`
- **Forward Host:** Docker network container name or host IP with mapped port
- **SSL Certificate:** Let's Encrypt (auto-requested by NPM)
- **Block Commmons Exploits:** Enabled (NPM default)
- **Access List:** As needed

### Proxy Host 2 — API

- **Domain:** `API_DOMAIN` (e.g., `api.example.com`)
- **Forward Port:** `80`
- **Forward Host:** Same as UI (both hit inner nginx which routes internally)
- **SSL Certificate:** Let's Encrypt (auto-requested by NPM)

**Note:** Both NPM proxy hosts forward to the same inner nginx container. The inner nginx uses a single unified `proxy.conf` with location-based routing: UI paths (`/`, `/app/`, `/fr/`, `/en/`) are proxied to `ui_upstream`, while API paths (`/v1/*`) are proxied to `api_upstream`.

---

## Migration Steps

1. Migrate `docker/nginx.conf` → `docker/nginx-standalone/nginx.conf`
2. Migrate `docker/nginx-proxy-custom.conf.template` → `docker/nginx-standalone/proxy.conf.template`
3. Update `docker-compose.standalone.yml` nginx volumes to reference `nginx-standalone/`
4. Create `docker-compose.standalone.yml` (from current `docker-compose.yml`)
5. Create `docker-compose.behind-proxy.yml`
6. Create `nginx-behind-proxy/` configs
7. Update `.env.example`
8. Update Makefile
9. Remove old `docker/nginx.conf` and `docker/nginx-proxy-custom.conf.template` from root
10. Remove old `docker/docker-compose.yml` (developer recreates via symlink)

---

## Verification

### Standalone Mode

```bash
make docker-setup-standalone
make prod-docker-deploy-up
curl -I https://${UI_DOMAIN}/           # → 200, HTTPS
curl -I https://${API_DOMAIN}/v1/healthcheck  # → 200, HTTPS
```

### Behind-Proxy Mode

```bash
make docker-setup-behind-proxy
make prod-docker-deploy-up
# Configure NPM proxy hosts
curl -I https://${UI_DOMAIN}/           # → 200, HTTPS (via NPM)
curl -I https://${API_DOMAIN}/v1/healthcheck  # → 200, HTTPS (via NPM)
```
---

## Operational Notes

### Database Permission Fix (Entrypoint)

The backend container's entrypoint script (`docker/entrypoint.sh`) runs as root
before starting the application. On each start it:
1. Fixes ownership recursively (`chown -R appuser:appuser /app/databases`)
2. Secures directories to `700` and files to `600` (owner-only access)
3. Drops privileges to `appuser` via `setpriv`

This means `sudo docker compose up` works without manual permission fixes —
the entrypoint handles it automatically.
If you ever need to fix permissions on the host (e.g., after manually creating
the database directory), run:

```bash
make docker-fix-db-perms
```

### Nginx Healthcheck

Nginx uses a curl-based healthcheck that verifies backend health through the
proxy: `curl -f http://localhost/healthcheck`. The `/healthcheck` location in
both proxy configs forwards to `backend:8181/v1/healthcheck`.

**Why not `nginx -t`?** Config syntax check doesn't verify upstream connectivity.

**Why not `curl http://localhost/`?** In standalone mode, the port 80 server
block redirects HTTP→HTTPS. The `/healthcheck` endpoint bypasses this redirect
in standalone mode (proxied to `api_upstream_http` in the port 80 block).

**Why override `default.conf`?** The nginx image ships a default server block
in `conf.d/default.conf` with `server_name _` that catches localhost requests
before our proxy config. Mounting an empty `default.conf` neutralizes it.

