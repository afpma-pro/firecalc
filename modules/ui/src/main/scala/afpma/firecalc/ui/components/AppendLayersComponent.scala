/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components
import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.daisyui.DaisyUIDynamicList
import afpma.firecalc.ui.daisyui.DaisyUIHorizontalForm
import afpma.firecalc.ui.daisyui.DaisyUIInputs
import afpma.firecalc.ui.formgen.*
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale

final case class AppendLayersComponent(
    append_layers_var: Var[List[AppendLayerDescr]]
)(using Locale, DisplayUnits) extends DaisyUIDynamicList:

    type Elem = AppendLayerDescr

    import hastranslations.given
    import horizontal_form_13384.given

    lazy val vnel_signal = air_intake_vnel_signal

    lazy val elems_v: Var[Seq[AppendLayerDescr]] = append_layers_var.bimap(_.toSeq)(_.toList)

    type XtraInputs = Unit
    type XtraOutputs = Option[Unit]

    override lazy val xtras_input_sig = Signal.fromValue(())
    override def getElemXtra(
        fromWElems: Seq[(Int, Elem)], 
        fromXtras: XtraInputs, 
        key: (Int, Elem)
    ): XtraOutputs = 
        None

    type DF[x] = DaisyUIHorizontalForm[x]

    private def renderElemTyped[AA <: AppendLayerDescr](i: Int, title: String, aa: AA, sig: Signal[(Int, AA, XtraOutputs)])(using DF[AA]): HtmlElement = 
        val (binders, elem_v) = makeAssociatedVarForIdx[AA](i)
        val node = elem_v.as_HtmlElement.amend(binders)
        val with_fieldset_node = DaisyUIInputs.FieldsetLegendWithContent(Some(title), node)
        val summary_node = DaisyUIInputs.FieldsetLegendWithContent(Some(title), div())
        renderIdWithIncrDescr[AA](i, (i, aa), sig, with_fieldset_node, Some(summary_node))

    lazy val rendered_elems_sig: Signal[Seq[HtmlElement]] = 
        // given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form.horizontal_form_Length_mm_cm
        welem_xtraoutput_sig.signal
            .splitMatchSeq(_._1)
            .handleCase[(Int, AppendLayerDescr, XtraOutputs), (Int, FromLambda, XtraOutputs),                          HtmlElement] { case (i, aa: FromLambda                          , x) => (i, aa, x)  } { (iaax, sig) => renderElemTyped[FromLambda]                             (iaax._1, I18N.append_layer_descr.FromLambda                           , iaax._2, sig) }
            .handleCase[(Int, AppendLayerDescr, XtraOutputs), (Int, FromLambdaUsingThickness, XtraOutputs),            HtmlElement] { case (i, aa: FromLambdaUsingThickness            , x) => (i, aa, x)  } { (iaax, sig) => renderElemTyped[FromLambdaUsingThickness]               (iaax._1, I18N.append_layer_descr.FromLambdaUsingThickness             , iaax._2, sig) }
            .handleCase[(Int, AppendLayerDescr, XtraOutputs), (Int, FromThermalResistanceUsingThickness, XtraOutputs), HtmlElement] { case (i, aa: FromThermalResistanceUsingThickness , x) => (i, aa, x)  } { (iaax, sig) => renderElemTyped[FromThermalResistanceUsingThickness]    (iaax._1, I18N.append_layer_descr.FromThermalResistanceUsingThickness  , iaax._2, sig) }
            .handleCase[(Int, AppendLayerDescr, XtraOutputs), (Int, FromThermalResistance, XtraOutputs),               HtmlElement] { case (i, aa: FromThermalResistance               , x) => (i, aa, x)  } { (iaax, sig) => renderElemTyped[FromThermalResistance]                  (iaax._1, I18N.append_layer_descr.FromThermalResistance                , iaax._2, sig) }
            .handleCase[(Int, AppendLayerDescr, XtraOutputs), (Int, AirSpaceUsingOuterShape, XtraOutputs),             HtmlElement] { case (i, aa: AirSpaceUsingOuterShape             , x) => (i, aa, x)  } { (iaax, sig) => renderElemTyped[AirSpaceUsingOuterShape]                (iaax._1, I18N.append_layer_descr.AirSpaceUsingOuterShape              , iaax._2, sig) }
            .handleCase[(Int, AppendLayerDescr, XtraOutputs), (Int, AirSpaceUsingThickness, XtraOutputs),              HtmlElement] { case (i, aa: AirSpaceUsingThickness              , x) => (i, aa, x)  } { (iaax, sig) => renderElemTyped[AirSpaceUsingThickness]                 (iaax._1, I18N.append_layer_descr.AirSpaceUsingThickness               , iaax._2, sig) }
            .toSignal

    override def renderContent: HtmlElement =
        div(cls := "py-4 gap-2",
            div(cls := "flex flex-col gap-2",
                children <-- rendered_elems_sig,
                div(cls := "flex-none", TagTreeMenuComponent[AppendLayerDescr](sampleTree, command_bus.writer, elems_size_v).node),
                // debug
                // div(cls := "flex-none",
                //     children <-- welems_var.signal.map(_.map(x => p(x.toString)))
                // )
            ),
        )

    // simple instance of tree (for testing only)
    lazy val sampleTree = TagTreeMenu[AppendLayerDescr](
        menu_elements
    )

    import defaultable.pipeShapeOuter
    import defaultable.given_Thickness
    import defaultable.given_ThermalConductivity
    import defaultable.given_ThermalResistance

    lazy val menu_elements = TagTreeMenu.Group[AppendLayerDescr](
        txt         = I18N.append_layer_descr._self,
        next        = List(
            TagTreeMenu.Leaf[FromLambda],
            TagTreeMenu.Leaf[FromLambdaUsingThickness],
            TagTreeMenu.Leaf[FromThermalResistanceUsingThickness],
            TagTreeMenu.Leaf[FromThermalResistance],
            TagTreeMenu.Leaf[AirSpaceUsingOuterShape],
            TagTreeMenu.Leaf[AirSpaceUsingThickness],
        )
    )

end AppendLayersComponent
