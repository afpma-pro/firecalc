/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import afpma.firecalc.ui.Component

import com.raquo.laminar.api.L.*

case class DaisyUITooltip(
    ttContent: HtmlElement,
    element: HtmlElement,
    ttStyle: String = "tooltip-neutral",
    ttPosition: String = "tooltip-top",
) extends Component:

    lazy val node =
        div(
            cls := s"tooltip $ttStyle $ttPosition",
            div(
                cls := "tooltip-content z-100 max-w-[30rem]",
                ttContent
            ),
            span(cls := "", element)
        )