/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.*
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*

import cats.data.Validated

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

/** Dynamic container that renders N pipe panels from the post-firebox slot vector.
  * Replaces the hardcoded FluePipePanel/ConnectorPipePanel/ChimneyPipePanel.
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

    private def addSlot(slot: PostFireboxPipeDescrSlot): Unit =
        postFireboxSlots_var.update(slots => slots :+ slot)

    private def removeSlot(idx: Int): Unit =
        postFireboxSlots_var.update(slots => slots.zipWithIndex.collect { case (s, i) if i != idx => s })

    private def moveSlot(fromIdx: Int, toIdx: Int): Unit =
        postFireboxSlots_var.update: slots =>
            if fromIdx < 0 || fromIdx >= slots.size || toIdx < 0 || toIdx >= slots.size || fromIdx == toIdx then slots
            else
                val buf = slots.toBuffer
                val elem = buf.remove(fromIdx)
                buf.insert(toIdx, elem)
                buf.toSeq

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
            // Move up
            if idx > 0 then
                button(
                    cls := "btn btn-ghost btn-xs btn-circle",
                    lucide.`chevron-up`,
                    onClick --> { _ => moveSlot(idx, idx - 1) }
                )
            else emptyNode,
            // Move down
            if idx < totalSlots - 1 then
                button(
                    cls := "btn btn-ghost btn-xs btn-circle",
                    lucide.`chevron-down`,
                    onClick --> { _ => moveSlot(idx, idx + 1) }
                )
            else emptyNode,
            // Remove
            button(
                cls := "btn btn-ghost btn-xs btn-circle text-error",
                lucide.`trash-2`(stroke_width = 1.5),
                onClick --> { _ => removeSlot(idx) }
            )
        )

    // ── Render all slot panels ───────────────────────────────────

    private lazy val panelsSig: Signal[Seq[HtmlElement]] =
        postFireboxSlots_var.signal.map: slots =>
            slots.zipWithIndex.map: (slot, idx) =>
                val panel = DynamicPipeSlotPanel.forSlot(idx, slot)
                div(
                    cls := "relative",
                    // Slot controls overlay (top-right of accordion panel)
                    div(
                        cls := "absolute top-1 right-10 z-50",
                        slotControls(idx, slots.size)
                    ),
                    panel.node
                )

    // ── Main node ────────────────────────────────────────────────

    override lazy val node: HtmlElement = div(
        toolbar,
        child.maybe <-- topologyWarning,
        div(children <-- panelsSig)
    )

end PostFireboxPipePanels
