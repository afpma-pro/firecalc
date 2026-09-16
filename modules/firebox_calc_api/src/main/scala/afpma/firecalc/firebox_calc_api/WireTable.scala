/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

final case class WireTable(
    header    : Option[String],
    colHeaders: Option[List[String]],
    rows      : List[List[String]]
)
