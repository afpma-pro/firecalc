/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

enum StandardOrComputationMethod(val reference: String):
    case EN_15544_2023 extends StandardOrComputationMethod("EN 15544:2023")