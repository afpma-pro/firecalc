/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.models.AppStateSchemaHelper

import utest.*

/**
 * Unit tests for the two reads of a persisted project state: the three-way decoding one
 * ([[ProjectStorage.outcomeOfRaw]] and its [[ProjectDecodeMemo]]) and the verbatim one
 * ([[ProjectStorage.storedPayload]], what a download hands over untouched).
 *
 * Pure by construction: both mappings take the stored payload as an argument and the
 * memo holds plain values, so no localStorage / DOM is needed to pin the three cases
 * the load path branches on — absent, present-but-unreadable, readable — nor the
 * one-decode-per-payload rule that keeps a page-load from decoding the same blob
 * twice, nor the single "nothing stored" rule both reads share.
 */
object ProjectStorageSpec extends TestSuite:

    private def storedYaml(reference: String): String =
        val base  = AppStateSchemaHelper.createInitialSchema()
        val descr = base.engine_state.project_description.copy(reference = reference)
        AppStateSchemaHelper
            .encodeToYaml(base.copy(engine_state = base.engine_state.copy(project_description = descr)))
            .get

    val tests = Tests:

        // ── readable: a payload that decodes is Ok, and its content survives ──

        test("readable payload decodes to Ok") {
            ProjectStorage.outcomeOfRaw(storedYaml("readable-probe")) match
                case ProjectLoadOutcome.Ok(schema) =>
                    assert(schema.engine_state.project_description.reference == "readable-probe")
                case outcome                       =>
                    assert(false)
        }

        // ── corrupt: present-but-unreadable is NOT the same as absent ────────

        test("undecodable bytes are Corrupt") {
            assert(ProjectStorage.outcomeOfRaw("{ invalid: yaml: structure: [[[[ }}") == ProjectLoadOutcome.Corrupt)
        }

        test("a schema version the app cannot read is Corrupt") {
            assert(
                ProjectStorage.outcomeOfRaw(
                    "version: 999\nengine_state:\n  version: 999\n"
                ) == ProjectLoadOutcome.Corrupt
            )
        }

        test("a payload whose version reads but whose body does not is Corrupt") {
            assert(ProjectStorage.outcomeOfRaw("version: 7\nengine_state: 42\n") == ProjectLoadOutcome.Corrupt)
        }

        // ── missing: nothing stored under that key ───────────────────────────

        test("no stored value is Missing") {
            assert(ProjectStorage.outcomeOfRaw(null) == ProjectLoadOutcome.Missing)
        }

        test("an empty stored value is Missing") {
            assert(ProjectStorage.outcomeOfRaw("") == ProjectLoadOutcome.Missing)
        }

        test("a whitespace-only stored value is Missing") {
            assert(ProjectStorage.outcomeOfRaw("   \n  \t  \n   ") == ProjectLoadOutcome.Missing)
        }

        // ── verbatim: the read a download uses, bytes untouched ──────────────

        test("a stored payload is handed back verbatim, trailing newline and all") {
            val payload = storedYaml("verbatim-probe") + "\n"
            assert(ProjectStorage.storedPayload(payload) == Some(payload))
        }

        test("an undecodable payload is handed back verbatim too (no decode, no repair)") {
            val payload = "{ invalid: yaml: structure: [[[[ }}\n"
            assert(ProjectStorage.storedPayload(payload) == Some(payload))
        }

        test("nothing stored is no bytes to hand over") {
            assert(ProjectStorage.storedPayload(null) == None              )
            assert(ProjectStorage.storedPayload("") == None                )
            assert(ProjectStorage.storedPayload("   \n  \t  \n   ") == None)
        }

        // ── decode memo: one decode per payload, never a stale outcome ───────

        test("the same payload for the same id is a hit") {
            val memo    = new ProjectDecodeMemo
            val id      = ProjectId("probe-id")
            val payload = "some payload"

            assert       (memo.get(id, payload) == None                            )
            memo.remember(id, payload, ProjectLoadOutcome.Corrupt                  )
            assert       (memo.get(id, payload) == Some(ProjectLoadOutcome.Corrupt))
        }

        test("a rewritten payload misses (a save is never masked)") {
            val memo = new ProjectDecodeMemo
            val id   = ProjectId("probe-id")

            memo.remember(id, "payload-before", ProjectLoadOutcome.Corrupt)
            assert       (memo.get(id, "payload-after") == None           )
        }

        test("another project's payload misses") {
            val memo = new ProjectDecodeMemo

            memo.remember(ProjectId("id-a"), "payload", ProjectLoadOutcome.Corrupt)
            assert       (memo.get(ProjectId("id-b"), "payload") == None          )
        }

        test("clearing forgets the stored payload (delete)") {
            val memo    = new ProjectDecodeMemo
            val id      = ProjectId("probe-id")
            val payload = "some payload"

            memo.remember(id, payload, ProjectLoadOutcome.Corrupt)
            memo.clear   (                                       )
            assert       (memo.get(id, payload) == None          )
        }

end ProjectStorageSpec
