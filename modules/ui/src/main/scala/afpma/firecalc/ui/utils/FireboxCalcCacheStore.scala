/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.firebox_calc_api.WireFireboxKind

import io.circe.syntax.*

import org.scalajs.dom

/**
 * `localStorage` adapter for the firebox-calc response cache.
 *
 * Deliberately thin: every decision — key shape, fingerprinting, hit/miss
 * classification, size budget — lives in the pure [[FireboxCalcCache]] and is
 * unit-tested there. This object only performs the two side effects
 * (`getItem`/`setItem`) and swallows storage faults, so a full or disabled
 * `localStorage` degrades to "no cache" rather than breaking the pre-fetch.
 */
object FireboxCalcCacheStore:

    /**
     * Read the cached response for `(projectId, kind)` if — and only if — it was
     * computed from a request with the same fingerprint as `requestBody`.
     *
     * A miss is logged with its reason (absent vs. inputs changed) because
     * "cache silently ignored" is exactly the failure mode that would make a
     * stale-results bug invisible.
     */
    def read(
        projectId  : String,
        kind       : WireFireboxKind,
        requestBody: String
    ): Option[FireboxCalcCacheEntry] =
        val raw =
            try dom.window.localStorage.getItem(FireboxCalcCache.key(projectId, kind))
            catch case _: Throwable => null
        FireboxCalcCache.read(raw, projectId, kind, requestBody) match
            case Right(entry) => Some(entry)
            case Left(miss)   =>
                miss match
                    case FireboxCalcCache.Miss.NoEntry                             => ()
                    case FireboxCalcCache.Miss.FingerprintMismatch(stored, expect) =>
                        dom.console.log(
                            s"[S5.4] firebox-calc cache miss for ${kind.wire}: inputs changed (stored=$stored expected=$expect)"
                        )
                    case FireboxCalcCache.Miss.WrongOwner(storedPrj, storedKind)   =>
                        dom.console.warn(
                            s"[S5.4] firebox-calc cache miss for ${kind.wire}: entry belongs to ($storedPrj, $storedKind)"
                        )
                    case FireboxCalcCache.Miss.Corrupt(detail)                     =>
                        dom.console.warn(s"[S5.4] firebox-calc cache entry undecodable, ignoring: $detail")
                None

    /**
     * Persist a successful response under `(projectId, kind)`.
     *
     * An oversized payload is skipped rather than attempted: see
     * [[FireboxCalcCache.MaxCachedChars]].
     */
    def write(
        projectId   : String,
        kind        : WireFireboxKind,
        requestBody : String,
        responseBody: String
    ): Unit =
        if FireboxCalcCache.exceedsBudget(responseBody) then
            dom.console.warn             (
                s"[S5.4] firebox-calc response for ${kind.wire} is ${responseBody.length} chars — above the ${FireboxCalcCache.MaxCachedChars}-char localStorage budget, not cached"
            )
        else
            val entry = FireboxCalcCache.entryFor(
                projectId    = projectId,
                kind         = kind,
                requestBody  = requestBody,
                responseBody = responseBody,
                savedAtMs    = scala.scalajs.js.Date.now()
            )
            try
                dom.window.localStorage.setItem(
                    FireboxCalcCache.key(projectId, kind),
                    entry.asJson.noSpaces
                )
            catch
                case ex: scala.scalajs.js.JavaScriptException =>
                    dom.console.warn(
                        s"[S5.4] could not cache firebox-calc response for ${kind.wire}: ${ex.getMessage}"
                    )

end FireboxCalcCacheStore
