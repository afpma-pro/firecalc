<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
-->

# Production Go-Live Runbook — FireCalc AFPMA

The operator step-by-step for cutting the `v1.0.0` release over to
`firecalc.afpma.pro` / `api.firecalc.afpma.pro` on **afpmadocker1**.
Written 2026-11-05 after the staging rehearsal of the same release candidate
(`7b0185fb`) succeeded end-to-end; every staging-discovered correction is
already folded into the commands below.

**Hand-off protocol:** every fenced block is for a human terminal — *dev
machine* or *afpmadocker1* — never for an agent. Each carries `# expect:`
comments; if reality differs, stop and report the exact line.

Companion docs: `docs/dev/guides/PRODUCTION.md` (steady-state operations),
`docs/dev/guides/STAGING.md`, `docs/dev/guides/INVOICE_NUMBERING_RUNBOOK.md`
(numbering authority). Ledger of every prepared item:
`FIRECALC_AFPMA_PRODUCTION_GO_TRACKING.md` (repo root, untracked).

The release identity is fixed (already cut and staging-validated):

| Thing | Value |
|---|---|
| Release commit **R** (public) | `7b0185fb` — version stamps `1.0.0`/`0.3.1`, certification strings, goldens |
| Pins commit **R2** (public) | `e734dc7e` — `VERSIONS` → R + private pin; branch tip today is the `staging` head (R chain + export fix + this runbook), pin unchanged |
| Private pin commit **P** | `36737cd` (private `dev`), pin `0.3.1+7b0185f` |
| Engine tag | `engine-0.3.1+7b0185f` (annotated, peels to R) |
| Application tag (this runbook) | `v1.0.0.production.0` (annotated, peels to R) |

---

## 1. Prerequisite (BLOCKING): GoCardless live organisation

Nothing deploys until the live organisation's credentials exist. The staging
walk proved the full order flow against GoCardless **sandbox**; production must
run against **live**, and the live webhook endpoint cannot even be registered
before DNS + TLS exist (sections 5–6), so plan the order: sections 2–6 first,
register the webhook at section 6, then paste the secrets here before 7.

### Operator values table — nine inputs

| # | Value | Where it goes | Notes |
|---|---|---|---|
| 1 | `<GC_LIVE_ACCESS_TOKEN>` | `firecalc-docker/configs/production/payments/gocardless-config.conf` → `access-token` | GoCardless dashboard → Developers → live access token. Paste **literally**; env hooks are rejected by the guard. |
| 2 | `<GC_LIVE_WEBHOOK_SECRET>` | same file → `webhook-secret` | Shown **once** when the live webhook is created (section 6). |
| 3 | SMTP password (shared mailbox) | `configs/production/payments/email-config.conf` → `smtp.password` | Already written literally (same as staging). Rotation is a **deferred task** (section 10). |
| 4 | `JWT_SECRET` | `firecalc-docker/.env.production` → `JWT_SECRET` | Already generated fresh at P4.2 (differs from staging). Record it in your password store; it is the production JWT key. |
| 5 | `LETSENCRYPT_EMAIL` | already set | Ops mailbox — supplied by the operator directly into `firecalc-docker/.env.production`; deliberately not written in this tracked doc (unused behind-proxy; NPM issues the certs). |
| 6 | `<AFPMADOCKER1_IP>` | DNS A records (section 5) | The VPS public IP. |
| 7 | `<NPM_CONTAINER>` | network decision (section 4) + section 6 | Discover it: see block below. |
| 8 | `<ADMIN_MAILBOX_ALIAS>` | OVH mailbox alias | The production admin alias (`+admin-production` sub-address of the ops mailbox; literal value lives in the gitignored `configs/production/payments/gocardless-config.conf` → `admin-email`). Must exist **before** the first invoice email (admin invoice notifications + GC admin emails). Create the alias in OVH. |
| 9 | Live webhook events | GoCardless dashboard | Mirror the sandbox subscription (charges/payments lifecycle). The handler verifies signatures and logs+emails anything unhandled, so over-subscribing is safe. **Do NOT enable the optional client certificate** — signature verification is the authentication (see section 6). |

