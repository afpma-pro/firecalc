/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.schema.v4.AppStateSchema_V4

// Alias to V4
type AppStateSchema = AppStateSchema_V4

object AppStateSchema:

    val LATEST_VERSION: Int = 4

    export AppStateSchema_V4.{given, *}
