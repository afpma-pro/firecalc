/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.{PipeFrame, Vec3}

import cats.data.Validated

import afpma.firecalc.engine.standard.VNelMcalcErr
import afpma.firecalc.i18n.implicits.given
import afpma.firecalc.ui.utils.flatMapVNelE
import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.*

import coulomb.policy.standard.given

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

final case class FluePipePanel()(using Locale, DisplayUnits) extends PipePanel:

    override protected def vizFieldsetIdPrefix: String = "flue"

    override protected def ownsVizElement(id: VizElementId): Boolean = id match
        case VizElementId.FluePipeElement(_) => true
        case _                               => false

    override protected def vizElementIndex(id: VizElementId): Int = id match
        case VizElementId.FluePipeElement(idx) => idx
        case _                                 => -1

    import hastranslations.given

    private given flowOnlyHorizontalForm_15544: FlowOnlyHorizontalForm_15544 = FlowOnlyHorizontalForm_15544()
    import flowOnlyHorizontalForm_15544.given

    type In  = FlowOnlyPipeDescr_15544
    type Out = FluePipe_15544
    type PT  = FluePipeT
    lazy val sectionType = FluePipeT

    lazy val titleString = I18N.panels.channel_pipe

    val channel_pipe_vnel2_signal = results_en15544_strict_sig.map: strict =>
        strict.andThen(_.validateFluePipeShape())

    val channel_pipe_vnel3_signal = results_en15544_channel_pipe.map: p_vnel =>
        p_vnel.andThen(p => p.`ph-(pR+pu)`)

    val channel_pipe_vnel4_signal = results_en15544_strict_sig.map: strict =>
        strict.andThen(_.primary.validateVelocitiesInFluePipe())

    val channel_pipe_vnel5_signal: Signal[VNelMcalcErr[Unit]] =
        results_en15544_strict_sig.flatMapVNelE: strict =>
            strict.primary.validateCitedConstraints()

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
            .combineWith(channel_pipe_vnel5_signal)
            .map((vBase, v5) =>
                vBase.andThen(_ => v5).andThen(_ => vBase)
            )

    lazy val elems_v = fluepipe_incrdescr_var

    type PipeIdsMapping = afpma.firecalc.engine.models.FluePipe_Module_15544.IdsMapping

    override lazy val pipeMappings_vnel_signal = fluepipe_mappings_vnel_signal
    override lazy val pipeResult_vnel_signal   = results_en15544_channel_pipe

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings.getUnsafe(idIncr).map(_.unwrap.unwrap)

    lazy val channel_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.flue)

    override lazy val quadrionSubtotal_sig = channel_pipe_quadrions_sig

    private lazy val lzSummary_sig: Signal[Option[String]] =
        results_en15544_channel_pipe
            .combineWith(results_en15544_strict_sig)
            .map: (vnelPipe, vnelStrict) =>
                for
                    pipe   <- vnelPipe.toOption
                    strict <- vnelStrict.toOption
                yield
                    val lzStr = f"${pipe.lengthSum.value}%.2f m"
                    strict.L_Z_min match
                        case Validated.Valid(lzMin) =>
                            val lzMinStr = f"${lzMin.unwrap.value}%.2f m"
                            I18N.panels.channel_pipe_length_with_min(lzStr, lzMinStr)
                        case Validated.Invalid(_) =>
                            I18N.panels.channel_pipe_length(lzStr)

    override protected lazy val titleXtraSig: Signal[Option[HtmlElement]] =
        statusIcon.combineWith(lzSummary_sig).map: (icon, lzOpt) =>
            Some(span(
                cls := "flex flex-row gap-x-2",
                icon,
                lzOpt.map(s => p(s)).getOrElse(emptyNode)
            ))

    private lazy val frameBeforeByIdx: Signal[Map[Int, PipeFrame]] =
        welems_var.signal.map: elems =>
            var frame: Option[PipeFrame] = None
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
                        case _: AddFlowOnlyPipeElement_15544 =>
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
        welem_xtraoutput_sig
            .splitMatchSeq(_._1)
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetInnerShape, XtraOutputs), HtmlElement] {
                case (i, incr: SetInnerShape, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetInnerShape](iix._1, I18N.set_prop.SetInnerShape, iix._2, sig, isProperty = true)
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetRoughness, XtraOutputs), HtmlElement] {
                case (i, incr: SetRoughness, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetRoughness](iix._1, I18N.set_prop.SetRoughness, iix._2, sig, isProperty = true)
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetMaterial, XtraOutputs), HtmlElement] {
                case (i, incr: SetMaterial, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetMaterial](iix._1, I18N.set_prop.SetMaterial, iix._2, sig, isProperty = true)
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, SetNumberOfFlows, XtraOutputs       ),
                HtmlElement
            ] { case (i, incr: SetNumberOfFlows, x) => (i, incr, x) } { (iix, sig) =>
                renderElemTyped[SetNumberOfFlows](
                    iix._1,
                    I18N.set_prop.SetNumberOfFlows,
                    iix._2,
                    sig,
                    isProperty = true
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetInitialDirection, XtraOutputs), HtmlElement] {
                case (i, incr: SetInitialDirection, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetInitialDirection](iix._1, I18N.set_prop.SetInitialDirection, iix._2, sig, isProperty = true)
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetInitialPosition, XtraOutputs), HtmlElement] {
                case (i, incr: SetInitialPosition, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetInitialPosition](iix._1, I18N.set_prop.SetInitialPosition, iix._2, sig, isProperty = true)
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetFinalPosition, XtraOutputs), HtmlElement] {
                case (i, incr: SetFinalPosition, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetFinalPosition](iix._1, I18N.set_prop.SetFinalPosition, iix._2, sig, isProperty = true)
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionSlopped, XtraOutputs      ),
                HtmlElement
            ] { case (i, incr: AddSectionSlopped, x) => (i, incr, x) } { (iix, sig) =>
                renderElemTyped[AddSectionSlopped](
                    iix._1,
                    I18N.add_element.AddSectionSlopped,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionSloppedForceManualElevationGain, XtraOutputs      ),
                HtmlElement
            ] { case (i, incr: AddSectionSloppedForceManualElevationGain, x) => (i, incr, x) } { (iix, sig) =>
                // should never happen, only allowed internally in engine
                ???
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionHorizontal, XtraOutputs   ),
                HtmlElement
            ] { case (i, incr: AddSectionHorizontal, x) => (i, incr, x) } { (iix, sig) =>
                renderElemTyped[AddSectionHorizontal](
                    iix._1,
                    I18N.add_element.AddSectionHorizontal,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionVertical, XtraOutputs     ),
                HtmlElement
            ] { case (i, incr: AddSectionVertical, x) => (i, incr, x) } { (iix, sig) =>
                renderElemTyped[AddSectionVertical](
                    iix._1,
                    I18N.add_element.AddSectionVertical,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSharpeAngle_0_to_180, XtraOutputs),
                HtmlElement
            ] { case (i, incr: AddSharpeAngle_0_to_180, x) => (i, incr, x) } { (iix, sig) =>
                renderElemTyped[AddSharpeAngle_0_to_180](
                    iix._1,
                    I18N.add_element.AddSharpeAngle_0_to_180,
                    iix._2,
                    sig,
                    isProperty      = false,
                    extra            = relativeDirectionExtra(iix._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddCircularArc_60, XtraOutputs      ),
                HtmlElement
            ] { case (i, incr: AddCircularArc_60, x) => (i, incr, x) } { (iix, sig) =>
                renderElemTyped[AddCircularArc_60](
                    iix._1,
                    I18N.add_element.AddCircularArc_60,
                    iix._2,
                    sig,
                    isProperty      = false,
                    extra            = relativeDirectionExtra(iix._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd))
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionShapeChange, XtraOutputs  ),
                HtmlElement
            ] { case (i, incr: AddSectionShapeChange, x) => (i, incr, x) } { (iix, sig) =>
                renderElemTyped[AddSectionShapeChange](
                    iix._1,
                    I18N.add_element.AddSectionShapeChange,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddFlowResistance, XtraOutputs      ),
                HtmlElement
            ] { case (i, incr: AddFlowResistance, x) => (i, incr, x) } { (iix, sig) =>
                renderElemTyped[AddFlowResistance](
                    iix._1,
                    I18N.add_element.AddFlowResistance,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, AddPressureDiff, XtraOutputs), HtmlElement] {
                case (i, incr: AddPressureDiff, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddPressureDiff](
                    iix._1,
                    I18N.add_element.AddPressureDiff,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .toSignal

    import defaultable_15544.incr_descr_en15544.given

    // simple instance of tree (for testing only)
    lazy val tagTreeMenu = TagTreeMenu(
        shortcut_start_new_pipe,
        shortcut_add_new_connector,
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

    lazy val shortcut_add_new_connector =
        TagTreeMenu.Shortcut  (
            txt   = I18N.set_prop.shortcuts.add_new_connector,
            elems = (
                SetRoughness(1.mm),
                SetInnerShape(Circle(180.mm)),
                AddSectionSlopped("connecteur", 6.cm)
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
            TagTreeMenu.Modal[FlowOnlyPipeDescr_15544](
                txt = I18N_UI.catalog.flow_resistance_presets,
                modalContent = (onSelect) =>
                    FlowResistanceCatalogSelectComponent(
                        entriesSignal = flowResistancePresetsSignal,
                        onSelect      = onSelect.contramap[FlowResistanceCatalogEntry](e =>
                            AddFlowResistance(e.name, e.zeta, e.cross_section)
                        )
                    ).node
            ),
            TagTreeMenu.Leaf                   (
                I18N.add_element.AddFlowResistance_wire_mesh_screen,
                AddFlowResistance(
                    s"${I18N.add_element.AddFlowResistance_wire_mesh_screen} (ζ = 0.61)",
                    0.61.unitless,
                    NoneOfEither
                )
            ),
            TagTreeMenu.Leaf                   (
                I18N.add_element.AddFlowResistance_solid_steel_grate,
                AddFlowResistance(
                    s"${I18N.add_element.AddFlowResistance_solid_steel_grate} (ζ = 1.23)",
                    1.23.unitless,
                    NoneOfEither
                )
            ),
            TagTreeMenu.Leaf                   (
                I18N.add_element.AddFlowResistance_butterfly_damper,
                AddFlowResistance(
                    s"${I18N.add_element.AddFlowResistance_butterfly_damper} (ζ = 0.25)",
                    0.25.unitless,
                    NoneOfEither
                )
            ),
            TagTreeMenu.Leaf[AddFlowResistance]("ζ spécifique")
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
            TagTreeMenu.Leaf[AddSharpeAngle_0_to_180],
            TagTreeMenu.Leaf[AddCircularArc_60]
        )
    )

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
            TagTreeMenu.Leaf[SetInnerShape]
        )
    )

end FluePipePanel
