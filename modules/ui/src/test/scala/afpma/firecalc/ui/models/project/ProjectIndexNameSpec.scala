/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.models.{AppStateSchemaHelper, appStateSchemaVar}
import afpma.firecalc.ui.models.schema.{AppStateSchema, LocalStorageKeys}

import io.taig.babel.{Language, Locale}
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

import utest.*

/**
 * Pins the "one name per project" invariants of the projects index
 * ([[ProjectIndex]]):
 *
 *  - the entry's `name` is a strictly derived projection of the state's project
 *    reference, re-projected when a project is OPENED as well as on save;
 *  - storage NEVER carries a localized placeholder — an absent name is the empty
 *    string, so no language is frozen into localStorage;
 *  - the localized "Untitled" fallback exists only at display time, through the
 *    one shared helper ([[ProjectEntry.displayName]]), following the current
 *    display locale.
 *
 * The load/save paths genuinely touch `dom.window.localStorage`, so the suite
 * installs the same window stub the remote-prefetch specs use (plus `location.hash`,
 * which `currentLocale` reads first — an empty hash falls through to the root
 * state var, no navigator needed).
 */
object ProjectIndexNameSpec extends TestSuite:

    /** Fresh `window` + `localStorage` stub (bare Node test env) → its backing store. */
    private def installWindow(): mutable.Map[String, String] =
        val g = js.Dynamic.global.globalThis
        if js.isUndefined            (g.selectDynamic("window")) || g.selectDynamic("window") == null then
            g.updateDynamic("window")(js.Dynamic.literal()     )
        val store = mutable.Map[String, String]()
        dom.window.asInstanceOf[js.Dynamic].localStorage = js.Dynamic.literal(
            getItem    = ((k: String) => store.getOrElse(k, null)): js.Function1[String, String],
            setItem    = ((k: String, v: String) => { store.update(k, v) }): js.Function2[String, String, Unit],
            removeItem = ((k: String) => store.remove(k)): js.Function1[String, Unit]
        )
        dom.window.asInstanceOf[js.Dynamic].location     = js.Dynamic.literal(hash = "")
        store

    private def schemaWithReference(reference: String): AppStateSchema =
        val base  = AppStateSchemaHelper.createInitialSchema()
        val descr = base.engine_state.project_description.copy(reference = reference)
        base.copy(engine_state = base.engine_state.copy(project_description = descr))

    val tests = Tests:

        // ── sync on OPEN: the card name follows the state, no save required ──

        test("opening a project re-projects the state reference into the index name") {
            installWindow()
            val id = ProjectId("open_sync_id")
            ProjectStorage.save(id, schemaWithReference("Cheminée du salon")                                    )
            ProjectIndex.save  (Vector(ProjectEntry(id, "stale card name", lastModified = 1.0, createdAt = 1.0)))

            assert(ProjectManager.restoreProjectState(id))

            val entry = ProjectIndex.findEntry(id).get
            assert(entry.name == "Cheminée du salon")
            // Opening is not a modification: the lastModified stamp must NOT move.
            assert(entry.lastModified == 1.0        )
        }

        // ── empty reference → the empty string in storage, never a localized literal ──

        test("opening an unnamed project stores the empty string, clearing any stale placeholder") {
            installWindow()
            val id = ProjectId("open_empty_id")
            ProjectStorage.save(id, schemaWithReference("")                                              )
            // A row frozen by an older build: a localized literal in storage.
            ProjectIndex.save  (Vector(ProjectEntry(id, "Untitled", lastModified = 1.0, createdAt = 1.0)))

            assert(ProjectManager.restoreProjectState(id))

            assert(ProjectIndex.findEntry(id).get.name == "")
            val raw = dom.window.localStorage.getItem(LocalStorageKeys.PROJECTS_INDEX)
            assert(!raw.contains("Untitled") && !raw.contains("Sans titre"))
        }

        test("saving an unnamed project writes the empty string, never a localized placeholder") {
            installWindow()
            val id = ProjectId("save_empty_id")
            // The pre-fix save path stored currentI18N.project_selector.no_name here.
            ProjectIndex.save                    (Vector(ProjectEntry(id, "Sans titre", lastModified = 1.0, createdAt = 1.0)))
            appStateSchemaVar.set                (schemaWithReference("")                                                    )
            ProjectManager.activeProjectIdVar.set(Some(id)                                                                   )

            ProjectManager.saveCurrentProject()

            assert(ProjectIndex.findEntry(id).get.name == ""        )
            // The save path re-stamps the modification time (drives last-opened ordering).
            assert(ProjectIndex.findEntry(id).get.lastModified > 1.0)
        }

        // ── display time is the ONLY place the localized fallback exists ─────

        test("the shared display helper shows the English fallback for an empty name") {
            given Locale = Locale(Language("en"))
            val empty    = ProjectEntry(ProjectId("d1"), "", lastModified = 0.0, createdAt = 0.0)
            assert(empty.displayName == "Untitled"                                )
            assert(empty.copy(name = "Salon insert").displayName == "Salon insert")
        }

        test("the shared display helper follows the current display locale") {
            given Locale = Locale(Language("fr"))
            val empty    = ProjectEntry(ProjectId("d2"), "", lastModified = 0.0, createdAt = 0.0)
            assert(empty.displayName == "Sans titre"                                        )
            assert(empty.copy(name = "Cheminée du salon").displayName == "Cheminée du salon")
        }

end ProjectIndexNameSpec
