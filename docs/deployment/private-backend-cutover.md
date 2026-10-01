<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
-->

# Private Calculation Backend — Deployment Cutover

Status: cutover complete on branch `firebox-api` (ticket 21, Phase 3 of the
IP-isolation program, recorded in the private repository).

This document records the deployed state, routing, versioning, health-check
and rate-limit setup, failure behavior, and rollback procedure for running
both public user workflows (Ecolabeled, Door15aCatalog) against the
independently versioned **private** calculation backend over HTTP only.

---

## 1. Cutover state

The public repository no longer contains any Austrian calculation code
(tickets 16–20). Both proprietary firebox kinds are calculated exclusively by
the standalone private backend:

- **Private backend** — `firebox_calc_server` in the private (non-AGPL,
  access-restricted) repository. Serves exactly `GET /v1/kinds` and
  `POST /v1/calculate` plus the operational `GET /health` endpoint, on port
  9000. Strict-backed registry only; no MCE-serving HTTP registration.
- **Public UI** — JS/fetch client adapter (`firebox_calc_client_js`);
  `VITE_FIREBOX_REMOTE_KINDS=ecolabeled,door15a-catalog` with
  `VITE_FIREBOX_CALC_BASE_URL` pointing at the API domain's `/v1` prefix.
- **Public payments** — JVM/http4s client adapter (`firebox_calc_client`);
  `calculation-client.base-uri` in the payments HOCON configuration points at
  the calc backend (`http://calc-backend:9000/v1` inside Docker).

The only calculation seam between the two repositories is the normative HTTP
interface. No public artifact links private code; the private server links no
AGPL artifact (14c dependency-closure audit, recorded in the private
repository).

### 1.1 Deployment postures

- **Self-hosted calc backend — the supported posture.** Both Austrian kinds
  reach their calculation exclusively over the HTTP seam: UI
  `VITE_FIREBOX_REMOTE_KINDS=ecolabeled,door15a-catalog` plus
  `VITE_FIREBOX_CALC_BASE_URL=https://<api-domain>/v1`; payments
  `calculation-client.base-uri`; compose `CALC_SERVER=true` (with-calc
  files; containers run `FIRECALC_MODE=with-calc`). There is no per-kind
  local switch on either side: no browser source toggle, and no JVM-side
  `firebox-engine-source` HOCON block — it has been removed from every
  shipped config and never had a parser; payments routes Austrian kinds to
  the calc backend unconditionally by DTO subtype. The public registry
  carries the two kinds only as constraint-free `foldedOnly` entries, so a
  "local" resolution for them is a misconfiguration, never a fallback.
- **Calc-free deploy.** With `CALC_SERVER` unset (the `.no-calc` compose
  variants, `FIRECALC_MODE=no-calc`) the two Austrian kinds MUST be
  disabled, not left to compute "locally": UI
  `VITE_FIREBOX_AVAIL_ECOLABELED=false` and
  `VITE_FIREBOX_AVAIL_DOOR15A_CATALOG=false` (plus the `..._BACKEND`
  variants), payments HOCON `firebox-availability.ecolabeled = false` and
  `firebox-availability.door15a-catalog = false`. The shipped staging
  payments config (`docker/configs/staging/payments/payments-config.conf`)
  already follows exactly this pattern. That is honest unavailability;
  leaving the remote-calc vars unset instead resolves both kinds to
  `Local` and yields degenerate empty results.

## 2. Routing configuration

### 2.1 Compose services (both modes)

`docker/docker-compose.standalone.yml` and `docker/docker-compose.behind-proxy.yml`
(selected when `CALC_SERVER=true`; the `.no-calc` variants omit this
service entirely) each define the calculation backend as an independent service:

