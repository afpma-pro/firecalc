/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import io.taig.babel.Language

import utest.*

/**
 * Unit tests for [[LocaleUrl]] — the pure grammar behind the URL-first
 * show-time locale resolution (`models.currentLocale`).
 *
 * Pure by construction: the helper takes the hash string, so no DOM is needed
 * to pin the grammar the Router's fragment routes produce.
 */
object LocaleUrlSpec extends TestSuite:

    val tests = Tests:

        // ── Accepted: the Router's fragment-route shapes ────────────────────

        test("project route resolves the locale segment") {
            assert(LocaleUrl.languageFromHash("#/en/project/abc") == Some(Language("en")))
        }

        test("selector route resolves the locale segment") {
            assert(LocaleUrl.languageFromHash("#/fr") == Some(Language("fr")))
        }

        test("extra trailing segments keep the first locale segment") {
            assert(LocaleUrl.languageFromHash("#/en/project/x/extra") == Some(Language("en")))
        }

        // ── Rejected: anything without a valid locale segment ──────────────

        test("empty hash") {
            assert(LocaleUrl.languageFromHash("") == None)
        }

        test("bare hash sign") {
            assert(LocaleUrl.languageFromHash("#") == None)
        }

        test("root route with empty segment") {
            assert(LocaleUrl.languageFromHash("#/") == None)
        }

        test("legacy no-slash form is not a route") {
            assert(LocaleUrl.languageFromHash("#fr") == None)
        }

        test("unknown language code") {
            assert(LocaleUrl.languageFromHash("#/de") == None)
        }

        test("language name instead of code") {
            assert(LocaleUrl.languageFromHash("#/english") == None)
        }

        test("case-mismatched code") {
            assert(LocaleUrl.languageFromHash("#/EN") == None)
        }

end LocaleUrlSpec