```bash
# on afpmadocker1 — discover the nginx-proxy-manager container name
docker ps --format '{{.Names}}' | grep -iE 'proxy|npm'
# expect: exactly one name (this is <NPM_CONTAINER>)
```

## 2. Release-cut chain and the GitHub pushes (dev machine)

The cut already exists on the `staging` branch (chain R → R2 → export-fix,
`v1.0.0.staging.0` + `engine-0.3.1+7b0185f` tagged locally). What remains is
the **production branch + tag** and the pushes. The `dev` push stays a
fast-forward; never `--tags` on the QA branch.

```bash
# on the DEV machine, in the public repo (branch `staging` tip: R chain + export fix
# + this runbook; VERSIONS pins unchanged since R2)
# expect: branch created, tip = staging tip (VERSIONS pins unchanged)
git branch production staging
# expect: annotated tag object; peel == 7b0185fb (R); message matches the v1.0.0.staging.0 convention
git tag -a v1.0.0.production.0 7b0185fb -m "FireCalc 1.0.0 production"
git cat-file -t v1.0.0.production.0
# expect: tag
git rev-parse 'v1.0.0.production.0^{commit}'
# expect: 7b0185fb81eaaf40b9891de6afaecb9abeb7b789

# push the release (fast-forward on dev; branch + tag explicit)
# expect: new branch production + tag on GitHub
git push -u origin production v1.0.0.production.0
# engine tag — no-op if the staging walk (S4.2) already pushed it
git push origin engine-0.3.1+7b0185f
```

```bash
# on the DEV machine, in the PRIVATE repo (firecalc-firebox-austrian-api, dev @ 36737cd)
# expect: carries the pin commit P; no private branch has an upstream yet, so name the ref;
#         the private origin holds no tags and none are pushed
git push origin dev
```

*Note:* pushing `staging` itself to origin was deferred by the operator during
the rehearsal — re-offer if you want the full history on GitHub:
`git push -u origin staging v1.0.0.staging.0` (fast-forward).

## 3. Tree transfers to afpmadocker1 (dry-run, then real)

**Third-repo prerequisite (discovered live 2026-11-05):** `modules/ui/package.json`
depends on `"@scala-js/vite-plugin-scalajs": "file:../../../vite-plugin-scalajs"` —
a sibling checkout of `afpma-pro/vite-plugin-scalajs` OUTSIDE the public repo. rsync
never carries it (it lives above the repo root) and it is absent from both group
folders, so a FRESH tree fails the vite build with
`Cannot find package '@scala-js/vite-plugin-scalajs'`. Ship it once per parent dir:

```bash
# on the DEV machine, from the public repo root (gitignored shim, same pattern
# as the repo pushes — recreatable from push-tree-to-vps.sh's usage comment)
# Dry-run first (local, no ssh): DRY_RUN=1 REMOTE_HOST= GROUP_DIR=/tmp/prod-tree ./push-repo-vite-plugin-scalajs-afpmadocker1.sh
./push-repo-vite-plugin-scalajs-afpmadocker1.sh
# ^ expect: two rsync file lists (firecalc-group/vite-plugin-scalajs AND
#   firecalc-group/production/vite-plugin-scalajs), each incl. dist/index.js,
#   no node_modules/.git; exit 0. The staging-parent copy pre-clears the same
#   trap for staging's next redeploy from a wiped node_modules.
```

Create the production layout first (the push scripts create only the final
path component), then dry-run both transfers, then run them for real.
`--delete` is never used, so VPS-only state (databases, certs, artifacts,
`.env` symlink) always survives.

```bash
# on afpmadocker1
# expect: empty production/ dir with two children created here so ownership is afpma
mkdir -p /home/afpma/data/firecalc-group/production
# expect: staging tree untouched
ls /home/afpma/data/firecalc-group/
```

