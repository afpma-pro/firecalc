/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.common.FireCalc_Version
import afpma.firecalc.dto.v7.*

// alias to V7
type FireCalcYAML = FireCalcYAML_V7

object FireCalcYAML extends FireCalcYAML_V7_Module:

    val LATEST_VERSION: FireCalc_Version = FireCalcYAML_V7.VERSION
