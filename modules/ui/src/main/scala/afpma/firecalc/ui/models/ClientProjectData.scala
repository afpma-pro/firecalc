/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.ui.models.schema.v1.ClientProjectData_V1

/**
 * Client-side only data containing sensitive customer information.
 * This data is NEVER sent to the backend.
 * 
 * Type alias to the latest version (V1).
 */
type ClientProjectData = ClientProjectData_V1

object ClientProjectData:
    export ClientProjectData_V1.{given, *}