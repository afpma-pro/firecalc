/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.generic

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application.ComputeAt
import afpma.firecalc.engine.models.PipeResult

/** State propagated from one pipe slot to the next during the post-firebox fold.
  *
  * Captures the quantities that a downstream pipe needs from its upstream neighbour:
  * exit temperature (becomes the next pipe's start temperature), and optionally
  * the last section's density and velocity (used by EN 13384 thermal calculations).
  */
case class UpstreamState(
    temp_start        : TCelsius,
    last_pipe_density : Option[Density],
    last_pipe_velocity: Option[FlowVelocity]
)

object UpstreamState:

    /** Initial state — used when there is no upstream pipe (start of chain). */
    def initial(temp: TCelsius): UpstreamState =
        UpstreamState(
            temp_start         = temp,
            last_pipe_density  = None,
            last_pipe_velocity = None
        )

    /** Derive upstream state from a computed pipe result, using the given
      * position strategy (Mean vs Middle) for density/velocity extraction.
      */
    def fromPipeResult(pr: PipeResult, computeAt: ComputeAt): UpstreamState =
        val density = computeAt match
            case ComputeAt.Mean   => pr.last_density_mean
            case ComputeAt.Middle => pr.last_density_middle
        val velocity = computeAt match
            case ComputeAt.Mean   => pr.last_velocity_mean
            case ComputeAt.Middle => pr.last_velocity_middle
        UpstreamState(
            temp_start         = pr.gas_temp_end,
            last_pipe_density  = density,
            last_pipe_velocity = velocity
        )
