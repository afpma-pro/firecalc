/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

/** The source of firebox engine computations (spec \S2.3, \S5.1). */
enum FireboxEngineSource:
    /**
     * Compute with the in-process engine registry. Sound only for the four
     * non-Austrian kinds (traditional, afpma-prse, single-tested,
     * custom-for-lab), which ship real local engines. `Ecolabeled` and
     * `Door15aCatalog` have NO in-process Austrian engine since the
     * ticket-16 cutover: `Local` selects their `foldedOnly` carriers —
     * no constraint algebra, no Austrian outputs — so it MUST NOT be
     * relied on for those kinds; configure them [[FireboxEngineSource.Remote]]
     * or disable them via the firebox-availability flags.
     */
    case Local

    /** Compute via a remote Firebox Calculation API server at `baseUri`. */
    case Remote(baseUri: String)
