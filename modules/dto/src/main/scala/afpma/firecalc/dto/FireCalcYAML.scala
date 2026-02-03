/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.v3.*
import afpma.firecalc.dto.common.FireCalc_Version

// alias to V3
type FireCalcYAML = FireCalcYAML_V3

object FireCalcYAML extends FireCalcYAML_V3_Module:

    val LATEST_VERSION: FireCalc_Version = FireCalc_Version(3)
