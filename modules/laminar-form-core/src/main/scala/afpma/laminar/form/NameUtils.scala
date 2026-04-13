/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

/** Inlined from i18n-utils — pure 5-line function, no dependency needed. */
object NameUtils:
    def titleCase(string: String): String =
        string
            .filter(_.isLetter)
            .split("(?=[A-Z])")
            .map(_.capitalize)
            .mkString(" ")
