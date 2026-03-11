/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.geometry.{PipeFrame, Vec3}
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.daisyui.RollAngleInput
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.flowResistancePresetsSignal

import coulomb.policy.standard.given

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

trait PipePanel_13384_FlowOnly(using Locale, DisplayUnits) extends PipePanel:

    import AddFlowOnlyPipeElement_13384.*
    import SetFlowOnlyPipeProp_13384.*

    type In = FlowOnlyPipeDescr_13384

    import hastranslations.given

    private given flowOnlyHorizontalForm_13384: FlowOnlyHorizontalForm_13384 = FlowOnlyHorizontalForm_13384()
    import flowOnlyHorizontalForm_13384.given

    private lazy val frameBeforeByIdx: Signal[Map[Int, PipeFrame]] =
        welems_var.signal.map: elems =>
            var frame: Option[PipeFrame] = None
            val builder = Map.newBuilder[Int, PipeFrame]
            for (idx, elem) <- elems do
                elem match
                    case SetInitialDirection(az, incl) =>
                        frame = Some(PipeFrame.initial(
                            Vec3.fromAzimuthElevation(az.toUnit[Degree].value, incl.toUnit[Degree].value)
                        ))
                    case _ => ()
                frame.foreach(f => builder += (idx -> f))
                elem match
                    case dc: AddDirectionChange =>
                        for
                            f <- frame
                            r <- dc.roll
                        do frame = Some(f.applyBend(dc.angle.toUnit[Degree].value, r.toUnit[Degree].value))
                    case _ => ()
            builder.result()

    private def frameBeforeSig(idx: Int): Signal[Option[PipeFrame]] =
        frameBeforeByIdx.map(_.get(idx))

    private lazy val directionAfterByIdx: Signal[Map[Int, Vec3]] =
        welems_var.signal.combineWith(frameBeforeByIdx).map: (elems, frameMap) =>
            elems.flatMap: (idx, elem) =>
                frameMap.get(idx).flatMap: frameBefore =>
                    elem match
                        case dc: AddDirectionChange =>
                            dc.roll.map: r =>
                                idx -> frameBefore.applyBend(dc.angle.toUnit[Degree].value, r.toUnit[Degree].value).direction
                        case _: AddFlowOnlyPipeElement_13384 =>
                            Some(idx -> frameBefore.direction)
                        case _ => None
            .toMap

    override protected def directionBadgeSig(idx: Int, xtraSig: Signal[XtraOutputs]): Signal[Option[Vec3]] =
        directionAfterByIdx.map(_.get(idx))

    override protected def frameBeforeSig_badge(idx: Int): Signal[Option[PipeFrame]] =
        frameBeforeByIdx.map(_.get(idx))

    private lazy val previousDirectionByIdx: Signal[Map[Int, Vec3]] =
        welems_var.signal.combineWith(frameBeforeByIdx).map: (elems, frameMap) =>
            elems
                .collect { case (idx, _: AddDirectionChange) => idx }
                .flatMap(idx => frameMap.get(idx).map(f => idx -> f.direction))
                .toMap

    override protected def previousDirectionSig_badge(idx: Int): Signal[Option[Vec3]] =
        previousDirectionByIdx.map(_.get(idx))

    private def rollExtra[A <: AddDirectionChange](
        idx   : Int,
        getter: A => Option[QtyD[Degree]],
        setter: (A, Option[QtyD[Degree]]) => A
    ): Var[A] => HtmlElement =
        ev => RollAngleInput(ev.zoomLazy(getter)(setter), frameBeforeSig(idx))

    private def rollBadgeVar[A <: AddDirectionChange](
        getter: A => Option[QtyD[Degree]],
        setter: (A, Option[QtyD[Degree]]) => A
    ): Var[A] => Option[Var[Option[QtyD[Degree]]]] =
        ev => Some(ev.zoomLazy(getter)(setter))

    lazy val rendered_elems_sig: Signal[Seq[HtmlElement]] =
        welem_xtraoutput_sig.signal
            .splitMatchSeq(_._1)
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, SetInnerShape, XtraOutputs), HtmlElement] {
                case (i, aa: SetInnerShape, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetInnerShape](iaax._1, I18N.set_prop.SetInnerShape, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, SetRoughness, XtraOutputs), HtmlElement] {
                case (i, aa: SetRoughness, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetRoughness](iaax._1, I18N.set_prop.SetRoughness, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, SetMaterial, XtraOutputs), HtmlElement] {
                case (i, aa: SetMaterial, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetMaterial](iaax._1, I18N.set_prop.SetMaterial, iaax._2, sig, isProperty = true)
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, SetNumberOfFlows, XtraOutputs       ),
                HtmlElement
            ] { case (i, aa: SetNumberOfFlows, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[SetNumberOfFlows](
                    iaax._1,
                    I18N.set_prop.SetNumberOfFlows,
                    iaax._2,
                    sig,
                    isProperty = true
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, SetInitialDirection, XtraOutputs), HtmlElement] {
                case (i, aa: SetInitialDirection, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetInitialDirection](iaax._1, I18N.set_prop.SetInitialDirection, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddSectionSlopped, XtraOutputs), HtmlElement] {
                case (i, aa: AddSectionSlopped, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSectionSlopped](
                    iaax._1,
                    I18N.add_element.AddSectionSlopped,
                    iaax._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, AddSectionHorizontal, XtraOutputs   ),
                HtmlElement
            ] { case (i, aa: AddSectionHorizontal, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSectionHorizontal](
                    iaax._1,
                    I18N.add_element.AddSectionHorizontal,
                    iaax._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, AddSectionVertical, XtraOutputs     ),
                HtmlElement
            ] { case (i, aa: AddSectionVertical, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSectionVertical](
                    iaax._1,
                    I18N.add_element.AddSectionVertical,
                    iaax._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, AddAngleAdjustable, XtraOutputs     ),
                HtmlElement
            ] { case (i, aa: AddAngleAdjustable, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddAngleAdjustable](
                    iaax._1,
                    I18N.add_element.AddAngleAdjustable,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, AddSharpeAngle_0_to_90, XtraOutputs ),
                HtmlElement
            ] { case (i, aa: AddSharpeAngle_0_to_90, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSharpeAngle_0_to_90](
                    iaax._1,
                    I18N.add_element.AddSharpeAngle_0_to_90,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs      ),
                (Int, AddSharpeAngle_0_to_90_Unsafe, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSharpeAngle_0_to_90_Unsafe, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSharpeAngle_0_to_90_Unsafe](
                    iaax._1,
                    I18N.add_element.AddSharpeAngle_0_to_90_Unsafe,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddSmoothCurve_90, XtraOutputs), HtmlElement] {
                case (i, aa: AddSmoothCurve_90, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_90](
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_90,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs ),
                (Int, AddSmoothCurve_90_Unsafe, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSmoothCurve_90_Unsafe, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_90_Unsafe](
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_90_Unsafe,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddSmoothCurve_60, XtraOutputs), HtmlElement] {
                case (i, aa: AddSmoothCurve_60, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_60](
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_60,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs ),
                (Int, AddSmoothCurve_60_Unsafe, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSmoothCurve_60_Unsafe, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_60_Unsafe](
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_60_Unsafe,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddElbows_2x45, XtraOutputs), HtmlElement] {
                case (i, aa: AddElbows_2x45, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddElbows_2x45](
                    iaax._1,
                    I18N.add_element.AddElbows_2x45,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddElbows_3x30, XtraOutputs), HtmlElement] {
                case (i, aa: AddElbows_3x30, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddElbows_3x30](
                    iaax._1,
                    I18N.add_element.AddElbows_3x30,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddElbows_4x22p5, XtraOutputs), HtmlElement] {
                case (i, aa: AddElbows_4x22p5, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddElbows_4x22p5](
                    iaax._1,
                    I18N.add_element.AddElbows_4x22p5,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra        = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r)),
                    badgeRollVar = rollBadgeVar(_.roll, (a, r) => a.copy(roll = r))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, AddSectionDecrease, XtraOutputs     ),
                HtmlElement
            ] { case (i, aa: AddSectionDecrease, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSectionDecrease](
                    iaax._1,
                    I18N.add_element.AddSectionDecrease,
                    iaax._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, AddSectionIncrease, XtraOutputs     ),
                HtmlElement
            ] { case (i, aa: AddSectionIncrease, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSectionIncrease](
                    iaax._1,
                    I18N.add_element.AddSectionIncrease,
                    iaax._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddFlowResistance, XtraOutputs), HtmlElement] {
                case (i, aa: AddFlowResistance, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddFlowResistance](
                    iaax._1,
                    I18N.add_element.AddFlowResistance,
                    iaax._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddPressureDiff, XtraOutputs), HtmlElement] {
                case (i, aa: AddPressureDiff, x) => (i, aa, x)
            } { (_, _) => throw new Exception("ERROR: AddPressureDiff not implemented.") }
            .toSignal

    import FlowOnlyDefaultable_13384.given

    // simple instance of tree (for testing only)
    lazy val tagTreeMenu = TagTreeMenu(
        shortcut_start_new_pipe,
        prop_elements,
        geom_elements
    )

    lazy val shortcut_start_new_pipe =
        import afpma.firecalc.ui.formgen.{Defaultable as D}
        TagTreeMenu.Shortcut  (
            txt   = I18N.set_prop.shortcuts.start_a_new_pipe,
            elems = (
                summon[D[SetMaterial]].default,
                summon[D[SetInnerShape]].default
            )
        )

    lazy val geom_elements = TagTreeMenu.Group(
        txt  = I18N.add_element._self,
        next = List(
            straight_elements,
            direction_change_elements,
            split_group,
            grids
        )
    )

    lazy val grids = TagTreeMenu.Group(
        txt  = I18N.add_element.AddFlowResistance,
        next = List(
            TagTreeMenu.Modal[FlowOnlyPipeDescr_13384](
                txt = I18N_UI.catalog.flow_resistance_presets,
                modalContent = (onSelect) =>
                    FlowResistanceCatalogSelectComponent(
                        entriesSignal = flowResistancePresetsSignal,
                        onSelect      = onSelect.contramap[FlowResistanceCatalogEntry](e =>
                            AddFlowResistance(e.name, e.zeta, e.cross_section)
                        )
                    ).node
            ),
            TagTreeMenu.Leaf[AddFlowResistance]("ζ")
        )
    )

    lazy val split_group = TagTreeMenu.Group(
        txt  = I18N.set_prop.SetNumberOfFlows,
        next = List(
            TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_NumberOfChannels, SetNumberOfFlows(2)),
            TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_Join, SetNumberOfFlows(1)            )
        )
    )

    lazy val straight_elements = TagTreeMenu.Group(
        txt  = I18N.add_element.add_section_element,
        next = List(
            TagTreeMenu.Leaf[AddSectionSlopped]
        )
    )

    lazy val direction_change_elements = TagTreeMenu.Group(
        txt  = I18N.add_element.add_direction_change_element,
        next = List(
            TagTreeMenu.Leaf[AddSharpeAngle_0_to_90_Unsafe],
            TagTreeMenu.Leaf[AddSmoothCurve_90_Unsafe],
            TagTreeMenu.Leaf[AddSmoothCurve_60_Unsafe],
            TagTreeMenu.Leaf[AddElbows_2x45],
            TagTreeMenu.Leaf[AddElbows_3x30],
            TagTreeMenu.Leaf[AddElbows_4x22p5]
        )
    )

    // prop

    lazy val prop_elements = TagTreeMenu.Group(
        txt  = I18N.set_prop._self,
        next = List(
            TagTreeMenu.Leaf[SetInitialDirection],
            TagTreeMenu.Leaf[SetMaterial],
            TagTreeMenu.Leaf[SetRoughness],
            TagTreeMenu.Leaf[SetInnerShape]
        )
    )
