/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot

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

    import afpma.firecalc.ui.formgen.{Defaultable as D}
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
        postFireboxSlots_var.update(slots => slots :+ slot)
        structureVersion.update(_ + 1)

    private def removeSlot(idx: Int): Unit =
        postFireboxSlots_var.update(slots => slots.zipWithIndex.collect { case (s, i) if i != idx => s })
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
        span(cls := "text-sm font-medium text-base-content/60", "Post-firebox pipes"),
        div(cls := "flex-1"),
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
        ),
        button(
            cls := "btn btn-xs btn-outline btn-accent",
            lucide.plus,
            span(cls := "ml-1", I18N.panels.chimney_pipe),
            onClick --> { _ => addSlot(PostFireboxPipeDescrSlot.ChimneySlot(defaultThermalContent)) }
        )
    )

    // ── Topology validation warning ──────────────────────────────

    private lazy val topologyWarning: Signal[Option[HtmlElement]] =
        topologyValidation_sig.map:
            case Validated.Valid(_) => None
            case Validated.Invalid(errors) =>
                Some(div(
                    cls := "alert alert-warning text-xs mx-4 my-1",
                    lucide.`triangle-alert`(),
                    span(errors.toList.map(_.toString).mkString("; "))
                ))

    // ── Per-slot controls (remove, move up/down) ─────────────────

    private def slotControls(idx: Int, totalSlots: Int): HtmlElement =
        div(
            cls := "flex items-center gap-1 mr-2",
            if idx > 0 then
                button(
                    cls := "btn btn-ghost btn-xs btn-circle",
                    lucide.`chevron-up`,
                    onClick --> { _ => moveSlot(idx, idx - 1) }
                )
            else emptyNode,
            if idx < totalSlots - 1 then
                button(
                    cls := "btn btn-ghost btn-xs btn-circle",
                    lucide.`chevron-down`,
                    onClick --> { _ => moveSlot(idx, idx + 1) }
                )
            else emptyNode,
            button(
                cls := "btn btn-ghost btn-xs btn-circle text-error",
                lucide.`trash-2`(stroke_width = 1.5),
                onClick --> { _ => removeSlot(idx) }
            )
        )

    // ── Build panels from current slot snapshot ──────────────────

    /** Build a stable panel list from the current slots.
      * Called once at init and after each structural mutation.
      */
    private def buildPanels(slots: Seq[PostFireboxPipeDescrSlot]): HtmlElement =
        div(
            slots.zipWithIndex.map: (slot, idx) =>
                val panel = DynamicPipeSlotPanel.forSlot(idx, slot)
                div(
                    cls := "relative",
                    div(
                        cls := "absolute top-1 right-10 z-50",
                        slotControls(idx, slots.size)
                    ),
                    panel.node
                )
        )

    // ── Main node ────────────────────────────────────────────────

    override lazy val node: HtmlElement = div(
        toolbar,
        child.maybe <-- topologyWarning,
        child <-- structureVersion.signal.map: _ =>
            buildPanels(postFireboxSlots_var.now())
    )

end PostFireboxPipePanels
