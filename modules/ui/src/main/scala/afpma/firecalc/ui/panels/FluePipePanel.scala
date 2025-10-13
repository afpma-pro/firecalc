/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*
import AddElement_15544.*
import SetProp_15544.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale
import afpma.firecalc.engine.utils.asVNelString

final case class FluePipePanel()(using Locale, DisplayUnits) extends PipePanel:

    import hastranslations.given
    import horizontal_form_15544.given

    type In = IncrDescr_15544
    type Out = FluePipe_EN15544
    
    lazy val titleString = I18N.panels.channel_pipe

    val channel_pipe_vnel2_signal = results_en15544_strict_sig.map: strict => 
        import cats.syntax.show.*
        strict.andThen(_
            .validateFluePipeShape()
            .leftMap(_.map(_.show))
        )

    val channel_pipe_vnel3_signal = results_en15544_channel_pipe.map: p_vnel =>
        p_vnel.andThen(p => p.`ph-(pR+pu)`.asVNelString)

    val channel_pipe_vnel4_signal = results_en15544_strict_sig.map: strict => 
        val p = (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.givens.nominal)
        strict.andThen(s =>
            val vnel = s.validateVelocitiesInFluePipe()(using p)
            filterAndMapFluePipeErrors(onlyFor = FluePipeT)(vnel)
        )

    lazy val vnel_signal = 
        fluepipe_vnel_signal
        .combineWith(channel_pipe_vnel2_signal)
        .combineWith(channel_pipe_vnel3_signal)
        .combineWith(channel_pipe_vnel4_signal)
        .map((v1, v2, v3, v4) => 
            v1
            .andThen(_ => v2)
            .andThen(_ => v3)
            .andThen(_ => v4)
            .andThen(_ => v1)
        )
            
    lazy val elems_v = fluepipe_incrdescr_var

    type PipeIdsMapping = afpma.firecalc.engine.models.FluePipe_Module_EN15544.IdsMapping

    override lazy val pipeMappings_vnel_signal = fluepipe_mappings_vnel_signal
    override lazy val pipeResult_vnel_signal = results_en15544_channel_pipe
    
    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings.getUnsafe(idIncr).map(_.unwrap.unwrap)

    lazy val channel_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.flue)

    override lazy val quadrionSubtotal_sig = channel_pipe_quadrions_sig

    lazy val rendered_elems_sig: Signal[Seq[HtmlElement]] = 
        welem_xtraoutput_sig
            .splitMatchSeq(_._1)
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, SetInnerShape         , XtraOutputs), HtmlElement] { case (i, incr: SetInnerShape        , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[SetInnerShape]              (iix._1, I18N.set_prop.SetInnerShape              , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, SetRoughness             , XtraOutputs), HtmlElement] { case (i, incr: SetRoughness            , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[SetRoughness]                  (iix._1, I18N.set_prop.SetRoughness                  , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, SetMaterial         , XtraOutputs), HtmlElement] { case (i, incr: SetMaterial        , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[SetMaterial]              (iix._1, I18N.set_prop.SetMaterial              , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, SetNumberOfFlows             , XtraOutputs), HtmlElement] { case (i, incr: SetNumberOfFlows            , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[SetNumberOfFlows]                  (iix._1, I18N.set_prop.SetNumberOfFlows                  , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, AddSectionSlopped                , XtraOutputs), HtmlElement] { case (i, incr: AddSectionSlopped               , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[AddSectionSlopped]                     (iix._1, I18N.add_element.AddSectionSlopped                 , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, AddSectionHorizontal      , XtraOutputs), HtmlElement] { case (i, incr: AddSectionHorizontal     , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[AddSectionHorizontal]           (iix._1, I18N.add_element.AddSectionHorizontal       , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, AddSectionVertical        , XtraOutputs), HtmlElement] { case (i, incr: AddSectionVertical       , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[AddSectionVertical]             (iix._1, I18N.add_element.AddSectionVertical         , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, AddSharpeAngle_0_to_180       , XtraOutputs), HtmlElement] { case (i, incr: AddSharpeAngle_0_to_180      , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[AddSharpeAngle_0_to_180]            (iix._1, I18N.add_element.AddSharpeAngle_0_to_180        , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, AddCircularArc_60         , XtraOutputs), HtmlElement] { case (i, incr: AddCircularArc_60        , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[AddCircularArc_60]              (iix._1, I18N.add_element.AddCircularArc_60          , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, AddSectionShapeChange , XtraOutputs), HtmlElement] { case (i, incr: AddSectionShapeChange, x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[AddSectionShapeChange]      (iix._1, I18N.add_element.AddSectionShapeChange  , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, AddFlowResistance        , XtraOutputs), HtmlElement] { case (i, incr: AddFlowResistance       , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[AddFlowResistance]             (iix._1, I18N.add_element.AddFlowResistance         , iix._2, sig) }
            .handleCase[(Int, IncrDescr_15544, XtraOutputs), (Int, AddPressureDiff          , XtraOutputs), HtmlElement] { case (i, incr: AddPressureDiff         , x) =>  (i, incr, x) } { (iix, sig) => renderElemTyped[AddPressureDiff]               (iix._1, I18N.add_element.AddPressureDiff           , iix._2, sig) }
            .toSignal

    import defaultable_15544.incr_descr_en15544.given

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
            TagTreeMenu.Leaf(I18N.add_element.AddFlowResistance_wire_mesh_screen,  AddFlowResistance(s"${I18N.add_element.AddFlowResistance_wire_mesh_screen} (ζ = 0.61)", 0.61.unitless, NoneOfEither)),
            TagTreeMenu.Leaf(I18N.add_element.AddFlowResistance_solid_steel_grate, AddFlowResistance(s"${I18N.add_element.AddFlowResistance_solid_steel_grate} (ζ = 1.23)", 1.23.unitless, NoneOfEither)),
            TagTreeMenu.Leaf(I18N.add_element.AddFlowResistance_butterfly_damper,  AddFlowResistance(s"${I18N.add_element.AddFlowResistance_butterfly_damper} (ζ = 0.25)", 0.25.unitless, NoneOfEither)),
            TagTreeMenu.Leaf[AddFlowResistance]("ζ spécifique")
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
            TagTreeMenu.Leaf[AddSharpeAngle_0_to_180],
            TagTreeMenu.Leaf[AddCircularArc_60],
        )
    )

    lazy val prop_elements = TagTreeMenu.Group(
        txt = I18N.set_prop._self,
        next = List(
            TagTreeMenu.Leaf[SetMaterial],
            TagTreeMenu.Leaf[SetRoughness],
            TagTreeMenu.Leaf[SetInnerShape],
        )
    )

end FluePipePanel
