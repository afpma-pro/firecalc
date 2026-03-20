# Implementation Plan: SEC-002 — Protect Staging Secrets and Rotate Credentials

## Finding Summary

**Severity**: Critical (CVSS 8.6)

Real credentials (SMTP password, GoCardless API tokens) exist in local config files (`docker/configs/staging/payments/`) that are excluded from git only via `.git/info/exclude` — a non-portable, local-only mechanism. The `.gitignore` has no matching patterns. One `git add .` on a fresh clone would commit these secrets.

## Current State

**Exposed credentials** (in local files, not tracked but unprotected):
- `docker/configs/staging/payments/gocardless-config.conf:19` — GoCardless sandbox access token
- `docker/configs/staging/payments/gocardless-config.conf:24` — GoCardless webhook secret
- `docker/configs/staging/payments/email-config.conf:22-23` — SMTP username (`logiciel@afpma.pro`) and password

**Current protection**: Only `.git/info/exclude` (local-only, not shared across clones).

**`.gitignore` coverage gaps**: No patterns matching `docker/configs/*/payments/*.conf`, `docker/configs/*/invoices/*`, or `docker/configs/*/reports/*`.

## Target State

- All config files with credentials covered by `.gitignore`
- All exposed credentials rotated
- Secrets scanning in pre-commit hooks and CI
- Template files for all config files (with placeholder values)

## Implementation Steps

### Step 1: Update .gitignore — DONE

**Commit**: `0edc77c`

Added to `.gitignore`:
```gitignore
# Docker deployment configs with credentials (use .template counterparts instead)
docker/configs/*/payments/*.conf
docker/configs/*/invoices/*.yaml
!docker/configs/**/*.template
!docker/configs/**/*.example

# Docker databases (contain transactional data, never commit)
docker/databases/
```

Note: logo files (`.jpg`/`.png`) were intentionally kept out of `.gitignore` — they are company assets, not credentials, and remain protected via `.git/info/exclude`.

### Step 2: Create Template Files — DONE

**Commit**: `0edc77c`

Created `.template` files alongside pre-existing `.example` files:
- `docker/configs/staging/payments/gocardless-config.conf.template` — concise scaffold with `YOUR_*` placeholders
- `docker/configs/staging/payments/email-config.conf.template` — concise scaffold with `YOUR_*` placeholders
- `docker/configs/staging/payments/payments-config.conf.template` — concise scaffold (JWT already uses `${JWT_SECRET}` env var)
- `docker/configs/staging/invoices/invoice-config.yaml.template` — concise scaffold with `YOUR_*` placeholders

Each `.template` file starts with a comment pointing to the `.example` counterpart for full setup documentation:
```
# Quick-start scaffold — copy to <name>.conf and fill in values.
# For detailed setup docs and alternatives, see the .example counterpart.
```

**Convention**: `.template` = quick copy-and-fill scaffold. `.example` = full onboarding docs with setup guides, troubleshooting, and alternative providers.

### Step 3: Rotate All Exposed Credentials — TODO

**Actions** (manual, by project administrator):

1. **SMTP password** (highest priority — real mail server):
   - [ ] Log into OVH mail admin for `logiciel@afpma.pro`
   - [ ] Change password immediately
   - [ ] Update `docker/configs/staging/payments/email-config.conf` locally
   - [ ] Update production config if same credentials are shared

2. **GoCardless sandbox token**:
   - [ ] Log into GoCardless dashboard → Developer → Sandbox
   - [ ] Revoke the exposed sandbox access token
   - [ ] Generate new sandbox access token
   - [ ] Update local config

3. **GoCardless webhook secret**:
   - [ ] Log into GoCardless dashboard → Webhooks
   - [ ] Regenerate webhook endpoint secret
   - [ ] Update local config

### Step 4: Add Secrets Scanning to Pre-commit Hook — DONE

**Commit**: `0edc77c`

Added secrets detection to `scripts/git-hooks/pre-commit` (runs before the license header report):
- Scans `git diff --cached` for patterns matching `(password|secret|token|api.?key)` followed by a quoted value of 8+ characters
- Skips `.template`, `.example`, and binary files
- Blocks commit if a potential secret is found (override with `--no-verify`)

### Step 5: Add Secrets Scanning to CI — DONE

**Commit**: `0edc77c`

Created `.github/workflows/secret-scan.yml`:
- Uses `gitleaks/gitleaks-action@v2` with full history scan (`fetch-depth: 0`)
- Triggers on **all pushes** (including to `main`) and all pull requests
- No `.gitleaks.toml` needed — git history was cleaned before push

### Step 6: Clean Up .git/info/exclude — DONE

**Commit**: `0edc77c`

Removed credential-file entries now covered by `.gitignore`. Kept logo entries (company assets, not credentials):
```
# staging config assets (logos — not credentials, but company-specific assets)
# Note: *.conf and *.yaml files are now covered by .gitignore
docker/configs/staging/invoices/logo.jpg
docker/configs/staging/invoices/logo.png
docker/configs/staging/reports/logo.jpg
```

## Remaining Work

### Credential Rotation (Step 3) — BLOCKED on admin access

| Credential | Service | Priority | Status |
|---|---|---|---|
| SMTP password | OVH mail admin | P0 (real mail server) | TODO |
| GoCardless sandbox token | GoCardless dashboard | P1 (sandbox only) | TODO |
| GoCardless webhook secret | GoCardless dashboard | P1 (sandbox only) | TODO |

### Post-Rotation Verification

- [ ] Test staging deployment with new SMTP credentials (send test email)
- [ ] Test staging payment flow with new GoCardless token
- [ ] Verify webhook delivery with new webhook secret
- [ ] Confirm CI secrets scan passes on first push

## Dependencies

- GoCardless dashboard access (for token rotation)
- OVH mail admin access (for SMTP password rotation)
- Production config files may need updating if they share credentials with staging

## Testing Plan

1. **Verify .gitignore**: On a fresh clone, run `git status` and confirm config files are not listed.
2. **Verify pre-commit hook**: Stage a file with `password = "test123456"` and verify the hook blocks the commit.
3. **Verify credential rotation**: After rotating, test staging deployment with new credentials.
4. **Verify CI**: Push a branch and confirm the secrets scanning step runs.

## Migration Notes

- **No breaking changes** for existing developers — their local config files remain untouched.
- New developers must copy `.template` or `.example` files and fill in credentials obtained from team admin.
- Document the setup process in `docs/dev/guides/STAGING.md`.

## Estimated Effort

**T-shirt size**: S (Small) for .gitignore + templates + hooks. Credential rotation is manual admin work.
**Priority**: P0 — the SMTP password for a real mail server is at risk until rotated.
