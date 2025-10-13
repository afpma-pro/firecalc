/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.FireCalcProjet
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*

import afpma.firecalc.dto.all.*
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*
import io.taig.babel.Language
import org.scalajs.dom
import org.scalajs.dom.HTMLDialogElement
import afpma.firecalc.ui.components.OrderPDFReportModalComponent

object DaisyUINavBar:

    val details = htmlTag("details")
    val summary = htmlTag("summary")

    val svgElement = svg.svg(
        svg.xmlns   := "http://www.w3.org/2000/svg",
        svg.cls     := "h-5 w-5",
        svg.fill    := "none",
        svg.viewBox := "0 0 24 24",
        svg.stroke  := "currentColor",
        svg.path(
            svg.svgAttr("stroke-linecap", StringAsIsCodec, None)  := "round",
            svg.svgAttr("stroke-linejoin", StringAsIsCodec, None) := "round",
            svg.svgAttr("stroke-width", StringAsIsCodec, None)    := "2",
            svg.svgAttr("d", StringAsIsCodec, None)               := "M4 6h16M4 12h8m-8 6h16"
        )
    )

    final case class HeaderMenuButton(
        titleLeftNode: HtmlElement,
        buttonString: String
    )(using Locale) extends Component:

        val node = div(
            cls := "flex flew-row navbar items-center justify-center bg-(--color-vlight-ocre) shadow-sm",
            div(
                cls := "flex-none",
                titleLeftNode
            ),
            div(
                cls := "flex-1 flex flex-row items-center justify-center gap-x-6",
                FireCalcProjet.NewBlankComponent(),
                FireCalcProjet.UploadComponent(),
                FireCalcProjet.BackupComponent(),
                FireCalcProjet.HardCodedAppStateComponent(
                    nextAppState = AppState.example_projet_15544,
                    buttonTitle = I18N_UI.buttons.load_example_project_15544
                ),
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
            div(
                cls := "shrink flex flex-row justify-end gap-2",

                // Order PDF Report Modal
                OrderPDFReportModalComponent(),

                // Expert (show detailed values in panels)

                div(
                    cls := "flex items-stretch gap-2",
                    DaisyUITooltip(
                        ttContent = div(I18N_UI.tooltips.display_details),
                        element = 
                            div(
                                tabIndex := 0,
                                role     := "button",
                                cls      := "btn btn-outline hover:btn-secondary rounded-field",
                                cls("text-base-content")                            <-- expertModeOn,
                                cls("text-base-content/40 hover:text-base-content") <-- expertModeOff,
                                lucide.`flask-conical`(stroke_width = 1.5),
                                onClick.mapToUnit --> { _ => 
                                    // toggle expert mode
                                    if (expertModeVar.now() == true) expertModeVar.set(false)
                                    else expertModeVar.set(true)
                                }
                            ),
                        ttPosition = "tooltip-bottom",
                    )
                ),

                // EN15544 button
                div(
                    cls := "flex items-stretch gap-2",
                    div(
                        tabIndex := 0,
                        role     := "button",
                        cls      := "btn btn-outline hover:btn-secondary rounded-field",
                        "EN 15544:2023"
                    ),
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

                
                // language button
                div(
                    cls := "flex items-stretch",
                    div(
                        cls := "dropdown dropdown-end",
                        div(
                            tabIndex := 0,
                            role     := "button",
                            cls      := "btn not-focus:btn-outline rounded-field hover:btn-secondary focus:btn-secondary focus:btn",
                            div(cls := "w-4 h-4 flex items-center justify-center", lucide.languages(stroke_width = 1.5)),
                            div(cls := "w-4 h-4 flex items-center justify-center", lucide.`chevron-down`),
                        ),
                        ul(
                            tabIndex := 0,
                            cls      := "menu dropdown-content place-content-end bg-base-200 rounded-box z-1 mt-4 w-18 p-2 shadow-sm",
                            children(
                                li(cls := "w-full place-content-center", a(dataAttr("id") := "France", router.navigateTo(HomePage(Language("fr"), Some(DisplayUnits.SI))), "FR")),
                                li(cls := "w-full place-content-center", a(dataAttr("id") := "English", router.navigateTo(HomePage(Language("en"), Some(DisplayUnits.SI))), "EN")),
                            ) <-- displayUnitsVar.signal.map(du => du == DisplayUnits.SI),
                            children(
                                li(cls := "w-full place-content-center", a(dataAttr("id") := "France", router.navigateTo(HomePage(Language("fr"), Some(DisplayUnits.Imperial))), "FR")),
                                li(cls := "w-full place-content-center", a(dataAttr("id") := "English", router.navigateTo(HomePage(Language("en"), Some(DisplayUnits.Imperial))), "EN")),
                            ) <-- displayUnitsVar.signal.map(du => du == DisplayUnits.Imperial)
                        )
                    )
                ),
                
                // units button
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
                        ul(
                            tabIndex := 0,
                            cls      := "menu dropdown-content bg-base-200 rounded-box z-1 mt-4 w-24 p-2 shadow-sm",
                            children <-- localeVar.signal.map(loc =>
                                Seq(
                                    li(a("SI", 
                                    router.navigateTo(HomePage(
                                        lang = loc.language,
                                        displayUnitsOpt = Some(DisplayUnits.SI)
                                    )))),
                                    li(a("Imperial",                                 
                                    router.navigateTo(HomePage(
                                        lang = loc.language,
                                        displayUnitsOpt = Some(DisplayUnits.Imperial)
                                    )))),
                                )
                            )
                            // li(a("imperial"))
                            // li(a("SI + imperial"))
                        )
                    )
                )
            )
        )
