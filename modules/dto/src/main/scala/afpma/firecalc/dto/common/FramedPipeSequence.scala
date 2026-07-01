/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */
package afpma.firecalc.dto.common

trait FramedPipeSequence[D, P] {
    def initialDirection: PipeInitialDirection
    def position        : P
    def descriptors     : Seq[D]
}
