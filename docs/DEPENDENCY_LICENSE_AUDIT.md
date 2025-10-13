<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Dependency License Audit - AGPLv3 Compatibility

This document analyzes the compatibility of third-party dependencies with the AGPLv3 license.

**Last Updated**: 2025-01-10  
**Project License**: AGPL-3.0-or-later

## Summary

✅ **All major dependencies are compatible with AGPLv3**

The project primarily uses permissive licenses (MIT, Apache 2.0, BSD) which are compatible with AGPLv3.

## License Compatibility Reference

### ✅ Compatible with AGPLv3

- **MIT License** - Permissive, widely compatible
- **Apache License 2.0** - Permissive with patent grant
- **BSD Licenses** (2-Clause, 3-Clause) - Permissive
- **ISC License** - Permissive, similar to MIT
- **Python-2.0** - Permissive (for Python components)

### ⚠️ Special Considerations

- **GPL/LGPL** - Compatible but with specific requirements
- **EPL** - Eclipse Public License, generally compatible

### ❌ Incompatible with AGPLv3

- Proprietary licenses
- No-commercial clauses
- No-derivatives clauses

## Core Dependencies Analysis

### Scala/JVM Dependencies (build.sbt)

| Dependency | Version | License | Status | Notes |
|---|---|---|---|---|
| **Scala Standard Library** | 3.7.3 | Apache 2.0 | ✅ Compatible | Core language |
| **Cats Core** | 2.13.0 | MIT | ✅ Compatible | Functional programming |
| **Cats Effect** | 3.6.1 | Apache 2.0 | ✅ Compatible | Effect system |
| **Circe** (core, generic, parser) | 0.14.13 | Apache 2.0 | ✅ Compatible | JSON library |
| **Circe YAML** | 0.16.0 | Apache 2.0 | ✅ Compatible | YAML support |
| **http4s** (server, client, dsl) | 0.23.30 | Apache 2.0 | ✅ Compatible | HTTP library |
| **Chimney** | 1.8.2 | Apache 2.0 | ✅ Compatible | Transformers |
| **Coulomb** (core, units) | 0.8.0 | Apache 2.0 | ✅ Compatible | Units library |
| **Kittens** | 3.5.0 | Apache 2.0 | ✅ Compatible | Cats derivation |
| **Quicklens** | 1.9.12 | Apache 2.0 | ✅ Compatible | Lens library |
| **Magnolia** (fork) | 1.3.16 | Apache 2.0 | ✅ Compatible | Derivation macro |
| **Babel** (i18n) | 0.5.3 | Apache 2.0 | ✅ Compatible | Internationalization |
| **ScalaTest** | 3.2.19 | Apache 2.0 | ✅ Compatible | Testing framework |
| **Logback** | 1.5.18 | EPL 1.0 / LGPL 2.1 | ✅ Compatible | Logging |
| **Log4Cats** | 2.7.1 | Apache 2.0 | ✅ Compatible | Logging facade |
| **ScalaTags** | 0.13.1 | MIT | ✅ Compatible | HTML generation |
| **Typesafe Config** | 1.4.3 | Apache 2.0 | ✅ Compatible | Configuration |
| **Emil** (email) | 0.15.0 | MIT | ✅ Compatible | Email library |
| **ScalaMolecule** | 0.25.1 | Apache 2.0 | ✅ Compatible | Database library |
| **Flyway** | 11.10.4 | Apache 2.0 | ✅ Compatible | Database migrations |
| **SQLite JDBC** | 3.50.3.0 | Apache 2.0 | ✅ Compatible | Database driver |
| **uTest** | 0.9.0 | MIT | ✅ Compatible | Testing framework |
| **OS-Lib** | 0.11.4 | MIT | ✅ Compatible | OS utilities |
| **Java-Typst** | 1.4.0 | MIT | ✅ Compatible | Typst integration |

### Scala.js Dependencies

| Dependency | Version | License | Status | Notes |
|---|---|---|---|---|
| **Scala.js** | 1.x | Apache 2.0 | ✅ Compatible | JS compilation |
| **Laminar** | 17.2.1 | MIT | ✅ Compatible | Reactive UI |
| **Airstream** | 17.2.1 | MIT | ✅ Compatible | FRP library |
| **Waypoint** | 9.0.0 | MIT | ✅ Compatible | Routing |
| **uPickle** | 4.1.0 | MIT | ✅ Compatible | Serialization |
| **scala-java-time** | 2.6.0 | BSD-3-Clause | ✅ Compatible | Time library for JS |
| **Coulomb Parser** | 0.8.0 | Apache 2.0 | ✅ Compatible | Units parsing |

