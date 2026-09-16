<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
-->

# Private Calculation Backend — Rollback Runbook

Operational rollback for the Phase 3 cutover (ticket 21; SPEC_00 decision 14,
last two rows). Use when the deployed private calculation backend shows a
production availability or behavioral regression after cutover.

**Goal:** restore calculation service while preserving the HTTP seam and
client compatibility. **Never** restore proprietary source or artifacts to
the public repository, link public and private builds, or bypass HTTP.

This is distinct from the Phase 1/2 private-branch rollback
(`.scratch/round-5-ip-isolation/docs/rollback-procedure-phase1.md` /
`rollback-procedure-phase2.md`), which resets the private development branch
to the pre-extraction checkpoint. That procedure is not available after
release and is not what this runbook does.

---

## 1. Preconditions

- A **prior client-compatible private backend image** is retained:
  `firecalc-${FIRECALC_ENV}-calc-server:<prior-tag>` (or the prior assembly
  jar `firecalc-calc-server-assembly.jar` from the previous private build).
  Client-compatible means it served the same normative contract the deployed
  public clients were built against: same paths (`GET /v1/kinds`,
  `POST /v1/calculate`), same wire shapes (OpenAPI `1.0.0`), same rate-limit
  headers and error taxonomy. The `CrossRepositoryAcceptanceSuite` and
  `BlackBoxHttpContractSuite` results for that build are the compatibility
  record.
- The public deployment (UI bundle, payments image, nginx config) is
  **unchanged** by this procedure.

## 2. Procedure

1. **Freeze the failing rollout.** Stop issuing new calc-backend deployments.

2. **Redeploy the prior private backend behind the unchanged routes.**
   - Image-based: retag or redeploy the retained prior image as the
     `calc-backend` service image and recreate the service:
     `docker compose up -d --no-deps --force-recreate calc-backend`
     (after pointing the service at the prior image/tag via your deployment
     tooling or a compose override).
   - Jar-based: rebuild the image from the retained prior assembly jar using
     the same public `docker/Dockerfile.calc-server` with the private
     checkout as build context (see cutover doc §6 step 2), then recreate the
     service as above.
   - The routes are unchanged: nginx still proxies exactly `/v1/kinds` and
     `/v1/calculate` to `calc-backend:9000`, and the health gate
     (`/calc-healthcheck` → `/health`) is unchanged.

3. **Wait for the health gate.** Compose recreates `calc-backend` and nginx
   admits traffic only once `curl -f http://localhost:9000/health` passes
   (`depends_on: condition: service_healthy`). Confirm:
   `docker compose ps` shows `calc-backend` healthy and nginx healthy
   (nginx's healthcheck probes both `/healthcheck` and `/calc-healthcheck`).

4. **Verify the normative seam through the proxy.**
   - `GET /v1/kinds` → 200, served-kind list, `X-RateLimit-*` headers.
   - `POST /v1/calculate` for one Ecolabeled and one Door15aCatalog request
     → 200 with contributions, constraints, report data, and complete
     `AsResult` state.
   - Or run the cross-repo acceptance against the deployment:
     `sbt -Dfirecalc.private.server.uri=http://<host>:9000/v1 "firebox_calc_testkit/testOnly *CrossRepositoryAcceptanceSuite"`.
   - `docker/smoke-test-deploy.sh` exercises the same probes plus the
     stop/restart recovery path.

5. **Confirm public clients are untouched.** No UI rebuild, no payments
   rebuild, no nginx route change occurred. The UI base URL and the payments
   `calculation-client.base-uri` still point at the same routes.

## 3. If the prior backend is also unavailable

Escalate: the outage is then in shared infrastructure (network, host, proxy),
not the cutover. The failure behavior is explicit by design — the UI shows
typed remote failure (or serves only a genuinely matching cached remote
response) and PDF requests fail explicitly after bounded timeout/retry
exhaustion. No implicit degradation exists to mask the outage.

## 4. Prohibited rollback actions

These would violate the IP-isolation program (SPEC_00 decision 14, out of
scope list) and are never part of any rollback:

- **Restoring proprietary source or artifacts to the public repository** —
  no Austrian engine source, no private assembly jar, no private build output
  is ever copied, committed, or published into the public repo. The prior
  backend image/jar stays in private registry/private storage.
- **Linking public and private builds** — no sbt dependency, submodule,
  shared classpath, or shared artifact between the repositories. The only
  seam is HTTP.
- **Bypassing HTTP** — no in-process Austrian engine, no shared-memory or
  file-based result handoff, no re-enabling a removed local calculation path
  in the UI or payments.
- **Adding a local fallback or response cache to payments/PDF** to ride out
  the outage — PDF generation must fail explicitly instead (SPEC_00
  decision 6).
- **Reintroducing a browser source toggle or offline Austrian engine** —
  the UI may only use its existing cache of genuine remote responses.

## 5. Post-rollback

- Record the rolled-back-to image tag/jar hash and the incident timeline.
- Repair the newer private backend in the private repository; re-qualify it
  (private test suite + `BlackBoxHttpContractSuite` +
  `CrossRepositoryAcceptanceSuite`) before the next cutover attempt.
- The forward fix follows the same deployment path as the original cutover
  (cutover doc §6) — a new client-compatible private backend behind the
  unchanged routes, public clients untouched.
