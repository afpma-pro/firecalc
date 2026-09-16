/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

enum WirePipeShape:
    case Circle(diameter: WireQty)
    case Square(side: WireQty)
    case Rectangle(a: WireQty, b: WireQty)
