# Documentation Audit & Creation Plan

## Context

FireCalc has **21 modules** but only **4 have a README.md** and **2 have an ARCHITECTURE.md**. Root-level docs (README, ARCHITECTURE, CONTRIBUTING) are solid. The `docs/dev/` tree is comprehensive. The gap is primarily at module level — 16 modules have zero documentation. Additionally, existing docs have concrete issues: broken links, missing module references, and references to non-existent directories.

**Goal**: Every module gets a README.md + ARCHITECTURE.md (where warranted), all existing docs are verified against current code, and a tracking document orchestrates the whole process.

---

## Phase 0: Create Tracking Document

Create `docs/DOCUMENTATION_AUDIT.md` — the central checklist for the entire audit.

Structure: 4 parts (Modules, Root docs, docs/dev/ files, Other docs), each with checkboxes `[ ]` / `[x]` / `[!]` (needs attention). Every item gets a status, action (Review/Create), and verified date.

This file is created first and updated as each doc is processed.

---

## Phase 1: Fix Known Issues (Quick Wins)

Before starting the systematic audit, fix the **already-identified problems**:

1. **`modules/payments/README.md`** — Remove broken links to `PRODUCT_CATALOG_TESTING.md` and `PRODUCT_CATALOG_UI_INTEGRATION.md` (these files don't exist)
2. **`docs/README.md`** — Fix broken links to `dev/design/` and `dev/testing/` (directories don't exist)
3. **`ARCHITECTURE.md` (root)** — Missing 4 modules: `catalog`, `graph`, `viz`, `xlsx_catalog` — add them

---

## Phase 2: Module Documentation (Batched by Complexity)

For each module: read source code, verify against `build.sbt` dependencies, and write/review docs.

### Templates

**Full README.md** (for Tier 1-3 modules): Purpose, key concepts, dependencies (from build.sbt), compilation target, quick start commands, source structure, related docs.

**Full ARCHITECTURE.md** (for Tier 1-2 modules only): Design overview, component structure, data flow, key design decisions, external dependencies.

**Lightweight README.md** (for Tier 4-5 modules): Purpose, structure (file list), how it works, dependencies, compilation. No separate ARCHITECTURE.md — architecture notes folded inline.

### Reference patterns
- Best existing README: `modules/payments/README.md` (269 lines)
- Best existing ARCHITECTURE: `modules/invoices/ARCHITECTURE.md` (122 lines)

### Batch 1 — Core Modules (highest impact)

| Module | Files | Lines | Action |
|--------|-------|-------|--------|
| **engine** | 220 | 34,960 | Review README.md, **Create ARCHITECTURE.md** |
| **dto** | 86 | 10,069 | **Create README.md + ARCHITECTURE.md** |
| **ui** | 136 | 21,227 | **Create README.md + ARCHITECTURE.md**, review existing CONFIG.md |
| **payments** | 77 | 12,417 | Review README.md (fix broken links), review ARCHITECTURE.md |

### Batch 2 — Functional Modules

| Module | Files | Lines | Action |
|--------|-------|-------|--------|
| **invoices** | 16 | 2,847 | Review README.md + ARCHITECTURE.md |
| **reports** | 6 | 787 | **Create README.md + ARCHITECTURE.md** |
| **catalog** | 6 | 450 | **Create README.md + ARCHITECTURE.md** |
| **xlsx_catalog** | 16 | 1,657 | Review README.md, **Create ARCHITECTURE.md** |
| **payments-shared** | 5 | 449 | **Create README.md + ARCHITECTURE.md** |

### Batch 3 — Infrastructure Modules

| Module | Files | Lines | Action |
|--------|-------|-------|--------|
| **units** | 5 | 858 | **Create README.md + ARCHITECTURE.md** |
| **utils** | 9 | 554 | **Create README.md + ARCHITECTURE.md** |
| **i18n** | 5 | 5,477 | **Create README.md + ARCHITECTURE.md** (hub doc for i18n family) |
| **i18n-utils** | 4 | 379 | **Create README.md** (link to i18n ARCHITECTURE) |

### Batch 4 — I18n Leaf Modules + Visualization (lightweight READMEs)

| Module | Files | Lines | Action |
|--------|-------|-------|--------|
| **ui-i18n** | 3 | 969 | **Create lightweight README.md** |
| **payments-i18n** | 3 | 471 | **Create lightweight README.md** |
| **payments-shared-i18n** | 4 | 297 | **Create lightweight README.md** |
| **invoices-i18n** | 3 | 316 | **Create lightweight README.md** |
| **viz** | 5 | 702 | **Create README.md** (architecture inline) |
| **graph** | 5 | 281 | **Create README.md** (architecture inline) |

### Batch 5 — Test Modules (lightweight READMEs)

| Module | Files | Lines | Action |
|--------|-------|-------|--------|
| **fdim** | 13 | 1,179 | **Create lightweight README.md** |
| **labo** | 5 | 1,156 | **Create lightweight README.md** |

### i18n Family Strategy

The `i18n` module gets the authoritative ARCHITECTURE.md describing the full pipeline (HOCON → sbt source generator → Babel runtime). All leaf i18n modules (ui-i18n, payments-i18n, etc.) get lightweight READMEs that link back to `../i18n/ARCHITECTURE.md`. No duplicate architecture docs.

---

## Phase 3: Root-Level Documentation Review

| File | Lines | Action |
|------|-------|--------|
| `README.md` | 201 | Verify commands, update module links to include all documented modules |
| `ARCHITECTURE.md` | 371 | Add missing modules (catalog, graph, viz, xlsx_catalog), verify dependency graph vs build.sbt |
| `CONTRIBUTING.md` | 203 | Verify workflow accuracy |
| `CLAUDE.md` | 208 | Verify commands, module list matches reality |

---

## Phase 4: docs/dev/ Documentation Review

For each file: verify all commands work, all file paths exist, all architectural claims match source.

### Core dev docs
| File | Lines | Action |
|------|-------|--------|
| `docs/dev/README.md` | 128 | Fix links, update to reference all module docs |
| `docs/dev/SCHEMA_VERSIONING_ARCHITECTURE.md` | 704 | Verify against current DTO version packages |
| `docs/dev/TRANSLATION_AUTOMATION_SPEC.md` | 552 | Verify against current i18n pipeline |
| `docs/dev/WINDOWS_CI_SBT_COURSIER_FILE_LOCKS.md` | 250 | Verify CI config still relevant |

### Developer guides
| File | Lines | Action |
|------|-------|--------|
| `docs/dev/guides/I18N.md` | 1,098 | Verify against current i18n module structure |
| `docs/dev/guides/MAKEFILE_REFERENCE.md` | 962 | Verify all make targets exist in Makefile |
| `docs/dev/guides/PRODUCTION.md` | 566 | Verify deployment steps |
| `docs/dev/guides/STAGING.md` | 431 | Verify staging workflow |
| `docs/dev/guides/VERSIONING_AND_RELEASE_STRATEGY.md` | 484 | Verify against current versioning |
| `docs/dev/guides/VERSIONING_SYSTEM.md` | 110 | Verify, check for overlap with above |
| `docs/dev/guides/DB_SQLITE3_MIGRATION_GUIDE.md` | 401 | Verify against current Flyway usage |
| `docs/dev/guides/GITHUB_ACTIONS_RELEASES.md` | 207 | Verify against `.github/workflows/` |
| `docs/dev/guides/OPEN_SOURCE_PRIVACY_SETUP.md` | 245 | Verify |
| `docs/dev/guides/DIRECTION_PROPAGATION.md` | 131 | Verify against current UI direction components |
| `docs/dev/guides/GOCARDLESS.md` | 20 | **Needs rewrite** — currently raw CLI notes, not documentation |

### Electron guides (7 files)
| File | Lines | Action |
|------|-------|--------|
| `docs/dev/guides/electron/README.md` | 138 | Verify |
| `docs/dev/guides/electron/QUICKSTART.md` | 173 | Verify |
| `docs/dev/guides/electron/DESKTOP_APP.md` | 322 | Verify |
| `docs/dev/guides/electron/LIVE_RELOAD.md` | 342 | Verify |
| `docs/dev/guides/electron/FILE_SYSTEM_INTEGRATION.md` | 312 | Verify |
| `docs/dev/guides/electron/AUTO_UPDATE.md` | 171 | Verify |
| `docs/dev/guides/electron/PACKAGING_AND_DISTRIBUTION.md` | 494 | Verify |

---

## Phase 5: Other Documentation Review

| File | Lines | Action |
|------|-------|--------|
| `docs/README.md` | 53 | Fix broken links to `dev/design/` and `dev/testing/` |
| `docs/LICENSE.md` | 95 | Verify |
| `docs/DEPENDENCY_LICENSE_AUDIT.md` | 221 | Verify deps still accurate |
| `docs/ci/LICENSE_CHECK_CI.md` | 435 | Verify against current CI workflows |
| `docs/user/README.md` | 27 | Decide: flesh out or leave as stub |
| `docs/user/INSTALLATION_GUIDE.md` | 373 | Verify against current releases |
| `configs/README.md` | 295 | Verify against current config structure |
| `docker/README.md` | 158 | Verify |
| `docker/CONFIG_SETUP.md` | 500 | Verify |
| `web/README.md` | 96 | Verify |
| `web/electron-app/build/README.md` | 32 | Verify |
| `scripts/git-hooks/README.md` | 105 | Verify |

---

## Execution Strategy

- **Subagents per batch**: Each batch of modules gets a subagent that reads all source files and writes/reviews the docs
- **Verification per doc**: Every factual claim (file paths, commands, dependencies, class names) checked against source
- **Tracking updates**: After each batch, update this tracking document
- **build.sbt is the authority**: All dependency claims verified against `build.sbt` `dependsOn` clauses
- **plans/ as context source**: Subagents should explore the `plans/` directory for additional codebase understanding. It contains implementation plans, PRDs, and design documents that were considered or executed at various points. These are **not authoritative** (code is the source of truth), but they provide valuable context about design intent, feature rationale, and historical decisions that can inform documentation writing

### Model Assignments

Orchestration (main conversation) runs on **Opus**. Subagents use the model best suited to task complexity:

| Batch / Phase | Model | Rationale |
|---------------|-------|-----------|
| Batch 1 — Core (engine, dto, ui, payments) | **Opus** | Large codebases (10K-35K lines), ARCHITECTURE.md creation, design reasoning |
| Batch 2 — Functional (reports, catalog, xlsx_catalog, payments-shared, invoices) | **Opus** | Architectural understanding needed for new ARCHITECTURE.md files |
| Batch 3 — Infrastructure (units, utils, i18n, i18n-utils) | **Sonnet** | Smaller scope, well-defined templates |
| Batch 4 — I18n leaves + viz (ui-i18n, payments-i18n, etc., viz, graph) | **Sonnet** | Lightweight READMEs, repetitive pattern |
| Batch 5 — Test modules (fdim, labo) | **Sonnet** | Lightweight READMEs |
| Phase 1 — Fix known issues | **Sonnet** | Mechanical fixes (broken links, missing entries) |
| Phase 3 — Root doc review | **Sonnet** | Verification against build.sbt, link checking |
| Phase 4 — docs/dev/ review | **Sonnet** | Mechanical verification (commands, paths, links) |
| Phase 5 — Other docs review | **Sonnet** | Mechanical verification |

Usage: `Agent(model="opus", ...)` or `Agent(model="sonnet", ...)` — the `model` parameter overrides per-subagent, no config change needed.

## Verification

After all docs are written/reviewed:
1. Run `grep -r 'PRODUCT_CATALOG_TESTING\|PRODUCT_CATALOG_UI_INTEGRATION\|dev/design\|dev/testing' docs/ modules/` to confirm no broken links remain
2. Verify every module under `modules/` has a `README.md`
3. Verify root ARCHITECTURE.md lists all 21 modules
4. Verify `docs/README.md` links all resolve

## Counts

| Category | Total | Create | Review | Known Issues |
|----------|-------|--------|--------|-------------|
| Module README.md | 21 | 16 | 5 | 1 (payments broken links) |
| Module ARCHITECTURE.md | 13* | 11 | 2 | — |
| Root docs | 4 | 0 | 4 | 1 (ARCHITECTURE missing modules) |
| docs/dev/ files | 18 | 0 | 18 | 2 (GOCARDLESS stub, dev/README broken links) |
| Other docs | 12 | 0 | 12 | 1 (docs/README broken links) |
| **Total** | **68** | **27** | **41** | **5** |

*\*8 small modules get architecture notes folded into README.md instead of a separate file*
