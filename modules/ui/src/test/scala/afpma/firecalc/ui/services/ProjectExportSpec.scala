/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.services

import afpma.firecalc.ui.i18n.ErrorDialogLabels

import io.taig.babel.Locales

import scala.scalajs.js

import utest.*

/**
 * The refusal contract of the stored-bytes export path — `ProjectExport.storedStateOrRefusal`,
 * the decision `downloadStoredState` is built on, tested as the pure function it is.
 *
 * What is being pinned is the shape of a failure, because this path exists for projects
 * that cannot be opened and the surface offering it is the global error dialog: a project
 * with nothing stored, or a browser whose storage cannot be read at all, must produce one
 * inline line and no file, never a second dialog, a failed Future or an exception in the
 * caller. Naming is pinned too — the file a recovery hands over has to be named exactly
 * like an export of the same project would be (sanitized index-entry name, and
 * `default_names.project` when the entry is gone), never a localized placeholder.
 *
 * The expected strings are hand-copied from
 * `modules/ui-i18n/src/main/resources/i18n/en.conf` and the labels are resolved through
 * `ErrorDialogLabels.resolve`, exactly like the dialog does; that duplication is the point
 * (same discipline as [[afpma.firecalc.ui.i18n.ErrorDialogLabelsSpec]]): only a copy
 * taken from the bundles can catch the embedded payload going stale or a field mapped to
 * the wrong key. The naming recipe itself is asserted with concrete expected names, not by
 * re-running it.
 *
 * uTest, no DOM: the `ui` tests run on a plain Node.js jsEnv (build.sbt `Test / jsEnv`,
 * no jsdom), which is why this spec tests the decision and NOT
 * [[ProjectExport.downloadStoredState]] itself — that one needs a browser, where it is
 * checked by hand: store one `projects_index` entry plus a `project_<id>_app_state`
 * payload that does not decode, open that project, and the dialog's download action must
 * hand over a file whose bytes equal `localStorage.getItem("project_<id>_app_state")`.
 */
object ProjectExportSpec extends TestSuite:

    // ── what en.conf declares, copied verbatim (fr differs; the values are the point) ──
    private val noStoredState      = "There is no saved file for this project in this browser's storage."
    private val storageUnavailable =
        "This browser's storage could not be read: there is no saved file to download."
    private val defaultProjectName = "project"

    /** The never-throwing label path the dialog itself uses — no `I18N_UI` here. */
    private def labels(): ErrorDialogLabels = ErrorDialogLabels.resolve(Locales.en)

    /** A payload with a trailing newline: the byte-for-byte promise is what is asserted. */
    private val storedPayload = "project:\n  name: Recovery probe\n"

    /** A storage backend that is blocked/unavailable: `getItem` throws, it does not return. */
    private def blockedStorage(): Option[String] =
        throw new js.JavaScriptException(new js.Error("localStorage is blocked"))

    val tests = Tests:

        test("a stored payload is handed over verbatim, named from the index entry") {
            val outcome = ProjectExport.storedStateOrRefusal(Some(storedPayload), Some("Recovery probe"), labels())
            assert(outcome == Right("Recovery_probe.fcalc" -> storedPayload))
        }

        test("nothing stored is refused, and no file is named") {
            // Left carries only the message, so this equality is also the proof that no
            // name was invented for a file that will not be written.
            val outcome = ProjectExport.storedStateOrRefusal(None, Some("Recovery probe"), labels())
            assert(outcome == Left(noStoredState))
        }

        test("an absent index entry names the file from default_names.project") {
            val outcome = ProjectExport.storedStateOrRefusal(Some(storedPayload), None, labels())
            assert(outcome == Right(s"${defaultProjectName}.fcalc" -> storedPayload))
        }

        test("an unreadable storage is refused inline; nothing escapes to the caller") {
            var indexWasRead = false
            def indexName(): Option[String]                           =
                indexWasRead = true
                Some("Recovery probe")
            // None = the call threw rather than refused, which is the contract breach.
            val outcome    : Option[Either[String, (String, String)]] =
                try Some(ProjectExport.storedStateOrRefusal(blockedStorage(), indexName(), labels()))
                catch case _: Throwable => None
            assert(outcome == Some(Left(storageUnavailable)))
            // The by-name arguments are what keep the projects-index read off a storage
            // that is already known unreadable — it must not have been attempted.
            assert(indexWasRead == false)
        }

end ProjectExportSpec
