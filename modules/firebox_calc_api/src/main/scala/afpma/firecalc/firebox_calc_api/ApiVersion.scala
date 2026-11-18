/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

type ApiVersion = String

/**
 * Catalog of known wire-generation tokens. These are historical wire constants:
 * fixtures, DTO defaults, and discovery envelopes pin a SPECIFIC generation on
 * purpose. Production code that calls or serves the generation this build
 * implements uses [[FireboxApiVersion.CURRENT]] instead — never a token
 * directly (a caller hard-pinning a generation is exactly how the 1.0.2
 * version-mismatch skew reached users). The type alias stays un-deprecated:
 * it is the wire type of the `apiVersion` field itself.
 *
 * Token-to-generation alignment: the tokens were NOT aligned with the DTO
 * generations up to now. `"v1"` is the special case — a pre-alignment label
 * coined when the calculate API shipped, which today still carries the current
 * V7-generation payload (`FireCalcYAML = FireCalcYAML_V7`). [[ApiVersion.v7]]
 * below names that equivalence. From the V8 cutover the numbering REALIGNS:
 * the next token is `"v8"`, issued when the V8 DTOs land — `"v2"` through
 * `"v7"` are skipped and must never be issued as wire tokens.
 */
object ApiVersion:
    /**
     * The wire token, as actually transmitted, for the pre-alignment era
     * generation (the V7-era payload). This is the only spelling of that
     * generation that ever goes on the wire.
     */
    val v1: ApiVersion = "v1"

    /**
     * Alias of [[v1]]: documents that the legacy `"v1"` token labels the V7
     * DTO generation. NOT a wire token — `"v7"` is never transmitted; it
     * exists so the token↔generation equivalence reads off the catalog.
     */
    val v7: ApiVersion = v1
