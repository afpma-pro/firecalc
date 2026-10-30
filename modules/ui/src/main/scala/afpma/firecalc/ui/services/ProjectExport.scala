/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.services

import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.models.appStateSchemaVar
import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.filename_var

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import io.taig.babel.Locale

/**
 * The single project-export path: encode the current app state as `.fcalc` YAML, then
 * hand it to `FileSystemService` (browser download and Electron save dialog). Shared by
 * the toolbar (`FireCalcProjet.BackupComponent`) and the global error dialog
 * (`GlobalErrorDialog`) — one encode/save implementation, no copy.
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
