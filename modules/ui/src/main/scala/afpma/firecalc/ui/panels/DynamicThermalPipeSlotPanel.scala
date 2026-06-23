/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*

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
        case NoFluePipeT    => "pipe-type-no-flue"
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
                case Some(PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d)) => d
                case Some(PostFireboxPipeDescrSlot_V7.ConnectorSlot(d))   => d
                case Some(PostFireboxPipeDescrSlot_V7.ChimneySlot(d))     => d
                case _                                                    => Seq.empty
        )((slots, descr) =>
            slots.zipWithIndex.map { case (s, i) =>
                if i != slotIndex then s
                else
                    s match
                        case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(_) =>
                            PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(descr)
                        case PostFireboxPipeDescrSlot_V7.ConnectorSlot(_)   =>
                            PostFireboxPipeDescrSlot_V7.ConnectorSlot(descr)
                        case PostFireboxPipeDescrSlot_V7.ChimneySlot(_)     =>
                            PostFireboxPipeDescrSlot_V7.ChimneySlot(descr)
                        case other                                          => other // shouldn't happen
            }
        )

    override protected def externalInitialFrameSig: Signal[Option[PipeFrame]] =
        slotInitialFrameSig(slotIndex)

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
