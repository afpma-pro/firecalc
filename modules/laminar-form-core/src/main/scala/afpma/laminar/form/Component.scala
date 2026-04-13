/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

import com.raquo.laminar.api.L.*
import com.raquo.laminar.modifiers.RenderableNode

/** Trivial component trait — provides a .node: HtmlElement. */
trait Component:
    def node: HtmlElement

object Component:
    implicit def component2HtmlElement(component: Component): HtmlElement =
        component.node

    given RenderableNode[Component] =
        RenderableNode[Component](renderNode = _.node)
