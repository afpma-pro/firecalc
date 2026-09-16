/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Calculation standard variant. `wire` is the normative OpenAPI enum value
 * (`WireStandardVariant` in `openapi/firebox-calc-api.yaml`).
 */
enum WireStandardVariant(val wire: String):
    case Strict extends WireStandardVariant("strict")
    case Mce    extends WireStandardVariant("mce")
