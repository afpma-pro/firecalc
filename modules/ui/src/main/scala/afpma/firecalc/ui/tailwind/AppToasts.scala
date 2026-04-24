/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.tailwind

import afpma.firecalc.engine.models.geometry.ChainEditDispatcher
import afpma.firecalc.engine.models.geometry.ChainEditDispatcher.Offer
import afpma.firecalc.engine.models.geometry.ChainEditDispatcher.PropagationStrategy
import afpma.firecalc.engine.models.geometry.ChainEditDispatcher.PropagationStrategy.*

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.models.lastDispatcherWrite_var
import afpma.firecalc.ui.models.postFireboxSlots_var
import afpma.firecalc.ui.models.rotateOffer_var

import com.raquo.laminar.api.L.*

/**
 * App-wide toast slot, rendered to the left of `Indicators` in the navbar.
 *
 * Currently hosts the post-firebox rotation offer. Designed as a stacking container so
 * additional transient notifications (save confirmations, validation warnings, etc.)
 * can be added here later without changing the layout.
 */
final case class AppToasts() extends Component:

    // TODO(i18n): hard-coded English strings; move to I18nData when the i18n sweep lands.
    // The toast is currently dormant (never surfaced — `rotateOffer_var` is always None)
    // because there is only one strategy. When more strategies are added, re-enable the
    // `rotateOffer_var.set(Some(Offer(...)))` call in `PostFireboxPipePanels.handleSlotSnapshot`
    // and extend this match with the new variants' labels.

    private def strategyLabel(s: PropagationStrategy): String = s match
        case RigidRotation => "Rigid rotation ★" // ★ = default indicator

    private def strategyButton(offer: Offer, strategy: PropagationStrategy): HtmlElement =
        val isActive = strategy == offer.appliedStrategy
        button(
            cls := (if isActive then "btn btn-xs btn-primary" else "btn btn-xs btn-ghost"),
            strategyLabel(strategy),
            onClick --> { _ =>
                if !isActive then
                    val rewritten = ChainEditDispatcher(offer.preEditSlots, offer.newSlots, offer.edit, strategy)
                    lastDispatcherWrite_var.set(Some(rewritten))
                    postFireboxSlots_var.set(rewritten)
                    rotateOffer_var.set(Some(offer.copy(appliedStrategy = strategy)))
            }
        )

    private lazy val rotationOfferToast: Signal[Option[HtmlElement]] =
        rotateOffer_var.signal.map:
            case None        => None
            case Some(offer) =>
                Some(
                    div(
                        cls := "alert alert-info shadow-lg text-xs py-1 px-2 flex-row items-center gap-2",
                        span("Propagation strategy:"),
                        div(
                            cls := "flex flex-row gap-1",
                            offer.alternatives.map(s => strategyButton(offer, s))
                        ),
                        button(
                            cls := "btn btn-xs btn-ghost",
                            "×", // ×
                            onClick --> { _ => rotateOffer_var.set(None) }
                        )
                    )
                )

    lazy val node =
        div(
            cls := "flex flex-col items-end gap-1",
            child.maybe <-- rotationOfferToast
        )

end AppToasts