| Service | Image | Port | Volumes | Healthcheck |
|---|---|---|---|---|
| `backend` (payments) | `firecalc-${FIRECALC_ENV}-payments:latest` | `8181` (internal) | `./configs:/app/configs:ro`, `./databases:/app/databases:rw` | `curl -f http://localhost:8181/v1/healthcheck` |
| `calc-backend` | `firecalc-${FIRECALC_ENV}-calc-server:latest` | `9000` (internal) | `./configs:/app/configs:ro` only — no database mounts | `curl -f http://localhost:9000/health` |

`calc-backend` is built from `docker/Dockerfile.calc-server` (JRE-only base,
`eclipse-temurin:21-jre-jammy`, non-root user). The `COPY` instruction expects
the private assembly jar at
`modules/firebox_calc_server/target/scala-3.9.0/firecalc-calc-server-assembly.jar`
**relative to the build context** — the build context must be the private
checkout (see §6 deployment procedure). The container does not inherit
payments database mounts, payments-only runtime dependencies, or any
dependency on payments artifacts.

### 2.2 Nginx routing (both modes)

The reverse proxy routes public applications to the calc backend through
**exactly** the two normative operations; every unrelated payments route still
reaches payments:

```nginx
upstream calc_upstream { server calc-backend:9000; }

location = /v1/calculate { ... proxy_pass http://calc_upstream; }   # POST
location = /v1/kinds     { ... proxy_pass http://calc_upstream; }   # GET
location = /calc-healthcheck { proxy_pass http://calc_upstream/health; }  # health gate only
```

- `location = /v1/calculate` and `location = /v1/kinds` are exact matches —
  no other path reaches `calc_upstream`.
- Payments routes (`/v1/purchase/*`, `/v1/payment_complete`,
  `/v1/payment_cancelled`, `/v1/healthcheck`, catch-all `/`) proxy to
  `api_upstream` (`backend:8181`) unchanged.
- Files: `docker/nginx-standalone/proxy.conf.template` (standalone, envsubst
  for `UI_DOMAIN`/`API_DOMAIN`) and `docker/nginx-behind-proxy/proxy.conf`
  (behind-proxy, no envsubst).

### 2.3 UI routing

The browser calls the calc backend cross-origin on the API domain. The base
URL is the API domain's `/v1` prefix (the same origin as
`VITE_BACKEND_HOST`), and the client appends `/calculate` and `/kinds`:

| Environment | File | `VITE_FIREBOX_CALC_BASE_URL` |
|---|---|---|
| development | `modules/ui/.env.development` | `http://localhost:9000/v1` (direct) |
| staging | `modules/ui/.env.staging` | `https://api.staging.firecalc.example.com/v1` |
| production | `modules/ui/.env.production` | `https://api.firecalc.example.com/v1` |

`VITE_FIREBOX_REMOTE_KINDS=ecolabeled,door15a-catalog` in all three. The
deployed UI CSP (`modules/ui/vite.config.js`) allows `connect-src` to the
backend origin built from `VITE_BACKEND_*` plus the origin of
`VITE_FIREBOX_CALC_BASE_URL` (path prefix stripped), so both a root-path base
URL on the backend origin and a dedicated calc origin need no further CSP
change. Do not leave the base URL empty in deployed builds: Austrian kinds
have no browser-side calculation source (tickets 09/10 removed it).

### 2.4 Payments routing

`BackendMain` constructs `Http4sCalcClient` from
`paymentsConfig.calculationClientConfig.baseUri` and injects it into
`CalculationReportServiceImpl`. The HOCON block (parsed by
`ConfigLoader.loadCalculationClientConfig`):

```hocon
payments.<environment> {
  calculation-client {
    base-uri = "http://calc-backend:9000/v1"   # Docker: compose service DNS
  }
}
```

Omitting the block defaults `base-uri` to `http://localhost:9000/v1` — the
standalone backend's default bind. `calculation-client.base-uri` is the only
key the block has: there is no `enabled` switch and no per-kind engine-source
switch, an unreachable backend produces a typed `CalculationReportError`,
never a local calculation. Templates:
`configs/templates/payments/payments-config.conf.template` and
`docker/configs/staging/payments/payments-config.conf.template` / `.example`.

