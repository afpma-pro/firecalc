/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.components.GlobalErrorDialog
import afpma.firecalc.ui.components.StorageWarningDialog
import afpma.firecalc.ui.services.CatalogImageStore
import afpma.firecalc.ui.services.VersionService
import afpma.firecalc.ui.views.*
import afpma.firecalc.ui.views.ProjectSelectorView

import com.raquo.laminar.api.L.*

import scala.scalajs.js
import scala.scalajs.js.`import`

import io.taig.babel.Locale
import org.scalajs.dom

object Frontend {

    import models.*

    lazy val writeUnifiedSchemaSubscription = appStateSchemaVar.signal.changes.distinct
        .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS)
        --> Observer[schema.AppStateSchema] { schemaVal =>
            import models.project.{ProjectManager, ProjectStorage, ProjectIndex}
            ProjectManager.activeProjectIdVar.now().foreach { id =>
                ProjectStorage.save(id, schemaVal)
                val name        = schemaVal.engine_state.project_description.reference
                val defaultName =
                    if name.nonEmpty then name else I18N_UI(using localeVar.now()).project_selector.no_name
                ProjectIndex.updateEntry(
                    id,
                    _.copy        (
                        name         = defaultName,
                        lastModified = scala.scalajs.js.Date.now()
                    )
                )
            }
        }

    lazy val writeCatalogSubscription = catalogStateVar.signal.changes.distinct
        .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS)
        --> Observer[CatalogState] { state =>
            try catalogWebStorageVar.set(state)
            catch
                case ex: js.JavaScriptException if models.project.LocalStorageUtils.isQuotaExceeded(ex) =>
                    val (usage, perKey) = models.project.LocalStorageUtils.estimateStorageUsage()
                    afpma.firecalc.ui.components.StorageWarningDialog.show(usage, perKey)
                case ex: js.JavaScriptException                                                         =>
                    dom.console.error(
                        s"[Frontend] Failed to save catalog state — localStorage unavailable: ${ex.getMessage}"
                    )
        }

    lazy val writeUIStateSubscription = uiStateVar.signal.changes.distinct
        .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS)
        --> Observer[UIState] { state =>
            try uiStateWebStorageVar.set(state)
            catch
                case ex: js.JavaScriptException if models.project.LocalStorageUtils.isQuotaExceeded(ex) =>
                    val (usage, perKey) = models.project.LocalStorageUtils.estimateStorageUsage()
                    afpma.firecalc.ui.components.StorageWarningDialog.show(usage, perKey)
                case ex: js.JavaScriptException                                                         =>
                    dom.console.error(
                        s"[Frontend] Failed to save UI state — localStorage unavailable: ${ex.getMessage}"
                    )
        }

    lazy val writeFireboxCacheSubscription = fireboxCacheStateVar.signal.changes.distinct
        .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS) --> Observer[FireboxCacheState] { cache =>
        try fireboxCacheWebStorageVar.set(cache)
        catch
            case ex: js.JavaScriptException if models.project.LocalStorageUtils.isQuotaExceeded(ex) =>
                val (usage, perKey) = models.project.LocalStorageUtils.estimateStorageUsage()
                afpma.firecalc.ui.components.StorageWarningDialog.show(usage, perKey)
            case ex: js.JavaScriptException                                                         =>
                dom.console.error(
                    s"[Frontend] Failed to save firebox cache — localStorage unavailable: ${ex.getMessage}"
                )
        models.project.ProjectManager.activeProjectIdVar.now().foreach { id =>
            models.project.ProjectManager.saveFireboxCache(id, cache)
        }
    }

    private val undoSnapshotObserver = Observer[schema.AppStateSchema](undoManager.pushSnapshot(_))

    lazy val undoSnapshotSubscription =
        appStateSchemaVar.signal.changes.distinct
            .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
            --> undoSnapshotObserver

    lazy val undoRedoKeyboardSubscription =
        documentEvents(_.onKeyDown)
            .filter { ev =>
                val dyn  = ev.asInstanceOf[scala.scalajs.js.Dynamic]
                val ctrl = dyn.ctrlKey
                val meta = dyn.metaKey
                // Guard: some events processed during Airstream transactions lack KeyboardEvent properties
                if scala.scalajs.js.isUndefined(ctrl) || scala.scalajs.js.isUndefined(meta) then false
                else
                    (ctrl.asInstanceOf[Boolean] || meta.asInstanceOf[Boolean]) &&
                    !scala.scalajs.js.isUndefined(dyn.key) && {
                        val key = dyn.key.asInstanceOf[String].toLowerCase
                        key == "z" || key == "y"
                    }
            }
            --> Observer[dom.KeyboardEvent] { ev =>
                ev.preventDefault()
                val key = ev.asInstanceOf[scala.scalajs.js.Dynamic].key.asInstanceOf[String].toLowerCase
                if key == "y" || ev.shiftKey then performRedo()
                else performUndo                             ()
            }

    lazy val app: Div = div(cls := "", child <-- router.currentPageSignal.map(renderPage)).amend(
        writeUnifiedSchemaSubscription,
        writeCatalogSubscription,
        writeUIStateSubscription,
        writeFireboxCacheSubscription,
        undoSnapshotSubscription,
        undoRedoKeyboardSubscription
        // results_en15544_outputs.map(err => ("OUTPUTS 15544", err))
        //     .tapEach(consoleLogVNelStringErrors) --> errorBusConsole
    )

