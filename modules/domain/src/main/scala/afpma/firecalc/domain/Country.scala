/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

import afpma.firecalc.i18n.implicits.I18N

import cats.Show

import io.taig.babel.Locale

// TODO: AvailableCountries ?
// TODO: I18N translations
enum Country:
    case France, Belgique

object Country:
    given Locale => Show[Country] = Show.show:
        case Country.France   => I18N.country_names.france
        case Country.Belgique => I18N.country_names.belgique
