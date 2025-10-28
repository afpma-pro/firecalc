# Versioning and Release Strategy

> **Scope**: This document covers **release tagging and version incrementing strategy**. For **runtime version display** in the UI, see [VERSIONING_SYSTEM.md](./VERSIONING_SYSTEM.md).

## Version Format Explained

Your project uses a multi-component version format:

```
electron-vA.B.X-bN-staging.Y
│        │ │ │  │   │       │
│        │ │ │  │   │       └─ Release iteration (GitHub Actions only)
│        │ │ │  │   └───────── Environment suffix
│        │ │ │  └───────────── Beta number (pre-release identifier)
│        │ │ └──────────────── Patch version (from build.sbt)
│        │ └─────────────────── Minor version (from build.sbt)
│        └───────────────────── Major version (from build.sbt)
└────────────────────────────── Module identifier (electron app)
```

### Example: `electron-v0.9.0-b6-staging.1`

- `electron-` = Module identifier (Electron desktop app releases)
- `0.9.0` = Semantic version (defined in [`build.sbt`](../../../build.sbt))
- `b6` = **Beta 6** - pre-release version identifier
- `staging` = Environment (staging/production)
- `.1` = Release iteration (CI/packaging only)

### Monorepo Module Tags

This is a **monorepo** containing multiple modules:
- **`payments`** - Backend API module
- **`ui`** - Frontend UI module (can be deployed standalone)
- **`electron`** - Desktop app (UI packaged with Electron)

**Tag prefixes** identify which module is being released:
- `electron-v*` - Electron desktop app releases (this module)
- `payments-v*` - Backend API releases (future)
- `ui-v*` - Standalone UI releases (future)

This prevents version conflicts and makes it clear which part of the system is being released.

### Stable vs Beta Releases

**Beta releases:** `v0.9.0-b1`, `v0.9.0-b2`, `v0.9.0-b6`, etc.
- Pre-release versions during development
- May have bugs or incomplete features
- Used for testing before stable release

**Stable releases:** `v0.9.0`, `v1.0.0`, `v1.1.0`, etc.
- Production-ready versions
- No beta suffix
- Final, tested, and approved for public use

## When to Increment Each Component

### 1. Major Version (A in vA.B.X)

**Change in:** [`build.sbt`](../../../build.sbt)

```scala
lazy val ui_base_version = "0.9.0"  // Change 0 → 1
```

**When to use:**
- Breaking changes to API or data formats
- Major feature overhaul
- Incompatible with previous versions
- User data migration required

**Example:** `electron-v0.9.0-b6-staging` → `electron-v1.0.0-b1-staging`

**Development process:**
1. Implement breaking changes in code
2. Update `build.sbt` version
3. Commit code changes
4. Push to staging: `git tag electron-v1.0.0-b1-staging`

---

### 2. Minor Version (B in vA.B.X)

**Change in:** [`build.sbt`](../../../build.sbt)

```scala
lazy val ui_base_version = "0.9.0"  // Change 9 → 10
```

**When to use:**
- New features added
- Backward compatible changes
- New UI components
- New calculation methods

**Example:** `electron-v0.9.0-b6-staging` → `electron-v0.10.0-b1-staging`

**Development process:**
1. Implement new features in code
2. Update `build.sbt` version
3. Commit code changes
4. Push to staging: `git tag electron-v0.10.0-b1-staging`

---

### 3. Patch Version (X in vA.B.X)

**Change in:** [`build.sbt`](../../../build.sbt)

```scala
lazy val ui_base_version = "0.9.0"  // Change 0 → 1
```

**When to use:**
- Bug fixes
- Small code improvements
- Performance optimizations
- Security patches
- NO new features

**Example:** `electron-v0.9.0-b6-staging` → `electron-v0.9.1-b6-staging`

**Development process:**
1. Fix bugs in code
2. Update `build.sbt` version
3. Commit code changes
4. Push to staging: `git tag electron-v0.9.1-b6-staging`

---

### 4. Beta Number (N in vA.B.X-bN)

**Change in:** [`build.sbt`](../../../build.sbt)

```scala
lazy val ui_base_version = "0.9.0-b6"  // Change b6 → b7
```

**What "b" means:** Beta - a pre-release version before the stable release

**When to increment beta number:**
- Iterating toward a stable release
- Each development cycle before v0.9.0 stable
- Adding/testing features for upcoming release
- Beta testing cycles

**When to remove "b" suffix:**
- Ready for stable/production release
- All features complete and tested
- `v0.9.0-b6` → `v0.9.0` (final stable)

**Example:** `electron-v0.9.0-b6-staging` → `electron-v0.9.0-b7-staging`

**Development lifecycle:**
```
electron-v0.9.0-b1 → -b2 → ... → -b6 → electron-v0.9.0 (stable)
                                         ↓
                                      electron-v0.9.1-b1 → -v0.9.1 (patch)
                                         ↓
                                      electron-v0.10.0-b1 → ... → -v0.10.0 (next minor)
```

**Development process:**
1. Make changes for next beta iteration
2. Update `build.sbt` beta number (b6 → b7)
3. Commit code changes
4. Push to staging: `git tag electron-v0.9.0-b7-staging`

