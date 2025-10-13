/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

// import com.raquo.laminar.api.L.{Owner => _, _}
import com.raquo.laminar.api.L.*
import com.raquo.laminar.modifiers.RenderableNode

trait Component {
  // val body: HtmlElement
  def node: HtmlElement
}

object Component {
  implicit def component2HtmlElement(component: Component): HtmlElement =
    component.node

  given RenderableNode[Component] = 
    RenderableNode[Component](renderNode = _.node)

}
