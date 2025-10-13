<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# License Header CI Enforcement

## Overview

This project uses a **hybrid CI enforcement strategy** for AGPL-3.0-or-later license headers:

1. **Changed Files Check** (`.github/workflows/license-check.yml`)
   - Runs on all PRs and feature branch pushes
   - Checks only files modified in the PR/push
   - **Fast feedback** (< 30 seconds)
   - **Purpose**: Catch violations early

2. **Full Repository Scan** (`.github/workflows/license-check-full.yml`)
   - Runs on pushes to main/master
   - Runs weekly (Monday 6 AM UTC)
   - Can be triggered manually
   - **Comprehensive compliance** (checks all files)
   - **Purpose**: Ensure nothing slips through

## How It Works

### Layered Enforcement

```
┌─────────────────────────────────────────┐
│  Developer: VSCode psi-header extension │
│  ↓ Auto-adds headers on save            │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│  Local: Pre-commit hook                 │
│  ↓ Blocks commits without headers       │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│  CI: Changed files check (PR)           │
│  ↓ Validates changed files              │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│  CI: Full scan (main branch)            │
│  ↓ Final compliance verification        │
└─────────────────────────────────────────┘
```

### Workflow Triggers

**Changed Files Check** (`license-check.yml`):
- ✅ Pull request opened/updated
- ✅ Push to feature branches
- ❌ Push to main (triggers full scan instead)

**Full Repository Scan** (`license-check-full.yml`):
- ✅ Push to main/master branch
- ✅ Every Monday at 6 AM UTC
- ✅ Manual workflow dispatch

## For Developers

### What to Expect on PRs

When you create or update a PR, the license check workflow runs automatically:

**If all files have headers:**
```
✅ License Header Check (Changed Files)
   All 5 file(s) have valid license headers
```

**If headers are missing:**
```
❌ License Header Check (Changed Files)
   Missing license headers in 2 file(s):
   - src/NewService.scala
   - scripts/deploy.sh
   
   See job summary for fix instructions
```

### How to Fix Violations

#### Option 1: VSCode psi-header Extension (Automatic)

The extension should auto-add headers when you save files. If not:

1. Ensure you have installed the extension
2. Check `.vscode/settings.json` configuration
3. Save the file again (Cmd/Ctrl+S)

#### Option 2: Manual Addition

1. Open the file
2. Add the appropriate header from [`LICENSE-HEADER-TEMPLATE.txt`](../../LICENSE-HEADER-TEMPLATE.txt)
3. Save and commit

### Local Testing

Test before pushing:

```bash
# Test on changed files (compared to main)
./scripts/ci/check-license-headers.sh --changed --base-ref origin/main

# Test on all files
./scripts/ci/check-license-headers.sh --all

# Or just rely on pre-commit hook
git commit
```

### Supported File Types

The CI check validates headers for:

