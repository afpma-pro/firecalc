# Implementation Plan: SEC-007 — Replace Unmaintained TLS Proxy Image

## Finding Summary

**Severity**: High (CVSS 7.4)

The TLS proxy uses `danieldent/nginx-ssl-proxy:latest` — a personal/community Docker image with no maintenance guarantees. The `:latest` tag is unpinned. This image terminates all HTTPS traffic for both the UI and API domains, making it the most security-critical component in the stack.

## Current State

```yaml
# docker-compose.yml:133-197
nginx-ssl-proxy:
    image: danieldent/nginx-ssl-proxy:latest  # Unpinned, unmaintained
    ports:
        - "80:80"
        - "443:443"
    environment:
        - DOMAIN=${UI_DOMAIN} ${API_DOMAIN}
    volumes:
        - letsencrypt-certs:/etc/letsencrypt
        - ./nginx-proxy-custom.conf:/etc/nginx/conf.d/custom.conf:ro
        - ./nginx-ui-server.conf:/etc/nginx/conf.d/nginx-ui-server.conf:ro
    entrypoint: /bin/bash
    command: -c "..."  # Custom entrypoint that overwrites default.conf
```

## Target State

- Official, actively maintained nginx image with pinned version
- Certbot sidecar for Let's Encrypt certificate management
- HSTS headers enabled (SEC-009)
- All existing custom nginx configs preserved
- Same external behavior (ports 80/443, automatic HTTPS)

## Implementation Steps

### Step 1: Replace with Official nginx + Certbot

**Files to modify**: `docker/docker-compose.yml`

Replace the single `nginx-ssl-proxy` service with two services:

```yaml
nginx:
    image: nginx:1.27-alpine  # Official, pinned to minor version
    container_name: firecalc-nginx
    restart: unless-stopped
    ports:
        - "80:80"
        - "443:443"
    volumes:
        - ./nginx.conf:/etc/nginx/nginx.conf:ro
        - ./nginx-proxy-custom.conf:/etc/nginx/conf.d/custom.conf:ro
        - ./nginx-ui-server.conf:/etc/nginx/conf.d/nginx-ui-server.conf:ro
        - letsencrypt-certs:/etc/letsencrypt:ro
        - certbot-webroot:/var/www/certbot:ro
    depends_on:
        - ui-server
        - api
    healthcheck:
        test: ["CMD", "nginx", "-t"]
        interval: 30s
        timeout: 5s
        retries: 3
    logging:
        driver: "json-file"
        options:
            max-size: "10m"
            max-file: "30"

certbot:
    image: certbot/certbot:v2.11.0  # Pinned version
    container_name: firecalc-certbot
    volumes:
        - letsencrypt-certs:/etc/letsencrypt
        - certbot-webroot:/var/www/certbot
    # Run certificate renewal check every 12 hours
    entrypoint: /bin/sh -c 'trap exit TERM; while :; do certbot renew --webroot -w /var/www/certbot --quiet; sleep 12h; done'

volumes:
    letsencrypt-certs:
    certbot-webroot:
```

### Step 2: Create Main nginx.conf

**Files to create**: `docker/nginx.conf`

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

    # SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;
    ssl_stapling on;
    ssl_stapling_verify on;

    # Rate limiting zones
    limit_req_zone $binary_remote_addr zone=api_auth:10m rate=10r/s;
    limit_req_zone $binary_remote_addr zone=api_intent:10m rate=2r/s;

    # Include server blocks
    include /etc/nginx/conf.d/*.conf;
}
```

### Step 3: Update nginx-proxy-custom.conf

**Files to modify**: `docker/nginx-proxy-custom.conf`

Add HSTS headers (SEC-009), certbot challenge location, and rate limiting:

```nginx
# Add to both HTTPS server blocks:
add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;

# Add certbot challenge location to HTTP server block:
location /.well-known/acme-challenge/ {
    root /var/www/certbot;
}

# Add rate limiting to API locations (SEC-003):
location = /v1/purchase/verify-and-process {
    limit_req zone=api_auth burst=5 nodelay;
    limit_req_status 429;
    proxy_pass http://api_upstream;
}

location = /v1/purchase/create-intent {
    limit_req zone=api_intent burst=3 nodelay;
    limit_req_status 429;
    proxy_pass http://api_upstream;
}
```

### Step 4: Initial Certificate Provisioning Script

**Files to create**: `docker/scripts/init-letsencrypt.sh`

```bash
#!/bin/bash
# Initial Let's Encrypt certificate provisioning
# Run once before first deployment

set -e

source ../.env

echo "Requesting certificates for: $UI_DOMAIN $API_DOMAIN"

docker compose run --rm certbot certonly \
    --webroot \
    -w /var/www/certbot \
    -d "$UI_DOMAIN" \
    -d "$API_DOMAIN" \
    --email "$LETSENCRYPT_EMAIL" \
    --agree-tos \
    --no-eff-email

echo "Certificates obtained. Reloading nginx..."
docker compose exec nginx nginx -s reload
```

### Step 5: Update Deployment Documentation

**Files to modify**: `docs/dev/guides/PRODUCTION.md`, `docs/dev/guides/STAGING.md`

Document the new two-service architecture, initial certificate provisioning, and certificate renewal monitoring.

## Dependencies

- DNS must be configured for both domains before certificate provisioning
- Existing Let's Encrypt certificates in the `letsencrypt-certs` volume are compatible
- SEC-009 (HSTS headers) is addressed as part of this plan

## Testing Plan

1. **Local test**: Run `docker compose up` with self-signed certificates. Verify nginx starts and proxies correctly.
2. **Staging test**: Deploy to staging. Run `init-letsencrypt.sh`. Verify HTTPS works with valid certificates.
3. **Certificate renewal test**: Verify `certbot renew --dry-run` succeeds.
4. **Header test**: `curl -I https://<domain>/` — verify HSTS, X-Frame-Options, X-Content-Type-Options headers present.
5. **SSL test**: Run SSL Labs test (ssllabs.com/ssltest/) against the domain to verify TLS configuration.

## Migration Notes

- **One-time migration**: Existing `letsencrypt-certs` volume is preserved. The certbot image can read existing certificates.
- **Downtime**: Brief downtime during the container swap (`docker compose down && docker compose up -d`). The nginx + certbot containers start quickly.
- **Rollback**: Keep the old `docker-compose.yml` as `docker-compose.yml.bak` for emergency rollback.
- The custom entrypoint hack (current `command: -c "..."`) is no longer needed with the official nginx image since configs are mounted directly.

## Estimated Effort

**T-shirt size**: M (Medium)
**Priority**: P1 — important for supply chain security, but not actively exploitable unless the upstream image is compromised.
