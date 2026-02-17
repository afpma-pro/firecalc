/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.common.FireCalc_Version
import afpma.firecalc.dto.v4.*

// alias to V4
type FireCalcYAML = FireCalcYAML_V4

object FireCalcYAML extends FireCalcYAML_V4_Module:

    val LATEST_VERSION: FireCalc_Version = FireCalc_Version(4)
