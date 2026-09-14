/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import org.scalajs.dom

/**
 * Minimal facade over the browser's connectivity signals, for the firebox-calc
 * pre-fetch's fail-fast path.
 *
 * Mirrors the existing idiom in
 * [[PaymentsBackendApiConnectivity.isNavigatorOnline]] (`dom.window.navigator.onLine`)
 * and adds the `online` event subscription the pre-fetch needs so returning
 * connectivity retries automatically instead of requiring a reload.
 *
 * '''`navigator.onLine` is advisory.''' `true` means only that a network
 * interface exists — it does NOT mean the firebox-calc server is reachable
 * (captive portal, DNS failure, server down all report `true`). It is therefore
 * used in exactly one direction: a `false` reading skips a retry ladder that
 * cannot possibly succeed, and a `true` reading concludes nothing and must go
 * through the normal ladder. There is deliberately no "network is fine"
 * predicate here to misuse.
 */
object NetworkStatus:

    /**
     * Whether the browser reports being offline.
     *
     * Phrased negatively on purpose: this is the only direction the reading is
     * trustworthy in. Defensive against a missing/patched `navigator` (test
     * environments, embedded webviews): anything other than an explicit
     * `onLine == false` is treated as "not known to be offline", which routes to
     * the normal retry ladder.
     */
    def isReportedOffline: Boolean =
        try dom.window.navigator.onLine == false
        catch case _: Throwable => false

    /**
     * Register `handler` to run whenever the browser regains connectivity.
     *
     * Returns a de-registration thunk so the caller owns the listener's
     * lifetime; the pre-fetch registers exactly once per module load.
     */
    def onOnline(handler: () => Unit): () => Unit =
        val listener: scala.scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => handler()
        dom.window.addEventListener("online", listener)
        () => dom.window.removeEventListener("online", listener)

end NetworkStatus
