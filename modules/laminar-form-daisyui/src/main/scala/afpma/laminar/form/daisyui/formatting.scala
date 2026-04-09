/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.daisyui

/** Inlined from ui.utils.formatting — removes trailing zeros from formatted doubles. */
extension (d: Double)
    def formatPrecise(maxPrecision: Int = 6): String =
        val formatted = String.format(s"%.${maxPrecision}f", d)
        if (formatted.contains('.') || formatted.contains(','))
            formatted
                .replaceAll("0*$", "")
                .replaceAll("\\.$", "")
                .replaceAll("\\,$", "")
        else
            formatted
