/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.ui.i18n.GlobalError

import scala.collection.mutable

import afpma.firecalc.ui.ProjectSelectorPage
import afpma.firecalc.ui.models.currentI18N
import afpma.firecalc.ui.models.localeVar
import afpma.firecalc.ui.models.project.ProjectManager
import afpma.firecalc.ui.router
import afpma.firecalc.ui.services.ProjectExport

import io.taig.babel.Locale
import org.scalajs.dom

object GlobalErrorDialog:

    // Labels are resolved at show time through models.currentI18N (see its definition
    // for the init-order / never-eager-val warning).
    private def i18nNow: GlobalError = currentI18N.global_error

    /**
     * Re-show suppression (mirrors StorageWarningDialog's once-per-episode guard): a
     * suppression key that has already been shown this page-load stays silent, so a
     * dismissed error does not immediately pop back on further render passes. A new key
     * still shows. Keys default to the displayed message; user-initiated flows go
     * through `showUserAttemptError`, which owns the per-attempt key, and caller-keyed
     * flows through `showErrorWithKey`. « Recharger la page » reloads, resetting the
     * set naturally.
     */
    private val shownMessages = mutable.Set.empty[String]

    private lazy val dialog: dom.HTMLDialogElement =
        val d = dom.document.createElement("dialog").asInstanceOf[dom.HTMLDialogElement]
        d.classList.add("modal")
        d.innerHTML = """
            <div class="modal-box">
                <h3 class="font-bold text-lg text-error" id="global-error-title"></h3>
                <p class="py-4 whitespace-pre-wrap" id="global-error-message"></p>
                <p class="text-error text-sm pb-4 hidden" id="global-error-save-failed"></p>
                <div class="divider my-0"></div>
                <!-- Recovery choices, stacked vertically under the message: a four-button
                     modal-action row overflows the modal box (dialog-overflow.png). -->
                <div class="flex flex-col gap-2 pb-4 pt-2">
                    <!-- Inline copy of lucide `file-down` (afpma.firecalc.ui.icons.lucide, the
                         toolbar export glyph): this raw-DOM dialog cannot mount Laminar nodes.
                         Duplication accepted for this one static glyph. -->
                    <button class="btn btn-outline w-full hidden" id="global-error-save">
                        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-file-down-icon lucide-file-down"><path d="M15 2H6a2 2 0 0 0 -2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"></path><path d="M14 2v4a2 2 0 0 0 2 2h4"></path><path d="M12 18v-6"></path><path d="m9 15 3 3 3-3"></path></svg>
                        <span id="global-error-save-label"></span>
                    </button>
                    <!-- Inline copy of lucide `file-stack` (afpma.firecalc.ui.icons.lucide, the
                         toolbar projects-list glyph): same raw-DOM constraint as above. -->
                    <button class="btn btn-outline w-full" id="global-error-go-to-projects">
                        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-file-stack-icon lucide-file-stack"><path d="M21 7h-3a2 2 0 0 1-2-2V2"></path><path d="M21 6v6.5c0 .8-.7 1.5-1.5 1.5h-7c-.8 0-1.5-.7-1.5-1.5v-9c0-.8.7-1.5 1.5-1.5H17Z"></path><path d="M7 8v8.8c0 .3.2.6.4.8.2.2.5.4.8.4H15"></path><path d="M3 12v8.8c0 .3.2.6.4.8.2.2.5.4.8.4H11"></path></svg>
                        <span id="global-error-go-to-projects-label"></span>
                    </button>
                </div>
                <div class="modal-action">
                    <button class="btn btn-outline" id="global-error-close"></button>
                    <button class="btn btn-error btn-outline" id="global-error-reload"></button>
                </div>
            </div>
            <form method="dialog" class="modal-backdrop"><button>close</button></form>
        """
        d.querySelector("#global-error-reload")
            .addEventListener("click", (_: dom.Event) => dom.window.location.reload())
        d.querySelector("#global-error-save")
            .addEventListener("click", (_: dom.Event) => saveCurrentProject())
        // Same navigation the toolbar and StorageWarningDialog ship: close the dialog,
        // render the projects list inside the running app (no reload). Project deletion —
        // the way out of a corrupted persisted state — only exists in the selector.
        d.querySelector("#global-error-go-to-projects")
            .addEventListener(
                "click",
                (_: dom.Event) => {
                    d.close         (                                             )
                    router.pushState(ProjectSelectorPage(localeVar.now().language))
                }
            )
        d.querySelector("#global-error-close")
            .addEventListener("click", (_: dom.Event) => d.close())
        dom.document.body.appendChild(d)
        d

    private def saveButton: dom.HTMLButtonElement =
        dialog.querySelector("#global-error-save").asInstanceOf[dom.HTMLButtonElement]

    private def saveLabel: dom.Element =
        dialog.querySelector("#global-error-save-label")

    private def saveFailure: dom.Element =
        dialog.querySelector("#global-error-save-failed")

    private def clearSaveFailure(): Unit =
        saveFailure.textContent = ""
        saveFailure.classList.add("hidden")

    /** Re-arm the save action: enabled button, no failure line, clean label. */
    private def resetSaveAction(): Unit =
        saveButton.disabled = false
        clearSaveFailure()
        saveLabel.textContent = i18nNow.save_button

    /**
     * Save the current project right from the dialog, through the shared export path
     * (`ProjectExport`). A failure renders inline under the message — never by calling
     * `showGenericError` from inside the dialog, which would just re-show the dialog.
     */
    private def saveCurrentProject(): Unit =
        import scala.concurrent.ExecutionContext.Implicits.global

        // Pending guard: a disabled button dispatches no click events.
        saveButton.disabled = true
        clearSaveFailure()

        given Locale = localeVar.now()
        ProjectExport.save().foreach { result =>
            result match
                case Left(error) =>
                    saveButton.disabled     = false
                    saveFailure.textContent = error
                    saveFailure.classList.remove("hidden")

                case Right(true) =>
                    // Success: the dialog stays open and the button shows the completed
                    // state, so the user sees the export landed. It stays disabled.
                    saveLabel.textContent = s"✓ ${i18nNow.save_done}"

                case Right(false) =>
                    // User cancelled the native save dialog (Electron only): silently
                    // re-arm the button, no visible change.
                    saveButton.disabled = false
        }

    /**
     * Show an error with an i18n-supplied title. Keys on the displayed message: the
     * right behaviour for errors re-raised by render passes (a dismissed error stays
     * silent). User-initiated flows must use `showUserAttemptError` instead, so their
     * retries give feedback again.
     */
    private def show(title: String, message: String): Unit = show(title, message, message)

    private def show(title: String, message: String, suppressionKey: String): Unit =
        // Re-show suppression: once a key has been shown, identical keys stay silent for
        // the rest of the page-load (the dismissed error does not pop back on further
        // render passes); a new key still updates the dialog.
        if shownMessages.contains(suppressionKey) then return
        shownMessages.add(suppressionKey)

        // A fresh error starts with a clean save action.
        resetSaveAction()

        // The action only exists when a project is open — e.g. an error raised while
        // opening a bad file from the projects list has no active project to save.
        if ProjectManager.activeProjectIdVar.now().isDefined then saveButton.classList.remove("hidden")
        else saveButton.classList.add                                                        ("hidden")

        dialog.querySelector("#global-error-title").textContent   = title
        dialog.querySelector("#global-error-message").textContent = message
        val i18n = i18nNow
        dialog.querySelector("#global-error-reload").textContent               = i18n.reload_button
        dialog.querySelector("#global-error-go-to-projects-label").textContent = i18n.go_to_projects
        dialog.querySelector("#global-error-close").textContent                = i18n.close_button
        // A different message arriving while the dialog is open already updated the text
        // above; showModal() on an open <dialog> throws InvalidStateError.
        if !dialog.open then dialog.showModal()

    /** Show a transaction loop error with i18n messages. */
    def showTransactionError(): Unit =
        show(i18nNow.title, i18nNow.transaction_msg)

    /** Show a generic application error with i18n title (render-pass default: keyed on the message). */
    def showGenericError(message: String): Unit = show(i18nNow.title, message)

    /**
     * Show an application error whose suppression is NOT the message: the caller supplies
     * the key. Needed by the load path — a project whose persisted state cannot be
     * decoded is reported once per page-load keyed on the project id, so re-entering the
     * same page cannot re-open a dismissed dialog, while a reload (a fresh page-load,
     * still hitting the same broken bytes) does re-show it. Keys are opaque here;
     * per-attempt keys belong to `showUserAttemptError`, the only place they are built.
     */
    def showErrorWithKey(suppressionKey: String, message: String): Unit =
        show(i18nNow.title, message, suppressionKey)

    /**
     * Show an error raised by an explicit user attempt (picking a bad file, pressing
     * save). Repeating the failing action must give feedback again, so the suppression
     * key carries the attempt timestamp: every attempt is a distinct episode, while the
     * suppression mechanism itself stays intact. This is the only place the per-attempt
     * key is built. Callers pass the error plus the attempted file name when it is in
     * hand (None when genuinely unknown, e.g. native-dialog failures); the file-error
     * display shape — "$name: $error" when a name is known, kept nameless otherwise —
     * also lives only here.
     */
    def showUserAttemptError(fileName: Option[String], message: String): Unit =
        val displayed = fileName.fold(message)(name => s"$name: $message")
        show(i18nNow.title, displayed, s"$displayed @ ${scala.scalajs.js.Date.now()}")

end GlobalErrorDialog
