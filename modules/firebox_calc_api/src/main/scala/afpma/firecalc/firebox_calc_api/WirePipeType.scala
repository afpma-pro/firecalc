/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Pipe type classification. `wire` is the normative OpenAPI enum value
 * (`WirePipeType` in `openapi/firebox-calc-api.yaml`).
 */
enum WirePipeType(val wire: String):
    case AirIntake     extends WirePipeType("airIntake")
    case CombustionAir extends WirePipeType("combustionAir")
    case Firebox       extends WirePipeType("firebox")
    case Flue          extends WirePipeType("flue")
    case Connector     extends WirePipeType("connector")
    case Chimney       extends WirePipeType("chimney")
    case NoFlue        extends WirePipeType("noFlue")
