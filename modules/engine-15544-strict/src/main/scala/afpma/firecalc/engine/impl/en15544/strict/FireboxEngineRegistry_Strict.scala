/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.alg.en15544.FireboxEngineAny
import afpma.firecalc.engine.alg.en15544.FireboxEngineRegistry
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.en15544.firebox.{AFPMA_PRSE, TraditionalFirebox}
import afpma.firecalc.engine.models.en15544.std.Firebox_15544

/**
 * The EN 15544 **strict** firebox engine registry (shared, ticket 16).
 *
 * Austrian-free by construction: the two proprietary kinds (`Ecolabeled`,
 * `Door15aCatalog`) register the generic remote-only carriers
 * ([[RemoteOnlyFireboxes_Strict.EcolabeledCarrier]] /
 * [[RemoteOnlyFireboxes_Strict.Door15aCatalogCarrier]]) through
 * `FireboxEngineAny.foldedOnly` — they own no per-term constraint algebra and
 * serve only what the remote `POST /v1/calculate` response provides. The
 * non-Austrian kinds keep their local generic engines via `from`.
 *
 * This is the ONLY public strict registry, identical on the JVM and Scala.js
 * (the ticket-11 platform split was retired with the Austrian JVM instances):
 * the entry ORDER and kind coverage are invariant 7, so `withEngine`'s
 * positional resolution and `checkTotality()` behave the same everywhere. No
 * proprietary Austrian model, constraint, formula, transformer, registration,
 * or engine symbol is referenced here, so none can reach any public artifact.
 */
object FireboxEngineRegistry_Strict
    extends FireboxEngineRegistry[
        FireboxPipe_Module_15544.FullDescr,
        CombustionAirPipe_Module_15544.FullDescr
    ]:

    val name: String = "FireboxEngineRegistry_Strict"

    import RemoteOnlyFireboxes_Strict.{EcolabeledCarrier, Door15aCatalogCarrier}

    val all: List[FireboxEngineAny[
        FireboxPipe_Module_15544.FullDescr,
        CombustionAirPipe_Module_15544.FullDescr
    ]] = List(
        FireboxEngineAny.foldedOnly[EcolabeledCarrier],
        FireboxEngineAny.from[TraditionalFirebox],
        FireboxEngineAny.from[AFPMA_PRSE],
        FireboxEngineAny.from[Firebox_15544.SingleTested],
        FireboxEngineAny.foldedOnly[Door15aCatalogCarrier],
        FireboxEngineAny.from[Firebox_15544.Traditional.CustomForLab]
    )

    locally { checkTotality() }
