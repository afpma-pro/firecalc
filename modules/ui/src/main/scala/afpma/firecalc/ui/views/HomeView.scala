/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.views

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.Footer
import afpma.firecalc.ui.daisyui.DaisyUINavBar
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin
import afpma.firecalc.ui.models.{viz3DPanelOn, graphPanelOn}
import afpma.firecalc.ui.tailwind.Indicators
import afpma.firecalc.ui.viz.{Viz3DPanel, GraphPanel}

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

final case class HomeView()(using Locale, DisplayUnits) extends Component {

    lazy val node: Div =
        div(
            cls := "mx-auto w-full",
            div(
                cls := "relative",
                div(
                    cls := "z-999 fixed top-0 w-full pr-scollbar",
                    div(
                        cls := "bg-base-100 h-42",
                        DaisyUINavBar.HeaderMenuButton(
                            titleLeftNode = div(
                                cls := "flex flex-row items-center justify-start",
                                a(
                                    cls := "flex-none w-64 btn btn-ghost text-xl", 
                                    p("FireCalc AFPMA"),
                                    p(
                                        cls := "flex-none text-base-content/40 text-sm self-center mt-1",
                                        s"(${summon[Locale].language.value})"
                                    )
                                ),
                            ),
                            buttonString  = I18N_UI.buttons.menu
                        ),
                        Indicators                    ()
                    )
                ),
                div(
                    cls := "relative top-42 flex flex-row",
                    // Left: accordion (full width or 2/3 when right panel open)
                    div(
                        cls <-- viz3DPanelOn.combineWith(graphPanelOn).map: (v, g) =>
                            {
                                if v then
                                    "w-2/3 overflow-y-auto" 
                                else if g then
                                    "w-1/2 overflow-y-auto" 
                                else "w-full"
                            },
                        DaisyUIVerticalAccordionAndJoin(),
                        Footer()
                    ),
                    // Right: 3D or Graph panel (only one at a time due to mutual exclusion)
                    child.maybe <-- viz3DPanelOn.combineWith(graphPanelOn).map:
                        case (true, _) =>
                            Some(div(
                                cls := "w-1/3 fixed right-0 top-42 bottom-0 p-2",
                                Viz3DPanel().node
                            ))
                        case (_, true) =>
                            Some(div(
                                cls := "w-1/2 fixed right-0 top-42 bottom-0 p-2",
                                GraphPanel().node
                            ))
                        case _ => None
                )
            )
        )

}
