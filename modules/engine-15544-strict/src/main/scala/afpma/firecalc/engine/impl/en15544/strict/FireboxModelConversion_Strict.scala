/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.dto.all.Firebox
import afpma.firecalc.engine.models.en15544.std.Firebox_15544

/**
 * DTO→engine-model conversion (shared, ticket 16).
 *
 * The public artifact links NO proprietary Austrian engine model, so the two
 * Austrian kinds convert to the generic remote-only carriers
 * ([[RemoteOnlyFireboxes_Strict.EcolabeledCarrier]] /
 * [[RemoteOnlyFireboxes_Strict.Door15aCatalogCarrier]]) built from the public
 * sealed DTO subtypes. The non-Austrian kinds use the generic chimney
 * transformers from `FireboxTransformers` unchanged. The match stays exhaustive
 * over the five sealed DTO subtypes; adding a sixth breaks compilation
 * intentionally (invariant 9 — no catch-all).
 */
object FireboxModelConversion_Strict:

    def toEngineModel(dto: Firebox): Firebox_15544 =
        import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers
        dto match
            case x: Firebox.Traditional            =>
                FireboxTransformers.transformer_Standard_TraditionalFirebox.transform(x)
            case x: Firebox.Ecolabeled             =>
                RemoteOnlyFireboxes_Strict.EcolabeledCarrier(x)
            case x: Firebox.AFPMA_PRSE             =>
                FireboxTransformers.transformer_AFPMA_PRSE.transform(x)
            case x: Firebox.SingleTested           =>
                afpma.firecalc.engine.models.en15544.firebox.single_tested.transformer_SingleTested.transform(x)
            case x: Firebox.Door15aFirebox_Catalog =>
                RemoteOnlyFireboxes_Strict.Door15aCatalogCarrier(x)

end FireboxModelConversion_Strict
