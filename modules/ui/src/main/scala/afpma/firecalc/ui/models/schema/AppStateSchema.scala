/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.schema.v3.*

// Alias to V3
type AppStateSchema = AppStateSchema_V3

object AppStateSchema:

    val LATEST_VERSION: Int = 3

    export AppStateSchema_V3.{given, *}
