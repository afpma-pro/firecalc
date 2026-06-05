/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.FrameReplay
import afpma.firecalc.engine.standard.*

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.utils.{combineWithDistinct, flatMapVNelE}

import cats.Show
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import coulomb.policy.standard.given

import io.taig.babel.Locale

/** Flow-only 15544 slot panel (flue pipe in Strict mode). */
final case class DynamicFlowOnlyPipeSlotPanel(
    slotIndex           : Int,
    slotControlsNode    : Option[HtmlElement]            = None,
    headIdx             : Option[Int]                    = None,
    isLastInHeadRegion  : Boolean                        = false,
    headRegionLengthsSig: Signal[Option[Vector[Double]]] = Signal.fromValue(None),
    lZMinSig            : Signal[Option[Double]]         = Signal.fromValue(None)
)                                            (using Locale, DisplayUnits)
    extends PipePanel:

    override protected def vizFieldsetIdPrefix : String              = s"slot-$slotIndex"
    override protected lazy val pipeTypeCls    : String              = "pipe-type-flue"
    override protected def accordionTitlePrefix: Option[HtmlElement] = slotControlsNode

    override protected def ownsVizElement(id: VizElementId): Boolean = id match
        case VizElementId.PostFireboxSlotElement(si, _) => si == slotIndex
        case VizElementId.FluePipeElement(_)            => slotIndex == 0 // backward compat
        case _                                          => false

    override protected def vizElementIndex(id: VizElementId): Int = id match
        case VizElementId.PostFireboxSlotElement(_, ei) => ei
        case VizElementId.FluePipeElement(idx)          => idx
        case _                                          => -1

    import hastranslations.given

    private given flowOnlyHorizontalForm_15544: FlowOnlyHorizontalForm_15544 = FlowOnlyHorizontalForm_15544()
    import flowOnlyHorizontalForm_15544.given

    private given flowOnlyPropertyShow_15544: FlowOnlyPropertyShow_15544 = FlowOnlyPropertyShow_15544()
    import flowOnlyPropertyShow_15544.given

    type In  = FlowOnlyPipeDescr_15544
    type Out = Any // type-erased — we use slot-indexed results
    type PT  = FluePipeT
    lazy val sectionType = FluePipeT

    lazy val titleString: String =
        headIdx match
            case Some(hi) => s"${I18N.panels.channel_pipe} #${hi + 1}"
            case None     => I18N.panels.channel_pipe

    override protected def titleNodeOpt: Option[HtmlElement] =
        headIdx.map: hi =>
            span(
                I18N.panels.channel_pipe,
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

    /** Zoom into the slot's descriptor sequence within the slot vector. */
    lazy val elems_v: Var[Seq[FlowOnlyPipeDescr_15544]] =
        postFireboxSlots_var.zoomLazy(slots =>
            slots.lift(slotIndex) match
                case Some(PostFireboxPipeDescrSlot.FlueSlot(d)) => d
                case _                                          => Seq.empty
        )((slots, descr) =>
            slots.zipWithIndex.map { case (s, i) =>
                if i == slotIndex then PostFireboxPipeDescrSlot.FlueSlot(descr) else s
            }
        )

    // ── IdsMapping: erased Int → Option[Int] ─────────────────────

    type PipeIdsMapping = Int => Option[Int]

    // Covariance carries this assignment without a cast:
    //   - `Signal[+A]` is covariant,
    //   - `ValidatedNel[+E, +A]` (= `Validated[NonEmptyList[E], A]`) is covariant in E,
    //   - `IncrementalValidation_Error <: MCalc_Error`,
    // so `Signal[ValidatedNel[IncrementalValidation_Error, _]] <: Signal[VNelMcalcErr[_]]`.
    override lazy val pipeMappings_vnel_signal: Signal[VNelMcalcErr[PipeIdsMapping]] =
        slotMappingFnSig(slotIndex)

    override lazy val pipeResult_vnel_signal: Signal[VNelMcalcErr[PipeResult]] =
        postFireboxPipeResults_sig.map: vnel =>
            vnel.andThen: results =>
                results.lift(slotIndex) match
                    case Some((_, pr)) => Validated.validNel(pr)
                    case None          => Validated.invalidNel(FluePipeNotDefinedYet)

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings(idIncr)

    // ── Validation ───────────────────────────────────────────────

    private lazy val pressureSumCheck_sig: Signal[VNelMcalcErr[Any]] =
        pipeResult_vnel_signal.map(_.andThen(_.`ph-(pR+pu)`))

    private lazy val velocityCheck_sig: Signal[VNelMcalcErr[Any]] =
        results_en15544_strict_sig.flatMapVNelE(_.primary.validateVelocitiesInFluePipe)

    private lazy val shapeCheck_sig: Signal[VNelMcalcErr[Any]] =
        results_en15544_strict_sig.flatMapVNelE(_.validateFluePipeShape())

    private lazy val citedConstraintsCheck_sig: Signal[VNelMcalcErr[Any]] =
        results_en15544_strict_sig.flatMapVNelE(_.primary.validateCitedConstraints)

    lazy val vnel_signal: Signal[ValidatedNel[MCalc_Error, Any]] =
        slotBuildResults_sig
            .combineWithDistinct(
                pipeResult_vnel_signal,
                pressureSumCheck_sig,
                velocityCheck_sig,
                shapeCheck_sig,
                citedConstraintsCheck_sig
            )
            .map: (results, pipeResultV, pressureV, velocityV, shapeV, citedV) =>
                val buildV = results
                    .lift(slotIndex)
                    .map(_.pipe)
                    .getOrElse(
                        Validated.invalidNel(FluePipeNotDefinedYet)
                    )
                buildV *> pipeResultV *> pressureV *> velocityV *> shapeV *> citedV

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

    // ── Frame tracking (UI-side, same as FluePipePanel) ──────────

    import afpma.firecalc.engine.models.geometry.Vec3

    // ── Auto-calc: firebox exit position (first flue slot only) ──

    private given AutoCalcHelper.ElemExtractors[FlowOnlyPipeDescr_15544] = AutoCalcHelper.ElemExtractors(
        asInitialDirection   = { case SetInitialDirection(az, incl) => (az, incl) },
        asDirectionChange    = { case dc: AddDirectionChange => (dc.angle, dc.absDir) },
        asInnerShape         = { case sis: SetInnerShape => sis.shape },
        withDirChangeAbsDir  = (e, newAbsDir) =>
            e match
                case x: AddSharpeAngle_0_to_180 => x.copy(absDir = newAbsDir)
                case x: AddCircularArc_60       => x.copy(absDir = newAbsDir)
                case _ => e,
        withInitialDirection = (e, az, incl) =>
            e match
                case x: SetInitialDirection => x.copy(azimuth = az, inclination = incl)
                case _ => e
    )

    /**
     * Reactive check: is this slot the first HEAD_REGION slot (index 0) AND is it
     * a `FlueSlot`?
     *
     * The auto-calc button auto-aligns the pipe's start to the firebox boundary —
     * only the very first slot (index 0) actually touches the firebox, regardless
     * of pipe type. Subsequent slots inherit their start from the previous slot's
     * endpoint.
     *
     * The unified rule is: "auto-calc button is visible iff this slot is the first
     * HEAD_REGION slot" — implemented here for `FlueSlot` and in
     * `isFirstHeadSlotAndIsConnectorSig` (thermal panel) for `ConnectorSlot`.
     * Delegates to the pure predicate in the companion object for testability.
     */
    private lazy val isFirstHeadSlotAndIsFlueSig: Signal[Boolean] =
        postFireboxSlots_var.signal.map(slots => DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, slotIndex))

    private def autoCalcStatusSig(posIdx: Int): Signal[(Boolean, Option[String])] =
        AutoCalcHelper.mkStatusSig(
            hasFrameSig = frameBeforeByIdx.map(_.contains(posIdx)),
            hasShapeSig = welems_var.signal.map(_.filter(_._1 < posIdx).exists: (_, e) =>
                summon[AutoCalcHelper.ElemExtractors[FlowOnlyPipeDescr_15544]].asInnerShape.isDefinedAt(e))
        )

    private def computeAutoPosition(posIdx: Int): Option[SetInitialPosition] =
        val elems    = welems_var.now()
        val frameOpt = AutoCalcHelper.replayFrame(elems, posIdx)
        val shapeOpt = AutoCalcHelper.lastShapeBefore(elems, posIdx)
        for
            frame <- frameOpt
            shape <- shapeOpt
        yield
            val fb  = firebox_var.now()
            val box = AutoCalcHelper.fireboxTargetBox(
                fb.firebox_width.value,
                fb.firebox_depth.value,
                fb.firebox_height.value
            )
            val (x, y, z) = AutoCalcHelper.computeTopAlignedPosition(frame, shape, box)
            SetInitialPosition(x.m, y.m, z.m)

    private def autoCalcExtra(posIdx: Int): Var[SetInitialPosition] => HtmlElement =
        AutoCalcHelper.autoCalcButton(
            autoCalcStatusSig(posIdx),
            () => computeAutoPosition(posIdx)
        )

    private lazy val frameBeforeByIdx: Signal[Map[Int, PipeFrame]] =
        welems_var.signal
            .combineWithDistinct(slotInitialFrameSig(slotIndex))
            .map: (elems, externalFrame) =>
                FrameReplay.replayFrameMap(elems, externalFrame)
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
                                        // For pinned direction changes, show the STORED pin as-is.
                                        // Previously this called `applyBendForFinalDir(...)` which
                                        // silently projects to the closest reachable direction when
                                        // the pin is unreachable at the current (frame, angle) — that
                                        // projection then overwrites the badge display and hides what
                                        // the user actually pinned. The badge's isCompatibleSig already
                                        // marks unreachable pins with a warning; let users see their
                                        // pin instead of the projection.
                                        // When absDir is None (freshly inserted element), fall back
                                        // to the incoming frame direction — mirrors thermal panel.
                                        val dir = dc.absDir match
                                            case Some(fd) =>
                                                val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                                                Vec3.fromAzimuthElevation(azDeg, elDeg)
                                            case None     =>
                                                frameBefore.direction
                                        Some(idx -> dir)
                                    case _ : AddFlowOnlyPipeElement_15544 =>
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

    /**
     * Build the `onBadgeDirectionCommit` callback for direction-change elements at `elemIdx`.
     *
     * Direction edits are now detected by the PostFireboxPipePanels observer via
     * ChainEditDispatcher.detectEdit — no explicit offer construction is needed here.
     * Returns None so the badge commit becomes a no-op at the panel level; the observer
     * fires when the Var is written and handles the offer.
     */
    private def mkOnDirectionCommit(
        elemIdx: Int
    ): Option[(Option[AbsoluteDirection], Option[AbsoluteDirection]) => Unit] =
        None

    /**
     * Build a callback for SetInitialDirection elements at `elemIdx`.
     *
     * Direction edits are now detected by the PostFireboxPipePanels observer via
     * ChainEditDispatcher.detectEdit. Returns None; the observer handles the offer.
     */
    private def mkOnInitialDirectionCommit(
        elemIdx: Int
    ): Option[(AzimuthDirection, InclinationDirection, AzimuthDirection, InclinationDirection) => Unit] =
        None

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

    // ── Rendered elements (same splitMatchSeq as FluePipePanel) ──

    lazy val rendered_elems_sig: Signal[Seq[HtmlElement]] =
        welem_xtraoutput_sig
            .splitMatchSeq(_._1)
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetInnerShape, XtraOutputs), HtmlElement] {
                case (i, incr: SetInnerShape, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetInnerShape]  (
                    iix._1,
                    I18N.set_prop.SetInnerShape,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetInnerShape]])
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetRoughness, XtraOutputs), HtmlElement] {
                case (i, incr: SetRoughness, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetRoughness]  (
                    iix._1,
                    I18N.set_prop.SetRoughness,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetRoughness]])
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetMaterial, XtraOutputs), HtmlElement] {
                case (i, incr: SetMaterial, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetMaterial]  (
                    iix._1,
                    I18N.set_prop.SetMaterial,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetMaterial]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, SetNumberOfFlows, XtraOutputs       ),
                HtmlElement
            ] { case (i, incr: SetNumberOfFlows, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetNumberOfFlows]  (
                    iix._1,
                    I18N.set_prop.SetNumberOfFlows_fieldName,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetNumberOfFlows]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, SetInitialDirection, XtraOutputs    ),
                HtmlElement
            ] { case (i, incr: SetInitialDirection, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                val elemIdx = iix._1
                val extraFn  : Var[SetInitialDirection] => HtmlElement = ev =>
                    import com.raquo.laminar.api.L.*
                    mkOnInitialDirectionCommit(elemIdx) match
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
                renderElemTyped[SetInitialDirection](
                    elemIdx,
                    I18N.set_prop.SetInitialDirection,
                    iix._2,
                    sig,
                    isProperty   = true,
                    extra        = extraFn,
                    propertyShow = Some(summon[Show[SetInitialDirection]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, SetInitialPosition, XtraOutputs     ),
                HtmlElement
            ] { case (i, incr: SetInitialPosition, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                // Auto-calc button only on the first HEAD_REGION slot (index 0) when it's a Flue —
                // subsequent slots inherit their start position from the previous slot's endpoint.
                // If the head chain starts with a Connector instead, the sibling predicate
                // `isFirstHeadSlotAndIsConnectorSig` in the thermal panel takes over.
                val extraFn  : Var[SetInitialPosition] => HtmlElement = ev =>
                    div(
                        child <-- isFirstHeadSlotAndIsFlueSig.map:
                            case true  => autoCalcExtra(iix._1)(ev)
                            case false => span()
                    )
                renderElemTyped[SetInitialPosition](
                    iix._1,
                    I18N.set_prop.SetInitialPosition,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetInitialPosition]]),
                    extra        = extraFn
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionSlopped, XtraOutputs      ),
                HtmlElement
            ] { case (i, incr: AddSectionSlopped, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddSectionSlopped](
                    iix._1,
                    I18N.add_element.AddSectionSlopped,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs                  ),
                (Int, AddSectionSloppedForceManualElevationGain, XtraOutputs),
                HtmlElement
            ] { case (i, incr: AddSectionSloppedForceManualElevationGain, x) =>
                (i, incr, x)
            } { (_, _) => ??? }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionHorizontal, XtraOutputs   ),
                HtmlElement
            ] { case (i, incr: AddSectionHorizontal, x) =>
                (i, incr, x)
            } { (iix, sig) =>
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
            ] { case (i, incr: AddSectionVertical, x) =>
                (i, incr, x)
            } { (iix, sig) =>
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
            ] { case (i, incr: AddSharpeAngle_0_to_180, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddSharpeAngle_0_to_180]            (
                    iix._1,
                    I18N.add_element.AddSharpeAngle_0_to_180,
                    iix._2,
                    sig,
                    isProperty             = false,
                    extra                  = relativeDirectionExtra(iix._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar       = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd)),
                    onBadgeDirectionCommit = mkOnDirectionCommit(iix._1)
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, AddCircularArc_60, XtraOutputs), HtmlElement] {
                case (i, incr: AddCircularArc_60, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddCircularArc_60]            (
                    iix._1,
                    I18N.add_element.AddCircularArc_60,
                    iix._2,
                    sig,
                    isProperty             = false,
                    extra                  = relativeDirectionExtra(iix._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar       = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd)),
                    onBadgeDirectionCommit = mkOnDirectionCommit(iix._1)
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionShapeChange, XtraOutputs  ),
                HtmlElement
            ] { case (i, incr: AddSectionShapeChange, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddSectionShapeChange](
                    iix._1,
                    I18N.add_element.AddSectionShapeChange,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, AddFlowResistance, XtraOutputs), HtmlElement] {
                case (i, incr: AddFlowResistance, x) => (i, incr, x)
            } { (iix, sig) =>
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
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, SetFinalPosition, XtraOutputs       ),
                HtmlElement
            ] { case (i, incr: SetFinalPosition, x) =>
                (i, incr, x)
            } { (_, _) =>
                span()
            }
            .toSignal
            .map(renderV7WrapperElems(slotIndex == 0))

    // ── Tag tree menu (same as FluePipePanel) ────────────────────

    import defaultable_15544.incr_descr_en15544.given

    lazy val tagTreeMenu = TagTreeMenu(
        shortcut_quick_flue_section,
        shortcut_start_new_pipe,
        shortcut_add_new_connector,
        prop_elements,
        geom_elements
    )

    lazy val shortcut_quick_flue_section =
        import afpma.laminar.form.{Defaultable as D}
        import afpma.firecalc.engine.models.FluePipe_Module_15544.innerShapeAtPrefix
        TagTreeMenu.ShortcutFn[FlowOnlyPipeDescr_15544]    (
            txt     = I18N_UI.shortcuts.quick_flue_section,
            compute = (insertIdx: Int) => {
                val prevShape: PipeShape = FluePipe_Module_15544.incremental
                    .define(elems_v.now()*)
                    .innerShapeAtPrefix(insertIdx)
                    .getOrElse(summon[D[SetInnerShape]].default.shape)
                Seq(
                    summon[D[AddSharpeAngle_0_to_180]].default,
                    SetInnerShape(prevShape),
                    summon[D[AddSectionSlopped]].default
                )
            }
        )

    lazy val shortcut_start_new_pipe =
        import afpma.laminar.form.{Defaultable as D}
        TagTreeMenu.Shortcut  (
            txt   = I18N.set_prop.shortcuts.start_a_new_pipe,
            elems = (summon[D[SetMaterial]].default, summon[D[SetInnerShape]].default)
        )

    lazy val shortcut_add_new_connector =
        TagTreeMenu.Shortcut  (
            txt   = I18N.set_prop.shortcuts.add_new_connector,
            elems = (SetRoughness(1.mm), SetInnerShape(Circle(180.mm)), AddSectionSlopped("connecteur", 6.cm))
        )

    lazy val geom_elements = TagTreeMenu.Group(
        txt  = I18N.add_element._self,
        next = List(
            TagTreeMenu.Leaf[AddSectionSlopped],
            TagTreeMenu.Group (
                txt  = I18N.add_element.add_direction_change_element,
                next = List(TagTreeMenu.Leaf[AddSharpeAngle_0_to_180], TagTreeMenu.Leaf[AddCircularArc_60])
            ),
            TagTreeMenu.Group (
                txt  = I18N.set_prop.SetNumberOfFlows,
                next = List(
                    TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_NumberOfChannels, SetNumberOfFlows(2)),
                    TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_Join, SetNumberOfFlows(1)            )
                )
            ),
            TagTreeMenu.Group (
                txt  = I18N.add_element.AddFlowResistance,
                next = List(
                    TagTreeMenu.Modal[FlowOnlyPipeDescr_15544]         (
                        txt          = afpma.firecalc.ui.i18n.implicits.I18N_UI.catalog.flow_resistance_presets,
                        modalContent = (onSelect) =>
                            FlowResistanceCatalogSelectComponent(
                                entriesSignal = flowResistancePresetsSignal,
                                onSelect      = onSelect.contramap[FlowResistanceCatalogEntry](e =>
                                    AddFlowResistance(e.name, e.zeta, e.cross_section)
                                )
                            ).node
                    ),
                    TagTreeMenu.Leaf[AddFlowResistance]                ("ζ spécifique")
                )
            )
        )
    )

    lazy val prop_elements = TagTreeMenu.Group(
        txt  = I18N.set_prop._self,
        next = List(
            TagTreeMenu.Group (
                txt  = I18N.set_prop._material_and_roughness,
                next = List(TagTreeMenu.Leaf[SetMaterial], TagTreeMenu.Leaf[SetRoughness])
            ),
            TagTreeMenu.Leaf[SetInnerShape]
        )
    )

end DynamicFlowOnlyPipeSlotPanel