Payments uses the client for Austrian PDF data only; non-Austrian kinds never
touch it. There is no local Austrian fallback and no calculation-response
cache (ticket 06/18): if the backend is unavailable through the client's
timeout/retry policy, PDF generation fails explicitly with a typed
`CalculationReportError` surfaced as `ReportFileAccessException`.

### 2.5 CORS (browser cross-origin access)

The private backend deliberately ships no CORS middleware; nginx supplies the
headers at the two calc locations (ticket 21):

- `$firecalc_cors_origin` maps the request `Origin` to the allowed UI origin
  and nothing else. Standalone mode inserts `https://${UI_DOMAIN}` via
  envsubst; behind-proxy mode ships fail-closed (empty map — uncomment and set
  your UI origin in `docker/nginx-behind-proxy/proxy.conf`).
- `Access-Control-Allow-Origin` is emitted `always` (on success and error
  responses), with `Vary: Origin` and `Access-Control-Expose-Headers` for the
  rate-limit headers (`X-RateLimit-Limit/Remaining/Reset`, `Retry-After`).
- `OPTIONS` preflights are answered `204` at nginx with
  `Access-Control-Allow-Methods: GET, POST, OPTIONS`,
  `Access-Control-Allow-Headers: Content-Type, X-Firecalc-App-Key`, and
  `Access-Control-Max-Age: 86400`; they are never forwarded to the backend.
- An unmatched `Origin` maps to empty, so no CORS headers are emitted and the
  browser rejects the response — the failure is explicit, never silent.

## 3. Versioning scheme

| Component | Version source | Current value |
|---|---|---|
| Normative contract | `modules/firebox_calc_api/openapi/firebox-calc-api.yaml` `info.version` | `1.0.0` |
| API path version | `ApiVersion.v1` → URL prefix `/v1` | `v1` |
| Private server | `../firecalc-firebox-austrian-api/build.sbt` `engine_version` (independent repository) | `0.3.0-b25-SNAPSHOT` |
| Public `firebox_calc_api` / `firebox_calc_client` / `firebox_calc_client_js` | `build.sbt` `engine_version` | `0.3.0-b25-SNAPSHOT` |
| Public UI / payments / reports | `build.sbt` `*_base_version` + engine suffix | `0.9.0-b25-SNAPSHOT+engine-0.3.0-b25-SNAPSHOT` |

The private backend and the public clients are versioned **independently** in
separate repositories; the values happen to be aligned today but neither side
consumes the other's version. Compatibility is defined by the normative
contract, not by version equality:

- same paths (`GET /v1/kinds`, `POST /v1/calculate`),
- same wire shapes (the OpenAPI document; the private server uses its own
  relicensed copy of the wire types, not the public AGPL artifact),
- same rate-limit policy and headers (`X-RateLimit-Limit/Remaining/Reset` with
  Unix-epoch reset semantics, `Retry-After` on 429),
- same error taxonomy (`unknown_kind`, `kind_not_served`, `malformed_request`,
  `unsupported_locale`, `unsupported_api_version`, `unsupported_standard`,
  `calculation_failed`, `internal_error`, `rate_limited`, `payload_too_large`).
  `unsupported_standard` is the strict-only posture: a request whose
  `standard` is not `strict` is refused at the private calc-core checkpoint
  with HTTP 400 and an `ErrorResponseEnvelope` (shared-contract variant
  `FireboxCalcServerError.UnsupportedStandard`; additive under OpenAPI
  `info.version: 1.0.0`, ADR-0011).

Demonstrated compatibility evidence:

- `BlackBoxHttpContractSuite` (private repo, ticket 15): 11 tests over real
  HTTP with `java.net.http.HttpClient` — a third client implementation,
  proving any conforming HTTP client interoperates.