### Node.js/NPM Dependencies (UI Module)

Based on [`modules/ui/package.json`](../modules/ui/package.json):

| Dependency | Version | License | Status | Notes |
|---|---|---|---|---|
| **Vite** | ^7.1.7 | MIT | ✅ Compatible | Build tool |
| **@scala-js/vite-plugin-scalajs** | ^1.1.0 | Apache 2.0 | ✅ Compatible | Scala.js integration |
| **TailwindCSS** | ^4.1.13 | MIT | ✅ Compatible | CSS framework |
| **@tailwindcss/typography** | ^0.5.19 | MIT | ✅ Compatible | Typography plugin |
| **@tailwindcss/vite** | ^4.1.13 | MIT | ✅ Compatible | Vite integration |
| **DaisyUI** | ^5.1.23 | MIT | ✅ Compatible | UI components |
| **TypeScript** | ^5.9.2 | Apache 2.0 | ✅ Compatible | Type checking |
| **vite-plugin-singlefile** | ^2.3.0 | MIT | ✅ Compatible | Single file build |

### Electron Dependencies

Based on [`web/package.json`](../web/package.json):

| Dependency | Version | License | Status | Notes |
|---|---|---|---|---|
| **Electron** | ^38.2.1 | MIT | ✅ Compatible | Desktop framework |
| **electron-updater** | ^6.1.7 | MIT | ✅ Compatible | Auto-update |
| **electron-builder** | ^25.1.8 | MIT | ✅ Compatible | Build tool |

## Transitive Dependencies

### Node Modules Analysis

The npm dependencies have their own dependencies. Based on [`web/package-lock.json`](../web/package-lock.json), common licenses found:

- **MIT** - Most common, fully compatible
- **ISC** - Permissive, compatible
- **BSD** variants - Compatible
- **Apache 2.0** - Compatible
- **Python-2.0** - Compatible (in some tools)
- **BlueOak-1.0.0** - Permissive, compatible
- **WTFPL** - Permissive, compatible
- **CC0-1.0** - Public domain, compatible

✅ **No incompatible licenses detected in Node dependencies**

## Special Cases

### 1. Electron (MIT License)

Electron is MIT licensed and compatible. Note:
- Electron includes Chromium (BSD) and Node.js (MIT)
- All compatible with AGPLv3
- Distribution includes these components

### 2. Custom Magnolia Fork

The project uses a custom fork of Magnolia:
- Original: Apache 2.0 (compatible)
- Fork maintained by project: `pro.afpma" %%% "magnolia" % "1.3.16`
- Remains Apache 2.0 licensed
- ✅ Compatible

### 3. ScalablyTyped Converter

Used for TypeScript definitions:
- License: MIT
- ✅ Compatible
- Only used at build time

## Recommendations

### ✅ Current State: Compliant

All dependencies are compatible with AGPLv3. No action required.

### Future Dependency Additions

When adding new dependencies:

1. **Check the license** - Use tools like:
   ```bash
   # For npm packages
   npm info <package-name> license
   
   # For Scala/sbt
   sbt dependencyLicenseInfo
   ```

2. **Verify compatibility** - Ensure license is permissive or compatible
   - ✅ MIT, Apache 2.0, BSD, ISC → Always OK
   - ⚠️ GPL, LGPL → Usually OK, check specifics
   - ❌ Proprietary, non-commercial → Never OK

3. **Document the license** - Add to this audit document

### License Check Commands

```bash
# Check npm dependencies
cd modules/ui
npm list --depth=0

# Check licenses with license-checker
npx license-checker --summary

# For Scala dependencies
sbt "show update"
```

## Compliance Checklist

- [x] All direct dependencies reviewed
- [x] All licenses documented
- [x] No incompatible licenses found
- [x] Transitive dependencies spot-checked
- [x] Build tools verified compatible
- [x] Runtime dependencies verified compatible

## Resources

- **SPDX License List**: https://spdx.org/licenses/
- **OSI Approved Licenses**: https://opensource.org/licenses
- **AGPLv3 Compatibility**: https://www.gnu.org/licenses/license-list.html
- **Dependency License Tool**: https://www.npmjs.com/package/license-checker

## Updates

This document should be reviewed when:
- Adding new dependencies
- Updating major versions
- Before major releases
- Quarterly (recommended)

---

**Status**: ✅ **All dependencies AGPLv3-compatible**  
**Last Audit**: 2025-01-10  
**Next Review**: 2025-04-10 (suggested)