---

### 5. Release Iteration (.Y suffix)

**NO CHANGE in build.sbt required!**

**When to use:**
- **GitHub Actions workflow fixes** (most common)
- **Electron packaging configuration changes**
- **Build environment issues**
- **CSP policy adjustments**
- **Asset/icon updates**
- **Metadata corrections**
- **Code signing changes**

**Key characteristic:** The actual **source code remains unchanged** from the previous release.

**Example:** `electron-v0.9.0-b6-staging` → `electron-v0.9.0-b6-staging.1`

**Process:**
1. **NO code changes** (or only build/config changes)
2. **NO build.sbt changes**
3. Fix GitHub Actions workflow or packaging config
4. Commit only the workflow/config changes
5. Delete old tag: `git push --delete origin electron-v0.9.0-b6-staging`
6. Create new tag: `git tag electron-v0.9.0-b6-staging.1`
7. Push new tag: `git push origin electron-v0.9.0-b6-staging.1`

### Common Use Cases for Release Iteration (.Y)

#### Scenario 1: GitHub Actions Workflow Broken
```bash
# First release attempt
git tag electron-v0.9.0-b6-staging
git push origin electron-v0.9.0-b6-staging
# → Build fails due to missing npm cache step

# Fix the workflow
git commit -m "fix: add npm cache to CI workflow"

# Retry with iteration
git push --delete origin electron-v0.9.0-b6-staging
git tag electron-v0.9.0-b6-staging.1
git push origin electron-v0.9.0-b6-staging.1
```

#### Scenario 2: Wrong Backend URL in electron-builder.yml
```bash
# First release - wrong API URL
git tag electron-v0.9.0-b6-staging
git push origin electron-v0.9.0-b6-staging

# Fix electron-builder.yml config
git commit -m "fix: correct staging API URL"

# Retry with iteration
git tag electron-v0.9.0-b6-staging.1
git push origin electron-v0.9.0-b6-staging.1
```

#### Scenario 3: Missing Icon in Package
```bash
# First release - icon not included
git tag electron-v0.9.0-b6-staging
git push origin electron-v0.9.0-b6-staging

# Add icon to build resources
git commit -m "fix: add missing app icon"

# Retry with iteration
git tag electron-v0.9.0-b6-staging.2
git push origin electron-v0.9.0-b6-staging.2
```

## Decision Tree

```
Do you need to change the compiled code?
├─ YES → Increment version in build.sbt
│  └─ What kind of change?
│     ├─ Breaking change → Major version (A)
│     ├─ New feature → Minor version (B)
│     ├─ Bug fix → Patch version (X)
│     └─ Beta iteration (still pre-release) → Beta number (N)
│
└─ NO → Just packaging/config change
   └─ Use release iteration (.Y)
      Examples:
      - GitHub Actions workflow fixes
      - electron-builder.yml changes
      - CSP policy updates
      - Asset/resource updates
      - Metadata corrections
```

## Practical Examples

### Example 1: Adding a New Feature (During Beta)

**Changes:**
- Add new chimney type calculation
- Update UI components
- Add new form fields

**Version strategy (still in beta):**
```bash
# Update build.sbt: b6 → b7 (next beta iteration)
git commit -am "feat: add new chimney type calculation"
git tag electron-v0.9.0-b7-staging
git push origin electron-v0.9.0-b7-staging
```

**Result:** `electron-v0.9.0-b6-staging` → `electron-v0.9.0-b7-staging`

### Example 1b: Adding a New Feature (After Stable Release)

**Changes:**
- Add new chimney type calculation (after electron-v0.9.0 stable is released)

**Version strategy (new minor version):**
```bash
# Update build.sbt: 0.9.0 → 0.10.0-b1 (new minor, start with beta)
git commit -am "feat: add new chimney type calculation"
git tag electron-v0.10.0-b1-staging
git push origin electron-v0.10.0-b1-staging
```

**Result:** `electron-v0.9.0` (stable) → `electron-v0.10.0-b1-staging`

---

### Example 2: Fixing a Bug (During Beta)

**Changes:**
- Fix calculation error in pressure loss
- No new features

**Version strategy (still in beta):**
```bash
# Update build.sbt: b6 → b7 (continue beta cycle)
git commit -am "fix: correct pressure loss calculation"
git tag electron-v0.9.0-b7-staging
git push origin electron-v0.9.0-b7-staging
```

**Result:** `electron-v0.9.0-b6-staging` → `electron-v0.9.0-b7-staging`

### Example 2b: Fixing a Bug (After Stable Release)

**Changes:**
- Critical bug in stable electron-v0.9.0 release

**Version strategy (patch the stable release):**
```bash
# Update build.sbt: 0.9.0 → 0.9.1-b1 (patch with beta)
git commit -am "fix: correct pressure loss calculation"
git tag electron-v0.9.1-b1-staging
git push origin electron-v0.9.1-b1-staging

# After testing, release stable patch
git tag electron-v0.9.1
git push origin electron-v0.9.1
```

