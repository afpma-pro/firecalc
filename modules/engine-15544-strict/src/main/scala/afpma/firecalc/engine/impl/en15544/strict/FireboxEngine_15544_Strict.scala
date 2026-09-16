/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.alg.en15544.FireboxEngine
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544

/**
 * Binds [[FireboxEngine]]'s descriptor type members to the EN 15544 strict
 * (flow-only) pipe descriptors.
 *
 * Replaces `FireboxToInternalPipes_15544_Strict` as the place where the strict
 * variant is chosen: previously the binding lived on a separate pipe-conversion
 * typeclass, now it lives on the bundled engine. Bound once here, never by a
 * per-kind instance — a per-kind binding is what would let the two standards
 * drift apart silently.
 */
trait FireboxEngine_15544_Strict[-F <: Firebox_15544] extends FireboxEngine[F]:
    override type FireboxPipe_FullDescr       = FireboxPipe_Module_15544.FullDescr
    override type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_15544.FullDescr

object FireboxEngine_15544_Strict:

    /** The strict descriptor pair, named once for registry declarations. */
    type FD = FireboxPipe_Module_15544.FullDescr
    type CD = CombustionAirPipe_Module_15544.FullDescr
