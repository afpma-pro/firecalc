/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.remote.RemoteFireboxPrefetch.prefetchedRemoteCachedAtSig
import afpma.firecalc.ui.remote.RemoteFireboxPrefetch.unavailableFireboxKindsSig
import afpma.firecalc.ui.utils.FireboxKindLabel

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

/**
 * Honest status of the remote firebox calculation (Q10-d part B / Q11).
 *
 * Two mutually compatible notices, both silent in the default all-local
 * configuration:
 *
 *  - '''Cached''' — results currently on screen were replayed from this device's
 *    cache, with the timestamp of the response that produced them. Required by
 *    Q10-d: cached numbers MUST NOT be presented as live.
 *  - '''Unavailable kinds''' — the kinds whose remote data is genuinely missing,
 *    named individually, plus the explicit reassurance that every other kind
 *    still calculates (Q11). Deliberately not a blocking dialog: blocking is
 *    what the per-kind assembly replaces.
 */
final case class RemoteCalcStatusBanner()(using Locale) extends Component:

    /**
     * Render a cache timestamp, mirroring `ProjectSelectorView.formatDate` so
     * the two places that show a stored-at time read alike.
     */
    private def formatSavedAt(savedAtMs: Double): String =
        val date = new scala.scalajs.js.Date(savedAtMs)
        s"${date.toLocaleDateString()} ${date.toLocaleTimeString()}"

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

    lazy val node: HtmlElement =
        div(
            cls := "flex flex-col items-end gap-1",
            child.maybe <-- cachedNotice,
            child.maybe <-- unavailableNotice
        )

end RemoteCalcStatusBanner
