/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import afpma.firecalc.dto.all.FireCalc_Version

/** Version constants and migration for .fcalc-db catalog files.
  * Versions use the same FireCalc_Version type and numbering as project YAML files.
  * Starting version: V4 (in sync with FireCalcYAML_V4).
  * Future migrations will reuse dto Chimney transformers per category.
  */
object CatalogMigrations:
    val CURRENT_VERSION: FireCalc_Version = FireCalc_Version(4)
