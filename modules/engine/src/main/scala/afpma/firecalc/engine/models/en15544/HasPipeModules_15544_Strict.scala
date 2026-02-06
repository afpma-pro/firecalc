/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithFlowOnlyAirIntake

trait HasPipeModules_15544_Strict
    extends HasPipeModules_15544Only_Strict
    with HasPipeModules_13384_WithFlowOnlyAirIntake