**Result:** `electron-v0.9.0` (stable) → `electron-v0.9.1-b1-staging` → `electron-v0.9.1` (stable)

---

### Example 3: CI/CD Configuration Fix

**Changes:**
- Fix GitHub Actions workflow syntax
- Update electron-builder.yml CSP policy
- **No code changes**

**Version strategy:**
```bash
# NO build.sbt changes
git commit -am "fix: correct GitHub Actions syntax and CSP policy"
git tag electron-v0.9.0-b6-staging.1
git push origin electron-v0.9.0-b6-staging.1
```

**Result:** `electron-v0.9.0-b6-staging` → `electron-v0.9.0-b6-staging.1`

---

### Example 4: Multiple Packaging Iterations

**Day 1:**
```bash
git tag electron-v0.9.0-b6-staging
git push origin electron-v0.9.0-b6-staging
# Build fails - missing dependency
```

**Day 1 - Fix 1:**
```bash
git commit -am "fix: add missing electron-builder dependency"
git tag electron-v0.9.0-b6-staging.1
git push origin electron-v0.9.0-b6-staging.1
# Build succeeds but icon is wrong
```

**Day 2 - Fix 2:**
```bash
git commit -am "fix: update app icon with correct branding"
git tag electron-v0.9.0-b6-staging.2
git push origin electron-v0.9.0-b6-staging.2
# Build succeeds, ready for testing
```

**Result:** `electron-v0.9.0-b6-staging` → `-staging.1` → `-staging.2`

## Environment Suffixes

### Staging (`-staging`)

**Purpose:** Pre-production testing

**Backend:** `https://api.staging.firecalc.afpma.pro`

**Tag format:** `electron-v*-staging*`

**Release type:** Draft (private)

**Use when:**
- Testing new features before production
- Validating bug fixes
- User acceptance testing
- Internal team testing

**Example tags:**
- `electron-v0.9.0-b6-staging` (beta 6 for Electron app v0.9.0)
- `electron-v0.9.0-b6-staging.1` (beta 6, packaging fix)
- `electron-v0.9.0-b7-staging` (beta 7 for Electron app v0.9.0)
- `electron-v0.9.0-staging` (stable release candidate for Electron app v0.9.0)

---

### Production (no suffix)

**Purpose:** Public release

**Backend:** `https://api.firecalc.afpma.pro`

**Tag format:** `electron-v*` (without `-staging`)

**Release type:** Public

**Use when:**
- Stable, tested code
- Ready for end users
- Fully validated features

**Example tags:**
- `electron-v1.0.0` (stable Electron app, no beta)
- `electron-v1.0.1` (stable patch)
- `electron-v1.1.0` (stable minor)
- `electron-v1.0.0-b1` (beta for Electron app v1.0.0, if needed)

## Best Practices

### DO ✅

1. **Use release iteration (.Y) for packaging fixes**
   - Faster iteration
   - No code recompilation needed
   - Same binary artifacts, different packaging

2. **Increment semantic version for code changes**
   - Clear version history
   - Proper semantic versioning
   - Users understand the impact

3. **Test in staging before production**
   - `electron-v0.9.0-b6-staging` → test → `electron-v0.9.0` (production)

4. **Document release notes**
   - Explain what changed
   - List known issues
   - Provide upgrade path

### DON'T ❌

1. **Don't increment patch version for packaging fixes**
   - ❌ `electron-v0.9.0` → `electron-v0.9.1` (just for workflow fix)
   - ✅ `electron-v0.9.0-staging` → `electron-v0.9.0-staging.1`

2. **Don't skip staging**
   - ❌ Directly tag production without staging test
   - ✅ Test in staging first, then promote

3. **Don't reuse tags**
   - ❌ Delete and recreate the same tag
   - ✅ Create new iteration tag

4. **Don't mix changes**
   - ❌ Code changes + workflow fixes in same commit
   - ✅ Separate commits for different concerns

## Summary Table

| Component | Location | Requires Code Change | Use Case | Example |
|-----------|----------|---------------------|----------|---------|
| Module prefix | Git tag only | N/A | Identify module | `electron-` (desktop app) |
| Major (A) | build.sbt | ✅ Yes | Breaking changes | `v0.9.0` → `v1.0.0` |
| Minor (B) | build.sbt | ✅ Yes | New features | `v0.9.0` → `v0.10.0` |
| Patch (X) | build.sbt | ✅ Yes | Bug fixes | `v0.9.0` → `v0.9.1` |
| Beta (bN) | build.sbt | ✅ Yes | Beta iterations | `b6` → `b7` (pre-release) |
| Release (.Y) | Git tag only | ❌ No | Packaging/config | `-staging` → `-staging.1` |

## Related Documentation

- **[VERSIONING_SYSTEM.md](./VERSIONING_SYSTEM.md)** - Runtime version display in the UI
- **[GITHUB_ACTIONS_RELEASES.md](./GITHUB_ACTIONS_RELEASES.md)** - CI/CD workflow and release automation
- **[STAGING.md](./STAGING.md)** - Staging environment setup and deployment
- **[Electron Packaging](./electron/PACKAGING_AND_DISTRIBUTION.md)** - Desktop app packaging guide