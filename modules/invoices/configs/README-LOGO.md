<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Company Logo Configuration

## Overview

The invoice system uses a default logo located in this `configs` directory. This approach treats the logo as sensitive configuration data that should be customized per deployment.

## Usage

### Default Logo
- The system will automatically use `modules/invoices/configs/logo.png` as the default logo
- This file is included in `.gitignore` to prevent accidental commits of sensitive branding

### Replacing the Logo

To use your own company logo:

1. **Replace the file**: Simply replace `modules/invoices/configs/logo.png` with your company's logo
2. **Supported formats**: PNG format is recommended for best compatibility
3. **Recommended size**: 200x100 pixels or similar aspect ratio for optimal display

### Alternative Logo Paths

If you need to specify a different logo path programmatically:

```scala
val companyWithCustomLogo = company.copy(logo = Some("path/to/custom/logo.png"))
val config = CompanyConfig(companyWithCustomLogo, templateConfig)
```

### Fallback Behavior

The logo resolution follows this priority:
1. **Company.logo** - Explicit path in Company object
2. **CompanyConfig.defaultLogoPath** - Default path (`modules/invoices/configs/logo.png`)
3. **None** - No logo displayed

## Security Considerations

- The logo file is excluded from version control via `.gitignore`
- Store company logos securely and replace the default logo in production environments
- Consider using environment-specific configurations for different deployments

## Example

```scala
// Uses default logo from configs/logo.png
val company = Company(name = "ACME Corp", logo = None, ...)
val config = CompanyConfig(company, templateConfig)

// Uses explicit logo path
val companyWithLogo = Company(name = "ACME Corp", logo = Some("custom/path/logo.png"), ...)
val configWithCustom = CompanyConfig(companyWithLogo, templateConfig)
