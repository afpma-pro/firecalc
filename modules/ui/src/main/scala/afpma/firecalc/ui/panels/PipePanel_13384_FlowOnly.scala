/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.FlowOnlyChannelTopologyOp_13384.*

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.units.Vec3

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.AIR_DISTRIB_HEIGHT_M
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.{
    anglePresetsSignal,
    firebox_var,
    flowResistancePresetsSignal,
    postFireboxInitialDir_var
}

import afpma.firecalc.ui.utils.combineWithDistinct
import cats.Show

import com.raquo.laminar.api.L.*

import coulomb.policy.standard.given

import io.taig.babel.Locale

trait PipePanel_13384_FlowOnly(using Locale, DisplayUnits) extends PipePanel:

    import AddFlowOnlyPipeElement_13384.*
    import SetFlowOnlyPipeProp_13384.*

    private given AutoCalcHelper.ElemExtractors[FlowOnlyPipeDescr_13384] = AutoCalcHelper.ElemExtractors(
        asInitialDirection  = PartialFunction.empty,
        asDirectionChange   = { case dc: AddDirectionChange => (dc.angle, dc.absDir) },
        asInnerShape        = { case sis: SetInnerShape => sis.shape },
        withDirChangeAbsDir = (e, newAbsDir) =>
            e match
                case x: AddAngleAdjustable            => x.copy(absDir = newAbsDir)
                case x: AddSharpeAngle_0_to_90        => x.copy(absDir = newAbsDir)
                case x: AddSharpeAngle_0_to_90_Unsafe => x.copy(absDir = newAbsDir)
                case x: AddSmoothCurve_90             => x.copy(absDir = newAbsDir)
                case x: AddSmoothCurve_90_Unsafe      => x.copy(absDir = newAbsDir)
                case x: AddSmoothCurve_60             => x.copy(absDir = newAbsDir)
                case x: AddSmoothCurve_60_Unsafe      => x.copy(absDir = newAbsDir)
                case x: AddElbows_2x45                => x.copy(absDir = newAbsDir)
                case x: AddElbows_3x30                => x.copy(absDir = newAbsDir)
                case x: AddElbows_4x22p5              => x.copy(absDir = newAbsDir)
                case _ => e
    )

    type In = FlowOnlyPipeDescr_13384

    import hastranslations.given

    private given flowOnlyHorizontalForm_13384: FlowOnlyHorizontalForm_13384 = FlowOnlyHorizontalForm_13384()
    import flowOnlyHorizontalForm_13384.given

    private given flowOnlyPropertyShow_13384: FlowOnlyPropertyShow_13384 = FlowOnlyPropertyShow_13384()
    import flowOnlyPropertyShow_13384.given

    protected def initialDirectionSig: Signal[PipeInitialDirection] = postFireboxInitialDir_var.signal

    /**
     * Opt-in flag for subclasses to render V7 wrapper-level elements
     * (PipeInitialDirection, Position3D, auto-calc button) before the slot pipeline.
     */
    protected def renderWrapperElems: Boolean = false

    protected lazy val frameBeforeByIdx: Signal[Map[Int, PipeFrame]] =
        initialDirectionSig
            .combineWith(welems_var.signal)
            .map: (initialDir, elems) =>
                val azDeg = initialDir.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0)
                val elDeg = InclinationDirection.toDegrees(initialDir.inclination)
                var frame: Option[PipeFrame] = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(azDeg, elDeg)))
                val builder = Map.newBuilder[Int, PipeFrame]
                for (idx, elem) <- elems do
                    frame.foreach(f => builder += (idx -> f))
                    elem match
                        case dc: AddDirectionChange =>
                            for
                                f  <- frame
                                fd <- dc.absDir
                            do
                                val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                                val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                                frame = Some(f.applyBendForFinalDir(dc.angle.toUnit[Degree].value, targetVec))
                        case _ => ()
                builder.result()

    private lazy val directionAfterByIdx: Signal[Map[Int, Vec3]] =
        welems_var.signal
            .combineWithDistinct(frameBeforeByIdx)
            .map: (elems, frameMap) =>
                elems
                    .flatMap: (idx, elem) =>
                        frameMap
                            .get(idx)
                            .flatMap: frameBefore =>
                                elem match
                                    case dc: AddDirectionChange           =>
                                        // Pinned: show the STORED pin as-is, not the engine's reachable
                                        // projection. See DynamicPipeSlotPanel.directionAfterByIdx for
                                        // rationale. The badge's isCompatibleSig renders the warning
                                        // indicator when the pin is unreachable at (frame, angle).
                                        val dir = dc.absDir match
                                            case Some(fd) =>
                                                val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                                                Vec3.fromAzimuthElevation(azDeg, elDeg)
                                            case None     =>
                                                frameBefore.direction
                                        Some(idx -> dir)
                                    case _ : AddFlowOnlyPipeElement_13384 =>
                                        Some(idx -> frameBefore.direction)
                                    case _ => None
                    .toMap

    override protected def directionBadgeSig(idx: Int, xtraSig: Signal[XtraOutputs]): Signal[Option[Vec3]] =
        directionAfterByIdx.map(_.get(idx))

    override protected def frameBeforeSig_badge(idx: Int): Signal[Option[PipeFrame]] =
        frameBeforeByIdx.map(_.get(idx))

    private lazy val previousDirectionByIdx: Signal[Map[Int, Vec3]] =
        welems_var.signal
            .combineWithDistinct(frameBeforeByIdx)
            .map: (elems, frameMap) =>
                elems
                    .collect { case (idx, _: AddDirectionChange) => idx }
                    .flatMap(idx => frameMap.get(idx).map(f => idx -> f.direction))
                    .toMap

    override protected def previousDirectionSig_badge(idx: Int): Signal[Option[Vec3]] =
        previousDirectionByIdx.map(_.get(idx))

    private def absDirBadgeVar[A <: AddDirectionChange](
        getter: A => Option[AbsoluteDirection],
        setter: (A, Option[AbsoluteDirection]) => A
    ): Var[A] => Option[Var[Option[AbsoluteDirection]]] =
        ev => Some(ev.zoomLazy(getter)(setter))

    private def relativeDirectionExtra[A <: AddDirectionChange](
        idx   : Int,
        getter: A => Option[AbsoluteDirection],
        setter: (A, Option[AbsoluteDirection]) => A
    ): Var[A] => HtmlElement =
        ev =>
            val fdVar = ev.zoomLazy(getter)(setter)
            RelativeDirectionInput    (
                frameBefore     = frameBeforeSig_badge(idx),
                deflectionAngle = deflectionAngleSig(idx),
                absDirVar       = fdVar
            ).node

    override protected def deflectionAngleSig(idx: Int): Signal[Option[Double]] =
        welems_var.signal.map: elems =>
            elems.collectFirst:
                case (i, dc: AddDirectionChange) if i == idx => dc.angle.toUnit[Degree].value

    lazy val rendered_elems_sig: Signal[Seq[HtmlElement]] =
        welem_xtraoutput_sig.signal
            .splitMatchSeq(_._1)
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, SetInnerShape, XtraOutputs), HtmlElement] {
                case (i, aa: SetInnerShape, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetInnerShape]  (
                    iaax._1,
                    I18N.set_prop.SetInnerShape,
                    iaax._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetInnerShape]])
                )
            }
            // Dev-only variant (SetInnerShapePreventSectionGeometryChangeAuto, backend-forbidden).
            // Not reachable from any UI menu; this clause only satisfies exhaustiveness of the
            // splitMatchSeq over the FlowOnly 13384 prop ADT. Rendered identically to plain
            // SetInnerShape (read-only shape display) should a dev ever load such a project.
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs                      ),
                (Int, SetInnerShapePreventSectionGeometryChangeAuto, XtraOutputs),
                HtmlElement
            ] { case (i, aa: SetInnerShapePreventSectionGeometryChangeAuto, x) =>
                (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetInnerShapePreventSectionGeometryChangeAuto]  (
                    iaax._1,
                    I18N.set_prop.SetInnerShape,
                    iaax._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetInnerShapePreventSectionGeometryChangeAuto]])
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, SetRoughness, XtraOutputs), HtmlElement] {
                case (i, aa: SetRoughness, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetRoughness]  (
                    iaax._1,
                    I18N.set_prop.SetRoughness,
                    iaax._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetRoughness]])
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, SetMaterial, XtraOutputs), HtmlElement] {
                case (i, aa: SetMaterial, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetMaterial]  (
                    iaax._1,
                    I18N.set_prop.SetMaterial,
                    iaax._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetMaterial]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, SetNumberOfFlows, XtraOutputs       ),
                HtmlElement
            ] { case (i, aa: SetNumberOfFlows, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[SetNumberOfFlows]  (
                    iaax._1,
                    I18N.set_prop.SetNumberOfFlows_fieldName,
                    iaax._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetNumberOfFlows]])
                )
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
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs                  ),
                (Int, AddSectionSloppedForceManualElevationGain, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSectionSloppedForceManualElevationGain, x) => (i, aa, x) } { (_, _) =>
                // should never happen, only allowed internally in engine
                ???
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
                renderElemTyped[AddAngleAdjustable]      (
                    iaax._1,
                    I18N.add_element.AddAngleAdjustable,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd)),
                    afterBadge       = ev =>
                        val selectDialog = AnglePresetCatalogSelectComponent(
                            entriesSignal = anglePresetsSignal,
                            onSelect      = Observer[AnglePresetCatalogEntry](entry =>
                                ev.update(a => a.copy(name = entry.reference, angle = entry.angle, zeta = entry.zeta))
                            )
                        )
                        div(
                            button(
                                cls := "btn btn-secondary btn-sm",
                                I18N_UI.catalog._self,
                                onClick --> { _ => selectDialog.open() }
                            ),
                            selectDialog.node
                        )
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, AddSharpeAngle_0_to_90, XtraOutputs ),
                HtmlElement
            ] { case (i, aa: AddSharpeAngle_0_to_90, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSharpeAngle_0_to_90]      (
                    iaax._1,
                    I18N.add_element.AddSharpeAngle_0_to_90,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs      ),
                (Int, AddSharpeAngle_0_to_90_Unsafe, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSharpeAngle_0_to_90_Unsafe, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSharpeAngle_0_to_90_Unsafe]      (
                    iaax._1,
                    I18N.add_element.AddSharpeAngle_0_to_90_Unsafe,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddSmoothCurve_90, XtraOutputs), HtmlElement] {
                case (i, aa: AddSmoothCurve_90, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_90]      (
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_90,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs ),
                (Int, AddSmoothCurve_90_Unsafe, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSmoothCurve_90_Unsafe, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_90_Unsafe]      (
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_90_Unsafe,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddSmoothCurve_60, XtraOutputs), HtmlElement] {
                case (i, aa: AddSmoothCurve_60, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_60]      (
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_60,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs ),
                (Int, AddSmoothCurve_60_Unsafe, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSmoothCurve_60_Unsafe, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_60_Unsafe]      (
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_60_Unsafe,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddElbows_2x45, XtraOutputs), HtmlElement] {
                case (i, aa: AddElbows_2x45, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddElbows_2x45]      (
                    iaax._1,
                    I18N.add_element.AddElbows_2x45,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddElbows_3x30, XtraOutputs), HtmlElement] {
                case (i, aa: AddElbows_3x30, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddElbows_3x30]      (
                    iaax._1,
                    I18N.add_element.AddElbows_3x30,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, AddElbows_4x22p5, XtraOutputs), HtmlElement] {
                case (i, aa: AddElbows_4x22p5, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddElbows_4x22p5]      (
                    iaax._1,
                    I18N.add_element.AddElbows_4x22p5,
                    iaax._2,
                    sig,
                    isProperty       = false,
                    extra            = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
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
            .map(renderV7WrapperElems(renderWrapperElems))

    import FlowOnlyDefaultable_13384.given

    // simple instance of tree (for testing only)
    lazy val tagTreeMenu = TagTreeMenu(
        shortcut_start_new_pipe,
        prop_elements,
        geom_elements
    )

    lazy val shortcut_start_new_pipe =
        import afpma.laminar.form.{Defaultable as D}
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
            TagTreeMenu.Leaf[AddSectionSlopped],
            direction_change_elements,
            split_group,
            grids
        )
    )

    lazy val grids = TagTreeMenu.Group(
        txt  = I18N.add_element.AddFlowResistance,
        next = List(
            TagTreeMenu.Modal[FlowOnlyPipeDescr_13384]         (
                txt          = I18N_UI.catalog.flow_resistance_presets,
                modalContent = (onSelect) =>
                    FlowResistanceCatalogSelectComponent(
                        entriesSignal = flowResistancePresetsSignal,
                        onSelect      = onSelect.contramap[FlowResistanceCatalogEntry](e =>
                            AddFlowResistance(e.name, e.zeta, e.cross_section)
                        )
                    ).node
            ),
            TagTreeMenu.Leaf[AddFlowResistance]                ("ζ")
        )
    )

    lazy val split_group = TagTreeMenu.Group(
        txt  = I18N.set_prop.SetNumberOfFlows,
        next = List(
            TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_NumberOfChannels, SetNumberOfFlows(2)),
            TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_Join, SetNumberOfFlows(1)            )
        )
    )

    // lazy val straight_elements = TagTreeMenu.Group(
    //     txt  = I18N.add_element.add_section_element,
    //     next = List(
    //         TagTreeMenu.Leaf[AddSectionSlopped]
    //     )
    // )

    lazy val direction_change_elements = TagTreeMenu.Group(
        txt  = I18N.add_element.add_direction_change_element,
        next = List(
            TagTreeMenu.Leaf[AddSharpeAngle_0_to_90_Unsafe],
            TagTreeMenu.Leaf[AddSmoothCurve_90_Unsafe],
            TagTreeMenu.Leaf[AddSmoothCurve_60_Unsafe],
            TagTreeMenu.Leaf[AddElbows_2x45],
            TagTreeMenu.Leaf[AddElbows_3x30],
            TagTreeMenu.Leaf[AddElbows_4x22p5],
            TagTreeMenu.Modal[FlowOnlyPipeDescr_13384]         (
                txt          = I18N_UI.catalog.angle_presets_from_catalog,
                modalContent = (onSelect) =>
                    AnglePresetCatalogSelectComponent(
                        entriesSignal = anglePresetsSignal,
                        onSelect      = onSelect.contramap[AnglePresetCatalogEntry](e =>
                            AddFlowOnlyPipeElement_13384.AddAngleAdjustable(e.reference, e.angle, e.zeta)
                        )
                    ).node
            ),
            TagTreeMenu.Leaf[AddAngleAdjustable]               (I18N_UI.catalog.custom_angle_bend)
        )
    )

    // prop

    lazy val prop_elements = TagTreeMenu.Group(
        txt  = I18N.set_prop._self,
        next = List(
            TagTreeMenu.Group (
                txt  = I18N.set_prop._material_and_roughness,
                next = List(
                    TagTreeMenu.Leaf[SetMaterial],
                    TagTreeMenu.Leaf[SetRoughness]
                )
            ),
            TagTreeMenu.Leaf[SetInnerShape]
        )
    )
