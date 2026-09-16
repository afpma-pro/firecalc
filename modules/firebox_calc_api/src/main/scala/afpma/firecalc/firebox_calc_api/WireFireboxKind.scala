/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Firebox kind identifier. `wire` is the normative OpenAPI enum value
 * (`WireFireboxKind` in `openapi/firebox-calc-api.yaml`) and is the single
 * source of truth for the JSON codec — see `Codecs.wireCodec`.
 */
enum WireFireboxKind(val wire: String):
    case Traditional    extends WireFireboxKind("traditional")
    case Ecolabeled     extends WireFireboxKind("ecolabeled")
    case AfpmaPrse      extends WireFireboxKind("afpmaPrse")
    case SingleTested   extends WireFireboxKind("singleTested")
    case Door15aCatalog extends WireFireboxKind("door15aCatalog")
    case CustomForLab   extends WireFireboxKind("customForLab")

    /**
     * Maps the wire kind value to the Firebox_V4 constructor name used in
     * wrapper-encoded JSON (circe semiauto derivation).
     * `CustomForLab` has no constructor in `Firebox_V4` and thus returns `None`.
     */
    def constructorName: Option[String] = this match
        case WireFireboxKind.Traditional    => Some("Traditional")
        case WireFireboxKind.Ecolabeled     => Some("Ecolabeled")
        case WireFireboxKind.AfpmaPrse      => Some("AFPMA_PRSE")
        case WireFireboxKind.SingleTested   => Some("SingleTested")
        case WireFireboxKind.Door15aCatalog => Some("Door15aFirebox_Catalog")
        case WireFireboxKind.CustomForLab   => None
