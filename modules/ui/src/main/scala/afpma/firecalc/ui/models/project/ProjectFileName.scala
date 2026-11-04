/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.payments.shared.Constants.FIRECALC_FILE_EXTENSION

/**
 * The app's ONE `.fcalc` download-name convention: every file FireCalc hands the user
 * goes through [[ProjectFileName.of]], so a recovery download is named exactly like an
 * export of the same project and re-imports through the "Open a file" filter that only
 * accepts `.fcalc`.
 */
object ProjectFileName:

    /**
     * Sanitize `storedName` (every non-alphanumeric char → `_`, runs collapsed,
     * leading/trailing `_` trimmed), then append [[FIRECALC_FILE_EXTENSION]]. An empty
     * stored name falls back to `defaultName`, which the CALLER resolves from
     * `default_names.project` through its own i18n path: the recipe itself stays
     * locale-free, so it is unit-testable with no i18n and no DOM.
     *
     * NEVER derive the name from [[ProjectEntry.displayName]] — that substitutes the
     * localized "Untitled", which the index contract keeps a display-time concern.
     */
    def of(storedName: String, defaultName: String): String =
        val fname_noext = Option
            .when(storedName.nonEmpty)(storedName)
            .getOrElse(defaultName)
            .map(c => if c.isLetterOrDigit then c else '_')
            .replaceAll("_+", "_")
            .stripPrefix("_")
            .stripSuffix("_")
        s"$fname_noext$FIRECALC_FILE_EXTENSION"
