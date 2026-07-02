/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AbsoluteDirection
import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_13384.*
import afpma.firecalc.dto.v7.AirIntakePosition

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.AirIntakePipeT
import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_13384
import afpma.firecalc.engine.standard.VNelMcalcErr

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.*

import cats.Show
import cats.data.*
import cats.syntax.all.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import _root_.coulomb.*
import _root_.coulomb.policy.standard.given
import afpma.laminar.form.daisyui.DaisyUIInputs
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

    override protected def initialDirectionSig: Signal[PipeInitialDirection] =
        engineStateVar
            .zoomLazy(_.air_intake_pipes.initialDir)((g, d) =>
                g.copy(air_intake_pipes = g.air_intake_pipes.copy(initialDir = d))
            )
            .signal

    override protected def renderWrapperElems: Boolean = true

    override protected def shapeAtPrefix(insertIdx: Int): PipeShape =
        import afpma.laminar.form.{Defaultable as D}
        import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_Module_13384
        import FlowOnlyAirIntakePipe_Module_13384.innerShapeAtPrefix
        import SetFlowOnlyPipeProp_13384.SetInnerShape
        import afpma.firecalc.ui.instances.FlowOnlyDefaultable_13384.given
        FlowOnlyAirIntakePipe_Module_13384.incremental
            .define(elems_v.now()*)
            .innerShapeAtPrefix(insertIdx)
            .getOrElse(summon[D[SetInnerShape]].default.shape)

    /** Override to use dynamic inner shape for SetInnerShape menu entry. */
    override lazy val prop_elements =
        import hastranslations.given
        import FlowOnlyDefaultable_13384.given
        import SetFlowOnlyPipeProp_13384.{SetInnerShape, SetMaterial, SetRoughness}
        TagTreeMenu.Group (
            txt  = I18N.set_prop._self,
            next = List(
                TagTreeMenu.Group                               (
                    txt     = I18N.set_prop._material_and_roughness,
                    next    = List(
                        TagTreeMenu.Leaf[SetMaterial],
                        TagTreeMenu.Leaf[SetRoughness]
                    )
                ),
                TagTreeMenu.LeafFn[SetFlowOnlyPipeProp_13384]   (
                    txt     = I18N.set_prop.SetInnerShape,
                    compute = (insertIdx: Int) => SetInnerShape(shapeAtPrefix(insertIdx))
                )
            )
        )

    private lazy val fixedWrapperElems: Seq[HtmlElement] =
        import v7.given
        import afpma.firecalc.ui.models.{airIntakePositionMode_var, airIntakeEffectivePosition_sig}

        val initialDirVar = engineStateVar.zoomLazy(_.air_intake_pipes.initialDir)((g, d) =>
            g.copy(air_intake_pipes = g.air_intake_pipes.copy(initialDir = d))
        )

        Seq[HtmlElement](
            renderFixedElem[PipeInitialDirection]          (
                title           = I18N.set_prop.PipeInitialDirection,
                v               = initialDirVar,
                isProperty      = true,
                propertyShow    = Some(summon[Show[PipeInitialDirection]])
            ),
            renderPositionRow                              (
                modeVar         = airIntakePositionMode_var,
                effectivePosSig = airIntakeEffectivePosition_sig
            )
        )

    override protected def renderV7WrapperElems(isFirstSlot: Boolean)(elems: Seq[HtmlElement]): Seq[HtmlElement] =
        if isFirstSlot then interleaveInsertSeparators(fixedWrapperElems ++ elems, startIdx = fixedWrapperElems.size)
        else interleaveInsertSeparators               (elems, startIdx                      = 0                     )

    /**
     * Mode-aware Position3D row (Auto/Manual) with an Initial/Final selector.
     * Auto: read-only displayed position (start in Initial, end/connection in Final).
     * Manual: editable position; Auto→Manual preserves the last displayed value.
     */
    private def renderPositionRow(
        modeVar        : Var[AirIntakePosition],
        effectivePosSig: Signal[(Position3D, Option[Position3D])]
    ): HtmlElement =
        import v7.given
        import afpma.firecalc.ui.models.airIntakeDisplayedPosition_sig

        // Single source of truth for both the dialog form and the compact row:
        // Initial modes show start offset, Final modes show end/connection point.
        val displayedPosSig = airIntakeDisplayedPosition_sig

        val titleSig = modeVar.signal.map {
            case AirIntakePosition.InitialAuto | AirIntakePosition.InitialManual(_) => I18N.set_prop.SetInitialPosition
            case AirIntakePosition.FinalAuto | AirIntakePosition.FinalManual(_)     => I18N.set_prop.SetFinalPosition
        }

        val initialTag = AirIntakePosition.InitialAuto.productPrefix
        val finalTag   = AirIntakePosition.FinalAuto.productPrefix

        // Initial/Final selector — keeps the stored position when switching phase.
        val selector = select(
            cls := "select select-bordered select-xs",
            value <-- modeVar.signal.map {
                case AirIntakePosition.InitialAuto | AirIntakePosition.InitialManual(_) => initialTag
                case AirIntakePosition.FinalAuto | AirIntakePosition.FinalManual(_)     => finalTag
            },
            option(value := initialTag, I18N.set_prop.SetInitialPosition),
            option(value := finalTag, I18N.set_prop.SetFinalPosition    ),
            onChange --> { e =>
                val targetTag = e.target.asInstanceOf[org.scalajs.dom.HTMLSelectElement].value
                modeVar.update {
                    case AirIntakePosition.InitialManual(p) if targetTag == finalTag => AirIntakePosition.FinalManual(p)
                    case AirIntakePosition.InitialAuto if targetTag == finalTag      => AirIntakePosition.FinalAuto
                    case AirIntakePosition.FinalManual(p) if targetTag == initialTag =>
                        AirIntakePosition.InitialManual(p)
                    case AirIntakePosition.FinalAuto if targetTag == initialTag      => AirIntakePosition.InitialAuto
                    case other                                                       => other
                }
            }
        )

        val dialog = ModeAwarePositionRow.modeAwarePositionDialog[AirIntakePosition](
            modeVar         = modeVar,
            isAuto          = {
                case AirIntakePosition.InitialAuto | AirIntakePosition.FinalAuto => true
                case _                                                           => false
            },
            toAuto          = {
                case AirIntakePosition.InitialManual(_) => AirIntakePosition.InitialAuto
                case AirIntakePosition.FinalManual(_)   => AirIntakePosition.FinalAuto
                case auto                               => auto
            },
            toManual        = (m, p) =>
                m match
                    case AirIntakePosition.InitialAuto | AirIntakePosition.InitialManual(_) =>
                        AirIntakePosition.InitialManual(p)
                    case AirIntakePosition.FinalAuto | AirIntakePosition.FinalManual(_)     =>
                        AirIntakePosition.FinalManual(p),
            displayedPosSig = displayedPosSig,
            titleSig        = titleSig,
            compactFormat   = (title, pos) => { import cats.syntax.show.*; s"$title: ${pos.show}" }
        )

        tr(
            td(
                div(
                    cls := "flex flex-row items-center gap-2",
                    div(
                        wrapLine("", dialog.compactNode, isProperty = true, widthClass = "w-auto"),
                        dialog.dialogNode
                    ).amend                                (
                        cls := pipeTypeCls,
                        dialog.binders
                    ),
                    DaisyUIInputs.FieldsetLegendWithContent(
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
