/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

/** The source of firebox engine computations (spec \S2.3, \S5.1). */
enum FireboxEngineSource:
    /** Compute locally via the in-process Austrian engine (default). */
    case Local

    /** Compute via a remote Firebox Calculation API server at `baseUri`. */
    case Remote(baseUri: String)
