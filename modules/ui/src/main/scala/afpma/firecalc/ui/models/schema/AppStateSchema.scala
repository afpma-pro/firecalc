/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.schema.v1.*

// Alias to V1
type AppStateSchema = AppStateSchema_V1

object AppStateSchema:

    val LATEST_VERSION: Int = 1
    
    export AppStateSchema_V1.{given, *}