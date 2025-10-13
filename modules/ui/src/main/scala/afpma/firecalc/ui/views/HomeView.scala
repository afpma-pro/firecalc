/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.views

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.Footer
import afpma.firecalc.ui.daisyui.DaisyUINavBar
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.tailwind.Indicators

import afpma.firecalc.dto.all.*
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale
import afpma.firecalc.ui.config.ViteEnv
import afpma.firecalc.ui.services.VersionService

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
                                    s"(${summon[Locale].language.value})",
                                    " - ",
                                    s"[${ViteEnv.modeString}]"
                                )
                            ),
                            buttonString = I18N_UI.buttons.menu
                        ),
                        Indicators(),
                    )
                ),
                div(
                    cls := "relative top-42",
                    DaisyUIVerticalAccordionAndJoin(),
                    Footer(),
                )
            )
        )

}
