/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.*

import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.utils.{combineWithDistinct, flatMapVNelE}

import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

/** Thermal 13384 slot panel (connector, chimney, thermal flue). */
final case class DynamicThermalPipeSlotPanel(
    slotIndex           : Int,
    pipeTypeVal         : PipeType,
    title               : String,
    slotControlsNode    : Option[HtmlElement]            = None,
    headIdx             : Option[Int]                    = None,
    isLastInHeadRegion  : Boolean                        = false,
    headRegionLengthsSig: Signal[Option[Vector[Double]]] = Signal.fromValue(None),
    lZMinSig            : Signal[Option[Double]]         = Signal.fromValue(None)
)                                           (using Locale, DisplayUnits)
    extends PipePanel_13384_Thermal:

    override protected def vizFieldsetIdPrefix : String              = s"slot-$slotIndex"
    override protected def accordionTitlePrefix: Option[HtmlElement] = slotControlsNode
    override protected lazy val pipeTypeCls    : String              = pipeTypeVal match
        case FluePipeT      => "pipe-type-flue"
        case ConnectorPipeT => "pipe-type-connector"
        case ChimneyPipeT   => "pipe-type-chimney"
        case _              => s"pipe-type-slot-$slotIndex"

    override protected def ownsVizElement(id: VizElementId): Boolean = id match
        case VizElementId.PostFireboxSlotElement(si, _) => si == slotIndex
        // backward compat for existing viz element IDs
        case VizElementId.ConnectorPipeElement(_)       => pipeTypeVal == ConnectorPipeT
        case VizElementId.ChimneyPipeElement(_)         => pipeTypeVal == ChimneyPipeT
        case _                                          => false

    override protected def vizElementIndex(id: VizElementId): Int = id match
        case VizElementId.PostFireboxSlotElement(_, ei) => ei
        case VizElementId.ConnectorPipeElement(idx)     => idx
        case VizElementId.ChimneyPipeElement(idx)       => idx
        case _                                          => -1

    type Out = Any // type-erased
    // `sectionType` is only consumed via `==` in the base class (see
    // `PipePanel.keepGlobalErrorsOrErrorsSpecificToSectionTyp`), so a singleton
    // path-dependent type (`pipeTypeVal.type`) would buy us nothing and force an
    // unchecked cast. Widen to `PipeType` — equality is all we need.
    type PT  = PipeType
    lazy val sectionType: PT = pipeTypeVal

    lazy val titleString: String =
        headIdx match
            case Some(hi) => s"$title #${hi + 1}"
            case None     => title

    override protected def titleNodeOpt: Option[HtmlElement] =
        headIdx.map: hi =>
            span(
                title,
                " ",
                span(cls := "text-base-content/40", s"#${hi + 1}")
            )

    // ── Head-region length summary in titleXtraSig ────────────────

    override protected lazy val titleXtraSig: Signal[Option[HtmlElement]] =
        headIdx match
            case None     =>
                // Outside head region — fall back to base (status icon only)
                statusIcon.map(n => Some(div(n)))
            case Some(hi) =>
                statusIcon
                    .combineWithDistinct(headRegionLengthsSig, lZMinSig)
                    .map: (icon, lengthsOpt, lzMinOpt) =>
                        val summaryOpt = DynamicPipeSlotPanel.lengthSummaryFromIndex(
                            headIdx                    = hi,
                            isLast                     = isLastInHeadRegion,
                            lengths                    = lengthsOpt,
                            lZMin                      = lzMinOpt,
                            fmtLength                  = v => f"$v%.2f m",
                            fmtChanLength              = I18N.panels.channel_pipe_length.apply,
                            fmtChanLengthWithMin       = I18N.panels.channel_pipe_length_with_min.apply,
                            fmtChanLengthWithCum       = I18N.panels.channel_pipe_length_with_cum.apply,
                            fmtChanLengthWithCumAndMin = I18N.panels.channel_pipe_length_with_cum_and_min.apply
                        )
                        Some(
                            div(
                                cls := "flex items-center",
                                icon,
                                summaryOpt.map(s => span(cls := "ml-2 text-xs font-normal", s)).getOrElse(emptyNode)
                            )
                        )

    // ── Slot-indexed wiring ──────────────────────────────────────

    override protected def isSlotZero: Boolean = slotIndex == 0

    lazy val elems_v: Var[Seq[ThermalPipeDescr_13384]] =
        postFireboxSlots_var.zoomLazy(slots =>
            slots.lift(slotIndex) match
                case Some(PostFireboxPipeDescrSlot.ThermalFlueSlot(d)) => d
                case Some(PostFireboxPipeDescrSlot.ConnectorSlot(d))   => d
                case Some(PostFireboxPipeDescrSlot.ChimneySlot(d))     => d
                case _                                                 => Seq.empty
        )((slots, descr) =>
            slots.zipWithIndex.map { case (s, i) =>
                if i != slotIndex then s
                else
                    s match
                        case PostFireboxPipeDescrSlot.ThermalFlueSlot(_) =>
                            PostFireboxPipeDescrSlot.ThermalFlueSlot(descr)
                        case PostFireboxPipeDescrSlot.ConnectorSlot(_)   => PostFireboxPipeDescrSlot.ConnectorSlot(descr)
                        case PostFireboxPipeDescrSlot.ChimneySlot(_)     => PostFireboxPipeDescrSlot.ChimneySlot(descr)
                        case other                                       => other // shouldn't happen
            }
        )

    override protected def externalInitialFrameSig: Signal[Option[PipeFrame]] =
        slotInitialFrameSig(slotIndex)

    // ── Auto-calc: firebox-boundary for Connector-first head chains (plan U2) ──

    import afpma.firecalc.dto.all.SetThermalPipeProp_13384.*

    private def mkOnInitialDirectionCommit_thermal(
        elemIdx: Int
    ): Option[(AzimuthDirection, InclinationDirection, AzimuthDirection, InclinationDirection) => Unit] =
        // Direction edits are detected by the PostFireboxPipePanels observer via
        // ChainEditDispatcher.detectEdit. No explicit offer construction needed here.
        None

    override protected def initialDirectionExtraFn(idx: Int): Var[SetInitialDirection] => HtmlElement =
        ev =>
            import com.raquo.laminar.api.L.*
            mkOnInitialDirectionCommit_thermal(idx) match
                case None           => span()
                case Some(onCommit) =>
                    var prevAz   = ev.now().azimuth
                    var prevIncl = ev.now().inclination
                    span(
                        ev.signal.changes --> { sid =>
                            val oldAz   = prevAz
                            val oldIncl = prevIncl
                            prevAz   = sid.azimuth
                            prevIncl = sid.inclination
                            onCommit(oldAz, oldIncl, sid.azimuth, sid.inclination)
                        }
                    )

    /**
     * Reactive: is this slot the first HEAD_REGION slot (index 0) AND is it a
     * `ConnectorSlot`? In that case the auto-calc button (normally attached to
     * the first FlueSlot) must migrate to this ConnectorSlot so Connector-first
     * chains still get firebox-boundary alignment.
     *
     * Physical applicability check (plan U2): the auto-calc algorithm is
     * `AutoCalcHelper.computeTopAlignedPosition`, which is purely geometric
     * (projects the pipe direction + cross-section onto a firebox-box face).
     * It has no dependency on EN 15544 flow-only vs EN 13384 thermal physics,
     * so wiring it to a ConnectorPipe is physically meaningful.
     *
     * Unified rule: "auto-calc visible iff this slot is the first HEAD_REGION
     * slot (index 0)" — type-agnostic. The sibling
     * `isFirstHeadSlotAndIsFlueSig` in the flow-only panel handles the Flue-first
     * case; this handles the Connector-first case. Delegates to the pure
     * predicate in the companion object for testability.
     */
    private lazy val isFirstHeadSlotAndIsConnectorSig: Signal[Boolean] =
        if pipeTypeVal != ConnectorPipeT then Signal.fromValue(false)
        else
            postFireboxSlots_var.signal.map                   (slots =>
                DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, slotIndex)
            )

    /**
     * Reactive: is this slot the first HEAD_REGION slot (index 0) AND is it a
     * `ThermalFlueSlot`? Mirrors the flow-only panel's
     * `isFirstHeadSlotAndIsFlueSig` for MCE / thermal-flue-first chains.
     * Delegates to the shared pure predicate (which now covers both `FlueSlot`
     * and `ThermalFlueSlot`) for testability.
     */
    private lazy val isFirstHeadSlotAndIsFlueSig: Signal[Boolean] =
        if pipeTypeVal != FluePipeT then Signal.fromValue(false                                                                   )
        else postFireboxSlots_var.signal.map             (slots => DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, slotIndex))

    private def connectorAutoCalcStatusSig(posIdx: Int): Signal[(Boolean, Option[String])] =
        AutoCalcHelper.mkStatusSig(
            hasFrameSig = frameBeforeForInitialPos(posIdx).map(_.isDefined),
            hasShapeSig = welems_var.signal.map(_.filter(_._1 < posIdx).exists: (_, e) =>
                summon[AutoCalcHelper.ElemExtractors[ThermalPipeDescr_13384]].asInnerShape.isDefinedAt(e))
        )

    private def frameBeforeForInitialPos(posIdx: Int): Signal[Option[PipeFrame]] =
        welems_var.signal.map: elems =>
            AutoCalcHelper.replayFrame(elems, posIdx)

    private def computeConnectorAutoPosition(posIdx: Int): Option[SetInitialPosition] =
        val elems    = welems_var.now()
        val frameOpt = AutoCalcHelper.replayFrame(elems, posIdx)
        val shapeOpt = AutoCalcHelper.lastShapeBefore(elems, posIdx)
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
                bottomZ   = 0.0,
                height    = fb.firebox_height.value
            )
            val (x, y, z) = AutoCalcHelper.computeTopAlignedPosition(frame, shape, box)
            SetInitialPosition(x.m, y.m, z.m)

    override protected def initialPositionExtraFn(idx: Int): Var[SetInitialPosition] => HtmlElement =
        ev =>
            div(
                child <-- isFirstHeadSlotAndIsConnectorSig.map:
                    case true  =>
                        AutoCalcHelper.autoCalcButton[SetInitialPosition](
                            connectorAutoCalcStatusSig(idx),
                            () => computeConnectorAutoPosition(idx)
                        )(ev)
                    case false => span(),
                child <-- isFirstHeadSlotAndIsFlueSig.map:
                    case true  =>
                        AutoCalcHelper.autoCalcButton[SetInitialPosition](
                            connectorAutoCalcStatusSig(idx),
                            () => computeConnectorAutoPosition(idx)
                        )(ev)
                    case false => span()
            )

    // ── IdsMapping: erased Int → Option[Int] ─────────────────────

    type PipeIdsMapping = Int => Option[Int]

    // Covariance carries this assignment — see the matching comment on the
    // flow-only panel's `pipeMappings_vnel_signal` above.
    override lazy val pipeMappings_vnel_signal: Signal[VNelMcalcErr[PipeIdsMapping]] =
        slotMappingFnSig(slotIndex)

    override lazy val pipeResult_vnel_signal: Signal[VNelMcalcErr[PipeResult]] =
        postFireboxPipeResults_sig.map: vnel =>
            vnel.andThen: results =>
                results.lift(slotIndex) match
                    case Some((_, pr)) => Validated.validNel(pr)
                    case None          => Validated.invalidNel(ChimneyPipeNotDefinedYet)

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings(idIncr)

    // ── Validation ───────────────────────────────────────────────

    private lazy val pressureSumCheck_sig: Signal[VNelMcalcErr[Any]] =
        pipeResult_vnel_signal.map(_.andThen(_.`ph-(pR+pu)`))

    private lazy val velocityCheck_sig: Signal[VNelMcalcErr[Any]] =
        results_en15544_strict_sig.flatMapVNelE: strict =>
            pipeTypeVal match
                case ConnectorPipeT => strict.primary.validateVelocitiesInConnectorPipe
                case ChimneyPipeT   => strict.primary.validateVelocitiesInChimneyPipe
                case _              => strict.primary.validateVelocitiesInFluePipe

    lazy val vnel_signal: Signal[ValidatedNel[MCalc_Error, Any]] =
        slotBuildResults_sig
            .combineWithDistinct(pipeResult_vnel_signal, pressureSumCheck_sig, velocityCheck_sig)
            .map: (results, pipeResultV, pressureV, velocityV) =>
                val buildV = results
                    .lift(slotIndex)
                    .map(_.pipe)
                    .getOrElse(
                        Validated.invalidNel(ChimneyPipeNotDefinedYet)
                    )
                buildV *> pipeResultV *> pressureV *> velocityV

    // ── Quadrion subtotal ────────────────────────────────────────

    override lazy val quadrionSubtotal_sig: Signal[Option[QuadrionSubtotal]] =
        pipeResult_vnel_signal.map: vnel =>
            vnel.toOption.map: pr =>
                QuadrionSubtotal   (
                    ph    = Some(pr.ph.value).filter(!_.isNaN),
                    pr    = Some(-1.0 * pr.pR.value).filter(!_.isNaN),
                    pu    = pr.pu.map(pu => (-1.0 * pu.value)).toOption.filter(!_.isNaN),
                    sigma = pr.`ph-(pR+pu)`.map(_.value).toOption.filter(!_.isNaN)
                )

end DynamicThermalPipeSlotPanel
