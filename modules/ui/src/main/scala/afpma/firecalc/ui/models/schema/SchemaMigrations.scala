/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import io.scalaland.chimney.Transformer
import afpma.firecalc.ui.models.schema.v1.AppStateSchema_V1

/**
 * Schema migration transformers using Chimney.
 * Placeholder for future V1 → V2 migrations.
 */
object SchemaMigrations:

    /**
     * Future: Transformer from V1 to V2
     * 
     * Example implementation when V2 is created:
     * 
     * given Transformer[AppStateSchema_V1, AppStateSchema_V2] = 
     *     Transformer.define[AppStateSchema_V1, AppStateSchema_V2]
     *         .withFieldComputed(_.version, _ => AppStateSchema_Version(2))
     *         .withFieldComputed(_.new_field, _ => defaultValue)
     *         .buildTransformer
     */

    /**
     * Migrate to latest version.
     * Currently returns V1 as-is since it's the latest.
     */
    def migrateToLatest(schema: AppStateSchema_V1): AppStateSchema_V1 = schema