/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.views

import afpma.firecalc.payments.shared.Constants.{FIRECALC_FILE_EXTENSION, LEGACY_FIRECALC_FILE_EXTENSION}

import afpma.firecalc.ui.*
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.project.*
import afpma.firecalc.ui.services.FileSystemService

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom

import scala.util.{Failure, Success}

final case class ProjectSelectorView()(using Locale) extends Component:

    private val projectsVar = Var(ProjectIndex.load())

    private def refresh(): Unit = projectsVar.set(ProjectIndex.load())

    private def formatDate(epoch: Double): String =
        val date = new scala.scalajs.js.Date(epoch)
        s"${date.toLocaleDateString()} ${date.toLocaleTimeString()}"

    private def createAndNavigate(): Unit =
        val id = ProjectManager.createNewProject()
        router.pushState(ProjectPage(localeVar.now().language, id))

    private def loadFromFileContent(yamlContent: String, fileName: String): Unit =
        AppStateSchemaHelper.decodeFromFile(yamlContent) match
            case Success(schema) =>
                val id = ProjectManager.openFromFile(schema)
                router.pushState(ProjectPage(localeVar.now().language, id))
            case Failure(e) =>
                dom.window.alert(s"Failed to load: ${e.getMessage}")

    lazy val node: Div =
        val hiddenFileInput = input(
            typ    := "file",
            accept := s"$FIRECALC_FILE_EXTENSION,$LEGACY_FIRECALC_FILE_EXTENSION,.yaml",
            cls    := "hidden",
            inContext { thisNode =>
                onChange --> { _ =>
                    val files = thisNode.ref.files
                    if files.length > 0 then
                        import scala.concurrent.ExecutionContext.Implicits.global
                        FileSystemService.readFileFromInput(files(0)).foreach {
                            case Right((content, fileName)) => loadFromFileContent(content, fileName)
                            case Left(error)                => dom.window.alert(error)
                        }
                        thisNode.ref.value = ""
                }
            }
        )

        div(
            cls := "mx-auto max-w-3xl p-8",
            hiddenFileInput,

            // Header
            h1(cls := "text-3xl font-bold mb-2", "FireCalc AFPMA"),
            p(cls := "text-base-content/60 mb-8", I18N_UI.project_selector.title),

            // Action buttons
            div(
                cls := "flex flex-row gap-3 mb-8",
                button(
                    cls := "btn btn-secondary",
                    lucide.plus,
                    I18N_UI.project_selector.new_project,
                    onClick --> { _ => createAndNavigate() }
                ),
                button(
                    cls := "btn btn-secondary btn-outline",
                    lucide.`folder-open`(stroke_width = 1.5),
                    I18N_UI.project_selector.open_file,
                    onClick --> { _ =>
                        if FileSystemService.isElectron then
                            import scala.concurrent.ExecutionContext.Implicits.global
                            FileSystemService.openFile().foreach {
                                case Right(Some((content, fileName))) => loadFromFileContent(content, fileName)
                                case Right(None)                      => () // cancelled
                                case Left(error)                      => dom.window.alert(error)
                            }
                        else hiddenFileInput.ref.click()
                    }
                )
            ),

            // Project list
            children <-- projectsVar.signal.map { projects =>
                if projects.isEmpty then
                    Seq(
                        div(
                            cls := "text-center text-base-content/50 py-16",
                            p(cls := "text-lg", I18N_UI.project_selector.no_projects)
                        )
                    )
                else
                    projects.sortBy(-_.lastModified).map(renderProjectCard)
            }
        )

    private def renderProjectCard(entry: ProjectEntry): Div =
        div(
            cls := "card bg-base-200 p-4 mb-3 flex flex-row items-center justify-between",
            // Left: project info
            div(
                cls := "flex flex-col",
                p(
                    cls := "font-medium",
                    if entry.name.nonEmpty then entry.name
                    else I18N_UI.project_selector.no_name
                ),
                p(
                    cls := "text-sm text-base-content/60",
                    s"${I18N_UI.project_selector.modified}: ${formatDate(entry.lastModified)}"
                )
            ),
            // Right: action buttons
            div(
                cls := "flex flex-row gap-2",
                button(
                    cls := "btn btn-sm btn-secondary",
                    I18N_UI.project_selector.open,
                    onClick --> { _ =>
                        router.pushState(ProjectPage(localeVar.now().language, entry.id))
                    }
                ),
                button(
                    cls := "btn btn-sm btn-error btn-outline",
                    lucide.`trash-2`(stroke_width = 1.5),
                    onClick --> { _ =>
                        if dom.window.confirm(I18N_UI.project_selector.confirm_delete(entry.name)) then
                            ProjectManager.deleteProject(entry.id)
                            refresh()
                    }
                )
            )
        )
