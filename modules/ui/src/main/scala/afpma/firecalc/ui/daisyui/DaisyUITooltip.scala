/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import afpma.firecalc.ui.Component

import com.raquo.laminar.api.L.*

import org.scalajs.dom

/**
 * DaisyUI-styled tooltip using a Portal to render content directly in document.body.
 *
 * This approach guarantees the tooltip escapes all overflow constraints and stacking contexts
 * of parent elements (like the accordion) by rendering the tooltip at the top level of the DOM.
 *
 * It creates a "proxy" container in the portal that mimics the position and dimensions of the
 * trigger element, and applies the standard DaisyUI tooltip classes to it.
 */
case class DaisyUITooltip(
    ttContent : HtmlElement,
    element   : HtmlElement,
    ttStyle   : String = "tooltip-neutral",
    ttPosition: String = "tooltip-top"
) extends Component:

    private val isVisible = Var(false)

    lazy val node =
        span(
            cls := "relative inline-block",
            onMouseEnter --> { _ => isVisible.set(true) },
            onMouseLeave --> { _ => isVisible.set(false) },
            onMountBind { ctx =>
                val triggerEl = ctx.thisNode.ref
                val rectVar   = Var(Option.empty[dom.DOMRect])
                val container = dom.document.createElement("div").asInstanceOf[dom.html.Div]
                var root      = Option.empty[RootNode]

                val tooltipProxy = div(
                    cls := s"tooltip tooltip-open $ttStyle $ttPosition",
                    styleAttr <-- rectVar.signal.map {
                        case Some(rect) =>
                            s"position: fixed; left: ${rect.left}px; top: ${rect.top}px; width: ${rect.width}px; height: ${rect.height}px; z-index: 9999; pointer-events: none;"
                        case None       => "display: none;"
                    },
                    div(
                        cls := "tooltip-content",
                        ttContent
                    )
                )

                val cleanup = () =>
                    root.foreach(_.unmount())
                    root = None
                    if dom.document.body.contains(container) then dom.document.body.removeChild(container)

                // Register cleanup with the owner
                val _ = new com.raquo.airstream.ownership.Subscription(ctx.owner, cleanup)

                isVisible.signal.distinct --> { visible =>
                    if visible then
                        rectVar.set(Some(triggerEl.getBoundingClientRect()))
                        if !dom.document.body.contains(container) then dom.document.body.appendChild(container)
                        if root.isEmpty then root = Some(render(container, tooltipProxy))
                    else
                        rectVar.set (None       )
                        root.foreach(_.unmount())
                        root = None
                        if dom.document.body.contains(container) then dom.document.body.removeChild(container)
                }
            },
            element
        )
