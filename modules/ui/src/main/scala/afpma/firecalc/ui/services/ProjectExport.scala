/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.services

import afpma.firecalc.ui.i18n.ErrorDialogLabels
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.models.appStateSchemaVar
import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.filename_var
import afpma.firecalc.ui.models.project.ProjectFileName
import afpma.firecalc.ui.models.project.ProjectId
import afpma.firecalc.ui.models.project.ProjectIndex
import afpma.firecalc.ui.models.project.ProjectStorage

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import io.taig.babel.Locale

/**
 * The single project-export path, in two flavours that share one save implementation:
 *
 *  - [[save]] encodes the current app state as `.fcalc` YAML and hands it over;
 *  - [[downloadStoredState]] hands over the STORED bytes of a named project, without
 *    encoding anything. That is the only way to get a document out of a project this
 *    version cannot open — a payload that does not decode has no in-memory model to
 *    encode, so recovery has to go around the decoder entirely.
 *
 * Those two bytes are the same only when nothing was left unsaved: the stored payload is
 * the last SAVED state, never the live model, so the two differ whenever there are
 * unsaved edits. The stored path does no decode, no migration, no re-encode and no
 * storage mutation of any kind — it reads, it names, it writes a file. The name comes
 * from the projects-index entry through [[ProjectFileName.of]] (never
 * `ProjectEntry.displayName`, which substitutes the localized "Untitled"), so a recovery
 * download is named exactly like an export of the same project and re-imports through the
 * same "Open a file" filter.
 *
 * Shared by the toolbar (`FireCalcProjet.BackupComponent`) and the global error dialog
 * (`GlobalErrorDialog`) — one save implementation, no copy.
 */
object ProjectExport:

    /**
     * Exports the full AppStateSchema (engine_state + sensitive_data + billing_data).
     * Backend PDF generation uses a separate code path (OrderPDFReportModalComponent)
     * that sends only engine_state — this file export is independent.
     *
     * @return `Left(message)` — a user-displayable failure (encode or save);
     *         `Right(true)` — content actually written / download handed off (the browser
     *         path is always `true`); `Right(false)` — the user cancelled the native save
     *         dialog (Electron only).
     */
    def save()(using Locale): Future[Either[String, Boolean]] =
        AppStateSchemaHelper.encodeToYaml(appStateSchemaVar.now()) match
            case Failure(ex)          =>
                Future.successful(Left(I18N_UI.errors.failed_to_encode_project.apply(ex.getMessage)))
            case Success(yamlContent) =>
                // FileSystemService handles both browser and Electron.
                FileSystemService.saveFile(filename_var.now(), yamlContent)

    /**
     * Give me the stored state of project `id` as a downloadable file. Verbatim bytes:
     * no decode, no migration, no re-encode, no storage mutation.
     *
     * Labels come from [[ErrorDialogLabels]], never from `I18N_UI`: this path is reached
     * from the error dialog, which exists precisely for the state where every direct
     * payload read throws. Neither refusal (nothing stored, storage unreadable) may become
     * a failed Future or an exception at the caller — the caller is the surface reporting
     * that the app is already broken.
     *
     * @return `Left(message)` — a user-displayable reason no file was produced;
     *         `Right(true)` — content handed off / written; `Right(false)` — the user
     *         cancelled the native save dialog (Electron only).
     */
    def downloadStoredState(id: ProjectId)(using locale: Locale): Future[Either[String, Boolean]] =
        val labels = ErrorDialogLabels.resolve(locale)
        storedStateOrRefusal(ProjectStorage.storedState(id), ProjectIndex.findEntry(id).map(_.name), labels) match
            case Left(message)        => Future.successful(Left(message))
            case Right((name, bytes)) => FileSystemService.saveFile(name, bytes)

    /**
     * The decision behind [[downloadStoredState]] — name + verbatim payload, or the reason
     * there is none. Kept pure and `private[services]` because it is the contract of the
     * stored-bytes path, not an implementation detail of one call site: any other surface
     * that offers a project's saved file (a projects-list card being the obvious one) has to
     * ask the same question about a project it cannot open, and there is no reason to make
     * that decision untestable by tying it to a DOM and a localStorage.
     *
     * Both reads are by-name deliberately: `indexName` runs only once a payload was
     * actually read, so the projects-index read never happens on a storage that is already
     * known to be unreadable, and the single `catch` covering both is what turns a blocked
     * `getItem` into the dialog's inline failure line. `None` means "nothing stored" by
     * [[ProjectStorage]]'s own rule (absent key or blank payload) — no file is produced,
     * and the naming of a file nobody asked for is never evaluated.
     */
    private[services] def storedStateOrRefusal(
        stored   : => Option[String],
        indexName: => Option[String],
        labels   : ErrorDialogLabels
    ): Either[String, (String, String)] =
        try
            stored match
                case None        => Left(labels.noStoredState)
                case Some(state) =>
                    Right(ProjectFileName.of(indexName.getOrElse(""), labels.defaultProjectName) -> state)
        catch
            // The storage backend, not the data, is what fails here: a blocked or disabled
            // localStorage throws (js.JavaScriptException) out of getItem.
            case _: Throwable => Left(labels.storageUnavailable)
