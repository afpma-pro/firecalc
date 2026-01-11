/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.schema.v2.*

// Alias to V2
type AppStateSchema = AppStateSchema_V2

object AppStateSchema:

    val LATEST_VERSION: Int = 2
    
    export AppStateSchema_V2.{given, *}