/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common
import afpma.firecalc.units.coulombutils.*

trait FireboxI:
    def firebox_depth : Length
    def firebox_width : Length
    def firebox_height: Length
