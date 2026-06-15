/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */
package afpma.firecalc.engine.impl.common

import afpma.firecalc.dto.common.*

trait FramedBuilderSupport:
    def withInitialDirection(dir: PipeInitialDirection): this.type

    /**
     * Wire the initial frame from a framed sequence into the builder.
     *
     * Note: only the initial *direction* is wired here. Position tracking
     * is the responsibility of the `PositionTracker` domain, not the builder's
     * `PropsState`.
     *
     * @param seq the framed sequence containing the initial frame
     * @return this builder
     */
    def fromFramedSequence[D](seq: FramedPipeSequence[D]): this.type =
        this.withInitialDirection(seq.initialFrame.direction)
