/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * The API version served and consumed by this build.
 *
 * The value is the DTO generation the shared codecs and the OpenAPI document
 * (`enum: [v1]` in `openapi/firebox-calc-api.yaml`) serialize today — the
 * legacy `"v1"` token carrying the V7-era payload; see [[ApiVersion]] for the
 * token↔generation history. Every wire-shape change MUST bump it in the same
 * commit (repo rule; the V8 cutover sets it to `"v8"`, realigning token and
 * generation numbering). The OAS drift-guard test in `FireboxCalcContractSuite`
 * pins this constant to the OpenAPI document.
 *
 * The binding names the DTO GENERATION ([[ApiVersion.v7]]), not the wire
 * spelling — today that generation transmits as `"v1"`, and only the catalog
 * knows that. At the V8 cutover this line becomes `ApiVersion.v8` and the
 * alias carries the realigned token.
 */
object FireboxApiVersion:
    val CURRENT: ApiVersion = ApiVersion.v7
