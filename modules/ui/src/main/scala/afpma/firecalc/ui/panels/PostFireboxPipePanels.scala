/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.ops.generic.TopologyError

import afpma.firecalc.ui.*
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*

import cats.data.Validated

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

/**
 * Dynamic container that renders pipe panels from the post-firebox slot vector.
 * Replaces the hardcoded FluePipePanel/ConnectorPipePanel/ChimneyPipePanel.
 *
 * Layout zones:
 *   - **Flue region** (indices 0 to fixedZoneStart-1): user-managed slots (FlueSlot,
 *     interleaved ConnectorSlot). At least one FlueSlot must remain.
 *   - **Fixed zone** (last 2 slots): trailing ConnectorSlot + ChimneySlot.
 *     Always present, no delete/move controls.
 *   - **Toolbar**: between flue region and fixed zone.
 */
final case class PostFireboxPipePanels()(using loc: Locale, du: DisplayUnits) extends Component:

    import afpma.laminar.form.{Defaultable as D}
    import afpma.firecalc.ui.instances.defaultable_15544.incr_descr_en15544.given

    // ── Default content for new slots ────────────────────────────

    private def defaultFlueContent: Seq[FlowOnlyPipeDescr_15544_V3] =
        Seq(
            summon[D[SetFlowOnlyPipeProp_15544.SetMaterial]].default,
            summon[D[SetFlowOnlyPipeProp_15544.SetInnerShape]].default
        )

    // ── Slot normalization ──────────────────────────────────────

    /**
     * Ensure the slot vector always ends with `..., ConnectorSlot, ChimneySlot`.
     * Mirrors `FireCalcYAML_Loader.normalizePostFireboxSlots` at the UI level.
     */
    private def normalizeSlots(slots: Seq[PostFireboxPipeDescrSlot]): Seq[PostFireboxPipeDescrSlot] =
        if slots.size < 2 then slots // degenerate — let topology error surface
        else
            val chimney = slots.last
            chimney match
                case _: PostFireboxPipeDescrSlot.ChimneySlot =>
                    slots.init.lastOption match
                        case Some(_: PostFireboxPipeDescrSlot.ConnectorSlot) => slots // already normalized
                        case _                                               => slots.init :+ PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty) :+ chimney
                case _ => slots // no chimney at end — degenerate, let topology error surface

    // ── Slot mutation helpers ────────────────────────────────────

    /** Trigger variable — incremented on every structural mutation to force panel rebuild. */
    private val structureVersion: Var[Int] = Var(0)

    /** Insert a slot into the flue region (before the fixed trailing connector + chimney). */
    private def addSlotToFlueRegion(slot: PostFireboxPipeDescrSlot): Unit =
        postFireboxSlots_var.update: slots =>
            val normalized     = normalizeSlots(slots)
            val fixedZoneStart = (normalized.size - 2).max(0)
            val (flueRegion, fixedZone) = normalized.splitAt(fixedZoneStart)
            flueRegion ++ Seq(slot) ++ fixedZone
        structureVersion.update(_ + 1)

    private def removeSlot(idx: Int): Unit =
        postFireboxSlots_var.update: slots =>
            val normalized     = normalizeSlots(slots)
            val fixedZoneStart = (normalized.size - 2).max(0)
            // Guard: cannot remove slots in fixed zone (trailing connector + chimney)
            if idx >= fixedZoneStart then normalized
            else
                // Guard: must keep at least 1 FlueSlot in the flue region
                val flueRegion    = normalized.take(fixedZoneStart)
                val flueSlotCount = flueRegion.count(_.isInstanceOf[PostFireboxPipeDescrSlot.FlueSlot])
                val isFlueSlot    = normalized(idx).isInstanceOf[PostFireboxPipeDescrSlot.FlueSlot]
                if isFlueSlot && flueSlotCount <= 1 then normalized
                else normalized.zipWithIndex.collect { case (s, i) if i != idx => s }
        structureVersion.update(_ + 1)

    private def moveSlot(fromIdx: Int, toIdx: Int): Unit =
        postFireboxSlots_var.update: slots =>
            val normalized     = normalizeSlots(slots)
            val fixedZoneStart = (normalized.size - 2).max(0)
            // Guard: both indices must be within the flue region
            if fromIdx < 0 || fromIdx >= fixedZoneStart ||
                toIdx < 0 || toIdx >= fixedZoneStart ||
                fromIdx == toIdx
            then normalized
            else
                val buf  = normalized.toBuffer
                val elem = buf.remove(fromIdx)
                buf.insert(toIdx, elem)
                buf.toSeq
        structureVersion.update(_ + 1)

    // ── Toolbar ──────────────────────────────────────────────────

    private lazy val toolbar: HtmlElement = div(
        cls := "flex items-center gap-2 px-4 py-2",
        button(
            cls := "btn btn-xs btn-outline btn-primary",
            lucide.plus,
            span(cls := "ml-1", I18N.panels.channel_pipe),
            onClick --> { _ => addSlotToFlueRegion(PostFireboxPipeDescrSlot.FlueSlot(defaultFlueContent)) }
        ),
        button(
            cls := "btn btn-xs btn-outline btn-secondary",
            lucide.plus,
            span(cls := "ml-1", I18N.panels.connector_pipe),
            onClick --> { _ => addSlotToFlueRegion(PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty)) }
        )
    )

    // ── Topology validation warning ──────────────────────────────

    private def topologyErrorLabel(e: TopologyError): String = e match
        case TopologyError.MissingChimney              => I18N.topology_errors.missing_chimney
        case TopologyError.ChimneyNotLast              => I18N.topology_errors.chimney_not_last
        case TopologyError.FluePipeAfterConnector      => I18N.topology_errors.flue_pipe_after_connector
        case TopologyError.MultipleConnectorsAfterFlue => I18N.topology_errors.multiple_connectors_after_flue
        case TopologyError.MissingConnectorAfterFlue   => I18N.topology_errors.missing_connector_after_flue

    private lazy val topologyWarning: Signal[Option[HtmlElement]] =
        topologyValidation_sig.map:
            case Validated.Valid(_)        => None
            case Validated.Invalid(errors) =>
                Some(
                    div(
                        cls := "alert alert-warning text-xs mx-4 my-1",
                        lucide.`triangle-alert`(),
                        span(errors.toList.map(topologyErrorLabel).mkString("; "))
                    )
                )

    // ── Per-slot controls (remove, move up/down) ─────────────────

    private def slotControls(idx: Int, totalSlots: Int, slot: PostFireboxPipeDescrSlot): HtmlElement =
        val fixedZoneStart = (totalSlots - 2).max(0)
        val isInFlueRegion = idx < fixedZoneStart

        val canMoveUp   = isInFlueRegion && idx > 0
        val canMoveDown = isInFlueRegion && idx < fixedZoneStart - 1

        val canDelete =
            if !isInFlueRegion then false
            else
                slot match
                    case _: PostFireboxPipeDescrSlot.FlueSlot =>
                        val flueRegion = postFireboxSlots_var.now().take(fixedZoneStart)
                        flueRegion.count(_.isInstanceOf[PostFireboxPipeDescrSlot.FlueSlot]) > 1
                    case _ => true // interleaved connectors can always be deleted

        div(
            cls := "flex-none flex items-center",
            // move up
            div(
                cls := "flex-none flex items-center text-base-content hover:bg-secondary hover:text-secondary-content justify-center w-6 h-6",
                when(canMoveUp)(
                    cls := "hover:text-base-content cursor-pointer",
                    lucide.`square-chevron-up`(stroke_width = 0.5),
                    onClick --> { _ => moveSlot(idx, idx - 1) }
                )
            ),
            // move down
            div(
                cls := "flex-none flex items-center text-base-content hover:bg-secondary hover:text-secondary-content justify-center w-6 h-6",
                when(canMoveDown)(
                    cls := "hover:text-base-content cursor-pointer",
                    lucide.`square-chevron-down`(stroke_width = 0.5),
                    onClick --> { _ => moveSlot(idx, idx + 1) }
                )
            ),
            // delete — only in flue region, and must keep at least one FlueSlot
            div(
                cls := "flex-none flex items-center text-base-content justify-center w-6 h-6",
                when(canDelete)(
                    cls := "hover:bg-secondary hover:text-secondary-content cursor-pointer",
                    lucide.`trash-2`(stroke_width = 0.5),
                    onClick --> { _ => removeSlot(idx) }
                )
            )
        )

    // ── Build panels from current slot snapshot ──────────────────

    /**
     * Build a stable panel list from the current slots.
     * Called once at init and after each structural mutation.
     *
     * Layout: [flue region panels] [toolbar] [trailing connector panel] [chimney panel]
     */
    private def buildPanels(slots: Seq[PostFireboxPipeDescrSlot]): HtmlElement =
        val normalized     = normalizeSlots(slots)
        val fixedZoneStart = (normalized.size - 2).max(0)
        div(
            normalized.zipWithIndex.flatMap: (slot, idx) =>
                val controls =
                    if idx >= fixedZoneStart then None // no controls for trailing connector + chimney
                    else Some(slotControls(idx, normalized.size, slot))
                val panel = DynamicPipeSlotPanel.forSlot(idx, slot, controls)
                // Insert toolbar between flue region and fixed zone
                if idx == fixedZoneStart then Seq(toolbar, panel.node)
                else Seq                         (panel.node         )
        )

    // ── Main node ────────────────────────────────────────────────

    override lazy val node: HtmlElement = div(
        cls := "w-full",
        // Sync external slot changes (project load, file open) into structureVersion.
        // Maps to structural identity (type ordinals) so property edits within slots don't trigger.
        // .distinct prevents loops when internal mutations (addSlot/removeSlot) also change the signal.
        // Also normalizes the slot vector to ensure trailing connector + chimney are always present.
        postFireboxSlots_var.signal.map(_.map(_.ordinal)).distinct.changes --> Observer[Seq[Int]]: _ =>
            val current    = postFireboxSlots_var.now()
            val normalized = normalizeSlots(current)
            if normalized != current then postFireboxSlots_var.set(normalized)
            structureVersion.update(_ + 1)
        ,
        child.maybe <-- topologyWarning,
        child <-- structureVersion.signal.map: _ =>
            buildPanels(postFireboxSlots_var.now())
    )

end PostFireboxPipePanels
