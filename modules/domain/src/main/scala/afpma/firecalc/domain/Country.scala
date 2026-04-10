/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

import cats.Show
import cats.derived.*

// TODO: AvailableCountries ?
enum Country derives Show:
    case France, Belgique, Autriche
