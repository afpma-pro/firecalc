/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import utest.*

/**
 * Pins the single `.fcalc` naming convention ([[ProjectFileName.of]]) that both
 * download paths go through: `filename_var` (the toolbar/Save export of the OPEN
 * project) and `ProjectExport.downloadStoredState` (the error dialog's "download the
 * saved file" action for a project that will not open).
 *
 * Exact strings are asserted, never a re-implementation: a recovery download has to be
 * named just as an export of the same project would be, so the two can never drift.
 * The recipe is locale-free — the localized fallback name is an argument — so no i18n
 * and no DOM is needed here.
 */
object ProjectFileNameSpec extends TestSuite:

    val tests = Tests:

        // ── the incident: a stored index name gets the export's name ──────────

        test("a stored name is sanitized exactly as the toolbar export sanitizes a reference") {
            assert(ProjectFileName.of("Recovery probe", "project") == "Recovery_probe.fcalc")
        }

        // ── empty name: the caller's default, i.e. default_names.project ──────

        test("an empty stored name falls back to the name the caller resolved") {
            // the en and fr default_names.project values
            assert(ProjectFileName.of("", "project") == "project.fcalc")
            assert(ProjectFileName.of("", "projet") == "projet.fcalc"  )
        }

        test("only a truly empty name falls back") {
            // The fallback triggers on an empty stored name only: a blank one is a name,
            // sanitizes to nothing, and yields the bare extension — as the export always did.
            assert(ProjectFileName.of("   ", "project") == ".fcalc")
        }

        // ── the sanitizer: non-alphanumerics → '_', runs collapsed, ends trimmed ──

        test("punctuation and spacing collapse to single underscores, letters keep their accents") {
            // 'é' is a letter and survives; '—', '°', '!' and the spaces are not
            assert(ProjectFileName.of("  café — n°7!!  ", "project") == "café_n_7.fcalc")
        }

        test("an all-punctuation name yields the bare extension") {
            // Parity with the toolbar export, which has always produced ".fcalc" for such
            // a name — not a designed outcome, just the one convention holding.
            assert(ProjectFileName.of("?!-", "project") == ".fcalc")
        }

end ProjectFileNameSpec
