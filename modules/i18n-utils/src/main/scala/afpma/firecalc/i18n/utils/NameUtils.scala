/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n.utils

object NameUtils {

  def titleCase(string: String): String =
    string
      .filter(_.isLetter)
      .split("(?=[A-Z])")
      .map(_.capitalize)
      .mkString(" ")
}