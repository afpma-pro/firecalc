/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import afpma.firecalc.ui.Component

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** DaisyUI-styled tooltip using a Portal to render content directly in document.body.
  *
  * This approach guarantees the tooltip escapes all overflow constraints and stacking contexts
  * of parent elements (like the accordion) by rendering the tooltip at the top level of the DOM.
  *
  * It creates a "proxy" container in the portal that mimics the position and dimensions of the
  * trigger element, and applies the standard DaisyUI tooltip classes to it.
  */
case class DaisyUITooltip(
    ttContent: HtmlElement,
    element: HtmlElement,
    ttStyle: String = "tooltip-neutral",
    ttPosition: String = "tooltip-top",
) extends Component:

    private val isVisible = Var(false)
    private val rectVar   = Var(Option.empty[dom.DOMRect])

    // Portal state
    private var portalRoot: Option[RootNode]  = None
    private val portalContainer: dom.html.Div = dom.document.createElement("div").asInstanceOf[dom.html.Div]

    lazy val node =
        span(
            cls := "relative inline-block",
            onMountCallback { ctx =>
                val owner     = ctx.owner
                val triggerEl = ctx.thisNode.ref

                // The portal content mimics the trigger element's position but lives in body
                // We apply the tooltip classes here so DaisyUI styles render correctly
                val tooltipProxy = div(
                    cls := s"tooltip tooltip-open $ttStyle $ttPosition",
                    styleAttr <-- rectVar.signal.map {
                        case Some(rect) =>
                            s"position: fixed; left: ${rect.left}px; top: ${rect.top}px; width: ${rect.width}px; height: ${rect.height}px; z-index: 9999; pointer-events: none;"
                        case None => "display: none;"
                    },
                    div(
                        cls := "tooltip-content",
                        ttContent
                    )
                )

                isVisible.signal.distinct.foreach { visible =>
                    if visible then
                        // Update position when showing
                        rectVar.set(Some(triggerEl.getBoundingClientRect()))

                        if !dom.document.body.contains(portalContainer) then
                            dom.document.body.appendChild(portalContainer)

                        if portalRoot.isEmpty then
                            portalRoot = Some(render(portalContainer, tooltipProxy))
                    else
                        rectVar.set(None)
                        portalRoot.foreach(_.unmount())
                        portalRoot = None
                        if dom.document.body.contains(portalContainer) then
                            dom.document.body.removeChild(portalContainer)
                }(using owner)
            },
            onUnmountCallback { _ =>
                portalRoot.foreach(_.unmount())
                portalRoot = None
                if dom.document.body.contains(portalContainer) then
                    dom.document.body.removeChild(portalContainer)
            },
            onMouseEnter --> { _ => isVisible.set(true) },
            onMouseLeave --> { _ => isVisible.set(false) },
            element
        )