/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.schema.v6.AppStateSchema_V6

// Alias to V6
type AppStateSchema = AppStateSchema_V6

object AppStateSchema:

    val LATEST_VERSION: Int = AppStateSchema_V6.VERSION.unwrap

    export AppStateSchema_V6.{given, *}
