/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/** Wire-stable API contract. Any shape change requires a matching OpenAPI document change in the same commit. */
final case class FireboxCalcRequest(
    apiVersion        : ApiVersion,
    kind              : WireFireboxKind,
    firebox           : afpma.firecalc.dto.v5.Firebox_V4,
    params            : Standard15544Params,
    airIntakePipeShape: Option[WirePipeShape],
    locale            : WireLocale,
    standard          : WireStandardVariant,
    operatingEnvelope : Option[WireOperatingEnvelope] = None
)
