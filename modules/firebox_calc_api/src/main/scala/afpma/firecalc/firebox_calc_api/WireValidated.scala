// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

package afpma.firecalc.firebox_calc_api

sealed trait WireValidated[+A] extends Product with Serializable

object WireValidated:
    case class Valid[A](value: A)                      extends WireValidated[A]
    case class Invalid[A](errors: List[WireErrorItem]) extends WireValidated[A]
