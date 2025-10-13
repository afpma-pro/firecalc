/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components.en13384

import afpma.firecalc.ui.*
import afpma.firecalc.ui.formgen.*

import afpma.firecalc.dto.all.*
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale

case class AddFlowResistanceComponent(
    addElement: AddElement_13384.AddFlowResistance
)(using Locale, DisplayUnits) extends Component:

    import afpma.firecalc.ui.instances.horizontal_form_13384.given

    lazy val addElementVar = Var(addElement)

    val node = addElementVar.as_HtmlElement
        // .amend(child <-- addElementVar.signal.map(_.toString))


end AddFlowResistanceComponent

