/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Draft condition identifier. `wire` is the normative OpenAPI enum value
 * (`WireDraftCondition` in `openapi/firebox-calc-api.yaml`) and is the single
 * source of truth for the JSON codec — see `Codecs.wireCodec`.
 *
 * Mirrors the engine's `DraftCondition` vocabulary exactly
 * (`engine-kernel/.../en13384/typedefs.scala`): the case names ARE the wire
 * strings, so the wire↔engine mapping is the identity on names.
 */
enum WireDraftCondition(val wire: String):
    case DraftMinOrPositivePressureMax extends WireDraftCondition("draftMinOrPositivePressureMax")
    case DraftMaxOrPositivePressureMin extends WireDraftCondition("draftMaxOrPositivePressureMin")
