<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Git Hooks

This directory contains git hooks that enforce project standards.

## Pre-commit Hook

The `pre-commit` hook automatically checks all staged files for AGPL-3.0-or-later license headers before allowing a commit.

### Features

- ✅ Checks all human-generated source files for license headers
- ✅ Supports: Scala, JS/TS, SQL, Shell, Config, Typst, and more
- ✅ Respects exclusion patterns (node_modules, target, generated files, etc.)
- ✅ Provides clear error messages with header format examples
- ✅ Works for all developers regardless of IDE choice

### Installation

**For all developers:**

Run the installation script from the repository root:

```bash
./scripts/install-git-hooks.sh
```

This will:
1. Copy the pre-commit hook to `.git/hooks/`
2. Make it executable
3. Display confirmation and usage information

### Manual Installation

If you prefer to install manually:

```bash
cp scripts/git-hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

### Usage

The hook runs automatically on every commit:

```bash
git add my-file.scala
git commit -m "feat: add feature"
# ✓ License header check passed (1 files checked)
```

When headers are missing:

```bash
git add file-without-header.scala
git commit -m "fix: update logic"
# ✗ ERROR: Missing license headers in 1 file(s):
#    file-without-header.scala
# 
# [Shows header format examples]
```

### Bypassing the Hook

Not recommended, but possible:

```bash
git commit --no-verify
```

### Maintenance

When the hook is updated:

1. Modify `scripts/git-hooks/pre-commit`
2. All developers run `./scripts/install-git-hooks.sh` again to update their local copy

### Supported File Types

- **Scala** (`.scala`, `.sbt`) - C-style comments `/* */`
- **JavaScript/TypeScript** (`.js`, `.ts`, `.jsx`, `.tsx`) - C-style comments `/* */`
- **CSS** (`.css`) - C-style comments `/* */`
- **Shell Scripts** (`.sh`) - Hash comments `#`
- **Configuration** (`.conf`, `.hocon`, `.properties`) - Hash comments `#`
- **SQL** (`.sql`) - SQL comments `--`
- **Typst** (`.typ`) - Double-slash comments `//`
- **Makefiles** - Hash comments `#`

### Excluded Files

The hook automatically excludes:
- `node_modules/`, `target/`, `dist/`, `build/`, `out/`
- Generated files (`moleculeGen/`, `databases/`)
- Lock files, binary files (images, PDFs)
- JSON, YAML, Markdown, HTML (explicitly excluded from header requirements)

## Documentation

For more details, see:
- [`docs/LICENSE.md`](../../docs/LICENSE.md) - License guide
- [`LICENSE-HEADER-TEMPLATE.txt`](../../LICENSE-HEADER-TEMPLATE.txt) - Header templates for all file types
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) - Contribution guidelines