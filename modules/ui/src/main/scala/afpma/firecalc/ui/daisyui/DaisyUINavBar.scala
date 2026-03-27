/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.CatalogManagerDialog
import afpma.firecalc.ui.components.FireCalcProjet
import afpma.firecalc.ui.components.OrderPDFReportModalComponent
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.models.{viz3DPanelVar, viz3DPanelOn, viz3DPanelOff}
import afpma.firecalc.ui.models.{graphPanelVar, graphPanelOn, graphPanelOff}

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*

import io.taig.babel.Language

object DaisyUINavBar:

    val details = htmlTag("details")
    val summary = htmlTag("summary")

    val svgElement = svg.svg(
        svg.xmlns   := "http://www.w3.org/2000/svg",
        svg.cls     := "h-5 w-5",
        svg.fill    := "none",
        svg.viewBox := "0 0 24 24",
        svg.stroke  := "currentColor",
        svg.path (
            svg.svgAttr("stroke-linecap", StringAsIsCodec, None)  := "round",
            svg.svgAttr("stroke-linejoin", StringAsIsCodec, None) := "round",
            svg.svgAttr("stroke-width", StringAsIsCodec, None)    := "2",
            svg.svgAttr("d", StringAsIsCodec, None)               := "M4 6h16M4 12h8m-8 6h16"
        )
    )

    final case class HeaderMenuButton(
        titleLeftNode: HtmlElement,
        buttonString : String
    )                                (using DisplayUnits, Locale)
        extends Component:

        val disabledAttr: HtmlAttr[Boolean] = htmlAttr("disabled", BooleanAsAttrPresenceCodec)

        private val catalogManagerDialog = CatalogManagerDialog()

        val node = div(
            cls := "flex flew-row navbar items-center justify-center bg-(--color-vlight-ocre) shadow-sm gap-x-2",
            div(
                cls := "flex-none",
                titleLeftNode
            ),
            div(
                cls := "flex-1 flex flex-row items-center gap-x-6",
                // Group 1: Undo / Redo
                div(
                    cls := "flex flex-row items-center gap-x-2",
                    // Undo button
                    div(
                        cls := "flex items-center h-6",
                        DaisyUITooltip (
                            ttContent  = div(I18N_UI.buttons.undo),
                            element    = div(
                                cls      := "btn btn-outline btn-square hover:bg-transparent hover:border-(--btn-color) !w-6 !h-6 !min-h-0 !p-0",
                                cls("text-base-content") <-- undoManager.canUndo,
                                cls("text-base-content/40") <-- undoManager.cannotUndo,
                                lucide.undo(stroke_width = 1.5, w = 16, h = 16),
                                onClick.mapToUnit --> { _ => performUndo() }
                            ),
                            ttPosition = "tooltip-bottom"
                        )
                    ),
                    // Redo button
                    div(
                        cls := "flex items-center h-6",
                        DaisyUITooltip (
                            ttContent  = div(I18N_UI.buttons.redo),
                            element    = div(
                                cls      := "btn btn-outline btn-square hover:bg-transparent hover:border-(--btn-color) !w-6 !h-6 !min-h-0 !p-0",
                                cls("text-base-content") <-- undoManager.canRedo,
                                cls("text-base-content/40") <-- undoManager.cannotRedo,
                                lucide.redo(stroke_width = 1.5, w = 16, h = 16),
                                onClick.mapToUnit --> { _ => performRedo() }
                            ),
                            ttPosition = "tooltip-bottom"
                        )
                    )
                ),
                div(cls := "flex-grow"),
                // Group 2: File operations
                div(
                    cls := "flex flex-row items-center gap-x-6",
                    FireCalcProjet.NewBlankComponent            (),
                    FireCalcProjet.UploadComponent              (),
                    FireCalcProjet.BackupComponent              (),
                    FireCalcProjet.HardCodedEngineStateComponent(
                        nextEngineState = EngineState.example_projet_15544,
                        buttonTitle     = I18N_UI.buttons.load_example_project_15544
                    )
                ),
                div(cls := "flex-grow"),
                // Group 3: Catalog
                div(
                    cls := "flex items-center h-6",
                    DaisyUITooltip(
                        ttContent  = div(I18N_UI.catalog.manager_title),
                        element    = div(
                            cls      := "btn btn-outline btn-square hover:bg-transparent hover:border-(--btn-color) text-base-content/60 !w-6 !h-6 !min-h-0 !p-0",
                            lucide.database(stroke_width = 1.5, w = 16, h = 16),
                            onClick --> { _ => catalogManagerDialog.open() }
                        ),
                        ttPosition = "tooltip-bottom"
                    )
                ),
                div(cls := "flex-grow"),
                // Group 4: Expert, 3D & Graph toggles
                div(
                    cls := "flex flex-row items-center gap-x-2",
                    // Expert mode toggle
                    div(
                        cls := "flex items-center h-6",
                        DaisyUITooltip(
                            ttContent  = div(I18N_UI.tooltips.display_details),
                            element    = div(
                                cls      := "btn btn-outline btn-square hover:bg-transparent hover:border-(--btn-color) !w-6 !h-6 !min-h-0 !p-0",
                                cls("bg-base-300 text-base-content border-base-content/30") <-- expertModeOn,
                                cls("text-base-content/40 hover:text-base-content") <-- expertModeOff,
                                lucide.`flask-conical`(stroke_width = 1.5),
                                onClick.mapToUnit --> { _ => expertModeVar.update(!_) }
                            ),
                            ttPosition = "tooltip-bottom"
                        )
                    ),
                    // 3D visualization toggle
                    div(
                        cls := "flex items-center h-6",
                        DaisyUITooltip(
                            ttContent  = div("3D"),
                            element    = div(
                                cls      := "btn btn-outline btn-square hover:bg-transparent hover:border-(--btn-color) !w-6 !h-6 !min-h-0 !p-0",
                                cls("bg-base-300 text-base-content border-base-content/30") <-- viz3DPanelOn,
                                cls("text-base-content/40 hover:text-base-content") <-- viz3DPanelOff,
                                lucide.box(stroke_width = 1.5, w = 16, h = 16),
                                onClick.mapToUnit --> { _ => viz3DPanelVar.update(!_) }
                            ),
                            ttPosition = "tooltip-bottom"
                        )
                    ),
                    // Graph (2D chart) toggle
                    div(
                        cls := "flex items-center h-6",
                        DaisyUITooltip(
                            ttContent  = div(I18N_UI.graph.title),
                            element    = div(
                                cls      := "btn btn-outline btn-square hover:bg-transparent hover:border-(--btn-color) !w-6 !h-6 !min-h-0 !p-0",
                                cls("bg-base-300 text-base-content border-base-content/30") <-- graphPanelOn,
                                cls("text-base-content/40 hover:text-base-content") <-- graphPanelOff,
                                lucide.`chart-line`(stroke_width = 1.5, w = 16, h = 16),
                                onClick.mapToUnit --> { _ => graphPanelVar.update(!_) }
                            ),
                            ttPosition = "tooltip-bottom"
                        )
                    )
                ),

                div(cls := "flex-grow"),
            ),
            // OPTIONAL

            // div(
            //     cls := "flex-1 flex flex-row justify-center gap-x-6",

            //     FireCalcProjet.HardCodedAppStateComponent(
            //         nextAppState = AppState.example_projet_15544,
            //         buttonTitle = I18N_UI.buttons.load_example_project_15544
            //     ),
            // ),

            // OPTIONAL

            // div(
            //     cls := "flex-1 flex flex-row justify-center gap-x-6",

            //     FireCalcProjet.HardCodedAppStateComponent(
            //         nextAppState = AppState.init_as_CasPratique_15544_FDIM_EX_03,
            //         buttonTitle = "Cas pratique ex03"
            //     ),
            // ),

            // EN15544 button
            div(
                cls := "flex items-stretch gap-2",
                div    (
                    tabIndex     := 0,
                    role         := "button",
                    disabledAttr := true,
                    cls          := "btn btn-outline hover:btn-secondary rounded-field",
                    "EN 15544:2023"
                )
                // div(
                //     cls := "dropdown dropdown-end",
                //     div(
                //         tabIndex := 0,
                //         role     := "button",
                //         cls      := "btn btn-outline rounded-field",
                //         "EN 15544:2023"
                //     ),
                //     ul(
                //         tabIndex := 0,
                //         cls      := "menu dropdown-content bg-base-200 rounded-box z-1 mt-4 w-24 p-2 shadow-sm",
                //         li(a("EN 15544:2023")),
                //         // MCE
                //         // Other variants
                //     )
                // )
            ),

            // ORDER PDF BUTTON
            div(
                cls := "shrink flex flex-row justify-end gap-2",

                // Order PDF Report Modal
                OrderPDFReportModalComponent(),

                // LANGUAGE button
                div(
                    cls := "flex items-stretch",
                    div(
                        cls := "dropdown dropdown-end",
                        div(
                            tabIndex := 0,
                            role     := "button",
                            cls      := "btn not-focus:btn-outline rounded-field hover:btn-secondary focus:btn-secondary focus:btn",
                            div(cls := "w-4 h-4 flex items-center justify-center", lucide.languages(stroke_width = 1.5))
                            // div(cls := "w-4 h-4 flex items-center justify-center", lucide.`chevron-down`),
                        ),
                        ul (
                            tabIndex := 0,
                            cls      := "menu dropdown-content place-content-end bg-base-200 rounded-box z-1 mt-4 w-18 p-2 shadow-sm",
                            children(
                                li(
                                    cls := "w-full place-content-center",
                                    a(
                                        dataAttr("id") := "France",
                                        router.navigateTo(HomePage(Language("fr"), Some(DisplayUnits.SI))),
                                        "FR"
                                    )
                                ),
                                li(
                                    cls := "w-full place-content-center",
                                    a(
                                        dataAttr("id") := "English",
                                        router.navigateTo(HomePage(Language("en"), Some(DisplayUnits.SI))),
                                        "EN"
                                    )
                                )
                            ) <-- displayUnitsVar.signal.map(du => du == DisplayUnits.SI),
                            children(
                                li(
                                    cls := "w-full place-content-center",
                                    a(
                                        dataAttr("id") := "France",
                                        router.navigateTo(HomePage(Language("fr"), Some(DisplayUnits.Imperial))),
                                        "FR"
                                    )
                                ),
                                li(
                                    cls := "w-full place-content-center",
                                    a(
                                        dataAttr("id") := "English",
                                        router.navigateTo(HomePage(Language("en"), Some(DisplayUnits.Imperial))),
                                        "EN"
                                    )
                                )
                            ) <-- displayUnitsVar.signal.map(du => du == DisplayUnits.Imperial)
                        )
                    )
                ),

                // UNITS button
                div(
                    cls := "flex items-stretch gap-2",
                    div(
                        cls := "dropdown dropdown-end",
                        div(
                            tabIndex := 0,
                            role     := "button",
                            cls      := "btn btn-outline hover:btn-secondary focus:btn-secondary rounded-field",
                            I18N_UI.buttons.units.toLowerCase()
                        ),
                        ul (
                            tabIndex := 0,
                            cls      := "menu dropdown-content bg-base-200 rounded-box z-1 mt-4 w-24 p-2 shadow-sm",
                            children <-- localeVar.signal.map(loc =>
                                Seq(
                                    li(
                                        a(
                                            "SI",
                                            router.navigateTo(
                                                HomePage           (
                                                    lang            = loc.language,
                                                    displayUnitsOpt = Some(DisplayUnits.SI)
                                                )
                                            )
                                        )
                                    ),
                                    li(
                                        a(
                                            "Imperial",
                                            router.navigateTo(
                                                HomePage           (
                                                    lang            = loc.language,
                                                    displayUnitsOpt = Some(DisplayUnits.Imperial)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                            // li(a("imperial"))
                            // li(a("SI + imperial"))
                        )
                    )
                )
            ),
            catalogManagerDialog.node,

            // Dismissible warning banner when catalog cache was reset (e.g., after DTO version bump)
            div(
                cls     := "fixed bottom-4 right-4 z-50 max-w-md",
                display <-- catalogDecodeFailed.signal.map(if _ then "" else "none"),
                div(
                    cls := "alert alert-warning shadow-lg text-sm",
                    span(I18N_UI.catalog.errors.cache_reset),
                    button(
                        cls     := "btn btn-sm btn-ghost",
                        "✕",
                        onClick --> { _ => catalogDecodeFailed.set(false) }
                    )
                )
            )
        )
