/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.engine.alg.en15544.FireboxEngine
import afpma.firecalc.engine.models.CombustionAirPipe_Module_13384
import afpma.firecalc.engine.models.FireboxPipe_Module_13384
import afpma.firecalc.engine.models.en15544.std.Firebox_15544

/**
 * Binds [[FireboxEngine]]'s descriptor type members to the EN 13384 thermal pipe
 * descriptors used by the MCE variant.
 *
 * The strict counterpart binds the flow-only `_15544` descriptors instead. That
 * divergence is the whole reason the two registries are separate objects rather
 * than one shared list.
 */
trait FireboxEngine_15544_MCE[-F <: Firebox_15544] extends FireboxEngine[F]:
    override type FireboxPipe_FullDescr       = FireboxPipe_Module_13384.FullDescr
    override type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_13384.FullDescr
