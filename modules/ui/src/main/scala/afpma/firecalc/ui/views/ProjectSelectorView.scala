/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.views

import afpma.firecalc.payments.shared.Constants.FIRECALC_FILE_EXTENSION
import afpma.firecalc.payments.shared.Constants.LEGACY_FIRECALC_FILE_EXTENSION

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.GlobalErrorDialog
import afpma.firecalc.ui.daisyui.DaisyUITooltip
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.project.*
import afpma.firecalc.ui.services.FileSystemService
import afpma.firecalc.ui.services.ProjectExport

import com.raquo.laminar.api.L.*

import scala.util.Failure
import scala.util.Success

import io.taig.babel.Locale
import org.scalajs.dom

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
            case Failure(e)      =>
                GlobalErrorDialog.showUserAttemptError(
                    Some                                             (fileName    ),
                    currentI18N.errors.failed_to_decode_project.apply(e.getMessage)
                )

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
                        // Capture the File up front: the input is cleared below, which
                        // empties the live FileList, and the name is needed for feedback.
                        val file = files(0)
                        FileSystemService.readFileFromInput(file).foreach {
                            case Right((content, fileName)) => loadFromFileContent(content, fileName)
                            case Left(error)                => GlobalErrorDialog.showUserAttemptError(Some(file.name), error)
                        }
                        thisNode.ref.value = ""
                }
            }
        )

        div(
            cls := "mx-auto max-w-3xl p-8",
            hiddenFileInput,

            // Header
            h1(cls := "text-3xl font-bold mb-2", "FireCalc AFPMA"                ),
            p (cls := "text-base-content/60 mb-8", I18N_UI.project_selector.title),

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
                                // No file name is known when the native dialog/read fails.
                                case Left(error)                      => GlobalErrorDialog.showUserAttemptError(None, error)
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
                else projects.sortBy(-_.lastModified).map(renderProjectCard)
            },

            // Storage usage footer
            storageUsageFooter
        )

    private def storageUsageFooter: Div =
        val expandedVar = Var(false)

        val (totalSize, perKeyBreakdown) = LocalStorageUtils.estimateStorageUsage()
        val allKeys = perKeyBreakdown.toSeq.sortBy(-_._2)

        val detailsChildren = allKeys.take(15).map { (k, size) =>
            p(cls := "ml-2", s"  $k: ${LocalStorageUtils.formatBytes(size)}")
        }

        div(
            cls := "mt-8 text-xs text-base-content/40 font-mono border-t",
            // Summary line (always visible, clickable to expand)
            div(
                cls := "cursor-pointer hover:text-base-content/60 transition-colors pt-2",
                onClick --> { _ => expandedVar.set(!expandedVar.now()) },
                children <-- expandedVar.signal.map { expanded =>
                    val icon = if expanded then lucide.`chevron-up` else lucide.`chevron-down`
                    Seq(icon, span(s"localStorage: ${LocalStorageUtils.formatBytes(totalSize)} (${allKeys.size} keys)"))
                }
            ),
            // Expandable details
            children <-- expandedVar.signal.map { expanded =>
                if expanded then detailsChildren else Seq.empty
            }
        )

    private def renderProjectCard(entry: ProjectEntry): Div =
        // Per-card, never per-list: the control is inert until clicked, so a pending
        // native save dialog cannot be stacked by a second press.
        val pending = Var(false)
        div(
            cls := "card bg-base-200 p-4 mb-3 flex flex-row items-center justify-between",
            // Left: project info
            div(
                cls := "flex flex-col",
                p(cls := "font-medium", entry.displayName),
                p(
                    cls := "text-sm text-base-content/60",
                    s"${I18N_UI.project_selector.modified}: ${formatDate(entry.lastModified)}"
                )
            ),
            // Right: action buttons
            div(
                cls := "flex flex-row gap-2",
                button        (
                    cls := "btn btn-sm btn-secondary",
                    I18N_UI.project_selector.open,
                    onClick --> { _ =>
                        router.pushState(ProjectPage(localeVar.now().language, entry.id))
                    }
                ),
                // Two invariants this control exists for:
                //  - the non-destructive escape for an unreadable project: its
                //    stored bytes are only ever removed by deleting the card,
                //    never by downloading them;
                //  - it reads nothing at render time (no `project_<id>_app_state`
                //    lookup, no decode): the card is inert until clicked.
                DaisyUITooltip(
                    ttContent  = p(I18N_UI.project_selector.download_stored),
                    element    = button(
                        cls        := "btn btn-sm btn-outline",
                        lucide.`file-down`(stroke_width = 1.5),
                        aria.label := I18N_UI.project_selector.download_stored,
                        disabled <-- pending,
                        onClick --> { _ =>
                            // Guard against a double press while a dialog is pending.
                            if !pending.now() then
                                pending.set(true)
                                import scala.concurrent.ExecutionContext.Implicits.global
                                ProjectExport.downloadStoredState(entry.id).onComplete { result =>
                                    result match
                                        // Left: nothing was stored, or storage could not be read.
                                        // Reported through the user-attempt path (per-attempt
                                        // suppression key), so pressing again reports again.
                                        case Success(Left(message)) =>
                                            GlobalErrorDialog.showUserAttemptError(Some(entry.displayName), message)
                                        // Right(true): handed off. Right(false): the native dialog
                                        // was cancelled. Both silent — only the button re-arms.
                                        case Success(Right(_))      => ()
                                        // Defensive: the primitive never fails by design.
                                        case Failure(ex)            =>
                                            GlobalErrorDialog
                                                .showUserAttemptError(Some(entry.displayName), ex.getMessage)
                                    // Re-armed on every outcome: a stuck disabled button would
                                    // make the only non-destructive escape unusable.
                                    pending.set(false)
                                }
                        }
                    ),
                    ttPosition = "tooltip-bottom"
                ),
                button(
                    cls := "btn btn-sm btn-error btn-outline",
                    lucide.`trash-2`(stroke_width = 1.5),
                    onClick --> { _ =>
                        // displayName, not entry.name: the confirmation names the
                        // project exactly as the card above it does (localized
                        // "Untitled" for an empty name, never raw empty quotes).
                        if dom.window.confirm(I18N_UI.project_selector.confirm_delete(entry.displayName)) then
                            ProjectManager.deleteProject(entry.id)
                            refresh                     (        )
                    }
                )
            )
        )
