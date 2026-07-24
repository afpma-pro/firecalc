/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import com.raquo.airstream.core.{EventStream, Signal}
import com.raquo.airstream.state.Var

import scala.scalajs.js
import io.taig.babel.Locale

/**
 * EventBus-driven polling with exponential backoff for connectivity checks.
 *
 * Replaces the periodic + flatMapMerge approach with self-scheduling via Var.
 * On failure, backoff increases: 2s → 4s → 8s → cap at 8s.
 * On success, backoff resets to 2s.
 */
object ConnectivityPolling:

    /** Backoff configuration: 2s → 4s → 8s → cap at 8s. */
    object Backoff:
        val initialMs: Int = 2000
        val maxMs    : Int = 8000

        def next(current: Int): Int =
            Math.min(current * 2, maxMs)

    /**
     * Creates an EventStream that polls when the trigger signal is true.
     *
     * Uses self-scheduling with exponential backoff.
     * On failure, backoff increases: 2s → 4s → 8s → cap at 8s.
     * On success, backoff resets to 2s.
     *
     * @param pollingTrigger Signal that controls when to start (true) and stop (false) polling
     * @param url The URL to check
     * @return EventStream of connectivity results
     */
    def createPollingStream(
        pollingTrigger: Signal[Boolean],
        url           : String
    )(using locale: Locale): EventStream[PaymentsBackendApiConnectivity.ConnectivityResult] =
        // Trigger var — incrementing it fires a new check
        val triggerVar = Var[Int](0)

        // Backoff state
        var currentBackoffMs = Backoff.initialMs
        var pendingTimeout: js.UndefOr[js.timers.SetTimeoutHandle] = js.undefined

        def scheduleNext(result: PaymentsBackendApiConnectivity.ConnectivityResult): Unit =
            // Cancel any pending timeout
            pendingTimeout.foreach(js.timers.clearTimeout)
            pendingTimeout = js.undefined

            result match
                case PaymentsBackendApiConnectivity.FullConnection(_, _) =>
                    // Success: reset backoff
                    currentBackoffMs = Backoff.initialMs

                case PaymentsBackendApiConnectivity.PartialConnection(
                        _,
                        PaymentsBackendApiConnectivity.BackendDisconnected(_)
                    ) =>
                    // Backend error: increase backoff
                    currentBackoffMs = Backoff.next(currentBackoffMs)

                case PaymentsBackendApiConnectivity.InternetOnly(_) =>
                    // No internet: use max backoff
                    currentBackoffMs = Backoff.maxMs

                case PaymentsBackendApiConnectivity.BackendCheckInProgress(_, _) =>
                case PaymentsBackendApiConnectivity.InternetCheckInProgress(_)   =>
                    // Still checking: keep current backoff
                    ()

                case PaymentsBackendApiConnectivity.CheckDisabled =>
                    // Polling disabled: don't schedule
                    return

            pendingTimeout = js.timers.setTimeout(currentBackoffMs) {
                // Trigger next check by incrementing the var
                triggerVar.set(triggerVar.now() + 1)
            }

        def cancelPending(): Unit =
            pendingTimeout.foreach(js.timers.clearTimeout)
            pendingTimeout = js.undefined

        // Build the polling stream from trigger changes
        val pollingStream: EventStream[PaymentsBackendApiConnectivity.ConnectivityResult] =
            triggerVar.signal.changes.flatMapMerge { _ =>
                PaymentsBackendApiConnectivity.performFullConnectivityCheck(url)(using locale).map { result =>
                    scheduleNext(result)
                    result
                }
            }

        // Wire up the polling trigger
        pollingTrigger.changes.flatMapSwitch { shouldPoll =>
            if shouldPoll then
                // Start polling with immediate check
                triggerVar.set(triggerVar.now() + 1)
                pollingStream
            else
                cancelPending        ()
                EventStream.fromValue(
                    PaymentsBackendApiConnectivity.CheckDisabled,
                    emitOnce = true
                )
        }

end ConnectivityPolling
