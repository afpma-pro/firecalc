/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import scala.util.Failure
import scala.util.Success

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.daisyui.DaisyUITooltip
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.services.FileSystemService

import com.raquo.laminar.api.L.*
import io.taig.babel.Locale
import org.scalajs.dom

object FireCalcProjet:

    val FIRECALC_FILE_EXTENSION = ".firecalc.yaml"

    case class HardCodedAppStateComponent(nextAppState: AppState, buttonTitle: String)(using Locale) extends Component:
        lazy val node = 
            div( 
                cls := "h-5",
                DaisyUITooltip(
                    ttContent = p(I18N_UI.tooltips.load_project("'Exemple'")),
                    element = 
                        div(
                            cls := "h-4 cursor-pointer",
                            div(
                                cls := "flex flex-row items-center gap-x-2",
                                lucide.`file-question-mark`(stroke_width = 1),
                                // p( cls := "text-sm", buttonTitle),
                            ),
                            onClick --> { _ => 
                                appStateVar.set(nextAppState)
                            }
                        ),
                    ttPosition = "tooltip-bottom"
                )
            )
            
    
    case class NewBlankComponent()(using Locale) extends Component:
        lazy val node = 
            div(
                cls := "",
                DaisyUITooltip(
                    ttContent = p(I18N_UI.tooltips.new_project),
                    element = 
                        div(
                            cls := "w-4 h-4 cursor-pointer",
                            lucide.`file`(stroke_width = 1),
                            onClick --> { _ => 
                                appStateVar.set(AppState.init)
                            }
                        ),
                    ttPosition = "tooltip-bottom"
                )
            )
            


    case class BackupComponent()(using Locale) extends Component:

        val isProcessingVar = Var(false)
        val errorVar = Var[Option[String]](None)

        def saveYaml(state: AppState): Unit =
            import scala.concurrent.ExecutionContext.Implicits.global
            
            isProcessingVar.set(true)
            errorVar.set(None)

            // Convert state to YAML
            AppState.encodeToYaml(state) match
                case Failure(ex) =>
                    errorVar.set(Some(I18N_UI.errors.failed_to_encode_project.apply(ex.getMessage)))
                    isProcessingVar.set(false)
                    
                case Success(yamlContent) =>
                    // Use FileSystemService which handles both browser and Electron
                    FileSystemService.saveFile(filename_var.now(), yamlContent).foreach {
                        case Left(error) =>
                            errorVar.set(Some(error))
                            isProcessingVar.set(false)
                            
                        case Right(_) =>
                            isProcessingVar.set(false)
                    }

        lazy val node = 
            div(
                    cls := "",

                    DaisyUITooltip(
                        ttContent = p(I18N_UI.tooltips.save_project),
                        element = 
                            div(
                                cls := "w-4 h-4 cursor-pointer",
                                disabled <-- isProcessingVar,
                                lucide.`file-down`(stroke_width = 1),
                                onClick --> { _ =>
                                    saveYaml(appStateVar.now())
                                }
                            ),
                        ttPosition = "tooltip-bottom"
                    ),

                    // Processing indicator
                    // child <-- isProcessingVar.signal.map {
                    //     case true => div(cls := "processing", "Preparing download...")
                    //     case false => emptyNode
                    // },

                    // Error message
                    // child <-- errorVar.signal.map {
                    //     case Some(msg) => div(cls := "error-message", s"Error: $msg")
                    //     case None => emptyNode
                    // }


            )
            
            
            


    case class UploadComponent()(using Locale) extends Component:

        val isLoadingVar = Var(false)
        val errorVar = Var[Option[String]](None)
        val fileNameVar = Var[Option[String]](None)

        /**
         * Load project from file content
         */
        def loadFromContent(yamlContent: String, fileName: String): Unit =
            scala.scalajs.js.Dynamic.global.console.log(s"Loading file: $fileName")
            fileNameVar.set(Some(fileName))
            isLoadingVar.set(true)
            errorVar.set(None)
            
            AppState.decodeFromYaml(yamlContent) match
                case Failure(e) =>
                    scala.scalajs.js.Dynamic.global.console.log("ERROR: Failed to load project")
                    scala.scalajs.js.Dynamic.global.console.log(e.getMessage())
                    errorVar.set(Some(I18N_UI.errors.failed_to_decode_project.apply(e.getMessage)))
                    isLoadingVar.set(false)
                    
                case Success(nextAppState) =>
                    scala.scalajs.js.Dynamic.global.console.log("Project loaded successfully")
                    appStateVar.set(nextAppState)
                    isLoadingVar.set(false)

        /**
         * Open file using Electron native dialog
         */
        def openFileElectron(): Unit =
            import scala.concurrent.ExecutionContext.Implicits.global
            
            isLoadingVar.set(true)
            errorVar.set(None)
            
            FileSystemService.openFile().foreach {
                case Left(error) =>
                    errorVar.set(Some(error))
                    isLoadingVar.set(false)
                    
                case Right(None) =>
                    // User cancelled
                    isLoadingVar.set(false)
                    
                case Right(Some((content, fileName))) =>
                    loadFromContent(content, fileName)
            }

        /**
         * Read file from browser file input
         */
        def readFileFromBrowser(file: dom.File): Unit =
            import scala.concurrent.ExecutionContext.Implicits.global
            
            isLoadingVar.set(true)
            errorVar.set(None)
            
            FileSystemService.readFileFromInput(file).foreach {
                case Left(error) =>
                    errorVar.set(Some(error))
                    isLoadingVar.set(false)
                    
                case Right((content, fileName)) =>
                    loadFromContent(content, fileName)
            }

        lazy val node =
            // Hidden file input for browser mode only
            val hiddenFileInput = input(
                typ := "file",
                accept := FIRECALC_FILE_EXTENSION,
                cls := "hidden",
                inContext { thisNode =>
                    onChange --> { _ =>
                        val files = thisNode.ref.files
                        if files.length > 0 then
                            readFileFromBrowser(files(0))
                    }
                }
            )

            div(
                cls := "",

                // Hidden file input (browser only)
                hiddenFileInput,

                // Visible button
                DaisyUITooltip(
                    ttContent = p(I18N_UI.tooltips.open_project),
                    element =
                        div(
                            cls := "w-4 h-4 cursor-pointer",
                            disabled <-- isLoadingVar,
                            lucide.`folder-open`(stroke_width = 1),
                            onClick --> { _ =>
                                if FileSystemService.isElectron then
                                    openFileElectron()
                                else
                                    hiddenFileInput.ref.click()
                            }
                        ),
                    ttPosition = "tooltip-bottom"
                ),

                // Error message
                child <-- errorVar.signal.map {
                    case Some(msg) => div(cls := "error-message", I18N_UI.errors.error_prefix.apply(msg))
                    case None => emptyNode
                }
            )