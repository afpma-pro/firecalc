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
import afpma.firecalc.ui.models.viz3DPanelOn
import afpma.firecalc.ui.tailwind.Indicators
import afpma.firecalc.ui.viz.Viz3DPanel

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
                                cls := "flex flex-row items-center justify-center",
                                a(cls := "flex-none btn btn-ghost text-xl", p("FireCalc AFPMA")),
                                p(
                                    cls := "flex-none text-base-content/40 text-sm self-center mt-1",
                                    s"(${summon[Locale].language.value})"
                                )
                            ),
                            buttonString  = I18N_UI.buttons.menu
                        ),
                        Indicators                    ()
                    )
                ),
                div(
                    cls := "relative top-42 flex flex-row",
                    // Left: accordion (full width or half when 3D panel open)
                    div(
                        cls <-- viz3DPanelOn.map(on => if on then "w-1/2 overflow-y-auto" else "w-full"),
                        DaisyUIVerticalAccordionAndJoin(),
                        Footer()
                    ),
                    // Right: 3D panel (fixed, only when visible)
                    child.maybe <-- viz3DPanelOn.map: on =>
                        Option.when(on)(
                            div(
                                cls := "w-1/2 fixed right-0 top-42 bottom-0 p-2",
                                Viz3DPanel().node
                            )
                        )
                )
            )
        )

}
