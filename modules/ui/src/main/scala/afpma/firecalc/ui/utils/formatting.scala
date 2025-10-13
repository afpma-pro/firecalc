/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

extension (d: Double)

    /** Prints only necessary chars when formatting doubles 
     * - remove trailing zeros, dot, or comma
     * - format using maxPrecision
    */
    def formatPrecise(maxPrecision: Int = 6): String =
        val formatted = String.format(s"%.${maxPrecision}f", d)
        if (formatted.contains('.') || formatted.contains(','))
            formatted
                .replaceAll("0*$", "")
                .replaceAll("\\.$", "")
                .replaceAll("\\,$", "")
        else
            formatted
