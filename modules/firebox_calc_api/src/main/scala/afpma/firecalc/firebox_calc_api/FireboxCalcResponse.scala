/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/** Wire-stable API contract. Any shape change requires a matching OpenAPI document change in the same commit. */
final case class FireboxCalcResponse(
    apiVersion               : ApiVersion,
    kind                     : WireFireboxKind,
    locale                   : WireLocale,
    fireboxContribution      : WireValidated[WireFireboxContribution],
    combustionAirContribution: WireValidated[WireFireboxContribution],
    constraints              : List[ConstraintVerdict],
    reportData               : FireboxReportData
)
