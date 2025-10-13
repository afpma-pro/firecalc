/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.instances.*

import afpma.firecalc.dto.all.*
import AddElement_13384.*
import SetProp_13384.*
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale

trait PipePanel_13384(using Locale, DisplayUnits) extends PipePanel:
    
    type In = IncrDescr_13384

    import hastranslations.given
    import horizontal_form_13384.given

    lazy val rendered_elems_sig: Signal[Seq[HtmlElement]] = 
        welem_xtraoutput_sig.signal
            .splitMatchSeq(_._1)
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetInnerShape                 , XtraOutputs), HtmlElement] { case (i, aa: SetInnerShape                 , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetInnerShape]                 (iaax._1, I18N.set_prop.SetInnerShape                    , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetOuterShape                 , XtraOutputs), HtmlElement] { case (i, aa: SetOuterShape                 , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetOuterShape]                 (iaax._1, I18N.set_prop.SetOuterShape                    , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetThickness                  , XtraOutputs), HtmlElement] { case (i, aa: SetThickness                  , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetThickness]                  (iaax._1, I18N.set_prop.SetThickness                     , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetRoughness                  , XtraOutputs), HtmlElement] { case (i, aa: SetRoughness                  , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetRoughness]                  (iaax._1, I18N.set_prop.SetRoughness                     , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetMaterial                   , XtraOutputs), HtmlElement] { case (i, aa: SetMaterial                   , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetMaterial]                   (iaax._1, I18N.set_prop.SetMaterial                      , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetLayer                      , XtraOutputs), HtmlElement] { case (i, aa: SetLayer                      , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetLayer]                      (iaax._1, I18N.set_prop.SetLayer                         , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetLayers                     , XtraOutputs), HtmlElement] { case (i, aa: SetLayers                     , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetLayers]                     (iaax._1, I18N.set_prop.SetLayers                        , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetAirSpaceAfterLayers        , XtraOutputs), HtmlElement] { case (i, aa: SetAirSpaceAfterLayers        , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetAirSpaceAfterLayers]        (iaax._1, I18N.set_prop.SetAirSpaceAfterLayers           , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetPipeLocation               , XtraOutputs), HtmlElement] { case (i, aa: SetPipeLocation               , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetPipeLocation]               (iaax._1, I18N.set_prop.SetPipeLocation                  , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetDuctType                   , XtraOutputs), HtmlElement] { case (i, aa: SetDuctType                   , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetDuctType]                   (iaax._1, I18N.set_prop.SetDuctType                      , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, SetNumberOfFlows              , XtraOutputs), HtmlElement] { case (i, aa: SetNumberOfFlows              , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[SetNumberOfFlows]              (iaax._1, I18N.set_prop.SetNumberOfFlows                 , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSectionSlopped             , XtraOutputs), HtmlElement] { case (i, aa: AddSectionSlopped             , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSectionSlopped]             (iaax._1, I18N.add_element.AddSectionSlopped             , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSectionHorizontal          , XtraOutputs), HtmlElement] { case (i, aa: AddSectionHorizontal          , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSectionHorizontal]          (iaax._1, I18N.add_element.AddSectionHorizontal          , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSectionVertical            , XtraOutputs), HtmlElement] { case (i, aa: AddSectionVertical            , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSectionVertical]            (iaax._1, I18N.add_element.AddSectionVertical            , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddAngleAdjustable            , XtraOutputs), HtmlElement] { case (i, aa: AddAngleAdjustable            , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddAngleAdjustable]            (iaax._1, I18N.add_element.AddAngleAdjustable            , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSharpeAngle_0_to_90        , XtraOutputs), HtmlElement] { case (i, aa: AddSharpeAngle_0_to_90        , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSharpeAngle_0_to_90]        (iaax._1, I18N.add_element.AddSharpeAngle_0_to_90        , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSharpeAngle_0_to_90_Unsafe , XtraOutputs), HtmlElement] { case (i, aa: AddSharpeAngle_0_to_90_Unsafe , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSharpeAngle_0_to_90_Unsafe] (iaax._1, I18N.add_element.AddSharpeAngle_0_to_90_Unsafe , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSmoothCurve_90             , XtraOutputs), HtmlElement] { case (i, aa: AddSmoothCurve_90             , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSmoothCurve_90]             (iaax._1, I18N.add_element.AddSmoothCurve_90             , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSmoothCurve_90_Unsafe      , XtraOutputs), HtmlElement] { case (i, aa: AddSmoothCurve_90_Unsafe      , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSmoothCurve_90_Unsafe]      (iaax._1, I18N.add_element.AddSmoothCurve_90_Unsafe      , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSmoothCurve_60             , XtraOutputs), HtmlElement] { case (i, aa: AddSmoothCurve_60             , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSmoothCurve_60]             (iaax._1, I18N.add_element.AddSmoothCurve_60             , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSmoothCurve_60_Unsafe      , XtraOutputs), HtmlElement] { case (i, aa: AddSmoothCurve_60_Unsafe      , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSmoothCurve_60_Unsafe]      (iaax._1, I18N.add_element.AddSmoothCurve_60_Unsafe      , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddElbows_2x45                , XtraOutputs), HtmlElement] { case (i, aa: AddElbows_2x45                , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddElbows_2x45]                (iaax._1, I18N.add_element.AddElbows_2x45                , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddElbows_3x30                , XtraOutputs), HtmlElement] { case (i, aa: AddElbows_3x30                , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddElbows_3x30]                (iaax._1, I18N.add_element.AddElbows_3x30                , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddElbows_4x22p5              , XtraOutputs), HtmlElement] { case (i, aa: AddElbows_4x22p5              , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddElbows_4x22p5]              (iaax._1, I18N.add_element.AddElbows_4x22p5              , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSectionDecrease            , XtraOutputs), HtmlElement] { case (i, aa: AddSectionDecrease            , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSectionDecrease]            (iaax._1, I18N.add_element.AddSectionDecrease            , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddSectionIncrease            , XtraOutputs), HtmlElement] { case (i, aa: AddSectionIncrease            , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddSectionIncrease]            (iaax._1, I18N.add_element.AddSectionIncrease            , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddFlowResistance             , XtraOutputs), HtmlElement] { case (i, aa: AddFlowResistance             , x) => (i, aa, x) } { (iaax, sig) => renderElemTyped[AddFlowResistance]             (iaax._1, I18N.add_element.AddFlowResistance             , iaax._2, sig) }
            .handleCase[(Int, IncrDescr_13384, XtraOutputs), (Int, AddPressureDiff               , XtraOutputs), HtmlElement] { case (i, aa: AddPressureDiff               , x) => (i, aa, x) } { (_, _)      => throw new Exception("ERROR: AddPressureDiff not implemented.") }
            .toSignal

    import defaultable_13384.incr_descr_en13384.given

    // simple instance of tree (for testing only)
    lazy val tagTreeMenu = TagTreeMenu(
        prop_elements,
        geom_elements,
    )

    lazy val geom_elements = TagTreeMenu.Group(
        txt         = I18N.add_element._self,
        next        = List(
            straight_elements,
            direction_change_elements,
            split_group,
            grids,
        ),
    )

    lazy val grids = TagTreeMenu.Group(
        txt         = I18N.add_element.AddFlowResistance,
        next        = List(
            TagTreeMenu.Leaf[AddFlowResistance]("ζ")
        )
    )

    lazy val split_group = TagTreeMenu.Group(
        txt         = I18N.set_prop.SetNumberOfFlows,
        next        = List(
            TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_NumberOfChannels, SetNumberOfFlows(2)),
            TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_Join, SetNumberOfFlows(1))
        )
    )

    lazy val straight_elements = TagTreeMenu.Group(
        txt = I18N.add_element.add_section_element,
        next = List(
            TagTreeMenu.Leaf[AddSectionVertical],
            TagTreeMenu.Leaf[AddSectionHorizontal],
            TagTreeMenu.Leaf[AddSectionSlopped]
        )
    )

    lazy val direction_change_elements = TagTreeMenu.Group(
        txt = I18N.add_element.add_direction_change_element,
        next = List(
            TagTreeMenu.Leaf[AddSharpeAngle_0_to_90_Unsafe],
            TagTreeMenu.Leaf[AddSmoothCurve_90_Unsafe],
            TagTreeMenu.Leaf[AddSmoothCurve_60_Unsafe],
            TagTreeMenu.Leaf[AddElbows_2x45],
            TagTreeMenu.Leaf[AddElbows_3x30],
            TagTreeMenu.Leaf[AddElbows_4x22p5],
        )
    )

    // prop

    lazy val prop_elements = TagTreeMenu.Group(
        txt = I18N.set_prop._self,
        next = 
            TagTreeMenu.Leaf[SetMaterial] ::
            TagTreeMenu.Leaf[SetRoughness] ::
            prop_elements_geom :: 
            prop_elements_insulation :: 
            TagTreeMenu.Leaf[SetAirSpaceAfterLayers] :: 
            TagTreeMenu.Leaf[SetPipeLocation] :: 
            Nil
    )

    lazy val prop_elements_geom = TagTreeMenu.Group(
        txt = I18N.set_prop._geometric_properties,
        next = List(
            TagTreeMenu.Leaf[SetInnerShape],
            TagTreeMenu.Leaf[SetOuterShape],
            TagTreeMenu.Leaf[SetThickness],
        )
    )

    lazy val prop_elements_insulation = TagTreeMenu.Group(
        txt = I18N.set_prop.define_layers,
        next = List(
            TagTreeMenu.Leaf[SetLayer],
            TagTreeMenu.Leaf[SetLayers],
        )
    )