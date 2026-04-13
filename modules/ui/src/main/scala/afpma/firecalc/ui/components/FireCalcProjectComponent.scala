/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.payments.shared.Constants.FIRECALC_FILE_EXTENSION
import afpma.firecalc.payments.shared.Constants.LEGACY_FIRECALC_FILE_EXTENSION

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.components.GlobalErrorDialog
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.models.project.ProjectManager
import afpma.firecalc.ui.services.FileSystemService

import com.raquo.laminar.api.L.*

import scala.util.Failure
import scala.util.Success

import afpma.laminar.form.daisyui.DaisyUITooltip
import io.taig.babel.Locale
import org.scalajs.dom

object FireCalcProjet:

    case class HardCodedEngineStateComponent(nextEngineState: EngineState, buttonTitle: String)(using Locale)
        extends Component:
        lazy val node =
            div(
                cls := "h-5",
                DaisyUITooltip (
                    ttContent  = p(I18N_UI.tooltips.load_project("'Exemple'")),
                    element    = div(
                        cls := "h-4 cursor-pointer",
                        div(
                            cls := "flex flex-row items-center gap-x-2",
                            lucide.`file-question-mark`(stroke_width = 1)
                            // p( cls := "text-sm", buttonTitle),
                        ),
                        onClick --> { _ =>
                            engineStateVar.set      (nextEngineState        )
                            undoManager.reset       (                       )
                            fireboxCacheStateVar.set(FireboxCacheState.empty)
                        }
                    ),
                    ttPosition = "tooltip-bottom"
                )
            )

    case class NewBlankComponent()(using Locale) extends Component:
        lazy val node =
            div(
                cls := "",
                DaisyUITooltip (
                    ttContent  = p(I18N_UI.tooltips.new_project),
                    element    = div(
                        cls := "w-4 h-4 cursor-pointer",
                        lucide.`file`(stroke_width = 1),
                        onClick --> { _ =>
                            val id = ProjectManager.createNewProject()
                            router.pushState(ProjectPage(localeVar.now().language, id))
                        }
                    ),
                    ttPosition = "tooltip-bottom"
                )
            )

    case class BackupComponent()(using Locale) extends Component:

        val isProcessingVar = Var(false)

        def saveYaml(): Unit =
            import scala.concurrent.ExecutionContext.Implicits.global

            isProcessingVar.set(true)

            // Export full AppStateSchema (engine_state + sensitive_data + billing_data).
            // Backend PDF generation uses a separate code path (OrderPDFReportModalComponent)
            // that sends only engine_state — this file export is independent.
            AppStateSchemaHelper.encodeToYaml(appStateSchemaVar.now()) match
                case Failure(ex) =>
                    GlobalErrorDialog.showGenericError(I18N_UI.errors.failed_to_encode_project.apply(ex.getMessage))
                    isProcessingVar.set               (false                                                       )

                case Success(yamlContent) =>
                    // Use FileSystemService which handles both browser and Electron
                    FileSystemService.saveFile(filename_var.now(), yamlContent).foreach {
                        case Left(error) =>
                            GlobalErrorDialog.showGenericError(error)
                            isProcessingVar.set               (false)

                        case Right(_) =>
                            isProcessingVar.set(false)
                    }

        lazy val node =
            div(
                cls := "",
                DaisyUITooltip (
                    ttContent  = p(I18N_UI.tooltips.save_project),
                    element    = div(
                        cls := "w-4 h-4 cursor-pointer",
                        disabled <-- isProcessingVar,
                        lucide.`file-down`(stroke_width = 1),
                        onClick --> { _ =>
                            saveYaml()
                        }
                    ),
                    ttPosition = "tooltip-bottom"
                )
            )

    case class UploadComponent()(using Locale) extends Component:

        val isLoadingVar = Var(false)
        val fileNameVar  = Var[Option[String]](None)

        /** Load project from file content */
        def loadFromContent(yamlContent: String, fileName: String): Unit =
            scala.scalajs.js.Dynamic.global.console.log(s"Loading file: $fileName")
            fileNameVar.set                            (Some(fileName)            )
            isLoadingVar.set                           (true                      )

            // Auto-detect full schema vs legacy engine-state-only format
            AppStateSchemaHelper.decodeFromFile(yamlContent) match
                case Failure(e) =>
                    scala.scalajs.js.Dynamic.global.console.log("ERROR: Failed to load project"                            )
                    scala.scalajs.js.Dynamic.global.console.log(e.getMessage()                                             )
                    GlobalErrorDialog.showGenericError         (I18N_UI.errors.failed_to_decode_project.apply(e.getMessage))
                    isLoadingVar.set                           (false                                                      )

                case Success(schema) =>
                    scala.scalajs.js.Dynamic.global.console.log("Project loaded successfully")
                    val id = ProjectManager.openFromFile(schema)
                    router.pushState(ProjectPage(localeVar.now().language, id))
                    isLoadingVar.set(false                                    )

        /** Open file using Electron native dialog */
        def openFileElectron(): Unit =
            import scala.concurrent.ExecutionContext.Implicits.global

            isLoadingVar.set(true)

            FileSystemService.openFile().onComplete {
                case Success(Left(error)) =>
                    GlobalErrorDialog.showGenericError(error)
                    isLoadingVar.set                  (false)

                case Success(Right(None)) =>
                    // User cancelled
                    isLoadingVar.set(false)

                case Success(Right(Some((content, fileName)))) =>
                    loadFromContent(content, fileName)

                case Failure(ex) =>
                    GlobalErrorDialog.showGenericError(ex.getMessage)
                    isLoadingVar.set                  (false        )
            }

        /** Read file from browser file input */
        def readFileFromBrowser(file: dom.File): Unit =
            import scala.concurrent.ExecutionContext.Implicits.global

            isLoadingVar.set(true)

            FileSystemService.readFileFromInput(file).onComplete {
                case Success(Left(error)) =>
                    GlobalErrorDialog.showGenericError(error)
                    isLoadingVar.set                  (false)

                case Success(Right((content, fileName))) =>
                    loadFromContent(content, fileName)

                case Failure(ex) =>
                    GlobalErrorDialog.showGenericError(ex.getMessage)
                    isLoadingVar.set                  (false        )
            }

        lazy val node =
            // Hidden file input for browser mode only
            val hiddenFileInput = input(
                typ    := "file",
                accept := s"$FIRECALC_FILE_EXTENSION,$LEGACY_FIRECALC_FILE_EXTENSION,.yaml",
                cls    := "hidden",
                inContext { thisNode =>
                    onChange --> { _ =>
                        val files = thisNode.ref.files
                        if files.length > 0 then readFileFromBrowser(files(0))
                        thisNode.ref.value = "" // Reset so same file can be re-selected
                    }
                }
            )

            div(
                cls := "",

                // Hidden file input (browser only)
                hiddenFileInput,

                // Visible button
                DaisyUITooltip (
                    ttContent  = p(I18N_UI.tooltips.open_project),
                    element    = div(
                        cls := "w-4 h-4 cursor-pointer",
                        disabled <-- isLoadingVar,
                        lucide.`folder-open`(stroke_width = 1),
                        onClick --> { _ =>
                            if FileSystemService.isElectron then openFileElectron()
                            else hiddenFileInput.ref.click                       ()
                        }
                    ),
                    ttPosition = "tooltip-bottom"
                )
            )
