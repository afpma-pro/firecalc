/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Load quantity identifier. `wire` is the normative OpenAPI enum value
 * (`WireLoadQty` in `openapi/firebox-calc-api.yaml`) and is the single source
 * of truth for the JSON codec — see `Codecs.wireCodec`.
 *
 * Mirrors the engine's `LoadQty` vocabulary exactly
 * (`engine-kernel/.../models/params.scala`).
 */
enum WireLoadQty(val wire: String):
    case Nominal extends WireLoadQty("nominal")
    case Reduced extends WireLoadQty("reduced")
