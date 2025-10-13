/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import com.raquo.laminar.api.L.*

object Style {

  type Mod = Modifier[HtmlElement]

  def serifFont: Mod =
    fontFamily("Libre Baskerville")

  def header: Mod =
    cls("text-sm text-gray-400 mb-1")

  def boldHeader: Mod =
    cls("text-4xl text-orange-600 mb-4")

  def bodyText: Mod = ???

}
