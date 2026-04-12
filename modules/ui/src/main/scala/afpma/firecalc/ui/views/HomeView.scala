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
import afpma.firecalc.ui.models.graphPanelOn
import afpma.firecalc.ui.models.viz3DPanelOn
import afpma.firecalc.ui.tailwind.Indicators
import afpma.firecalc.ui.viz.GraphPanel
import afpma.firecalc.ui.viz.Viz3DPanel

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

final case class HomeView()(using Locale, DisplayUnits) extends Component {

    lazy val node: Div =
        val anyPanelOn = viz3DPanelOn.combineWith(graphPanelOn).map(_ || _)
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
                                )
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
                        cls <-- anyPanelOn.map(on =>
                            if on then "w-2/3"
                            else "w-full"
                        ),
                        DaisyUIVerticalAccordionAndJoin(),
                        Footer                         ()
                    ),
                    // Right: 3D and/or Graph panel (both can be active, split vertically)
                    // The outer container only mounts/unmounts on none↔any transitions (anyPanelOn deduplicates).
                    // Each panel mounts/unmounts independently based on its own toggle.
                    // Height is reactive via cls <-- so toggling one panel doesn't destroy the other —
                    // the existing Three.js ResizeObserver handles the canvas resize.
                    child.maybe <-- anyPanelOn.map:
                        case false => None
                        case true  =>
                            Some(
                                div(
                                    cls := "w-1/3 fixed right-0 top-42 bottom-0 p-2 flex flex-col gap-2",
                                    child.maybe <-- viz3DPanelOn.map:
                                        case false => None
                                        case true  =>
                                            Some(
                                                div(
                                                    cls <-- graphPanelOn.map(g =>
                                                        if g then "h-1/2 overflow-hidden" else "h-full"
                                                    ),
                                                    Viz3DPanel().node
                                                )
                                            ),
                                    child.maybe <-- graphPanelOn.map:
                                        case false => None
                                        case true  =>
                                            Some(
                                                div(
                                                    cls <-- viz3DPanelOn.map(v =>
                                                        if v then "h-1/2 overflow-hidden" else "h-full"
                                                    ),
                                                    GraphPanel().node
                                                )
                                            )
                                )
                            )
                )
            )
        )

}