- `CrossRepositoryAcceptanceSuite` (public repo,
  `modules/firebox_calc_testkit`, ticket 17): public requests built only from
  public wire types and DTOs are accepted by the private server over real
  HTTP, and complete `AsResult` operating-point state returns through the wire
  and folds into the generic `RemoteBackedFireboxEngine`. Gated on
  `-Dfirecalc.private.server.uri` (canceled, never failing, when the private
  deployment is absent).

A wire-shape change requires an atomic OpenAPI update (SPEC_00 decision 3) and
re-running both suites before deploying a new private backend behind the same
routes.

## 4. Health checks and gating

| Layer | Mechanism |
|---|---|
| Container | `HEALTHCHECK` in `Dockerfile.calc-server`: `curl -f http://localhost:9000/health` (30s interval, 3 retries, 5s start period) |
| Compose | `calc-backend` healthcheck mirrors the container check; nginx `depends_on: calc-backend: condition: service_healthy` in both modes — the proxy admits no traffic before the calc backend is ready |
| Nginx | nginx's own healthcheck probes both `/healthcheck` (payments) and `/calc-healthcheck` (calc) independently, so calc readiness loss is observable separately from payments |
| Application | `GET /health` returns JSON `{status, endpoints}`; startup reporting prints bind address, served endpoints, rate limits, and app-key status, so a missing route is detectable before traffic is admitted |

Health is operational, not normative: `GET /health` is separate from the
calculation contract (SPEC_00 §Further Notes).

## 5. Rate limiting

Two layers, defense in depth. Nginx is the stricter first line; the
application token bucket is authoritative for the contract headers.
`set_real_ip_from` + `real_ip_recursive` restore the client address on the
inner nginx so its `$binary_remote_addr` zones are per-client; the restored
XFF (first entry = true client) is what the application bucket keys on.

| Endpoint | Nginx (`firebox_calc` zone) | Application token bucket |
|---|---|---|
| `POST /v1/calculate` | `rate=1r/s`, `burst=5 nodelay`, 429 on excess | capacity 20, refill 2/s |
| `GET /v1/kinds` | `rate=1r/s`, `burst=10 nodelay`, 429 on excess | capacity 10, refill 0.5/s |

Application keys are composite (client IP prefix — /24 IPv4, /64 IPv6, read
from `X-Forwarded-For` behind the proxy — plus optional `X-Firecalc-App-Key`).
Responses carry `X-RateLimit-Limit/Remaining/Reset` (reset is an absolute Unix
epoch) and 429s carry `Retry-After`. Since the extraction the application
defaults were doubled to 2x headroom for edit-driven recompute use (private
repo commit `c033a02`: calculate 10→20 burst at 60→120/min, kinds 5→10 burst
at 10→30/min); the nginx edge zones above remain the binding first line.

## 6. Deployment procedure

1. **Build the private assembly** (private checkout, never the public repo):
   `sbt --batch firebox_calc_server/assembly` →
   `modules/firebox_calc_server/target/scala-3.9.0/firecalc-calc-server-assembly.jar`.
2. **Build the calc image with the private checkout as build context** so the
   `COPY` path resolves, while the Dockerfile stays in the public repo:
   `docker build -f <public-checkout>/docker/Dockerfile.calc-server -t firecalc-${FIRECALC_ENV}-calc-server:latest <private-checkout>`
   (Compose's `context: ..` assumes the public checkout layout; for the calc
   image, build explicitly against the private checkout and let compose use the
   resulting image, or point a compose override's `build.context` at the
   private checkout.)
3. **Configure**: `docker/configs/<env>/payments/payments-config.conf` with
   `calculation-client.base-uri = "http://calc-backend:9000/v1"`; UI built
   from `modules/ui/.env.<mode>` with the API-domain base URL (§2.3);
   behind-proxy mode: set the CORS origin in
   `docker/nginx-behind-proxy/proxy.conf` (§2.5).
