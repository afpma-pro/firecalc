/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.tailwind

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.daisyui.DaisyUITooltip

import com.raquo.laminar.api.L.*

/**
 * Wraps an Indicator component with an error tooltip that appears on hover
 * only when the error signal is true.
 *
 * @param indicator The indicator component to wrap
 * @param errorSignal Signal indicating whether an error condition is present
 * @param tooltipContent The static content to display in the tooltip
 * @param ttStyle DaisyUI tooltip style class (default: "tooltip-error")
 * @param ttPosition DaisyUI tooltip position class (default: "tooltip-top")
 */
final case class IndicatorWithErrorTooltip(
    indicator: Indicator,
    errorSignal: Signal[Boolean],
    tooltipContent: HtmlElement,
    ttStyle: String = "tooltip-error",
    ttPosition: String = "tooltip-top",
) extends Component:

    lazy val node: HtmlElement =
        div(
            cls := "inline-block",
            child <-- errorSignal.map { hasError =>
                if hasError then
                    DaisyUITooltip(
                        ttContent = tooltipContent,
                        element = indicator.node,
                        ttStyle = ttStyle,
                        ttPosition = ttPosition
                    ).node
                else
                    indicator.node
            }
        )
