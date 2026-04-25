/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.Vec3

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.AIR_DISTRIB_HEIGHT_M
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.anglePresetsSignal
import afpma.firecalc.ui.models.firebox_var
import afpma.firecalc.ui.models.flowResistancePresetsSignal

import cats.Show

import com.raquo.laminar.api.L.*

import coulomb.policy.standard.given

import io.taig.babel.Locale

trait PipePanel_13384_FlowOnly(using Locale, DisplayUnits) extends PipePanel:

    import AddFlowOnlyPipeElement_13384.*
    import SetFlowOnlyPipeProp_13384.*

    private given AutoCalcHelper.ElemExtractors[FlowOnlyPipeDescr_13384] = AutoCalcHelper.ElemExtractors(
        asInitialDirection  = { case SetInitialDirection(az, incl) => (az, incl) },
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

    private lazy val frameBeforeByIdx: Signal[Map[Int, PipeFrame]] =
        welems_var.signal.map: elems =>
            var frame: Option[PipeFrame] = None
            val builder = Map.newBuilder[Int, PipeFrame]
            for (idx, elem) <- elems do
                elem match
                    case SetInitialDirection(az, incl) =>
                        val azDeg = AzimuthDirection.toDegrees(az)
                        val elDeg = InclinationDirection.toDegrees(incl)
                        frame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(azDeg, elDeg)))
                    case _                             => ()
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

    // ── Auto-calc: air distribution box position ─────────────────────────

    /**
     * Status signal for the air intake auto-calc button.
     * Uses FINAL state (any direction + any shape in entire list), since we're
     * aligning the pipe's endpoint to the air distrib box.
     */
    private lazy val airIntakeAutoCalcStatusSig: Signal[(Boolean, Option[String])] =
        AutoCalcHelper.mkStatusSig(
            hasFrameSig = frameBeforeByIdx.map(_.nonEmpty),
            hasShapeSig = welems_var.signal.map(_.exists: (_, e) =>
                summon[AutoCalcHelper.ElemExtractors[FlowOnlyPipeDescr_13384]].asInnerShape.isDefinedAt(e))
        )

    /**
     * Compute position on the air distribution box boundary.
     *
     * Uses the FINAL pipe direction (replays all elements) since we're aligning
     * the end of the last element to the air distrib box.
     *
     * The air distrib box is centered at (0, 0) with same width/depth as the firebox,
     * bottom at z = −AIR_DISTRIB_HEIGHT_M, top at z = 0 (firebox floor).
     *
     * Z alignment (non-vertical): lowest point of pipe opening = air distrib bottom.
     * Vertical Up: center of bottom face.
     * Vertical Down: center of top face.
     */
    private def computeAirDistribPosition: Option[(Double, Double, Double)] =
        val elems    = welems_var.now()
        val frameOpt = AutoCalcHelper.replayFrame(elems, Int.MaxValue)
        val shapeOpt = AutoCalcHelper.lastShapeBefore(elems, Int.MaxValue)
        for
            frame <- frameOpt
            shape <- shapeOpt
        yield
            val fb  = firebox_var.now()
            val box = AutoCalcHelper.TargetBox(
                centerX   = 0.0,
                centerY   = 0.0,
                halfWidth = fb.firebox_width.value / 2.0,
                halfDepth = fb.firebox_depth.value / 2.0,
                bottomZ   = -AIR_DISTRIB_HEIGHT_M,
                height    = AIR_DISTRIB_HEIGHT_M
            )
            AutoCalcHelper.computeBottomAlignedPosition(frame, shape, box)

    /** Auto-calc button helper — generic over SetInitialPosition / SetFinalPosition. */
    private def airIntakeAutoCalcExtra[A](ctor: (Length, Length, Length) => A): Var[A] => HtmlElement =
        AutoCalcHelper.autoCalcButton(
            airIntakeAutoCalcStatusSig,
            () => computeAirDistribPosition.map((x, y, z) => ctor(x.m, y.m, z.m))
        )

    private lazy val directionAfterByIdx: Signal[Map[Int, Vec3]] =
        welems_var.signal
            .combineWith(frameBeforeByIdx)
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
            .combineWith(frameBeforeByIdx)
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
                    I18N.set_prop.SetNumberOfFlows,
                    iaax._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetNumberOfFlows]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, SetInitialDirection, XtraOutputs    ),
                HtmlElement
            ] { case (i, aa: SetInitialDirection, x) =>
                (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetInitialDirection]  (
                    iaax._1,
                    I18N.set_prop.SetInitialDirection,
                    iaax._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetInitialDirection]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_13384, XtraOutputs),
                (Int, SetInitialPosition, XtraOutputs     ),
                HtmlElement
            ] { case (i, aa: SetInitialPosition, x) =>
                (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetInitialPosition]  (
                    iaax._1,
                    I18N.set_prop.SetInitialPosition,
                    iaax._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetInitialPosition]])
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_13384, XtraOutputs), (Int, SetFinalPosition, XtraOutputs), HtmlElement] {
                case (i, aa: SetFinalPosition, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetFinalPosition]  (
                    iaax._1,
                    I18N.set_prop.SetFinalPosition,
                    iaax._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetFinalPosition]]),
                    extra        = airIntakeAutoCalcExtra(SetFinalPosition.apply)
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
            TagTreeMenu.Leaf[AddAngleAdjustable]
        )
    )

    // prop

    lazy val prop_elements = TagTreeMenu.Group(
        txt  = I18N.set_prop._self,
        next = List(
            TagTreeMenu.Group (
                txt  = I18N.set_prop._position_and_direction,
                next = List(
                    TagTreeMenu.Leaf[SetInitialPosition],
                    TagTreeMenu.Leaf[SetInitialDirection],
                    TagTreeMenu.Leaf[SetFinalPosition]
                )
            ),
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
