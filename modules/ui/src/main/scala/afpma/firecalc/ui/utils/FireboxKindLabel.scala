/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.alg.en15544.FireboxKind

import io.taig.babel.Locale

/**
 * Localized display name for a [[FireboxKind]].
 *
 * The per-kind availability messages (Q11) must name the kind the user selected,
 * and they must name it the same way the firebox picker does. Both read
 * `I18N.firebox_names`, mirroring `FireboxCalcService`'s server-side
 * `kindDisplayName`, so a message and the selector can never disagree.
 *
 * Exhaustive over `FireboxKind` including the parameterised `BlackBox`, whose
 * own `name` is its display name — no catch-all (invariant 9).
 */
object FireboxKindLabel:

    def apply(kind: FireboxKind)(using Locale): String = kind match
        case FireboxKind.Traditional    => I18N.firebox_names.traditional
        case FireboxKind.Ecolabeled     => I18N.firebox_names.ecolabeled
        case FireboxKind.AFPMA_PRSE     => I18N.firebox_names.afpma_prse
        case FireboxKind.SingleTested   => I18N.firebox_names.single_tested
        case FireboxKind.Door15aCatalog => I18N.firebox_names.door_15a_firebox
        case FireboxKind.CustomForLab   => I18N.firebox_names.custom_lab_tested
        case FireboxKind.BlackBox(name) => name

end FireboxKindLabel
