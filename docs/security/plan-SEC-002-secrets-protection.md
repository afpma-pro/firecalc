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

### Step 1: Update .gitignore (Immediate)

**Files to modify**: `.gitignore`

Add these patterns:

```gitignore
# Deployment configs with credentials (use templates instead)
docker/configs/*/payments/*.conf
docker/configs/*/invoices/*.yaml
docker/configs/*/invoices/*.jpg
docker/configs/*/invoices/*.png
docker/configs/*/reports/*.jpg
docker/configs/*/reports/*.png
!docker/configs/**/*.template
!docker/configs/**/*.example
```

### Step 2: Create Template Files

**Files to create**: For each real config file, create a `.template` counterpart:

```hocon
# docker/configs/staging/payments/gocardless-config.conf.template
gocardless {
    access-token = "YOUR_GOCARDLESS_SANDBOX_TOKEN_HERE"
    webhook-secret = "YOUR_GOCARDLESS_WEBHOOK_SECRET_HERE"
    environment = "sandbox"
    base-url = "https://api-sandbox.gocardless.com"
}
```

```hocon
# docker/configs/staging/payments/email-config.conf.template
email {
    smtp-host = "YOUR_SMTP_HOST"
    smtp-port = 465
    username = "YOUR_EMAIL_USERNAME"
    password = "YOUR_EMAIL_PASSWORD"
    from-address = "YOUR_FROM_ADDRESS"
    support-email = "YOUR_SUPPORT_EMAIL"
}
```

### Step 3: Rotate All Exposed Credentials

**Actions** (manual, by project administrator):

1. **SMTP password** (highest priority — real mail server):
   - Log into OVH mail admin for `logiciel@afpma.pro`
   - Change password immediately
   - Update `docker/configs/staging/payments/email-config.conf` locally
   - Update production config if same credentials are shared

2. **GoCardless sandbox token**:
   - Log into GoCardless dashboard → Developer → Sandbox
   - Revoke `<REDACTED>`
   - Generate new sandbox access token
   - Update local config

3. **GoCardless webhook secret**:
   - Log into GoCardless dashboard → Webhooks
   - Regenerate webhook endpoint secret
   - Update local config

### Step 4: Add Secrets Scanning to Pre-commit Hook

**Files to modify**: `scripts/git-hooks/pre-commit`

Add a basic secrets detection check after the license header check:

```bash
# ── Secret detection ──────────────────────────────────────
echo "Checking for potential secrets in staged files..."

SECRETS_FOUND=0
for file in $STAGED_FILES; do
    # Skip binary and template files
    if file "$file" | grep -q "binary"; then continue; fi
    if [[ "$file" == *.template ]] || [[ "$file" == *.example ]]; then continue; fi

    # Check for common secret patterns
    if git diff --cached -- "$file" | grep -qiE \
        '(password|secret|token|api.?key)\s*[:=]\s*"[^"]{8,}"'; then
        echo "⚠️  Potential secret in: $file"
        SECRETS_FOUND=1
    fi
done

if [ $SECRETS_FOUND -eq 1 ]; then
    echo "ERROR: Potential secrets detected in staged files."
    echo "If these are false positives, use: git commit --no-verify"
    exit 1
fi
```

### Step 5: Add Secrets Scanning to CI

**Files to modify**: `.github/workflows/license-check.yml` (or create new workflow)

Add a step using `gitleaks` or `trufflehog`:

```yaml
- name: Scan for secrets
  uses: gitleaks/gitleaks-action@v2
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Step 6: Clean Up .git/info/exclude

**Files to modify**: `.git/info/exclude`

Remove the patterns that are now covered by `.gitignore`. This file should not be relied upon for security-critical exclusions.

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
- New developers must copy `.template` files and fill in credentials obtained from team admin.
- Document the setup process in `docs/dev/guides/STAGING.md`.

## Estimated Effort

**T-shirt size**: S (Small) for .gitignore + templates. Credential rotation is manual admin work.
**Priority**: P0 — the SMTP password for a real mail server is at risk.