//   def consoleLogVNelStringErrors(k_vnel: (String, VNelString[?])): Unit = k_vnel match
//     case (key, Validated.Invalid(nel)) =>
//         scala.scalajs.js.Dynamic.global.console.log(s"[$key] ======")
//         scala.scalajs.js.Dynamic.global.console.log(s"[$key] ERROR :")
//         nel.toList.foreach(x => scala.scalajs.js.Dynamic.global.console.log(x))
//         scala.scalajs.js.Dynamic.global.console.log(s"[$key] ======")
//     case _ => ()

    def renderPage(page: Page): Div = page match

        case ProjectSelectorPage(lang) =>
            given loc: Locale = Locale(language = lang)
            models.project.ProjectManager.saveCurrentProject    (    )
            models.project.ProjectManager.activeProjectIdVar.set(None)
            ProjectSelectorView().node

        case ProjectPage(lang, projectId, displayUnitsOpt) =>
            given loc: Locale       = Locale(language = lang)
            given du : DisplayUnits = displayUnitsOpt.getOrElse(DisplayUnits.SI)

            // Do NOT mutate appStateSchemaVar here: renderPage runs inside
            // `child <-- router.currentPageSignal.map(renderPage)`, so the previous
            // page's Div is still mounted when we execute. Its reactive subtree
            // (DaisyUIDynamicList / splitMatchSeq caches keyed by list index) would
            // observe the incoming schema at stale indices and throw ClassCastException
            // before Laminar gets a chance to unmount it. Instead, verify the project
            // exists here, then install it in onMountCallback — by then the previous
            // subtree is detached and its observers are dead.
            if !models.project.ProjectManager.projectExists(projectId) then
                dom.window.setTimeout(() => router.replaceState(ProjectSelectorPage(lang)), 0)
                div                  (p("Projet introuvable...")                             )
            else
                HomeView().node
                    .amend(onMountCallback { _ =>
                        models.project.ProjectManager.switchToProject(projectId)
                        localeVar.set                                (loc      )
                        displayUnitsVar.set                          (du       )
                    })

        case DefaultPage =>
            val lang = localeVar.now().language
            models.project.ProjectMigration.migrateIfNeeded() match
                case Some(id) =>
                    dom.window.setTimeout(() => router.replaceState(ProjectPage(lang, id)), 0)
                    div                  (p("Migration en cours...")                         )
                case None     =>
                    // Load last opened project if any, otherwise show selector
                    val lastProject = models.project.ProjectIndex
                        .load()
                        .sortBy(-_.lastModified)
                        .headOption
                    lastProject match
                        case Some(entry) =>
                            dom.window.setTimeout(() => router.replaceState(ProjectPage(lang, entry.id)), 0)
                            div                  (p("Chargement...")                                       )
                        case None        =>
                            dom.window.setTimeout(() => router.replaceState(ProjectSelectorPage(lang)), 0)
                            div                  (p("Chargement...")                                     )

    def main(args: Array[String]): Unit =

        // Log version information to console on startup
        VersionService.logVersionToConsole()

        // Initialize global error dialog i18n using the current locale
        GlobalErrorDialog.setI18n   (I18N_UI(using localeVar.now()).global_error   )
        StorageWarningDialog.setI18n(I18N_UI(using localeVar.now()).storage_warning)

        def isTransactionLoop(msg: String): Boolean =
            msg.contains("Transaction depth exceeded") || msg.contains("maxDepth")

        // Global error handlers (raw DOM, independent of Laminar)
        dom.window.addEventListener(
            "error",
            (e: dom.ErrorEvent) =>
                val msg = Option(e.message).getOrElse("Unknown error")
                if isTransactionLoop                   (msg) then GlobalErrorDialog.showTransactionError()
                else GlobalErrorDialog.showGenericError(msg)
        )

        dom.window.addEventListener(
            "unhandledrejection",
            (e: dom.Event) =>
                val reason = e.asInstanceOf[js.Dynamic].reason
                val msg    = if reason != null && !js.isUndefined(reason) then reason.toString else "Unknown error"
                GlobalErrorDialog.showGenericError(msg)
        )

        // Airstream unhandled error callback — catches errors from the reactive graph
        // (e.g. Transaction depth exceeded) that don't propagate to DOM error events
        com.raquo.airstream.core.AirstreamError.registerUnhandledErrorCallback: (err: Throwable) =>
            val msg = Option(err.getMessage).getOrElse("Unknown error")
            if isTransactionLoop                   (msg) then GlobalErrorDialog.showTransactionError()
            else GlobalErrorDialog.showGenericError(msg)

        // com.raquo.airstream.core.Transaction.maxDepth = Int.MaxValue
        com.raquo.airstream.core.Transaction.maxDepth = 1000

        waitForLoad {
            // Fire-and-forget: populate imagesVar from IndexedDB for catalog picker dialogs
            CatalogImageStore.loadAll()

            // Preload project state from the URL BEFORE render, so the reactive graph
            // sees the restored schema on first access. Without this, appStateSchemaVar
            // starts at EngineState.init (createInitialSchema), and the init→restored
            // transition that happens when the router's renderPage later calls
            // switchToProject corrupts derived panel-level Vars that captured init
            // values at construction time. See plan: the-dev-environment-memoized-steele.
            preloadProjectFromUrl()

            val appContainer = dom.document.querySelector("#app")
            appContainer.innerHTML = ""
            unmount()

            val rootNode = render(appContainer, app)
            storeUnmount(rootNode)

            // val elements: js.Array[String | org.scalajs.dom.HTMLElement] =
            //   Seq("#carneaux-split-left", "#carneaux-split-right").toJSArray

            // typings.splitJs.mod(elements)

            //   JSpreadsheetTable.init("jspreadsheet-carneaux")
        }

    /**
     * Preload project state before the reactive graph is built.
     *
     * On a full page reload, `appStateSchemaVar` is a lazy `Var` whose default
     * initializer produces `createInitialSchema()` (= `EngineState.init`). If we let the
     * normal render flow handle project restoration, the reactive graph is constructed
     * on top of that init state and only later transitions to the restored schema via
     * `ProjectManager.switchToProject`. That init→restored transition corrupts derived
     * panel-level Vars that captured init values at construction time (visible on reload
     * as a head/tail blend of init and restored pipe elements).
     *
     * This helper resolves the target project id by asking the router what page the
     * current URL maps to (`router.currentPageSignal.now()` — safe here because Waypoint
     * eagerly initializes `currentPageSignal` at `Router` object construction, well
     * before `main()` runs) and installs the restored schema via
     * `ProjectManager.restoreProjectState` BEFORE `render(appContainer, app)`. When the
     * router subsequently resolves the same `ProjectPage`, `switchToProject`
     * short-circuits via its `if activeProjectIdVar.now().contains(id) then return true`
     * guard — so no extra `appStateSchemaVar.set` fires and the reactive graph never
     * sees the init value.
     *
     * For `DefaultPage` URLs (root `/`), we mirror `renderPage(DefaultPage)`'s fallback
     * decisions synchronously: run legacy-layout migration, then preload the last-opened
     * project if any. The URL stays at `DefaultPage` for the first render tick; the
     * router's `setTimeout(replaceState(ProjectPage(...)))` then triggers a second
     * resolution to `ProjectPage(lang, id)` whose `switchToProject(id)` short-circuits
     * because the preload already set `activeProjectIdVar`.
     */
    private def preloadProjectFromUrl(): Unit =
        import models.project.{ProjectManager, ProjectMigration, ProjectIndex}

        val targetId: Option[models.project.ProjectId] =
            router.currentPageSignal.now() match
                case ProjectPage(_, id, _) => Some(id)
                case DefaultPage           =>
                    // Mirror DefaultPage's fallback chain: migration result, then last-opened.
                    ProjectMigration
                        .migrateIfNeeded()
                        .orElse(ProjectIndex.load().sortBy(-_.lastModified).headOption.map(_.id))
                case _: ProjectSelectorPage => None

        targetId.foreach(ProjectManager.restoreProjectState)

    def waitForLoad(f: => Any): Unit =
        if (dom.window.asInstanceOf[js.Dynamic].documentLoaded == null)
            documentEvents(_.onDomContentLoaded).foreach { _ =>
                dom.window.asInstanceOf[js.Dynamic].documentLoaded = true
                f
            }(using unsafeWindowOwner)
        else
            f

    def unmount(): Unit =
        if (scala.scalajs.LinkingInfo.developmentMode) {
            Option(dom.window.asInstanceOf[js.Dynamic].__laminar_root_unmount)
                .collect {
                    case x if !js.isUndefined(x) =>
                        x.asInstanceOf[js.Function0[Unit]]
                }
                .foreach(_.apply())
        }

    def storeUnmount(rootNode: RootNode): Unit = {
        val unmountFunction: js.Function0[Any] = () => rootNode.unmount()
        dom.window.asInstanceOf[js.Dynamic].__laminar_root_unmount = unmountFunction
    }

    if (!js.isUndefined(`import`.meta.hot) && !js.isUndefined(`import`.meta.hot.accept)) {
        `import`.meta.hot.accept { (_: Any) => }
    }
}
