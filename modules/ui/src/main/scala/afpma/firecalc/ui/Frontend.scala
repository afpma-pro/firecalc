/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.components.GlobalErrorDialog
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.services.{CatalogImageStore, VersionService}
import afpma.firecalc.ui.views.*

import com.raquo.laminar.api.L.*

import scala.scalajs.js
import scala.scalajs.js.`import`

import io.taig.babel.Locale
import org.scalajs.dom

object Frontend {

    import models.*

    lazy val writeUnifiedSchemaSubscription = appStateSchemaVar.signal.changes.distinct
        .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS) --> appStateSchemaWebStorageVar.writer

    lazy val writeCatalogSubscription = catalogStateVar.signal.changes.distinct
        .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS) --> catalogWebStorageVar.writer

    lazy val writeUIStateSubscription = uiStateVar.signal.changes.distinct
        .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS) --> uiStateWebStorageVar.writer

    private val undoSnapshotObserver = Observer[schema.AppStateSchema](undoManager.pushSnapshot(_))

    lazy val undoSnapshotSubscription =
        appStateSchemaVar.signal.changes
            .distinct
            .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
            --> undoSnapshotObserver

    lazy val undoRedoKeyboardSubscription =
        documentEvents(_.onKeyDown)
            .filter { ev =>
                val dyn = ev.asInstanceOf[scala.scalajs.js.Dynamic]
                val ctrl = dyn.ctrlKey
                val meta = dyn.metaKey
                // Guard: some events processed during Airstream transactions lack KeyboardEvent properties
                if scala.scalajs.js.isUndefined(ctrl) || scala.scalajs.js.isUndefined(meta) then false
                else (ctrl.asInstanceOf[Boolean] || meta.asInstanceOf[Boolean]) &&
                    !scala.scalajs.js.isUndefined(dyn.key) &&
                    dyn.key.asInstanceOf[String].toLowerCase == "z"
            }
            --> Observer[dom.KeyboardEvent] { ev =>
                ev.preventDefault()
                if ev.shiftKey then performRedo()
                else performUndo()
            }

    lazy val app: Div = div(cls := "", child <-- router.currentPageSignal.map(renderPage)).amend(
        writeUnifiedSchemaSubscription,
        writeCatalogSubscription,
        writeUIStateSubscription,
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

        case HomePage(lang, displayUnitsOpt) =>
            given loc: Locale       = Locale(language = lang)
            given du : DisplayUnits = displayUnitsOpt.getOrElse(DisplayUnits.SI)

            HomeView().node
                .amend(onMountCallback(_ =>
                    localeVar.set      (loc)
                    displayUnitsVar.set(du )
                ))

        case DefaultPage =>
            renderPage(
                HomePage           (
                    lang            = localeVar.now().language,
                    displayUnitsOpt = Some(displayUnitsVar.now())
                )
            )

    def main(args: Array[String]): Unit =

        // Log version information to console on startup
        VersionService.logVersionToConsole()

        // Initialize global error dialog i18n using the current locale
        GlobalErrorDialog.setI18n(I18N_UI(using localeVar.now()).global_error)

        def isTransactionLoop(msg: String): Boolean =
            msg.contains("Transaction depth exceeded") || msg.contains("maxDepth")

        // Global error handlers (raw DOM, independent of Laminar)
        dom.window.addEventListener("error", (e: dom.ErrorEvent) =>
            val msg = Option(e.message).getOrElse("Unknown error")
            if isTransactionLoop(msg) then GlobalErrorDialog.showTransactionError()
            else GlobalErrorDialog.showGenericError(msg)
        )

        dom.window.addEventListener("unhandledrejection", (e: dom.Event) =>
            val reason = e.asInstanceOf[js.Dynamic].reason
            val msg = if reason != null && !js.isUndefined(reason) then reason.toString else "Unknown error"
            GlobalErrorDialog.showGenericError(msg)
        )

        // Airstream unhandled error callback — catches errors from the reactive graph
        // (e.g. Transaction depth exceeded) that don't propagate to DOM error events
        com.raquo.airstream.core.AirstreamError.registerUnhandledErrorCallback: (err: Throwable) =>
            val msg = Option(err.getMessage).getOrElse("Unknown error")
            if isTransactionLoop(msg) then GlobalErrorDialog.showTransactionError()
            else GlobalErrorDialog.showGenericError(msg)

        // com.raquo.airstream.core.Transaction.maxDepth = Int.MaxValue
        com.raquo.airstream.core.Transaction.maxDepth = 1000

        waitForLoad {
            // Fire-and-forget: populate imagesVar from IndexedDB for catalog picker dialogs
            CatalogImageStore.loadAll()

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