```bash
# on the DEV machine, public repo root, branch `production` checked out
# expect: lists configs/production/**, firecalc-docker/.env.production,
#         modules/ui/.env.production.local, modules/invoices/configs/logo.png;
#         NO firecalc-docker/.env line (prod list excludes it)
DRY_RUN=1 ./push-repo-prod-afpmadocker1.sh
# expect: .git/refs/tags/ entries present (the gate needs them)
DRY_RUN=1 ./push-repo-firebox-prod-afpmadocker1.sh

# real runs
# expect: rsync completes, exit 0
./push-repo-prod-afpmadocker1.sh && ./push-repo-firebox-prod-afpmadocker1.sh
```

## 4. One-time VPS setup (afpmadocker1, production tree)

```bash
cd /home/afpma/data/firecalc-group/production/firecalc/firecalc-docker
# expect: symlink created
ln -sf docker-compose.behind-proxy.yml docker-compose.yml
# expect: carrier keys present (build-local mode + production FIREBOX path)
ln -sf .env.production .env
grep -n 'FIRECALC_BUILD_LOCAL\|FIREBOX_LOCAL_DIR\|FIRECALC_ENV=production' .env
```

**Network decision.** Resolve what compose actually asks for:

```bash
# expect: a networks: block naming the external network compose attaches to
docker compose config | grep -A12 '^networks:'
```

- **Plan default — reuse the existing shared `proxy` network** (it already
  exists with the staging stack and NPM is attached to it; staging containers
  and production containers coexist because container names and the
  `COMPOSE_PROJECT_NAME=firecalc-prod` namespaces differ). No command needed;
  skip to the next block.
- Alternative — if you prefer isolation, create the resolved network name and
  attach NPM to it:
  ```bash
  # <RESOLVED_NETWORK> = the external name from `docker compose config`
  sudo docker network create <RESOLVED_NETWORK>
  sudo docker network connect <RESOLVED_NETWORK> <NPM_CONTAINER>
  ```

```bash
# expect: databases/production/ created; perms fixed to the container user (999)
mkdir -p databases/production
make fix-db-perms
```

The production payments DB **does not exist yet** — the backend creates it
empty on first boot and the invoice counter starts at `FCALC-[YYYY]-0001`
(`invoice.counter-start = 1` applies cleanly to a fresh DB, unlike the
existing staging DB where the counter had already advanced).

## 5. DNS (external)

In the `afpma.pro` zone (OVH): add `A` records `firecalc` and `api.firecalc`
→ `<AFPMADOCKER1_IP>` (add `AAAA` only if the host has IPv6). Staging records
stay as they are.

```bash
# check propagation from anywhere, before touching NPM
# expect: both resolve to <AFPMADOCKER1_IP>
dig +short firecalc.afpma.pro A
dig +short api.firecalc.afpma.pro A
```

## 6. nginx-proxy-manager: proxy hosts, certificates, live webhook

In the NPM UI (staging hosts untouched):

1. Proxy host `firecalc.afpma.pro` → `firecalc-production-nginx:80`, on the
   network decided in section 4 (the `proxy` network reaches it by container
   name). SSL certificate: request Let's Encrypt after it saves (HTTP
   challenge).
2. Proxy host `api.firecalc.afpma.pro` → same target, own LE certificate.
3. Both hosts: Force SSL on.

Then register the **live** GoCardless webhook (DNS + certs must answer now):

- URL: `https://api.firecalc.afpma.pro/v1/webhooks/gocardless`
- Events: mirror the sandbox subscription (section 1, row 9).
- Client certificate: **leave disabled** — signature verification is the
  authentication; optional mTLS would be host-scoped at NPM, rejecting browser
  traffic on the same host and breaking LE renewal.
- Copy the shown secret **immediately** → value #2 of the table →
  `gocardless-config.conf` (literal paste, replace the placeholder).

