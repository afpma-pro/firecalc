<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# License Guide

FireCalc AFPMA is licensed under **GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later)**.

- 📄 **Full License**: [`../LICENSE`](../LICENSE)
- 🤝 **Contributing**: [`../CONTRIBUTING.md`](../CONTRIBUTING.md)

## What is AGPLv3?

AGPLv3 grants four essential freedoms:

1. **Use** the software for any purpose
2. **Study** and modify the source code  
3. **Redistribute** copies
4. **Distribute modifications**

### Network Use Clause (Critical)

If you run a modified version as a network service, you **must** provide source code to all network users. This applies to the **payments backend module**.

## Requirements

### ✅ You Can
- Use for personal/commercial purposes
- Modify the source code
- Distribute original or modified copies
- Run as a network service (with source code availability)

### ⚠️ You Must
- Include LICENSE file with distributions
- Provide complete source code
- Provide source to network users (see below)
- Document modifications
- License derivatives under AGPLv3
- Preserve copyright notices

### ❌ You Cannot
- Remove license notices
- Claim warranty
- Use different license for derivatives

## Network Service Compliance

For the **payments backend** (`modules/payments/`):

**Implementation example:**
```scala
case GET -> Root / "source" =>
  Ok("""FireCalc AFPMA - Licensed under AGPLv3
       |Source: https://github.com/afpma-pro/firecalc
       |Version: [version]
       |""".stripMargin)
```

See [`modules/payments/AGPLV3_NETWORK_COMPLIANCE.md`](../modules/payments/AGPLV3_NETWORK_COMPLIANCE.md) for details.

## Compatible Dependencies

✅ **Compatible**: MIT, Apache 2.0, BSD, ISC, LGPL, GPL  
❌ **Incompatible**: Proprietary, non-commercial, no-derivatives licenses

See [`DEPENDENCY_LICENSE_AUDIT.md`](DEPENDENCY_LICENSE_AUDIT.md) for full audit.

## Contributing

All contributors must:
1. Sign commits with DCO: `git commit -s`
2. Add license headers to new files (see [`LICENSE-HEADER-TEMPLATE.txt`](../LICENSE-HEADER-TEMPLATE.txt))
3. License contributions under AGPLv3

Pre-commit hook automatically checks headers - install with:
```bash
./scripts/install-git-hooks.sh
```

## Resources

- **AGPLv3**: https://www.gnu.org/licenses/agpl-3.0.html
- **FAQ**: https://www.gnu.org/licenses/gpl-faq.html#AGPLv3
- **SPDX**: https://spdx.org/licenses/

## Questions?

- General: https://www.afpma.pro
- Issues: Project repository
- Legal: Consult a lawyer (this is informational only)

---

**Copyright © 2025 Association Française du Poêle Maçonné Artisanal**  
Licensed under [AGPL-3.0-or-later](../LICENSE)