/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import afpma.firecalc.domain.PipeShape
import afpma.firecalc.engine.standard.AirIntakePipeShapeTopologyMismatch
import afpma.firecalc.engine.standard.FireboxError

import cats.syntax.all.*

/**
 * Shared checks for the declared air-intake pipe shape, callable from any
 * firebox kind's `firebox_custom_constraints`.
 */
object AirIntakeShapeChecks:
    /**
     * Declared intake shape vs topology-computed pipe-end shape; tolerance-aware.
     *
     * Two shapes are equal when they are the same variant and their dimensions
     * round to the same tenth-of-centimeter ([[PipeShape.equalsTolerance]]).
     * A mismatch yields [[AirIntakePipeShapeTopologyMismatch]] carrying the
     * `show` rendering of both shapes.
     */
    def topologyMismatch(declared: PipeShape, computed: PipeShape): Option[FireboxError] =
        if declared.equalsTolerance(computed) then None
        else Some(AirIntakePipeShapeTopologyMismatch(declared = declared.show, computed = computed.show))

end AirIntakeShapeChecks