```bash
# expect: both hosts serve TLS without warnings
curl -sI https://firecalc.afpma.pro/ | head -1
curl -sI https://api.firecalc.afpma.pro/v1/kinds | head -1
```

## 7. Deploy + smoke (afpmadocker1)

Pre-deploy snapshot — `N/A` on the very first deploy (no DB exists yet);
**mandatory** for every later redeploy/migration/rollback:

```bash
# on afpmadocker1, only if the prod DB already exists
mkdir -p /home/afpma/data/firecalc-group/production/db-backups
TS=$(date +%Y%m%d-%H%M)
sudo sqlite3 /home/afpma/data/firecalc-group/production/firecalc/firecalc-docker/databases/production/firecalc-payments-production.db \
  ".backup /home/afpma/data/firecalc-group/production/db-backups/firecalc-payments-production-$TS-pre-deploy.db"
sudo chown afpma:afpma /home/afpma/data/firecalc-group/production/db-backups/firecalc-payments-production-$TS-pre-deploy.db
# expect: the -pre-deploy.db file appears
ls -lh /home/afpma/data/firecalc-group/production/db-backups/
```

Deploy (this is the root `prod-docker-deploy-up` wrapper, spelled out so the
stage you're in is always visible):

```bash
cd /home/afpma/data/firecalc-group/production/firecalc
# expect, in order: config guard silent; engine gate GREEN on engine-0.3.1+7b0185f;
#   linked local trees (no clone — "local-tree mode" lines); publish 0.3.1+7b0185f;
#   public jars; UI fullLinkJS + host assertion (no example.com); landing export;
#   THEN the private firebox_calc_server assembly LAST; images; certs; compose up.
# A wrong tag/pin chain dies at the gate — that is the check working, don't
# "fix" the VPS, report the line.
make -C firecalc-docker deploy MODE=behind-proxy CALC_SERVER=true
# expect: 4 containers Up (healthy), none Restarting
docker compose -f firecalc-docker/docker-compose.yml ps
```

**First-deploy triage:** a backend stuck in `restarting` (not unhealthy) is
the invoice-numbering guard exiting before ports bind — read the
`=== invoice numbering state (boot) ===` block in
`docker compose -f firecalc-docker/docker-compose.yml logs backend`; repairs
per `docs/dev/guides/INVOICE_NUMBERING_RUNBOOK.md`.

Smoke:

```bash
# expect: PASS lines; the localhost HTTP block is skipped in behind-proxy mode
# (expected — smoke proves container health only; the public health checks
#  below are what exercise the calc seam)
make -C firecalc-docker smoke CALC_SERVER=true
```

**Rollback = a consistent triple**, all three from the same generation:
public tag (`v1.0.0.production.0` → R) + the private commit named by the
production tree's `VERSIONS` (`36737cd`) + the `invoice.counter-start` /
`counter-starting-number` spelling recorded in the gitignored configs. Mixing
generations re-opens the boot-guard failure mode.

## 8. Operator verification list (post-deploy proof)

```bash
# expect: 302 to /fr/
curl -sI https://firecalc.afpma.pro/ | grep -i 'HTTP\|location'
# expect: 200 each
curl -so /dev/null -w '%{http_code}\n' https://firecalc.afpma.pro/fr/
curl -so /dev/null -w '%{http_code}\n' https://firecalc.afpma.pro/app/
# expect: JSON "info" block with engine_version 0.3.1, payments_base_version 1.0.0 (no status key)
curl -sS https://firecalc.afpma.pro/healthcheck
# expect: {"status":"healthy",…}
curl -sS https://firecalc.afpma.pro/calc-healthcheck
# expect: six kinds in wire spelling
curl -sS https://api.firecalc.afpma.pro/v1/kinds
```

```bash
# on afpmadocker1 — backend boot evidence
# expect: firebox-availability: traditional=enabled, ecolabeled=disabled,
#         afpma-prse=disabled, single-tested=enabled, door15a-catalog=disabled
# expect: === invoice numbering state (boot) === block, counter-start = 1
docker compose -f /home/afpma/data/firecalc-group/production/firecalc/firecalc-docker/docker-compose.yml logs --no-color backend 2>&1 | grep -iE 'firebox-availability|invoice numbering'
```

Then, **through the UI** (browser, `https://firecalc.afpma.pro`):

- **Decisive pair:** an Ecolabeled project selects and **calculates** (network
  tab shows `POST https://api.firecalc.afpma.pro/v1/calculate` → 200) while its
  order controls are disabled; a direct `POST /v1/purchase/create-intent`
  returns `403 {"errorCode":"firebox_type_disabled"}`.
- **One-artefact proof:** complete one `traditional` order with a real
  GoCardless live charge → invoice `FCALC-[YYYY]-0001`; its PDF has **no DRAFT
  watermark**, footer `Certifié par l'AFPMA`, version lines
  `reports-v1.0.0 engine-v0.3.1`.
- UI console shows the availability lines (Ecolabeled visible, orderable flag
  off; AFPMA_PRSE hidden).

## 9. Backups (nightly cron)

```bash
# A long one-line crontab entry survives terminal paste-wrap badly (observed
# 2026-11-05: fragments → "bad minute"; crontab installs atomically, so a
# failed paste changes nothing). Install a small script instead, then ONE
# short crontab line.
command -v sqlite3
# ^ expect: /usr/bin/sqlite3 (adjust the script if not)

sudo tee /home/afpma/data/firecalc-group/production/db-backups/firecalc-prod-backup.sh > /dev/null <<'EOF'
#!/bin/sh
# FireCalc production DB backup (PROD_GO_LIVE.md §9). .backup is WAL-safe.
# Lives beside the snapshots it produces; the find -delete targets *.db only.
DB=/home/afpma/data/firecalc-group/production/firecalc/firecalc-docker/databases/production/firecalc-payments-production.db
DEST=/home/afpma/data/firecalc-group/production/db-backups
TS=$(date +%F)
/usr/bin/sqlite3 "$DB" ".backup $DEST/firecalc-payments-production-$TS.db"
find "$DEST" -name '*.db' -mtime +30 -delete
EOF
sudo chmod 755 /home/afpma/data/firecalc-group/production/db-backups/firecalc-prod-backup.sh

# manual run = the install verification:
sudo /home/afpma/data/firecalc-group/production/db-backups/firecalc-prod-backup.sh
# ^ expect: silent; today's file appears
ls -lh /home/afpma/data/firecalc-group/production/db-backups/

# keep existing root entries, append ONE short line:
( sudo crontab -l 2>/dev/null; echo '30 3 * * * /home/afpma/data/firecalc-group/production/db-backups/firecalc-prod-backup.sh' ) | sudo crontab -
sudo crontab -l | tail -2
# ^ expect: the firecalc-prod-backup.sh line exactly once; other entries intact
```

Nightly `.backup`, 30-day retention (the `find … -delete` tail above).

## 10. Deferred items (recorded, not part of go-live)

1. **Opening the Austrian kinds (`ecolabeled`, `door15a-catalog`) for orders**
   later: flip the two `firebox-availability` keys in
   `configs/production/payments/payments-config.conf` **and** the two
   `VITE_FIREBOX_AVAIL_*_BACKEND` flags in `modules/ui/.env.production.local`,
   then **rebuild** (`make -C firecalc-docker deploy MODE=behind-proxy CALC_SERVER=true`)
   — the UI flags are build-time; a restart alone changes nothing. Everything
   else (calc-server, REMOTE_KINDS, routing) is already live and proven.
2. **SMTP password rotation** (staging + production in the same change, then
   redeploy both). Own task; both configs carry the current literal.
3. **`afpma-prse` evolution**: make it selectable-but-not-orderable
   (`VITE_FIREBOX_AVAIL_AFPMA_PRSE=true` only), or remote-calculated (add to
   `VITE_FIREBOX_REMOTE_KINDS`). Own task.
