<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Contributing to FireCalc AFPMA

Thank you for your interest in contributing to FireCalc AFPMA! This document provides guidelines for contributing to the project.

## License Agreement

By contributing to FireCalc AFPMA, you agree that your contributions will be licensed under the [GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later)](LICENSE).

### What This Means

- Your code contributions become part of the AGPLv3 licensed codebase
- Users of the software (including over a network) have the right to the source code
- Derivative works must also be licensed under AGPLv3
- You retain copyright to your contributions while granting the project rights to use them under AGPLv3

## Code Standards

### License Headers (Enforced)

**⚠️ MANDATORY**: All new files MUST include the appropriate license header.

**Automatic Addition:**
- VSCode users: Install `psi-header` extension (configured in `.vscode/settings.json`)
- Headers are auto-added when you save files
- No manual work needed for VSCode users

**Manual Addition:**
See [`LICENSE-HEADER-TEMPLATE.txt`](LICENSE-HEADER-TEMPLATE.txt) for all templates.

**Scala files example:**
```scala
/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
```

**Configuration files example:**
```conf
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
```

**Enforcement:**
- ✅ Pre-commit hook blocks commits without headers
- ✅ GitHub Actions CI checks all PRs
- ✅ Cannot merge without valid headers

**If CI Check Fails:**
1. Add missing headers (use VSCode psi-header or copy from template)
2. Commit and push again
3. CI will automatically re-check

See [docs/ci/LICENSE_CHECK_CI.md](docs/ci/LICENSE_CHECK_CI.md) for troubleshooting.

### Code Style

- Follow the project's existing code style
- Use `scalafmt` for Scala code formatting
- Run `make format` before committing (if available)
- Follow SOLID, DRY, KISS, and YAGNI principles

### Documentation

- Document public APIs and complex logic
- Update relevant documentation when changing functionality
- Keep README and other docs up to date

## Contribution Workflow

### First-Time Setup

**Install git hooks (required):**
```bash
./scripts/install-git-hooks.sh
```

This installs a pre-commit hook that automatically checks for license headers. See [`scripts/git-hooks/README.md`](scripts/git-hooks/README.md) for details.

### Making Contributions

1. **Fork the repository** and create a feature branch
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**
   - Add license headers to new files (VSCode psi-header extension does this automatically)
   - Write clear, documented code
   - Add tests for new functionality

3. **Test your changes**
   ```bash
   # Run tests
   sbt test
   
   # Run specific module tests
   sbt "ui/test"
   sbt "payments/test"
   ```

4. **Commit with sign-off**
   ```bash
   git add .
   git commit -s -m "feat: add new feature"
   # Pre-commit hook automatically checks license headers
   ```

5. **Push and create a Pull Request**
   ```bash
   git push origin feature/your-feature-name
   ```

6. **CI Checks (Automatic)**
   - GitHub Actions automatically checks your PR for license headers
   - All files must have valid AGPL-3.0-or-later headers
   - CI will fail if headers are missing - see job summary for fix instructions
   - See [docs/ci/LICENSE_CHECK_CI.md](docs/ci/LICENSE_CHECK_CI.md) for details

### Automated Enforcement

This project uses **layered license header enforcement**:

1. **VSCode Extension** (Optional): `psi-header` auto-adds headers when you save files
2. **Pre-commit Hook** (Required): Checks headers before allowing commits
3. **GitHub Actions CI** (Required): Validates headers on all PRs

**All three layers must pass** for your contribution to be merged.

## Commit Message Guidelines

Use conventional commit format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Example:**
```
feat(payments): add support for EUR currency

Implemented EUR currency support in the payments module.
Updated invoice generation to handle EUR formatting.

Closes #123
Signed-off-by: Your Name <your.email@example.com>
```

## AGPLv3 Specific Considerations

### Network Use Clause

This project includes a network service (payments backend). Under AGPLv3:

- Users interacting over a network must have access to source code
- When modifying the backend, ensure source availability mechanisms remain intact
- Document any network-facing changes

### Dependency Compatibility

When adding dependencies, ensure they are compatible with AGPLv3:

**✅ Compatible licenses:**
- MIT, Apache 2.0, BSD (permissive)
- LGPL, GPL (copyleft, but AGPLv3 is stricter)
- Other AGPLv3 code

**❌ Incompatible licenses:**
- Proprietary licenses
- Some restrictive Creative Commons licenses

## Questions?

- 📧 Contact: [AFPMA](https://www.afpma.pro)
- 📖 Read the full license: [`LICENSE`](LICENSE)
- 🔍 Check existing issues and merge requests

## Code of Conduct

- Be respectful and professional
- Focus on constructive feedback
- Help create a welcoming environment for all contributors

---

Thank you for contributing to FireCalc AFPMA! 🔥