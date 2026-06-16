/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AbsoluteDirection
import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_13384.*
import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_13384.*
import afpma.firecalc.dto.v7.AirIntakePosition
import afpma.firecalc.ui.instances.V7FormInstances

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.standard.VNelMcalcErr
import afpma.firecalc.engine.models.AirIntakePipeT
import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_13384
import afpma.firecalc.engine.models.geometry.PositionTracker
import afpma.firecalc.units.Vec3

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.ui.*
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.models.*

import cats.data.*
import cats.syntax.all.*
import cats.Show

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import afpma.laminar.form.Form.as_HtmlElement
import afpma.laminar.form.daisyui.DaisyUIInputs
import org.scalajs.dom.HTMLDialogElement

import _root_.coulomb.*
import _root_.coulomb.policy.standard.given

import io.taig.babel.Locale

final case class FlowOnlyAirIntakePipePanel()(using Locale, DisplayUnits) extends PipePanel_13384_FlowOnly:
    private lazy val v7 = V7FormInstances()

    override protected def vizFieldsetIdPrefix              : String  = "airintake"
    override protected def ownsVizElement(id: VizElementId) : Boolean = id match
        case VizElementId.AirIntakePipeElement(_) => true
        case _                                    => false
    override protected def vizElementIndex(id: VizElementId): Int     = id match
        case VizElementId.AirIntakePipeElement(idx) => idx
        case _                                      => -1

    type Out = FlowOnlyAirIntakePipe_13384
    type PT  = AirIntakePipeT
    lazy val sectionType = AirIntakePipeT

    lazy val titleString = I18N.panels.air_intake

    lazy val vnel_signal = air_intake_vnel_signal
        .combineWith(air_intake_pipe_vnel2_signal)
        .map((v1, v2) => (v1: VNelMcalcErr[FlowOnlyAirIntakePipe_13384]) <* v2)

    lazy val elems_v: Var[Seq[FlowOnlyPipeDescr_13384]] = air_intake_incrdescr_var

    type PipeIdsMapping = afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_Module_13384.IdsMapping

    override lazy val pipeMappings_vnel_signal = air_intake_mappings_vnel_signal
    override lazy val pipeResult_vnel_signal   = results_en15544_air_intake_pipe

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings.getUnsafe(idIncr).map(_.unwrap.unwrap)

    lazy val air_intake_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.airIntake)

    override lazy val quadrionSubtotal_sig = air_intake_pipe_quadrions_sig

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

    override protected def initialDirectionSig: Signal[PipeInitialDirection] =
        engineStateVar
            .zoomLazy(_.air_intake_pipes.initialDir)((g, d) =>
                g.copy(air_intake_pipes = g.air_intake_pipes.copy(initialDir = d))
            )
            .signal

    override protected def renderWrapperElems: Boolean = true

    override protected def renderV7WrapperElems(isFirstSlot: Boolean)(elems: Seq[HtmlElement]): Seq[HtmlElement] =
        if isFirstSlot then
            import v7.given

            val initialDirVar = engineStateVar.zoomLazy(_.air_intake_pipes.initialDir)((g, d) =>
                g.copy(air_intake_pipes = g.air_intake_pipes.copy(initialDir = d))
            )

            val positionVar = engineStateVar.zoomLazy(_.air_intake_pipes.position)((g, p) =>
                g.copy(air_intake_pipes = g.air_intake_pipes.copy(position = p))
            )

            val wrapperPositionAutoCalc: Var[Position3D] => HtmlElement =
                val statusSig = AutoCalcHelper.mkStatusSig(
                    hasFrameSig = Signal.fromValue(true),
                    hasShapeSig = elems_v.signal.map { es =>
                        val extract = summon[AutoCalcHelper.ElemExtractors[FlowOnlyPipeDescr_13384]].asInnerShape
                        es.exists(extract.isDefinedAt)
                    }
                )
                val compute   = () =>
                    val posMode   = positionVar.now()
                    val fb        = firebox_var.now()
                    val box       = AutoCalcHelper.TargetBox(
                        centerX   = 0.0,
                        centerY   = 0.0,
                        halfWidth = fb.firebox_width.value / 2.0,
                        halfDepth = fb.firebox_depth.value / 2.0,
                        bottomZ   = -AIR_DISTRIB_HEIGHT_M,
                        height    = AIR_DISTRIB_HEIGHT_M
                    )
                    val initFrame =
                        AutoCalcHelper.wrapperDirectionToFrame(engineStateVar.now().air_intake_pipes.initialDir)
                    posMode match
                        // Initial: align pipe END to air distrib box, then back-calculate the new initial position
                        case AirIntakePosition.Initial(_) =>
                            val elems    = welems_var.now()
                            val frameOpt = AutoCalcHelper.replayFrame(elems, Int.MaxValue, Some(initFrame))
                            val shapeOpt = AutoCalcHelper.lastShapeBefore(elems, Int.MaxValue)
                            for
                                frame <- frameOpt
                                shape <- shapeOpt
                            yield
                                val (tx, ty, tz) = AutoCalcHelper.computeBottomAlignedPosition(frame, shape, box)
                                val targetPt  = Vec3(tx, ty, tz)
                                val framed    = engineStateVar.now().air_intake_pipes
                                // Run pipe from origin → get raw final point before translation
                                val rawResult = PositionTracker.computeFlowOnly13384(
                                    framed.descr,
                                    initialDirection = framed.initialDir,
                                    initialPosition  = Position3D.Origin,
                                    externalFrame    = None,
                                    startPoint       = Vec3(0, 0, 0),
                                    finalPoint       = None
                                )
                                // Offset = target - rawFinal. New initial = origin + offset = offset itself.
                                val offset    = targetPt - rawResult.finalPoint
                                Position3D(offset.x.meters, offset.y.meters, offset.z.meters)
                        // Final: replay all descriptors → final frame + last shape
                        case AirIntakePosition.Final(_)   =>
                            val elems    = welems_var.now()
                            val frameOpt = AutoCalcHelper.replayFrame(elems, Int.MaxValue, Some(initFrame))
                            val shapeOpt = AutoCalcHelper.lastShapeBefore(elems, Int.MaxValue)
                            for
                                frame <- frameOpt
                                shape <- shapeOpt
                            yield
                                val (x, y, z) = AutoCalcHelper.computeBottomAlignedPosition(frame, shape, box)
                                Position3D(x.m, y.m, z.m)
                AutoCalcHelper.autoCalcButton[Position3D](statusSig, compute)

            val fixedElems = Seq[HtmlElement](
                renderFixedElem[PipeInitialDirection]       (
                    title        = I18N.set_prop.PipeInitialDirection,
                    v            = initialDirVar,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[PipeInitialDirection]])
                ),
                renderPositionRow                           (positionVar, wrapperPositionAutoCalc)
            )
            interleaveInsertSeparators(fixedElems ++ elems, startIdx = fixedElems.size)
        else interleaveInsertSeparators(elems, startIdx = 0)

    /**
     * Render mode selector + Position3D form on the same row.
     * Title is reactive: "Position initiale" / "Position finale" based on mode.
     */
    private def renderPositionRow(
        positionVar            : Var[AirIntakePosition],
        wrapperPositionAutoCalc: Var[Position3D] => HtmlElement
    ): HtmlElement =
        import v7.given

        val pos3DVar = positionVar.zoomLazy {
            case AirIntakePosition.Initial(p) => p
            case AirIntakePosition.Final(p)   => p
        }((pos, newP) =>
            pos match
                case AirIntakePosition.Initial(_) => AirIntakePosition.Initial(newP)
                case AirIntakePosition.Final(_)   => AirIntakePosition.Final(newP)
        )

        val titleSig = positionVar.signal.map {
            case AirIntakePosition.Initial(_) => I18N.set_prop.SetInitialPosition
            case AirIntakePosition.Final(_)   => I18N.set_prop.SetFinalPosition
        }

        // Derive case identifiers from the enum itself via productPrefix
        val initialTag = AirIntakePosition.Initial(Position3D.Origin).productPrefix
        val finalTag   = AirIntakePosition.Final(Position3D.Origin).productPrefix

        val selector = select(
            cls := "select select-bordered select-xs",
            value <-- positionVar.signal.map(_.productPrefix),
            option(value := initialTag, I18N.set_prop.SetInitialPosition),
            option(value := finalTag, I18N.set_prop.SetFinalPosition    ),
            onChange --> { e =>
                val targetTag = e.target.asInstanceOf[org.scalajs.dom.HTMLSelectElement].value
                positionVar.update {
                    case AirIntakePosition.Initial(p) if targetTag == finalTag => AirIntakePosition.Final(p)
                    case AirIntakePosition.Final(p) if targetTag == initialTag => AirIntakePosition.Initial(p)
                    case other                                                 => other
                }
            }
        )

        val formNode = div(
            cls := "flex flex-row justify-start items-end gap-2",
            div(cls := "flex-none", pos3DVar.as_HtmlElement),
            div(
                cls := "flex items-center gap-2",
                children <-- positionVar.signal.map { _ =>
                    Seq(wrapperPositionAutoCalc(pos3DVar))
                }
            )
        )

        lazy val dialogNode: HtmlElement = dialogTag(
            cls := "modal",
            div (
                cls := "modal-box w-11/12 max-w-5xl",
                h3 (cls := "font-bold text-lg mb-4", text <-- titleSig),
                formNode,
                div(
                    cls := "modal-action",
                    button(
                        cls := "btn btn-sm btn-primary",
                        I18N_UI.buttons.close,
                        onClick --> { _ =>
                            dialogNode.ref.asInstanceOf[HTMLDialogElement].close()
                        }
                    )
                )
            ),
            form(method := "dialog", cls := "modal-backdrop", button("close"))
        )

        val compactNode = div(
            cls := "cursor-pointer py-1",
            span(
                cls := "underline decoration-dashed decoration-base-content/50 hover:decoration-base-content",
                text <-- titleSig.combineWith(pos3DVar.signal).map { case (title, pos) =>
                    s"$title: ${summon[Show[Position3D]].show(pos)}"
                }
            ),
            onClick --> { _ =>
                dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()
            }
        )

        tr(
            td(
                div(
                    cls := "flex flex-row items-center gap-2",
                    div(
                        wrapLine("", compactNode, isProperty = true, widthClass = "w-auto"),
                        dialogNode
                    ).amend                                    (cls := pipeTypeCls),
                    DaisyUIInputs.FieldsetLegendWithContent    (
                        None,
                        selector,
                        bgClass     = "bg-base-100",
                        borderClass = "border-none",
                        widthClass  = "w-auto"
                    )
                )
            )
        )

    // ── Direction-incompatible warning ─────────────────────────────

    override protected def warningVnelSig: Signal[ValidatedNel[PanelStatusHelper.PanelWarning, Unit]] =
        PanelStatusHelper.directionWarningSignal   (
            elemsSignal    = welems_var.signal,
            framesSignal   = frameBeforeByIdx,
            isIncompatible = (_, elem, frame) =>
                elem match
                    case (_, dc: AddDirectionChange) if dc.absDir.isDefined =>
                        val (az, el) = AbsoluteDirection.toAzimuthElevationDeg(dc.absDir.get)
                        val target = Vec3.fromAzimuthElevation(az, el)
                        !frame.isReachable(target, dc.angle.toUnit[Degree].value)
                    case _ => false
        )

end FlowOnlyAirIntakePipePanel