| Extension | Comment Style | Example |
|-----------|---------------|---------|
| `.scala`, `.sbt` | C-style `/* */` | [See template](../../LICENSE-HEADER-TEMPLATE.txt#L9-L15) |
| `.js`, `.ts`, `.jsx`, `.tsx` | C-style `/* */` | [See template](../../LICENSE-HEADER-TEMPLATE.txt#L18-L24) |
| `.css` | C-style `/* */` | [See template](../../LICENSE-HEADER-TEMPLATE.txt#L69-L74) |
| `.sh` | Hash `#` | [See template](../../LICENSE-HEADER-TEMPLATE.txt#L78-L82) |
| `.conf`, `.hocon`, `.properties` | Hash `#` | [See template](../../LICENSE-HEADER-TEMPLATE.txt#L28-L31) |
| `.sql` | SQL `--` | [See template](../../LICENSE-HEADER-TEMPLATE.txt#L35-L38) |
| `.typ` | Double-slash `//` | [See template](../../LICENSE-HEADER-TEMPLATE.txt#L86-L89) |
| `Makefile` | Hash `#` | [See template](../../LICENSE-HEADER-TEMPLATE.txt#L28-L31) |

### Excluded Files

These files are **not checked** (from `.vscode/settings.json`):

- `node_modules/`, `target/`, `dist/`, `build/`, `out/`
- Generated files (`moleculeGen/`, `databases/`)
- Lock files (`*.lock`, `yarn.lock`, `package-lock.json`)
- Binary files (`.svg`, `.png`, `.jpg`, `.pdf`, etc.)
- Config files (`.json`, `.yaml`, `.yml`)
- Documentation (`.md`)
- HTML/XML files

## For Maintainers

### Workflow Management

#### Enable/Disable Workflows

**Disable changed files check:**
```yaml
# In .github/workflows/license-check.yml
on:
  # Comment out to disable
  # pull_request:
  #   types: [opened, synchronize, reopened]
```

**Change full scan schedule:**
```yaml
# In .github/workflows/license-check-full.yml
on:
  schedule:
    - cron: '0 6 * * 1'  # Change time/day here
```

#### Manual Triggers

Trigger full scan manually:
1. Go to GitHub Actions tab
2. Select "License Header Check (Full Repository)"
3. Click "Run workflow"
4. Select branch
5. Click "Run workflow"

### Updating Check Logic

When you need to update the checking logic:

1. **Modify the script:**
   ```bash
   # Edit the main check script
   vim scripts/ci/check-license-headers.sh
   
   # Or update exclusions
   vim scripts/ci/exclusions.sh
   ```

2. **Test locally:**
   ```bash
   # Test changed files mode
   ./scripts/ci/check-license-headers.sh --changed --base-ref HEAD~1
   
   # Test full scan mode
   ./scripts/ci/check-license-headers.sh --all
   ```

3. **Commit and push:**
   ```bash
   git add scripts/ci/
   git commit -m "fix(ci): update license check logic"
   git push
   ```

4. **Verify in CI:**
   - Changes apply immediately to all future runs
   - No workflow file changes needed (unless changing triggers)

### Adding New File Types

To add support for a new file type:

1. **Add to check script** (`scripts/ci/check-license-headers.sh`):
   ```bash
   # Around line 107, add new case
   newext)
       check_appropriate_header "$file"
       return $?
       ;;
   ```

2. **Add to pre-commit hook** (`scripts/git-hooks/pre-commit`):
   ```bash
   # Keep logic synchronized
   newext)
       CHECKED_FILES+=("$file")
       if ! check_appropriate_header "$file"; then
           MISSING_HEADERS+=("$file")
       fi
       ;;
   ```

3. **Update documentation**:
   - Add to [`LICENSE-HEADER-TEMPLATE.txt`](../../LICENSE-HEADER-TEMPLATE.txt)
   - Update this document
   - Inform team

### Troubleshooting

#### False Positives

If files are incorrectly flagged as missing headers:

1. **Check exclusion patterns** in `scripts/ci/exclusions.sh`
2. **Add to exclusions** if needed:
   ```bash
   # In exclusions.sh
   EXCLUDE_PATTERNS+=(
       "*.newext"
   )
   ```

#### CI Check Not Running

Possible causes:
1. Workflow file has syntax errors → Check Actions tab
2. Branch filters exclude your branch → Check workflow `on:` section
3. Permissions issue → Check repository settings

#### Different Results Local vs CI

Possible causes:
1. **Different base reference:**
   - Local: Might compare to different branch
   - CI: Compares to PR base or origin/main
   
2. **Different git history:**
   - Ensure `fetch-depth: 0` in workflow for full history
   
3. **File system differences:**
   - Unlikely, but check line endings (CRLF vs LF)

## Integration with Branch Protection

### Recommended Settings

Configure branch protection for `main`:

1. **Navigate to**: Repository Settings → Branches → Branch protection rules
2. **Add rule for**: `main`
3. **Enable**:
   - ✅ Require status checks to pass before merging
   - ✅ Require branches to be up to date before merging
4. **Required status checks**:
   - ✅ `check-headers / Check License Headers`
5. **Save changes**

**Effect:** PRs cannot merge into main without passing license checks.

### Optional: Require for Feature Branches

For stricter enforcement:

1. Add pattern: `feature/*`, `bugfix/*`, etc.
2. Same requirements as main
3. Forces license compliance from first commit

## Performance Optimization

### Current Performance

- **Changed files check**: ~10-30 seconds
  - Scales with number of changed files
  - Typical PR: < 20 seconds

- **Full scan**: ~1-3 minutes
  - Scales with repository size
  - Current repository: ~2 minutes

### Optimization Tips

1. **Use shallow clones** when possible:
   ```yaml
   - uses: actions/checkout@v4
     with:
       fetch-depth: 1  # For full scans
   ```

2. **Cache dependencies** (none currently needed)

3. **Run in parallel** with other checks:
   ```yaml
   jobs:
     license-check:
       # ...
     tests:
       # Runs concurrently
   ```

## Metrics & Monitoring

### Success Metrics

Track these metrics over time:
- **Violation rate**: Should trend toward 0%
- **Check duration**: Should remain < 30s for changed files
- **False positive rate**: Should be 0%
- **Developer resolution time**: Should be < 5 minutes

### Monitoring Commands

```bash
# Count files without headers (rough estimate)
git ls-files | while read f; do
  [ -f "$f" ] && ! head -n 5 "$f" | grep -q "SPDX-License-Identifier" && echo "$f"
done | wc -l

# Test full scan locally
./scripts/ci/check-license-headers.sh --all
```

## FAQ

### Q: Why do we have both pre-commit hook and CI check?

**A:** Defense in depth.
- **Pre-commit hook**: Can be bypassed (`--no-verify`)
- **CI check**: Cannot be bypassed, always runs
- **Together**: Ensures compliance

### Q: Can I exclude a specific file from checks?

**A:** Yes, add to `scripts/ci/exclusions.sh`:
```bash
EXCLUDE_FILES+=(
    "path/to/special-file.scala"
)
```

### Q: What if I need to commit a file without headers temporarily?

**A:** Not recommended, but:
```bash
# Local
git commit --no-verify

# CI will still catch it on PR
# You'll need to fix before merge
```

### Q: How do I add headers to many files at once?

**A:** Use automated tool:
```bash
# Option 1: Google's addlicense
go install github.com/google/addlicense@latest
addlicense -c "Association Française du Poêle Maçonné Artisanal" \
  -l agpl -y 2025 \
  -ignore "node_modules/**" \
  -ignore "target/**" \
  $(find . -name "*.scala" -o -name "*.js")

# Option 2: Incremental with VSCode
# Just save each file - psi-header adds headers automatically
```

### Q: Can I run the check on a specific file?

**A:** Yes, but the script expects lists. Workaround:
```bash
# Create temporary list
echo "path/to/file.scala" | while read f; do
  ./scripts/ci/check-license-headers.sh --all
done
```

Or check manually:
```bash
head -n 5 path/to/file.scala | grep "SPDX-License-Identifier: AGPL-3.0-or-later"
```

## Related Documentation

- [`../LICENSE.md`](../LICENSE.md) - License guide
- [`../../scripts/git-hooks/README.md`](../../scripts/git-hooks/README.md) - Pre-commit hook documentation
- [`../../LICENSE-HEADER-TEMPLATE.txt`](../../LICENSE-HEADER-TEMPLATE.txt) - Header templates
- [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md) - Contribution guidelines
- [`.github/workflows/license-check.yml`](../../.github/workflows/license-check.yml) - Changed files workflow
- [`.github/workflows/license-check-full.yml`](../../.github/workflows/license-check-full.yml) - Full scan workflow

## Support

**For issues with CI checks:**
- Check job summary for specific file violations
- Review this documentation
- Ask in development channel
- Open issue with `ci` label

**For questions about licensing:**
- See [`docs/LICENSE.md`](../LICENSE.md)
- Contact: https://www.afpma.pro
- Legal questions: Consult a lawyer

---

**Last Updated:** 2025-01-10  
**Maintainer:** AFPMA Development Team