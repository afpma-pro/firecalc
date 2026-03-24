/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.common.FireCalc_Version
import afpma.firecalc.dto.v5.*

// alias to V5
type FireCalcYAML = FireCalcYAML_V5

object FireCalcYAML extends FireCalcYAML_V5_Module:

    val LATEST_VERSION: FireCalc_Version = FireCalcYAML_V5.VERSION
