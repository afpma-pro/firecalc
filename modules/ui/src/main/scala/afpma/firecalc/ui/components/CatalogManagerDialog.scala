/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.catalog.CatalogParseError
import afpma.firecalc.catalog.CatalogParser
import afpma.firecalc.payments.shared.Constants.FIRECALC_CATALOG_FILE_EXTENSION
import afpma.firecalc.ui.*
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.models.{CatalogState, catalogStateVar}
import afpma.firecalc.ui.services.FileSystemService
import afpma.firecalc.utils.BuildInfo

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom
import org.scalajs.dom.HTMLDialogElement

import scala.concurrent.ExecutionContext.Implicits.global

case class CatalogManagerDialog()(using Locale) extends Component:

    private val errorMessageVar: Var[Option[String]] = Var(None)

    def open(): Unit =
        errorMessageVar.set(None)
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit = dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private def handleFileLoad(file: dom.File): Unit =
        FileSystemService.readFileFromInput(file).foreach:
            case Left(err) =>
                errorMessageVar.set(Some(err))
            case Right((content, _)) =>
                CatalogParser.parse(content) match
                    case Left(parseError) =>
                        val msg = parseError match
                            case CatalogParseError.InvalidFile(detail) =>
                                I18N_UI.catalog.errors.invalid_file.replace("{detail}", detail)
                            case CatalogParseError.MissingVersion =>
                                I18N_UI.catalog.errors.missing_version
                            case CatalogParseError.VersionTooNew(found, supported) =>
                                I18N_UI.catalog.errors.version_too_new
                                    .replace("{found}", found.unwrap.toString)
                                    .replace("{supported}", supported.unwrap.toString)
                            case CatalogParseError.MigrationFailed(version, detail) =>
                                I18N_UI.catalog.errors.migration_failed
                                    .replace("{version}", version.unwrap.toString)
                                    .replace("{detail}", detail)
                            case CatalogParseError.DecodeError(category, detail) =>
                                I18N_UI.catalog.errors.decode_error
                                    .replace("{category}", category)
                                    .replace("{detail}", detail)
                        errorMessageVar.set(Some(msg))
                    case Right(catalogFile) =>
                        val merged = CatalogState.merge(catalogStateVar.now(), catalogFile)
                        catalogStateVar.set(merged)
                        errorMessageVar.set(None)

    private lazy val fileInput: Input = input(
        typ    := "file",
        accept := FIRECALC_CATALOG_FILE_EXTENSION,
        cls    := "hidden",
        onChange --> { ev =>
            val target = ev.target.asInstanceOf[dom.html.Input]
            val files  = target.files
            if files.length > 0 then handleFileLoad(files(0))
            target.value = "" // Reset so same file can be re-selected
        }
    )

    private lazy val dialogNode: HtmlElement = dialogTag(
        cls := "modal",
        div(
            cls := "modal-box w-11/12 max-w-xl",

            // Title
            h3(cls := "font-bold text-lg mb-4", I18N_UI.catalog.manager_title),

            // Download section
            div(
                cls := "mb-4",
                h4(cls := "font-semibold text-sm mb-2 text-base-content/70", I18N_UI.catalog.download_section),
                ul(
                    cls := "list-disc list-inside space-y-1 text-sm",
                    li(a(cls := "link link-primary", href := BuildInfo.Repository.url, target := "_blank", I18N_UI.catalog.afpma_catalog_page))
                )
            ),

            // Loaded entries
            div(
                cls := "mb-4",
                h4(cls := "font-semibold text-sm mb-2 text-base-content/70", I18N_UI.catalog.loaded_entries),
                div(
                    cls := "text-sm space-y-1",
                    child <-- catalogStateVar.signal.map: state =>
                        val hasEntries = state.door_15a_fireboxes.nonEmpty || state.single_tested_fireboxes.nonEmpty || state.pipe_presets.nonEmpty || state.casing_presets.nonEmpty || state.flow_resistance_presets.nonEmpty
                        if !hasEntries then
                            p(cls := "text-base-content/50 italic", I18N_UI.catalog.no_catalog_loaded)
                        else
                            div(
                                Option.when(state.door_15a_fireboxes.nonEmpty)(
                                    p(s"• ${I18N_UI.catalog.door_15a_fireboxes}: ${state.door_15a_fireboxes.size}")
                                ),
                                Option.when(state.single_tested_fireboxes.nonEmpty)(
                                    p(s"• ${I18N_UI.catalog.single_tested_fireboxes}: ${state.single_tested_fireboxes.size}")
                                ),
                                Option.when(state.pipe_presets.nonEmpty)(
                                    p(s"• ${I18N_UI.catalog.pipe_presets}: ${state.pipe_presets.size}")
                                ),
                                Option.when(state.casing_presets.nonEmpty)(
                                    p(s"• ${I18N_UI.catalog.casing_presets}: ${state.casing_presets.size}")
                                ),
                                Option.when(state.flow_resistance_presets.nonEmpty)(
                                    p(s"• ${I18N_UI.catalog.flow_resistance_presets}: ${state.flow_resistance_presets.size}")
                                )
                            )
                )
            ),

            // Error message
            div(
                cls     := "mb-4",
                display <-- errorMessageVar.signal.map(_.fold("none")(_ => "")),
                div(
                    cls := "alert alert-error text-sm",
                    child.text <-- errorMessageVar.signal.map(_.getOrElse(""))
                )
            ),

            // Actions
            div(
                cls := "modal-action flex flex-wrap gap-2",
                fileInput,
                button(
                    cls := "btn btn-sm btn-secondary",
                    I18N_UI.catalog.import_catalog_button,
                    onClick --> { _ => fileInput.ref.click() }
                ),
                button(
                    cls := "btn btn-sm btn-outline btn-error",
                    I18N_UI.catalog.clear_all_button,
                    onClick --> { _ =>
                        catalogStateVar.set(CatalogState.empty)
                        errorMessageVar.set(None)
                    }
                ),
                button(
                    cls := "btn btn-sm",
                    I18N_UI.buttons.close,
                    onClick --> { _ => close() }
                )
            )
        ),
        form(
            method := "dialog",
            cls    := "modal-backdrop",
            button("close")
        )
    )

    val node: HtmlElement = dialogNode

end CatalogManagerDialog
