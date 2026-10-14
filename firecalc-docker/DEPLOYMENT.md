# FireCalc Docker Deployment Guide

## Deployment Modes

FireCalc supports four deployment configurations:

| Mode | Calculation Server | TLS Termination | Compose File |
|------|-------------------|-----------------|--------------|
| Standalone (with calc) | Yes | Inner nginx (Let's Encrypt) | `docker-compose.standalone.yml` |
| Standalone (no calc) | No | Inner nginx (Let's Encrypt) | `docker-compose.standalone.no-calc.yml` |
| Behind-Proxy (with calc) | Yes | External proxy | `docker-compose.behind-proxy.yml` |
| Behind-Proxy (no calc) | No | External proxy | `docker-compose.behind-proxy.no-calc.yml` |

**Supported posture: self-host the calc backend over the HTTP seam** (the
with-calc variants above). "Calculation Server: No" does **not** mean
calculations happen locally — the public repository ships no Austrian engine
source, so `Ecolabeled` and `Door15aCatalog` have no in-process calculator at
all: a no-calc deployment simply has no calculation seam for those two kinds.

- The four non-Austrian kinds (`traditional`, `afpma-prse`, `single-tested`,
  `custom-for-lab`) still compute in-process as before.
- The two Austrian kinds are unavailable: the UI has no browser-side engine for
  them (they are registered as folded-only carriers with no constraint algebra,
  so a local run yields degenerate results, never a real calculation) and
  payments PDF generation fails with
  `Austrian firebox detected but remote calculation service is not configured`.

The two supported configurations:

1. **Self-host the calc backend** — the approved posture (with-calc variants).
   Set `CALC_SERVER=true` (Makefile) so `FIRECALC_MODE=with-calc`, and run the
   `calc-server` container on the same host: it listens on port 9000 and nginx
   proxies `GET /v1/kinds` and `POST /v1/calculate` under the API domain's `/v1`
   prefix. Point the UI at it with `VITE_FIREBOX_REMOTE_KINDS=ecolabeled,door15a-catalog`
   and `VITE_FIREBOX_CALC_BASE_URL=https://<api-domain>/v1`, and the payments
   backend at it with `calculation-client.base-uri` (e.g. `http://calc-server:9000/v1`).
   The calc-server assembly JAR is built in the private (non-AGPL) repository —
   `scripts/deploy.sh` builds it from the ref pinned in `VERSIONS` (clone mode)
   or from a local checkout linked via `FIRECALC_BUILD_LOCAL=true` +
   `FIREBOX_LOCAL_DIR` (rsync-based hosts, no git credentials), and places
   `artifacts/firecalc-calc-server-assembly.jar` in the Docker build context.
2. **Disable the Austrian kinds** when no calc backend is available:
   `firebox-availability.ecolabeled = false` and
   `firebox-availability.door15a-catalog = false` in the payments HOCON config,
   plus `VITE_FIREBOX_AVAIL_ECOLABELED=false` and
   `VITE_FIREBOX_AVAIL_DOOR15A_CATALOG=false` in the UI environment. Explicit
   unavailability is honest; a missing calc backend is not a local fallback.

### Standalone Mode

Self-contained deployment with inner nginx handling TLS termination and Let's Encrypt certificate management. Exposes ports 80 and 443 on the host.

**Environment variables** (`.env` file):
- `FIRECALC_ENV` — Environment name (staging, production, etc.)
- `UI_DOMAIN` — UI domain name (e.g., `firecalc.example.com`)
- `API_DOMAIN` — API domain name (e.g., `api.example.com`)
- `LETSENCRYPT_EMAIL` — Email for Let's Encrypt notifications
- `LETSENCRYPT_STAGING` — `true` for staging certs, `false` for production

### Behind-Proxy Mode

Inner nginx serves plain HTTP on the Docker network only — no host ports exposed. An external reverse proxy (e.g., nginx-proxy-manager) handles TLS termination and domain routing.

**Environment variables** (`.env` file):
- `FIRECALC_ENV` — Environment name (staging, production, etc.)

> Note: `UI_DOMAIN` and `API_DOMAIN` are NOT needed in behind-proxy mode. The outer proxy handles domain routing and TLS.

## Quick Start

1. Create a symlink to activate your desired mode:
   ```bash
   cd firecalc-docker
   ln -s docker-compose.standalone.yml docker-compose.yml
   ```

2. Create a `.env` file with the required environment variables.

3. Build and start:
   ```bash
   docker compose build
   docker compose up -d
   ```

## Behind-Proxy Setup

When using behind-proxy mode with an external reverse proxy (e.g., nginx-proxy-manager):

### 1. Outer Proxy Configuration

Create two proxy hosts forwarding to the inner nginx container:

**Proxy Host 1 — UI:**
- Domain: your UI domain (e.g., `firecalc.example.com`)
- Forward Host: nginx container on Docker network (or host IP)
- Forward Port: 80
- SSL: Let's Encrypt (auto-requested by NPM)

**Proxy Host 2 — API:**
- Domain: your API domain (e.g., `api.example.com`)
- Forward Host: same as UI (both hit inner nginx)
- Forward Port: 80
- SSL: Let's Encrypt (auto-requested by NPM)

### 2. Docker Network Connectivity

The outer proxy must reach the inner nginx container. Before starting the stack,
create the external `proxy` network that the behind-proxy compose files reference:

```bash
# The network name follows Docker Compose project naming:
# <compose-project-name>-proxy (default project name = directory name)
docker network create firecalc-docker-proxy
```

Then connect your outer proxy container (e.g., nginx-proxy-manager) to this network:

```bash
docker network connect firecalc-docker-proxy <outer-proxy-container>
```

The behind-proxy compose files declare `proxy: external: true`, so Docker Compose
will use the pre-existing network. The inner nginx container joins it automatically.

### 3. Pre-deployment Checklist

- Build images: `docker compose build`
- Create `configs/` directory with necessary YAML/CONF files
- Ensure `databases/` directory exists
- Review and update `.env` file with production values
- Configure outer proxy proxy hosts

### 4. Monitoring and Maintenance

- Check service health: `docker compose ps`
- View logs: `docker compose logs -f [service_name]`
- Restart services: `docker compose restart [service_name]`

## Nginx Configuration Architecture

The nginx configuration uses a template-based approach with shared configuration files:

```
nginx/
├── entrypoint.sh              # Startup script (envsubst processing)
├── shared/                    # Shared configuration fragments
│   ├── common.conf            # Global preamble, logging, upstreams
│   ├── default-server.conf       # Port 80 server (unified)
│   ├── https-servers.conf        # Port 443 servers (unified)
│   ├── calc-endpoints.conf       # HTTP calc API locations
│   ├── calc-https-endpoints.conf # HTTPS calc API locations
│   ├── calc-pre-server.conf      # HTTP calc pre-server variables
│   ├── calc-healthcheck.conf     # Calc healthcheck endpoint
│   └── cors-options.conf         # Shared CORS preflight response
├── templates/                 # Template files (thin include wrappers)
│   ├── standalone.tpl
│   ├── standalone.no-calc.tpl
│   ├── behind-proxy.tpl
│   └── behind-proxy.no-calc.tpl
└── *.conf                     # Generated configs (for reference only)
```

### How It Works

1. At container startup, `entrypoint.sh` copies the template and shared files to a writable directory.
2. `envsubst` substitutes domain placeholders (`${UI_DOMAIN}`, `${API_DOMAIN}`) in the generated files.
3. Include paths are updated to point to the generated shared files.
4. Nginx starts with the generated configuration.

The `shared/` files are the source of truth. Templates are thin include wrappers that compose the shared fragments into a complete configuration.
