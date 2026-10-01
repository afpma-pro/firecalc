/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.RemoteFireboxPending
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.models.localeVar
import afpma.firecalc.ui.remote.RemoteFireboxPrefetch.prefetchInFlightSig
import afpma.firecalc.ui.remote.RemoteFireboxPrefetch.prefetchedRemoteCachedAtSig
import afpma.firecalc.ui.remote.RemoteFireboxPrefetch.unavailableFireboxKindsSig
import afpma.firecalc.ui.remote.RemoteFireboxPrefetch.activeKindRefusalSig
import afpma.firecalc.ui.utils.FireboxKindLabel

import cats.syntax.show.*

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

/**
 * Honest status of the remote firebox calculation.
 *
 * Four notices. The first three are silent when no kind is configured
 * `Remote` (that is not a working Austrian configuration: no remote vars
 * means no calc backend is configured for the two kinds):
 *
 *  - '''Connecting''' — a remote fetch for the active kind is in flight and
 *    nothing matched in the cache; the sections show their carry-over or
 *    nothing, and this names what the UI is actually waiting for. Mutually
 *    exclusive with the cached notice for the active kind: a cache hit is
 *    served synchronously, so `Pending` only occurs on a miss.
 *  - '''Cached''' — results currently on screen were replayed from this
 *    device's cache, with the timestamp of the response that produced them.
 *    Cached numbers MUST NOT be presented as live.
 *  - '''Unavailable kinds''' — the kinds whose remote data is genuinely
 *    missing, named individually, plus the explicit reassurance that every
 *    other kind still calculates. Deliberately not a blocking dialog:
 *    blocking is what the per-kind assembly replaces.
 *  - '''Refused''' — the active kind is an Austrian kind the deployment
 *    resolved to the in-process engine, which the public product does not
 *    carry (ADR-0010): the banner names the kind and the missing calculator
 *    with the very wording the remote-only carriers refuse with, so the
 *    operator sees the deploy condition instead of a standards finding.
 *    Strictly separate from the unavailable notice: a network blip is never
 *    reported as a missing calculator, and a missing calculator is never
 *    reported as an outage (story 8).
 */
final case class RemoteCalcStatusBanner()(using Locale) extends Component:

    /**
     * Render a cache timestamp, mirroring `ProjectSelectorView.formatDate` so
     * the two places that show a stored-at time read alike.
     */
    private def formatSavedAt(savedAtMs: Double): String =
        val date = new scala.scalajs.js.Date(savedAtMs)
        s"${date.toLocaleDateString()} ${date.toLocaleTimeString()}"

    private lazy val connectingNotice: Signal[Option[HtmlElement]] =
        prefetchInFlightSig.map: inFlight =>
            Option.when(inFlight):
                div(
                    cls := "alert alert-info shadow-lg text-xs py-1 px-2",
                    span((RemoteFireboxPending: MCalc_Error).show)
                )

    private lazy val cachedNotice: Signal[Option[HtmlElement]] =
        prefetchedRemoteCachedAtSig.map:
            case None            => None
            case Some(savedAtMs) =>
                Some(
                    div(
                        cls := "alert alert-warning shadow-lg text-xs py-1 px-2 flex-col items-start gap-0",
                        span(cls := "font-semibold", I18N_UI.remote_calc.cached_banner_title),
                        span(I18N_UI.remote_calc.cached_banner_detail(formatSavedAt(savedAtMs)))
                    )
                )

    private lazy val unavailableNotice: Signal[Option[HtmlElement]] =
        unavailableFireboxKindsSig.map: kinds =>
            Option.when(kinds.nonEmpty):
                val names = kinds.toList.map(FireboxKindLabel(_)).sorted.mkString(", ")
                div(
                    cls := "alert alert-error shadow-lg text-xs py-1 px-2",
                    span(I18N_UI.remote_calc.kinds_unavailable_banner(names))
                )

    private lazy val refusedNotice: Signal[Option[HtmlElement]] =
        activeKindRefusalSig
            .combineWith(localeVar.signal)
            .map: (refused, _) =>
                refused.map: kind =>
                    // Localized at render time under this component's own `Locale`,
                    // through the SAME i18n key the remote-only carriers fold into
                    // their blocking verdict — browser and report path refuse alike
                    // (story 10). Depending on `localeVar` re-fires the notice on an
                    // EN↔FR switch: nothing is rendered from a string baked at
                    // resolution time.
                    div(
                        cls := "alert alert-error shadow-lg text-xs py-1 px-2",
                        span(I18N.builder_errors.no_calculator_configured(FireboxKindLabel(kind)))
                    )

    lazy val node: HtmlElement =
        div(
            cls := "flex flex-col items-end gap-1",
            child.maybe <-- connectingNotice,
            child.maybe <-- cachedNotice,
            child.maybe <-- unavailableNotice,
            child.maybe <-- refusedNotice
        )

end RemoteCalcStatusBanner
