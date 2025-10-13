/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v2

import afpma.firecalc.dto.common.*

final case class FireCalcYAML_V2(
    version: FireCalc_Version
) extends FireCalcYAML_Format
