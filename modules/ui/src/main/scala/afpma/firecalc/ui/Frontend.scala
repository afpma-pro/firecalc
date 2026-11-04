/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.components.GlobalErrorDialog
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

    lazy val writeUnifiedSchemaSubscription =
        utils.debounceWithStaleGuard        (
            signal         = appStateSchemaVar.signal,
            readIdentity   = () => models.project.ProjectManager.activeProjectIdVar.now(),
            identitySignal = models.project.ProjectManager.activeProjectIdVar.signal,
            debounceMs     = LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS
        ) { schemaVal =>
            import models.project.{ProjectStorage, ProjectIndex}
            models.project.ProjectManager.activeProjectIdVar.now().foreach { id =>
                ProjectStorage.save        (id, schemaVal                                           )
                // The index name is re-projected verbatim from the live reference;
                // no localized placeholder is ever stored (see ProjectIndex.syncNameOnSave).
                ProjectIndex.syncNameOnSave(id, schemaVal.engine_state.project_description.reference)
            }
        } --> Observer[Unit](_ => ())

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

    lazy val writeFireboxCacheSubscription =
        utils.debounceWithStaleGuard        (
            signal         = fireboxCacheStateVar.signal,
            readIdentity   = () => models.project.ProjectManager.activeProjectIdVar.now(),
            identitySignal = models.project.ProjectManager.activeProjectIdVar.signal,
            debounceMs     = LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS
        ) { cache =>
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
        } --> Observer[Unit](_ => ())

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
        undoRedoKeyboardSubscription,
        // The remote firebox pre-fetch's explicit start seam — mounting this
        // binder is what wires the feature.
        remote.RemoteFireboxPrefetch.prefetchRemoteDataBinder
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

    def renderPage(page: Page): Div =
        // The URL is the single locale authority: resolve it and sync the show-time
        // channel before ANY node is constructed, so everything resolved at show/act
        // time speaks the URL's language. The sync writes the ROOT Var directly:
        // localeVar is a two-level zoomLazy chain and Airstream only detects staleness
        // once the parent is started, so a lazy-zoom write issued inside this mount
        // transaction never lands on the root — show-time readers (models.currentLocale)
        // would otherwise still resolve the pre-sync locale (proven on cold boot).
        // The root URL carries no locale segment: its resolution IS the channel's
        // current value (cold boot: the schema default).
        given loc: Locale = page match
            case ProjectSelectorPage(lang) => Locale(language = lang)
            case ProjectPage(lang, _, _)   => Locale(language = lang)
            case DefaultPage               => currentLocale
        appStateSchemaVar.update(s => s.copy(engine_state = s.engine_state.copy(locale = loc)))
        page match

            case ProjectSelectorPage(lang) =>
                models.project.ProjectManager.saveCurrentProject    (    )
                models.project.ProjectManager.activeProjectIdVar.set(None)
                ProjectSelectorView().node

            case ProjectPage(lang, projectId, displayUnitsOpt) =>
                given du: DisplayUnits = displayUnitsOpt.getOrElse(DisplayUnits.SI)

                // The render that follows the startup preload (cold boot / reload of a
                // project URL) is the FIRST render of that id; any later one is a user
                // navigation (a projects-list card click). Consumed here so the unreadable
                // case can pick its suppression policy (reportUnreadableProject).
                val fromStartupPreload = startupPreloadedProjectId.contains(projectId)
                if fromStartupPreload then startupPreloadedProjectId = None

                // Do NOT install the incoming project schema here: renderPage runs inside
                // `child <-- router.currentPageSignal.map(renderPage)`, so the previous
                // page's Div is still mounted when we execute. Its reactive subtree
                // (DaisyUIDynamicList / splitMatchSeq caches keyed by list index) would
                // observe the incoming schema at stale indices and throw ClassCastException
                // before Laminar gets a chance to unmount it. Instead, read the persisted
                // state here, then install it in onMountCallback — by then the previous
                // subtree is detached and its observers are dead.
                //
                // The read is three-way (ProjectLoadOutcome), because "absent" and
                // "present but unreadable" are different answers to give the user: the
                // first gets the quiet redirect with a translated one-frame message, the
                // second the application-error dialog (its escape actions; deleting the
                // card in the projects list is the recovery) before the same redirect. The
                // corrupt bytes are deliberately left in storage.
                //
                // The corrupt case takes its message from GlobalErrorDialog
                // (.unreadableProjectMessage), NOT from currentI18N: this frame and its
                // report run in the same render, and a direct payload read throws there
                // exactly when the payload and the typed layer disagree — which hid the
                // dialog entirely (the caller threw before it was reached). The dialog
                // resolves its own labels with a built-in fallback, so the report and this
                // frame always have text. The Missing branch below stays on currentI18N: it
                // has no dialog to fall back to, so a payload mismatch still breaks it.
                models.project.ProjectManager.projectOutcome(projectId) match
                    case models.project.ProjectLoadOutcome.Ok(_)   =>
                        HomeView().node
                            .amend(onMountCallback { _ =>
                                models.project.ProjectManager.switchToProject(projectId)
                                displayUnitsVar.set                          (du       )
                            })
                    case models.project.ProjectLoadOutcome.Corrupt =>
                        reportUnreadableProject(projectId, fromStartupPreload                          )
                        dom.window.setTimeout  (() => router.replaceState(ProjectSelectorPage(lang)), 0)
                        div                    (p(GlobalErrorDialog.unreadableProjectMessage)          )
                    case models.project.ProjectLoadOutcome.Missing =>
                        dom.window.setTimeout(() => router.replaceState(ProjectSelectorPage(lang)), 0)
                        div                  (p(currentI18N.project_selector.project_not_found)      )

            case DefaultPage =>
                val lang = loc.language
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
     *
     * The resolved id is also recorded (`startupPreloadedProjectId`): the load path has
     * to tell this startup render from a later user navigation to the same project.
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

        targetId.foreach { id =>
            startupPreloadedProjectId = Some(id)
            ProjectManager.restoreProjectState(id)
        }

    /**
     * The project id [[preloadProjectFromUrl]] resolved from the URL, consumed by the
     * first `ProjectPage` render of that id (see renderPage). That render is the startup
     * one — cold boot or reload of a project URL; any later render of the same id is a
     * user navigation. Only the unreadable-project report needs the difference.
     */
    private var startupPreloadedProjectId: Option[models.project.ProjectId] = None

    /**
     * Report a project whose stored state does NOT decode (the application-error dialog;
     * its data stays in storage). The suppression policy follows how the load happened:
     *  - startup (the render right after the preload): shown once per page-load, keyed on
     *    the project id — a reload is a fresh page-load and does re-show it, because the
     *    trap genuinely repeats on every reload.
     *  - anything else (opening the project from its card): the per-attempt variant, so
     *    repeated attempts each give feedback instead of the silent bounce this replaces.
     *    The projects index holds the card's name, so the dialog names the project.
     *
     * The message comes from the dialog itself (`GlobalErrorDialog.unreadableProjectMessage`),
     * never from `currentI18N`: the dialog resolves its labels with a built-in fallback, so
     * this report cannot be the thing that throws before the dialog is reached — the exact
     * failure this path had when the generated payload and the typed layer disagreed.
     *
     * This is the only report that names a project, so it is the only one passing the id:
     * that is what offers the dialog's "download the saved file" action for bytes this
     * version cannot decode. The file-import callers keep the 2-argument forms and get no
     * such action — they have no stored project behind the failure.
     */
    private def reportUnreadableProject(projectId: models.project.ProjectId, fromStartupPreload: Boolean): Unit =
        import models.project.ProjectIndex

        val message = GlobalErrorDialog.unreadableProjectMessage
        val name    = ProjectIndex
            .findEntry(projectId)
            .map(_.displayName(using currentLocale))
        if fromStartupPreload then
            GlobalErrorDialog.showErrorWithKey     (
                s"unreadable-project:${projectId.value}",
                name.fold(message)(n => s"$n: $message"),
                Some              (projectId           )
            )
        else GlobalErrorDialog.showUserAttemptError(name, message, Some(projectId))

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
