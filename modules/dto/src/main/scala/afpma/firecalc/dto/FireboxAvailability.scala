/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto
import afpma.firecalc.dto.v5.Firebox_V4 as Firebox

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.domain.FireboxAvailability
import io.taig.babel.Locale

object FireboxAvailabilityExtensions:

    private val localizedTypeNames: Map[String, Locale ?=> String] = Map(
        "Traditional"            -> I18N.firebox_names.traditional,
        "Ecolabeled"             -> I18N.firebox_names.ecolabeled,
        "AFPMA_PRSE"             -> I18N.firebox_names.afpma_prse,
        "SingleTested"           -> I18N.firebox_names.single_tested,
        "Door15aFirebox_Catalog" -> I18N.firebox_names.door_15a_firebox
    )

    extension (fa: FireboxAvailability)
        def allows(fb: Firebox): Boolean = fb match
            case _: Firebox.Traditional            => fa.traditional
            case _: Firebox.Ecolabeled             => fa.ecolabeled
            case _: Firebox.AFPMA_PRSE             => fa.afpmaPrse
            case _: Firebox.SingleTested           => fa.singleTested
            case _: Firebox.Door15aFirebox_Catalog => fa.door15aCatalog

    extension (fb: Firebox)
        def typeName: String = fb match
            case _: Firebox.Traditional            => "Traditional"
            case _: Firebox.Ecolabeled             => "Ecolabeled"
            case _: Firebox.AFPMA_PRSE             => "AFPMA_PRSE"
            case _: Firebox.SingleTested           => "SingleTested"
            case _: Firebox.Door15aFirebox_Catalog => "Door15aFirebox_Catalog"

        def localizedTypeName: Locale ?=> String = fb.typeName.localizedTypeName

    extension (typeName: String)
        def localizedTypeName: Locale ?=> String =
            given loc: Locale = summon[Locale]
            localizedTypeNames
                .get(typeName)
                .map[String](_(using loc))
                .getOrElse(typeName)
