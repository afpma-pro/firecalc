/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.engine.alg.en15544.FireboxEngineAny
import afpma.firecalc.engine.alg.en15544.FireboxEngineRegistry
import afpma.firecalc.engine.models.CombustionAirPipe_Module_13384
import afpma.firecalc.engine.models.FireboxPipe_Module_13384
import afpma.firecalc.engine.models.en15544.firebox.{AFPMA_PRSE, TraditionalFirebox}
import afpma.firecalc.engine.models.en15544.std.Firebox_15544

/**
 * The EN 15544 **MCE** firebox engine registry.
 *
 * Parameterised by the thermal `_13384` descriptor pair, where the strict
 * registry uses the flow-only `_15544` pair. Ticket 16 removed the two
 * proprietary Austrian kinds (`Ecolabeled`, `Door15aCatalog`) and their
 * instances from the public artifact — the private repository owns them now —
 * so the public MCE registry serves the four non-Austrian kinds, in the
 * established relative order (invariant 7).
 */
object FireboxEngineRegistry_MCE
    extends FireboxEngineRegistry[
        FireboxPipe_Module_13384.FullDescr,
        CombustionAirPipe_Module_13384.FullDescr
    ]:

    val name: String = "FireboxEngineRegistry_MCE"

    /**
     * Entry order mirrors the retired resolver's match-arm order (see strict),
     * with the two proprietary Austrian entries removed (ticket 16).
     */
    val all: List[FireboxEngineAny[
        FireboxPipe_Module_13384.FullDescr,
        CombustionAirPipe_Module_13384.FullDescr
    ]] = List(
        FireboxEngineAny.from[TraditionalFirebox],
        FireboxEngineAny.from[AFPMA_PRSE],
        FireboxEngineAny.from[Firebox_15544.SingleTested],
        FireboxEngineAny.from[Firebox_15544.Traditional.CustomForLab]
    )

    // No `checkTotality()` here: the closed taxonomy still names the two
    // proprietary kinds, but their MCE engines are private (ticket 16).
    // `withEngine` fails typed for the unregistered kinds — no catch-all
    // (invariant 9).
