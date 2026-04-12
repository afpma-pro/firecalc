/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot

import afpma.firecalc.engine.ops.generic.TopologyError

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.*
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*

import cats.data.Validated

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

/** Dynamic container that renders pipe panels from the post-firebox slot vector.
  * Replaces the hardcoded FluePipePanel/ConnectorPipePanel/ChimneyPipePanel.
  *
  * IMPORTANT: Panels are created once from the initial slot vector and reused.
  * Dynamic add/remove/reorder rebuilds the panel list by setting a flag that
  * triggers a full re-render via `child <--`. This avoids Laminar anti-patterns
  * around creating elements inside Signal.map.
  */
final case class PostFireboxPipePanels()(using loc: Locale, du: DisplayUnits) extends Component:

    import afpma.laminar.form.{Defaultable as D}
    import afpma.firecalc.ui.instances.defaultable_15544.incr_descr_en15544.given
    import afpma.firecalc.ui.instances.defaultable_13384.incr_descr_en13384.given

    // ── Default content for new slots ────────────────────────────

    private def defaultFlueContent: Seq[FlowOnlyPipeDescr_15544_V3] =
        Seq(
            summon[D[SetFlowOnlyPipeProp_15544.SetMaterial]].default,
            summon[D[SetFlowOnlyPipeProp_15544.SetInnerShape]].default
        )

    private def defaultThermalContent: Seq[ThermalPipeDescr_13384_V3] =
        Seq(
            summon[D[SetThermalPipeProp_13384.SetPipeLocation]].default,
            summon[D[SetThermalPipeProp_13384.SetMaterial]].default,
            summon[D[SetThermalPipeProp_13384.SetInnerShape]].default,
            summon[D[SetThermalPipeProp_13384.SetLayer]].default
        )

    // ── Slot mutation helpers ────────────────────────────────────

    /** Trigger variable — incremented on every structural mutation to force panel rebuild. */
    private val structureVersion: Var[Int] = Var(0)

    private def addSlot(slot: PostFireboxPipeDescrSlot): Unit =
        postFireboxSlots_var.update: slots =>
            // Insert before the chimney (always the last slot)
            val insertIdx = (slots.size - 1).max(0)
            val (before, after) = slots.splitAt(insertIdx)
            before ++ Seq(slot) ++ after
        structureVersion.update(_ + 1)

    private def removeSlot(idx: Int): Unit =
        postFireboxSlots_var.update: slots =>
            if idx >= 0 && idx < slots.size && !slots(idx).isInstanceOf[PostFireboxPipeDescrSlot.ChimneySlot] then
                slots.zipWithIndex.collect { case (s, i) if i != idx => s }
            else slots
        structureVersion.update(_ + 1)

    private def moveSlot(fromIdx: Int, toIdx: Int): Unit =
        postFireboxSlots_var.update: slots =>
            if fromIdx < 0 || fromIdx >= slots.size || toIdx < 0 || toIdx >= slots.size || fromIdx == toIdx then slots
            else
                val buf = slots.toBuffer
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
            onClick --> { _ => addSlot(PostFireboxPipeDescrSlot.FlueSlot(defaultFlueContent)) }
        ),
        button(
            cls := "btn btn-xs btn-outline btn-secondary",
            lucide.plus,
            span(cls := "ml-1", I18N.panels.connector_pipe),
            onClick --> { _ => addSlot(PostFireboxPipeDescrSlot.ConnectorSlot(defaultThermalContent)) }
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
            case Validated.Valid(_) => None
            case Validated.Invalid(errors) =>
                Some(div(
                    cls := "alert alert-warning text-xs mx-4 my-1",
                    lucide.`triangle-alert`(),
                    span(errors.toList.map(topologyErrorLabel).mkString("; "))
                ))

    // ── Per-slot controls (remove, move up/down) ─────────────────

    private def slotControls(idx: Int, totalSlots: Int, slot: PostFireboxPipeDescrSlot): HtmlElement =
        val isChimney = slot match
            case _: PostFireboxPipeDescrSlot.ChimneySlot => true
            case _                                       => false
        val chimneyIdx  = totalSlots - 1
        val canMoveUp   = !isChimney && idx > 0
        val canMoveDown = !isChimney && idx < chimneyIdx - 1
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
            // delete — chimney is mandatory, cannot be removed
            div(
                cls := "flex-none flex items-center text-base-content justify-center w-6 h-6",
                when(!isChimney)(
                    cls := "hover:bg-secondary hover:text-secondary-content cursor-pointer",
                    lucide.`trash-2`(stroke_width = 0.5),
                    onClick --> { _ => removeSlot(idx) }
                )
            )
        )

    // ── Build panels from current slot snapshot ──────────────────

    /** Build a stable panel list from the current slots.
      * Called once at init and after each structural mutation.
      */
    private def buildPanels(slots: Seq[PostFireboxPipeDescrSlot]): HtmlElement =
        div(
            slots.zipWithIndex.flatMap: (slot, idx) =>
                val controls = slotControls(idx, slots.size, slot)
                val panel = DynamicPipeSlotPanel.forSlot(idx, slot, Some(controls))
                // Insert toolbar just before the last slot (chimney)
                if idx == slots.size - 1 && slots.size > 1 then
                    Seq(toolbar, panel.node)
                else
                    Seq(panel.node)
            ,
            // Fallback: show toolbar at the end if only one slot
            Option.when(slots.size <= 1)(toolbar)
        )

    // ── Main node ────────────────────────────────────────────────

    override lazy val node: HtmlElement = div(
        cls := "w-full",
        // Sync external slot changes (project load, file open) into structureVersion.
        // Maps to structural identity (type ordinals) so property edits within slots don't trigger.
        // .distinct prevents loops when internal mutations (addSlot/removeSlot) also change the signal.
        postFireboxSlots_var.signal.map(_.map(_.ordinal)).distinct.changes --> Observer[Seq[Int]]: _ =>
            structureVersion.update(_ + 1),
        child.maybe <-- topologyWarning,
        child <-- structureVersion.signal.map: _ =>
            buildPanels(postFireboxSlots_var.now())
    )

end PostFireboxPipePanels
