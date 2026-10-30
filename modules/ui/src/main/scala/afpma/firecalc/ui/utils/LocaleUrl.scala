/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import io.taig.babel.Language

/**
 * Reads the locale segment out of a URL fragment hash.
 *
 * Why a URL read exists: inside Airstream's own propagation Transaction — the one
 * the route render, panel construction and error-dialog show all run in —
 * `Var.now()` returns the PRE-transaction value, even for the root Var written on
 * the previous line. Show-time (error-dialog) locale resolution therefore reads
 * the live URL instead: the URL is the single locale authority (the Router
 * rewrites its locale segment on every route render) and can never be
 * transaction-stale.
 *
 * Grammar mirrors the Router's fragment routes (`Router.scala`,
 * `fragmentBasePath = pathname + "/#"`): the selector route is `/{lang}`, the
 * project route `/{lang}/project/{id}`. So the hash must be `#/` followed by the
 * locale segment; the first segment must be exactly `"en"` or `"fr"`. Anything
 * else — the locale-less root route (`""`, `"#"`, `"#/"`), the no-slash legacy
 * form (`"#fr"`), an unknown or miscased code (`"#/de"`, `"#/english"`,
 * `"#/EN"`) — is None, and the caller falls back to its own default.
 *
 * Stays pure (no DOM import) so the grammar is JVM-testable: callers pass
 * `dom.window.location.hash`.
 */
object LocaleUrl:

    /** The locale segment of a fragment hash, e.g. `"#/en/project/abc"` → `en`. */
    def languageFromHash(hash: String): Option[Language] = hash.split("/") match
        case Array("#", "en", _*) => Some(Language("en"))
        case Array("#", "fr", _*) => Some(Language("fr"))
        case _                    => None

end LocaleUrl
