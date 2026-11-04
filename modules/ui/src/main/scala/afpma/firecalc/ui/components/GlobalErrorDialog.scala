/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.ui.i18n.ErrorDialogLabels

import scala.collection.mutable
import scala.concurrent.Future

import afpma.firecalc.ui.ProjectSelectorPage
import afpma.firecalc.ui.models.currentLocale
import afpma.firecalc.ui.models.localeVar
import afpma.firecalc.ui.models.project.ProjectId
import afpma.firecalc.ui.models.project.ProjectManager
import afpma.firecalc.ui.router
import afpma.firecalc.ui.services.ProjectExport

import io.taig.babel.Locale
import org.scalajs.dom

object GlobalErrorDialog:

    // Single label-resolution point, through ErrorDialogLabels: a payload/typed-layer
    // mismatch makes every `currentI18N` read throw, which is how this dialog went
    // silent. `resolve` never throws, so nothing here can fail on a broken payload — a
    // leaf no locale supplies renders as its key path and is only logged. Locale comes
    // from models.currentLocale, the show-time authority, NOT `localeVar.now()`: that is
    // transaction-stale inside the route render this dialog shows from.
    //
    // Cached for the page lifetime, keyed by the locale. `resolve`'s fast path touches
    // `implicits.I18Ns`, and a `lazy val` whose initializer threw is left uninitialized,
    // so uncached every label read (title before the `shownMessages` check, `show`,
    // `resetActions`, `unreadableProjectMessage` — suppressed errors included)
    // re-decodes both bundles, then every leaf. Kept here rather than in `ui-i18n`: an
    // object-scope `lazy val` there sits in front of the Scala.js init-order hazard.
    private var cachedLabels: Option[(Locale, ErrorDialogLabels)] = None

    private def labels(): ErrorDialogLabels =
        val locale = currentLocale
        cachedLabels
            .filter(_._1 == locale)
            .map(_._2)
            .getOrElse:
                val resolved = ErrorDialogLabels.resolve(locale)
                // One-shot diagnosis of the degraded state: a leaf missing from every
                // bundle is a build/data fault the user cannot retry, so the key paths are
                // named once and the dialog keeps rendering. Only a fresh resolution gets
                // here, and that happens at most once per locale per page.
                if resolved.unresolved.nonEmpty then
                    dom.console.error(
                        s"Global error dialog: no locale supplied these i18n keys — showing their key paths: " +
                            resolved.unresolved.mkString(", ")
                    )
                cachedLabels = Some(locale -> resolved)
                resolved

    /**
     * The "stored but unreadable project" message, resolved through the same never-throwing
     * path as the dialog's own labels — the dialog owns this text so the caller reporting a
     * corrupt persisted state cannot be the one that throws before the dialog is reached
     * (what happened when both `reportUnreadableProject` and the corrupt-state frame read
     * `currentI18N.errors.unreadable_project_state`: no dialog, no message, one console
     * stack).
     */
    def unreadableProjectMessage: String = labels().unreadableProjectState

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
                <p class="text-error text-sm pb-4 hidden" id="global-error-action-failed"></p>
                <div class="divider my-0"></div>
                <!-- Recovery choices, stacked vertically under the message: a modal-action
                     row with too many buttons overflows the modal box, so each action gets
                     its own full-width row. -->
                <div class="flex flex-col gap-2 pb-4 pt-2">
                    <!-- Inline copy of lucide `file-down` (afpma.firecalc.ui.icons.lucide, the
                         toolbar export glyph): this raw-DOM dialog cannot mount Laminar nodes.
                         Duplication accepted for this one static glyph. -->
                    <button class="btn btn-outline w-full hidden" id="global-error-save">
                        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-file-down-icon lucide-file-down"><path d="M15 2H6a2 2 0 0 0 -2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"></path><path d="M14 2v4a2 2 0 0 0 2 2h4"></path><path d="M12 18v-6"></path><path d="m9 15 3 3 3-3"></path></svg>
                        <span id="global-error-save-label"></span>
                    </button>
                    <!-- Inline copy of lucide `download` (deliberately NOT the `file-down`
                         glyph the save action uses: this is different bytes, not a second
                         way to save): same raw-DOM constraint as above. -->
                    <button class="btn btn-outline w-full hidden" id="global-error-download-stored">
                        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-download-icon lucide-download"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" x2="12" y1="15" y2="3"></line></svg>
                        <span id="global-error-download-stored-label"></span>
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
        // No listener for #global-error-download-stored: `show` wires that button with the
        // project id it was opened for, and unwires it when there is none.
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

    private def actionFailure: dom.Element =
        dialog.querySelector("#global-error-action-failed")

    private def downloadStoredButton: dom.HTMLButtonElement =
        dialog.querySelector("#global-error-download-stored").asInstanceOf[dom.HTMLButtonElement]

    private def downloadStoredLabel: dom.Element =
        dialog.querySelector("#global-error-download-stored-label")

    private def clearActionFailure(): Unit =
        actionFailure.textContent = ""
        actionFailure.classList.add("hidden")

    /** A fresh error starts with both recovery actions armed: enabled, own label, no failure line. */
    private def resetActions(): Unit =
        resetAction       (saveButton, saveLabel, labels().saveButton                              )
        resetAction       (downloadStoredButton, downloadStoredLabel, labels().downloadStoredButton)
        clearActionFailure(                                                                        )

    /**
     * Run a recovery action (save the live model, or hand over a project's stored bytes) from
     * inside the dialog and report it inline. This dialog is the surface already reporting a
     * failure, so an action failure goes in the paragraph under the message — never into
     * `showGenericError`, which would just re-show this dialog (see `show`), and never as an
     * escaped exception, which would take the app down with the error it was showing.
     *
     * `reArmAfterSuccess` is the one real difference between the two actions: saving is a
     * moment that has been taken, so Save stays spent until a fresh error re-arms it, while
     * handing over stored bytes consumes nothing — the payload stays in storage and the delete
     * action stays the only thing that removes it, so asking again is normal (and gives the
     * same file). Every attempt starts from the action's own label, so a repeat never inherits
     * the ✓ of the previous one.
     *
     * The Locale is resolved once, from `currentLocale` — the same show-time authority
     * `labels()` uses. `localeVar.now()` is transaction-stale inside the route render this
     * dialog shows from, and a button label from one locale next to a failure line from
     * another is a mismatch the user cannot explain.
     */
    private def runInlineAction(
        button           : dom.HTMLButtonElement,
        label            : dom.Element,
        cleanLabel       : => String,
        doneLabel        : => String,
        reArmAfterSuccess: Boolean
    )(action: Locale => Future[Either[String, Boolean]]): Unit =
        import scala.concurrent.ExecutionContext.Implicits.global

        given locale: Locale = currentLocale

        resetAction(button, label, cleanLabel)
        // Pending guard: a disabled button dispatches no click events.
        button.disabled = true

        action(locale).foreach {
            case Left(error) =>
                button.disabled           = false
                actionFailure.textContent = error
                actionFailure.classList.remove("hidden")

            case Right(true) =>
                // Success: the dialog stays open and the button shows the completed state, so
                // the user sees the file landed.
                label.textContent = s"✓ $doneLabel"
                button.disabled   = !reArmAfterSuccess

            case Right(false) =>
                // The user cancelled the native save dialog (Electron only): re-arm the button
                // with no visible change.
                button.disabled = false
        }

    /** Re-arm one action: enabled, its own label, failure line cleared. */
    private def resetAction(button: dom.HTMLButtonElement, label: dom.Element, cleanLabel: => String): Unit =
        button.disabled   = false
        label.textContent = cleanLabel
        clearActionFailure()

    /**
     * Save the current project right from the dialog, through the shared export path
     * (`ProjectExport`). Only available when a project is open — see `show`.
     */
    private def saveCurrentProject(): Unit =
        runInlineAction(saveButton, saveLabel, labels().saveButton, labels().saveDone, reArmAfterSuccess = false)(
            locale => ProjectExport.save()(using locale)
        )

    /**
     * Hand over the STORED bytes of the project this dialog was opened for, through the shared
     * stored-bytes path (`ProjectExport.downloadStoredState`): no decode, no re-encode, the
     * only way out of a payload this version cannot read.
     *
     * The id is a parameter, not dialog state: `show` wires this handler with the id it was
     * given and hides the button otherwise, so the click always has one. Keyed on that id,
     * never on `ProjectManager.activeProjectIdVar` like the save action — a project whose
     * payload does not decode never became the active one, so nothing else in the app knows
     * which project the user is being told about.
     */
    private def downloadStoredState(id: ProjectId): Unit =
        runInlineAction(
            downloadStoredButton,
            downloadStoredLabel,
            labels().downloadStoredButton,
            labels().downloadStoredDone,
            reArmAfterSuccess = true
        )(locale => ProjectExport.downloadStoredState(id)(using locale))

    /**
     * Show an error keyed on its own text — the right suppression policy for errors re-raised
     * by render passes (a dismissed error stays silent). Titles come from `labels()`, never
     * from a direct payload read. User-initiated flows must use `showUserAttemptError`
     * instead, so their retries give feedback again.
     */
    private def showMessageKeyed(title: String, message: String): Unit =
        show(title, message, message)

    /**
     * `storedStateOf` is the project whose stored bytes the dialog may hand back as a file:
     * Some only for the unreadable-project report, the one error that names a project. Every
     * other path leaves it out and gets no such action.
     */
    private def show(
        title         : String,
        message       : String,
        suppressionKey: String,
        storedStateOf : Option[ProjectId] = None
    ): Unit =
        // Re-show suppression: once a key has been shown, identical keys stay silent for
        // the rest of the page-load (the dismissed error does not pop back on further
        // render passes); a new key still updates the dialog.
        if shownMessages.contains(suppressionKey) then return
        shownMessages.add(suppressionKey)

        try
            // A fresh error starts with both recovery actions armed.
            resetActions()

            // The save action only exists when a project is open — e.g. an error raised while
            // opening a bad file from the projects list has no active project to save.
            saveButton.classList.toggle("hidden", ProjectManager.activeProjectIdVar.now().isEmpty)

            // The stored-bytes action is keyed on the project this dialog was opened for, never
            // on the active one: an unreadable project has no active project to offer. Wiring the
            // handler here, around the id itself, is what makes "a click always has a project"
            // a fact of the code rather than of an ordering argument: no id, no handler, and the
            // button is hidden.
            downloadStoredButton.classList.toggle("hidden", storedStateOf.isEmpty)
            storedStateOf match
                case Some(id) => downloadStoredButton.onclick = (_: dom.Event) => downloadStoredState(id)
                case None     => downloadStoredButton.onclick = null

            dialog.querySelector("#global-error-title").textContent   = title
            dialog.querySelector("#global-error-message").textContent = message
            val l = labels()
            dialog.querySelector("#global-error-reload").textContent               = l.reloadButton
            dialog.querySelector("#global-error-go-to-projects-label").textContent = l.goToProjects
            dialog.querySelector("#global-error-close").textContent                = l.closeButton
            // A different message arriving while the dialog is open already updated the text
            // above; showModal() on an open <dialog> throws InvalidStateError.
            if !dialog.open then dialog.showModal()
        catch
            // This dialog is the escape hatch for every other failure: nothing is left to
            // report an error raised here to, so it can only be logged — an exception
            // escaping `show*` would take the whole app down with the error it was showing.
            case ex: Throwable =>
                dom.console.error(s"Global error dialog: failed to render the error — $ex\n$message")

    /** Show a transaction loop error with dialog labels (translated when the payload decodes). */
    def showTransactionError(): Unit =
        val l = labels()
        showMessageKeyed(l.errorTitle, l.transactionMsg)

    /** Show a generic application error with the dialog title (render-pass default: keyed on the message). */
    def showGenericError(message: String): Unit = showMessageKeyed(labels().errorTitle, message)

    /**
     * Show an application error whose suppression is NOT the message: the caller supplies
     * the key. Needed by the load path — a project whose persisted state cannot be
     * decoded is reported once per page-load keyed on the project id, so re-entering the
     * same page cannot re-open a dismissed dialog, while a reload (a fresh page-load,
     * still hitting the same broken bytes) does re-show it. Keys are opaque here;
     * per-attempt keys belong to `showUserAttemptError`, the only place they are built.
     * `storedStateOf` is the project the report is about, which is what offers the
     * stored-bytes action; only the unreadable-project report has one.
     */
    def showErrorWithKey(suppressionKey: String, message: String, storedStateOf: Option[ProjectId] = None): Unit =
        show(labels().errorTitle, message, suppressionKey, storedStateOf)

    /**
     * Show an error raised by an explicit user attempt (picking a bad file, pressing
     * save). Repeating the failing action must give feedback again, so the suppression
     * key carries the attempt timestamp: every attempt is a distinct episode, while the
     * suppression mechanism itself stays intact. This is the only place the per-attempt
     * key is built. Callers pass the error plus the attempted file name when it is in
     * hand (None when genuinely unknown, e.g. native-dialog failures); the file-error
     * display shape — "$name: $error" when a name is known, kept nameless otherwise —
     * also lives only here. `storedStateOf` is the project the attempt was on, when the
     * attempt was on a known project (see `showErrorWithKey`).
     */
    def showUserAttemptError(fileName: Option[String], message: String, storedStateOf: Option[ProjectId] = None): Unit =
        val displayed = fileName.fold(message)(name => s"$name: $message")
        show(labels().errorTitle, displayed, s"$displayed @ ${scala.scalajs.js.Date.now()}", storedStateOf)

end GlobalErrorDialog
