/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import afpma.firecalc.dto.all.FireCalc_Version
import afpma.firecalc.dto.v4.FireCalcYAML_V4
import afpma.firecalc.dto.v5.FireCalcYAML_V5

/**
 * Version constants and migration for .fcalc-db catalog files.
 * Versions use the same FireCalc_Version type and numbering as project YAML files.
 * Starting version: V4 (in sync with FireCalcYAML_V4).
 * When a future version changes catalog entry types, add version-specific
 * migration in CatalogParser.decodeAndMigrate using dto Chimney transformers.
 */
object CatalogMigrations:
    /** Current catalog file format version. */
    val CURRENT_VERSION: FireCalc_Version = FireCalcYAML_V5.VERSION

    /** Oldest version that can be migrated. V1/V2/V3 catalogs never existed. */
    val OLDEST_SUPPORTED_VERSION: FireCalc_Version = FireCalcYAML_V4.VERSION
