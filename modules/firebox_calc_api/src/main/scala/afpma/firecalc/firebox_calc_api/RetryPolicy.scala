/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

import scala.concurrent.duration.*

/**
 * Bounded retry policy for the calculation client.
 *
 * The policy bounds the **total number of attempts** (1 initial + N retries).
 * For example, `maxAttempts = 3` means 1 initial attempt and up to 2 retries.
 * After exhaustion, the client returns a typed [[CalculationFailure.TransportError]]
 * rather than hanging or concealing the failure.
 *
 * Only transient transport errors are eligible for retry. Protocol errors
 * (4xx/5xx responses) are returned immediately without retry.
 * @param maxAttempts Total attempt budget including the initial attempt (0 = no retry, default = 3)
 * @param initialDelay Initial back-off delay (default = 100ms)
 * @param maxDelay Maximum back-off delay (default = 5s)
 * @param backOffFactor Multiplier for exponential back-off (default = 2.0)
 */
final case class RetryPolicy(
    maxAttempts  : Int            = 3,
    initialDelay : FiniteDuration = 100.millis,
    maxDelay     : FiniteDuration = 5.seconds,
    backOffFactor: Double         = 2.0
):
    require(maxAttempts >= 0, "maxAttempts must be non-negative"    )
    require(initialDelay.length > 0, "initialDelay must be positive")
    require(maxDelay.length > 0, "maxDelay must be positive"        )
    require(backOffFactor >= 1.0, "backOffFactor must be >= 1.0"    )

    /** Whether retries are enabled. */
    def enabled: Boolean = maxAttempts > 0

    /** Compute the delay for the N-th retry attempt (0-indexed). */
    def delayForAttempt(attempt: Int): FiniteDuration =
        require(attempt >= 0, "attempt must be non-negative")
        val exponential = initialDelay.length * Math.pow(backOffFactor, attempt).toLong
        FiniteDuration(exponential.min(maxDelay.length), maxDelay.unit)

end RetryPolicy

object RetryPolicy:
    /** No retries — fail fast on the first transport error. */
    val none: RetryPolicy = RetryPolicy(maxAttempts = 0)

    /** Default bounded retry: up to 3 attempts with exponential back-off. */
    val default: RetryPolicy = RetryPolicy()

end RetryPolicy
