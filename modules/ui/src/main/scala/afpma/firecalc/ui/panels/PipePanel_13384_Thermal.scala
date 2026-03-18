/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AddThermalPipeElement_13384.*
import afpma.firecalc.dto.all.SetThermalPipeProp_13384.*

import afpma.firecalc.engine.models.geometry.{PipeFrame, Vec3}
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.pipePresetsSignal
import afpma.firecalc.ui.models.casingPresetsSignal
import afpma.firecalc.ui.models.flowResistancePresetsSignal
import afpma.firecalc.ui.i18n.implicits.given

import coulomb.policy.standard.given

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

trait PipePanel_13384_Thermal(using Locale, DisplayUnits) extends PipePanel:

    type In = ThermalPipeDescr_13384

    import hastranslations.given

    private given thermalHorizontalForm_13384: ThermalHorizontalForm_13384 = ThermalHorizontalForm_13384()
    import thermalHorizontalForm_13384.given

    /** Override to supply an inherited PipeFrame from the previous pipe.
     *  Defaults to no external frame (first pipe in a sequence, or direction tracking inactive).
     */
    protected def externalInitialFrameSig: Signal[Option[PipeFrame]] = Signal.fromValue(None)

    /** Compute PipeFrame per element index by scanning the element list.
     *  This runs in the UI, independent of engine success, so direction labels
     *  are available even when the pipe has validation errors.
     *  When `externalInitialFrameSig` provides a frame, that frame seeds the
     *  computation for pipes that have no `SetInitialDirection` of their own.
     */
    private lazy val frameBeforeByIdx: Signal[Map[Int, PipeFrame]] =
        welems_var.signal.combineWith(externalInitialFrameSig).map: (elems, externalFrame) =>
            var frame: Option[PipeFrame] = externalFrame
            val builder = Map.newBuilder[Int, PipeFrame]
            for (idx, elem) <- elems do
                elem match
                    case SetInitialDirection(az, incl) =>
                        val azDeg  = AzimuthDirection.toDegrees(az)
                        val elDeg  = InclinationDirection.toDegrees(incl)
                        frame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(azDeg, elDeg)))
                    case _ => ()
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

    /**
     * Direction AFTER each element, keyed by element index. Used for the direction badge.
     * Only populated for AddThermalPipeElement_13384 subtypes (geometric elements);
     * property setters (SetThermalPipeProp_13384) are excluded — no badge for them.
     * - DC with absDir: direction after the bend
     * - DC without absDir: no badge entry
     * - Straight sections: direction from the frame before
     */
    private lazy val directionAfterByIdx: Signal[Map[Int, Vec3]] =
        welems_var.signal.combineWith(frameBeforeByIdx).map: (elems, frameMap) =>
            elems.flatMap: (idx, elem) =>
                frameMap.get(idx).flatMap: frameBefore =>
                    elem match
                        case dc: AddDirectionChange =>
                            dc.absDir.map: fd =>
                                val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                                val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                                idx -> frameBefore.applyBendForFinalDir(dc.angle.toUnit[Degree].value, targetVec).direction
                        case _: AddThermalPipeElement_13384 =>
                            Some(idx -> frameBefore.direction)
                        case _ => None
            .toMap

    override protected def directionBadgeSig(idx: Int, xtraSig: Signal[XtraOutputs]): Signal[Option[Vec3]] =
        directionAfterByIdx.map(_.get(idx))

    override protected def frameBeforeSig_badge(idx: Int): Signal[Option[PipeFrame]] =
        frameBeforeByIdx.map(_.get(idx))

    /**
     * Direction coming INTO the element, only for direction-change elements.
     * Used by DirectionBadgeComponent to determine editable vs read-only mode.
     */
    private lazy val previousDirectionByIdx: Signal[Map[Int, Vec3]] =
        welems_var.signal.combineWith(frameBeforeByIdx).map: (elems, frameMap) =>
            elems
                .collect { case (idx, _: AddDirectionChange) => idx }
                .flatMap(idx => frameMap.get(idx).map(f => idx -> f.direction))
                .toMap

    override protected def previousDirectionSig_badge(idx: Int): Signal[Option[Vec3]] =
        previousDirectionByIdx.map(_.get(idx))

    /**
     * Returns the `badgeFinalDirVar` factory for a DC element.
     * The derived Var zooms into the absDir field of the element.
     */
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
            RelativeDirectionInput(
                frameBefore     = frameBeforeSig_badge(idx),
                deflectionAngle = deflectionAngleSig(idx),
                absDirVar     = fdVar
            ).node

    override protected def deflectionAngleSig(idx: Int): Signal[Option[Double]] =
        welems_var.signal.map: elems =>
            elems.collectFirst:
                case (i, dc: AddDirectionChange) if i == idx => dc.angle.toUnit[Degree].value

    lazy val rendered_elems_sig: Signal[Seq[HtmlElement]] =
        welem_xtraoutput_sig.signal
            .splitMatchSeq(_._1)
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetPropertiesInBatch, XtraOutputs), HtmlElement] {
                case (i, aa: SetPropertiesInBatch, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetPropertiesInBatch](iaax._1, I18N.set_prop.SetPropertiesInBatch, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, LinedFlue, XtraOutputs), HtmlElement] {
                case (i, aa: LinedFlue, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[LinedFlue](iaax._1, I18N.set_prop.LinedFlue, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetInnerShape, XtraOutputs), HtmlElement] {
                case (i, aa: SetInnerShape, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetInnerShape](iaax._1, I18N.set_prop.SetInnerShape, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetOuterShape, XtraOutputs), HtmlElement] {
                case (i, aa: SetOuterShape, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetOuterShape](iaax._1, I18N.set_prop.SetOuterShape, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetThickness, XtraOutputs), HtmlElement] {
                case (i, aa: SetThickness, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetThickness](iaax._1, I18N.set_prop.SetThickness, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetRoughness, XtraOutputs), HtmlElement] {
                case (i, aa: SetRoughness, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetRoughness](iaax._1, I18N.set_prop.SetRoughness, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetMaterial, XtraOutputs), HtmlElement] {
                case (i, aa: SetMaterial, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetMaterial](iaax._1, I18N.set_prop.SetMaterial, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetLayer, XtraOutputs), HtmlElement] {
                case (i, aa: SetLayer, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetLayer](iaax._1, I18N.set_prop.SetLayer, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetLayers, XtraOutputs), HtmlElement] {
                case (i, aa: SetLayers, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetLayers](iaax._1, I18N.set_prop.SetLayers, iaax._2, sig, isProperty = true)
            }
            .handleCase[
                (Int, ThermalPipeDescr_13384, XtraOutputs),
                (Int, SetAirSpaceAfterLayers, XtraOutputs),
                HtmlElement
            ] { case (i, aa: SetAirSpaceAfterLayers, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[SetAirSpaceAfterLayers](
                    iaax._1,
                    I18N.set_prop.SetAirSpaceAfterLayers,
                    iaax._2,
                    sig,
                    isProperty = true
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetPipeLocation, XtraOutputs), HtmlElement] {
                case (i, aa: SetPipeLocation, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetPipeLocation](
                    iaax._1,
                    I18N.set_prop.SetPipeLocation,
                    iaax._2,
                    sig,
                    isProperty = true
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetDuctType, XtraOutputs), HtmlElement] {
                case (i, aa: SetDuctType, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetDuctType](iaax._1, I18N.set_prop.SetDuctType, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetInitialDirection, XtraOutputs), HtmlElement] {
                case (i, aa: SetInitialDirection, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetInitialDirection](iaax._1, I18N.set_prop.SetInitialDirection, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetInitialPosition, XtraOutputs), HtmlElement] {
                case (i, aa: SetInitialPosition, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetInitialPosition](iaax._1, I18N.set_prop.SetInitialPosition, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetFinalPosition, XtraOutputs), HtmlElement] {
                case (i, aa: SetFinalPosition, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetFinalPosition](iaax._1, I18N.set_prop.SetFinalPosition, iaax._2, sig, isProperty = true)
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, SetNumberOfFlows, XtraOutputs), HtmlElement] {
                case (i, aa: SetNumberOfFlows, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[SetNumberOfFlows](
                    iaax._1,
                    I18N.set_prop.SetNumberOfFlows,
                    iaax._2,
                    sig,
                    isProperty = true
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddSectionSlopped, XtraOutputs), HtmlElement] {
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
                (Int, ThermalPipeDescr_13384, XtraOutputs),
                (Int, AddSectionSloppedForceManualElevationGain, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSectionSloppedForceManualElevationGain, x) => (i, aa, x) } { (_, _) =>
                // should never happen, only allowed internally in engine
                ???
            }
            .handleCase[
                (Int, ThermalPipeDescr_13384, XtraOutputs),
                (Int, AddSectionHorizontal, XtraOutputs  ),
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
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddSectionVertical, XtraOutputs), HtmlElement] {
                case (i, aa: AddSectionVertical, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSectionVertical](
                    iaax._1,
                    I18N.add_element.AddSectionVertical,
                    iaax._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddAngleAdjustable, XtraOutputs), HtmlElement] {
                case (i, aa: AddAngleAdjustable, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddAngleAdjustable](
                    iaax._1,
                    I18N.add_element.AddAngleAdjustable,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[
                (Int, ThermalPipeDescr_13384, XtraOutputs),
                (Int, AddSharpeAngle_0_to_90, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSharpeAngle_0_to_90, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSharpeAngle_0_to_90](
                    iaax._1,
                    I18N.add_element.AddSharpeAngle_0_to_90,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[
                (Int, ThermalPipeDescr_13384, XtraOutputs       ),
                (Int, AddSharpeAngle_0_to_90_Unsafe, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSharpeAngle_0_to_90_Unsafe, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSharpeAngle_0_to_90_Unsafe](
                    iaax._1,
                    I18N.add_element.AddSharpeAngle_0_to_90_Unsafe,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddSmoothCurve_90, XtraOutputs), HtmlElement] {
                case (i, aa: AddSmoothCurve_90, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_90](
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_90,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[
                (Int, ThermalPipeDescr_13384, XtraOutputs  ),
                (Int, AddSmoothCurve_90_Unsafe, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSmoothCurve_90_Unsafe, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_90_Unsafe](
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_90_Unsafe,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddSmoothCurve_60, XtraOutputs), HtmlElement] {
                case (i, aa: AddSmoothCurve_60, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_60](
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_60,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[
                (Int, ThermalPipeDescr_13384, XtraOutputs  ),
                (Int, AddSmoothCurve_60_Unsafe, XtraOutputs),
                HtmlElement
            ] { case (i, aa: AddSmoothCurve_60_Unsafe, x) => (i, aa, x) } { (iaax, sig) =>
                renderElemTyped[AddSmoothCurve_60_Unsafe](
                    iaax._1,
                    I18N.add_element.AddSmoothCurve_60_Unsafe,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddElbows_2x45, XtraOutputs), HtmlElement] {
                case (i, aa: AddElbows_2x45, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddElbows_2x45](
                    iaax._1,
                    I18N.add_element.AddElbows_2x45,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddElbows_3x30, XtraOutputs), HtmlElement] {
                case (i, aa: AddElbows_3x30, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddElbows_3x30](
                    iaax._1,
                    I18N.add_element.AddElbows_3x30,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddElbows_4x22p5, XtraOutputs), HtmlElement] {
                case (i, aa: AddElbows_4x22p5, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddElbows_4x22p5](
                    iaax._1,
                    I18N.add_element.AddElbows_4x22p5,
                    iaax._2,
                    sig,
                    isProperty = false,
                    extra = relativeDirectionExtra(iaax._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddSectionDecrease, XtraOutputs), HtmlElement] {
                case (i, aa: AddSectionDecrease, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSectionDecrease](
                    iaax._1,
                    I18N.add_element.AddSectionDecrease,
                    iaax._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddSectionIncrease, XtraOutputs), HtmlElement] {
                case (i, aa: AddSectionIncrease, x) => (i, aa, x)
            } { (iaax, sig) =>
                renderElemTyped[AddSectionIncrease](
                    iaax._1,
                    I18N.add_element.AddSectionIncrease,
                    iaax._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddFlowResistance, XtraOutputs), HtmlElement] {
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
            .handleCase[(Int, ThermalPipeDescr_13384, XtraOutputs), (Int, AddPressureDiff, XtraOutputs), HtmlElement] {
                case (i, aa: AddPressureDiff, x) => (i, aa, x)
            } { (_, _) => throw new Exception("ERROR: AddPressureDiff not implemented.") }
            .toSignal

    import defaultable_13384.incr_descr_en13384.given

    // simple instance of tree (for testing only)
    lazy val tagTreeMenu = TagTreeMenu(
        shortcut_start_new_pipe,
        prop_elements,
        geom_elements,
        catalog_elements
    )

    lazy val shortcut_start_new_pipe =
        import afpma.firecalc.ui.formgen.{Defaultable as D}
        TagTreeMenu.Shortcut  (
            txt   = I18N.set_prop.shortcuts.start_a_new_pipe,
            elems = (
                summon[D[SetPipeLocation]].default,
                summon[D[SetMaterial]].default,
                summon[D[SetInnerShape]].default,
                summon[D[SetLayer]].default
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
            TagTreeMenu.Leaf[AddElbows_4x22p5]
        )
    )

    // prop

    lazy val prop_elements = TagTreeMenu.Group(
        txt  = I18N.set_prop._self,
        next = List(
            TagTreeMenu.Group(
                txt  = I18N.set_prop._position_and_direction,
                next = List(
                    TagTreeMenu.Leaf[SetInitialPosition],
                    TagTreeMenu.Leaf[SetInitialDirection],
                    TagTreeMenu.Leaf[SetFinalPosition]
                )
            ),
            TagTreeMenu.Group(
                txt  = I18N.set_prop._material_and_roughness,
                next = List(
                    TagTreeMenu.Leaf[SetMaterial],
                    TagTreeMenu.Leaf[SetRoughness]
                )
            ),
            TagTreeMenu.Leaf[SetPipeLocation],
            prop_elements_geom,
            prop_elements_insulation,
            TagTreeMenu.Leaf[SetAirSpaceAfterLayers]
        )
    )

    lazy val prop_elements_geom = TagTreeMenu.Group(
        txt  = I18N.set_prop._geometric_properties,
        next = List(
            TagTreeMenu.Leaf[SetInnerShape],
            TagTreeMenu.Leaf[SetOuterShape],
            TagTreeMenu.Leaf[SetThickness]
        )
    )

    lazy val prop_elements_insulation = TagTreeMenu.Group(
        txt  = I18N.set_prop.define_layers,
        next = List(
            TagTreeMenu.Leaf[SetLayer],
            TagTreeMenu.Leaf[SetLayers]
        )
    )

    // catalog
    lazy val catalog_elements = TagTreeMenu.Group(
        txt  = I18N_UI.catalog._self,
        next = List(
            TagTreeMenu.Modal[ThermalPipeDescr_13384](
                txt = I18N_UI.catalog.simple_pipe,
                modalContent = (onSelect) =>
                    PipeCatalogSelectComponent(
                        entriesSignal = pipePresetsSignal,
                        onSelect      = onSelect.contramap[SetPropertiesInBatch](identity)
                    ).node
            ),
            TagTreeMenu.Modal[ThermalPipeDescr_13384](
                txt = I18N_UI.catalog.lined_flue,
                modalContent = (onSelect) =>
                    LinedFlueCatalogSelectComponent(
                        pipePresetsSignal   = pipePresetsSignal,
                        casingPresetsSignal = casingPresetsSignal,
                        onSelect            = onSelect.contramap[LinedFlue](identity)
                    ).node
            ),
            TagTreeMenu.Modal[ThermalPipeDescr_13384](
                txt = I18N_UI.catalog.flow_resistance_presets,
                modalContent = (onSelect) =>
                    FlowResistanceCatalogSelectComponent(
                        entriesSignal = flowResistancePresetsSignal,
                        onSelect      = onSelect.contramap[FlowResistanceCatalogEntry](e =>
                            AddFlowResistance(e.name, e.zeta, e.cross_section)
                        )
                    ).node
            )
        )
    )
