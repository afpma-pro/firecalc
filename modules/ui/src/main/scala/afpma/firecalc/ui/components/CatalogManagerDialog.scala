/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.utils.BuildInfo

import afpma.firecalc.payments.shared.Constants.FIRECALC_CATALOG_FILE_EXTENSION

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.models.CatalogState
import afpma.firecalc.ui.models.catalogStateVar
import afpma.firecalc.ui.services.CatalogImageStore
import afpma.firecalc.ui.services.FileSystemService

import com.raquo.laminar.api.L.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import afpma.firecalc.catalog.CatalogParseError
import afpma.firecalc.catalog.CatalogParser
import io.taig.babel.Locale
import org.scalajs.dom
import org.scalajs.dom.HTMLDialogElement

case class CatalogManagerDialog()(using Locale) extends Component:

    private val errorMessageVar: Var[Option[String]] = Var(None)

    def open(): Unit =
        errorMessageVar.set(None)
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit = dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private def formatParseError(parseError: CatalogParseError): String = parseError match
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

    private def handleFilesLoad(files: List[dom.File]): Unit =
        Future.traverse(files)(FileSystemService.readFileFromInput).foreach: results =>
            val errors  = List.newBuilder[String]
            var allImages    = Map.empty[String, String]
            var allWarnings  = List.empty[String]

            val catalogFiles = results.zip(files).flatMap:
                case (Left(err), file) =>
                    errors += s"${file.name}: $err"
                    None
                case (Right((content, _)), file) =>
                    CatalogParser.parse(content) match
                        case Left(parseError) =>
                            errors += s"${file.name}: ${formatParseError(parseError)}"
                            None
                        case Right(catalogFile) =>
                            val (images, warnings) = CatalogState.extractImages(catalogFile)
                            allImages = allImages ++ images
                            allWarnings = allWarnings ++ warnings
                            Some(catalogFile)

            if catalogFiles.nonEmpty then
                val merged = catalogFiles.foldLeft(catalogStateVar.now())(CatalogState.merge)
                catalogStateVar.set(merged)
                if allImages.nonEmpty then
                    CatalogImageStore.putAll(allImages).recover { case e =>
                        dom.console.warn(s"Failed to store catalog images: ${e.getMessage}")
                    }
                if allWarnings.nonEmpty then
                    dom.console.warn(s"Catalog image warnings: ${allWarnings.mkString(", ")}")

            val errorList = errors.result()
            if errorList.nonEmpty then errorMessageVar.set(Some(errorList.mkString("\n")))
            else errorMessageVar.set(None)

    private lazy val fileInput: Input = input(
        typ      := "file",
        accept   := FIRECALC_CATALOG_FILE_EXTENSION,
        multiple := true,
        cls      := "hidden",
        onChange --> { ev =>
            val target = ev.target.asInstanceOf[dom.html.Input]
            val files  = List.tabulate(target.files.length)(target.files(_))
            if files.nonEmpty then handleFilesLoad(files)
            target.value = "" // Reset so same files can be re-selected
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
                        val hasEntries = state.door_15a_fireboxes.nonEmpty || state.single_tested_fireboxes.nonEmpty || state.pipe_presets.nonEmpty || state.casing_presets.nonEmpty || state.flow_resistance_presets.nonEmpty || state.angle_presets.nonEmpty
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
                                ),
                                Option.when(state.angle_presets.nonEmpty)(
                                    p(s"• ${I18N_UI.catalog.angle_presets}: ${state.angle_presets.size}")
                                )
                            )
                )
            ),

            // Error message
            div(
                cls     := "mb-4",
                display <-- errorMessageVar.signal.map(_.fold("none")(_ => "")),
                div(
                    cls        := "alert alert-error text-sm",
                    whiteSpace := "pre-line",
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
                        CatalogImageStore.clear()
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