4. **Start**: `docker compose up -d` (mode selected by the
   `docker-compose.yml` symlink — see `docker/DUAL-MODE-DEPLOYMENT.md`).
   Nginx waits for both backends' health.
5. **Smoke test**: `docker/smoke-test-deploy.sh` — probes `/health`,
   `GET /v1/kinds`, `POST /v1/calculate` through nginx, then stops
   calc-backend and verifies payments still answers while calc fails
   explicitly (502/503), then restarts and verifies recovery.
6. **Cross-repo acceptance** (optional, against the deployment):
   `sbt -Dfirecalc.private.server.uri=http://<host>:9000/v1 "firebox_calc_testkit/testOnly *CrossRepositoryAcceptanceSuite"`.

## 7. Failure behavior (outage)

- **UI, backend unavailable**: the prefetch cycle surfaces a typed `Failed`
  state (transport/protocol/decode failure classes) — explicit remote failure,
  never a silent local approximation. If a genuinely matching cached remote
  response exists under the established key/fingerprint rules, the UI may serve
  it as `Cached`; the cache stores remote response data only and can never
  invoke Austrian calculation (tickets 09–12 evidence, phase-2 gate evidence
  §2–3, private repository).
- **PDF, backend unavailable**: payments retries within the configured
  timeout/retry policy, then fails explicitly with a typed
  `CalculationReportError` → `ReportFileAccessException`. No local fallback,
  no payments-side cache, and a browser-side cache can never satisfy a
  payments request (ticket 18 `RemotePdfAcceptanceSuite`).
- **Proxy-level**: with calc-backend down, nginx returns 502/503 for the two
  calc locations while payments routes keep serving (smoke-test Phase 4).

## 8. Rollback

See **[private-backend-rollback.md](private-backend-rollback.md)** — the
service-restoring operational rollback deploys the prior client-compatible
private backend behind the unchanged routes, without changing public clients,
without restoring proprietary source or artifacts to the public repository,
without linking public and private builds, and without bypassing HTTP.

## 9. Evidence index

| Checklist item (ticket 21) | Evidence |
|---|---|
| Routing through exactly the two normative operations, health gating, payments routing preserved | §2 above; ticket 05 implementation notes (private repository); `docker/smoke-test-deploy.sh` |
| Independent versioning + demonstrated contract compatibility | §3; `BlackBoxHttpContractSuite` (ticket 15 evidence §7); `CrossRepositoryAcceptanceSuite` (ticket 17) |
| Ecolabeled/Door15aCatalog end-to-end workflows with complete outputs and `AsResult` | Ticket 12 Phase 2 gate: `RemotePrefetchEndToEndSpec` (ui, 8 tests) + `CrossRepositoryAcceptanceSuite` AsResult fold; phase-2 gate evidence (private repository) §1 |
| PDF only from a fresh backend response; no fallback/cache | Ticket 18: `RemotePdfAcceptanceSuite` (payments, 11 tests); ticket 06 implementation notes |
| Explicit outage behavior (UI + PDF) | `RemotePrefetchFailureSpec` (ui, 10 tests), `FetchCalcClientSuite`, `Http4sCalcClientSuite`; `RemotePdfAcceptanceSuite` exhaustion tests; phase-2 gate evidence (private repository) §2, §4 |
| Strict-backed calculation unchanged, every served kind advertised, no MCE serving path — exactly one documented refusal site, non-Austrian behavior retained | Ticket 15 evidence §2 (Strict registry, 6 served kinds; the deployment is strict-only: no MCE serving path, and a non-strict `standard` request is refused at the private calc-core checkpoint with HTTP 400 `unsupported_standard` — the single documented refusal site); ticket 12 evidence §6; `Phase2InvariantAcceptanceSuite` (nine invariants, JVM + JS) |
| Rollback deploys prior backend behind unchanged routes | `private-backend-rollback.md` |
| No rollback restores proprietary source/artifacts, links builds, or bypasses HTTP | `private-backend-rollback.md` §4; SPEC_00 decision 14 |
