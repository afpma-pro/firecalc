/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.v1.*

// alias to V1
type FireCalcYAML = FireCalcYAML_V1

object FireCalcYAML extends FireCalcYAML_V1_Module:

    val LATEST_VERSION: Int = 1