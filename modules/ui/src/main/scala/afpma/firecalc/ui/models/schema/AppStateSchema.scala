/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.schema.v5.AppStateSchema_V5

// Alias to V5
type AppStateSchema = AppStateSchema_V5

object AppStateSchema:

    val LATEST_VERSION: Int = AppStateSchema_V5.VERSION.unwrap

    export AppStateSchema_V5.{given, *}
