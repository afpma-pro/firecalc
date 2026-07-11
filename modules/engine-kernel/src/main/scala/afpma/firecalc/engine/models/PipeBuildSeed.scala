/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.NbOfFlows
import afpma.firecalc.engine.models.geometry.PipeFrame

final case class PipeBuildSeed(
    frame : Option[PipeFrame],
    nFlows: NbOfFlows
)

object PipeBuildSeed:
    val default: PipeBuildSeed = PipeBuildSeed(None, NbOfFlows(1))

    def fromFrame(frame: Option[PipeFrame]): PipeBuildSeed =
        PipeBuildSeed(frame, NbOfFlows(1))

final case class BuiltPipe[A](
    value   : A,
    nextSeed: PipeBuildSeed
)
