/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

enum DisplayUnits:
    case SI, Imperial

object DisplayUnits:

    def summon(using ev: DisplayUnits) = ev

    def fromString(str: String): Option[DisplayUnits] = 
        DisplayUnits.values.find(_.toString == str)

    type SI         = SI.type
    type Imperial   = Imperial.type
