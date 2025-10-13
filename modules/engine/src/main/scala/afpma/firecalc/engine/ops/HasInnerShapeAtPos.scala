/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.engine.ops.PositionOp

import afpma.firecalc.dto.all.*

trait HasInnerShapeAtPos[A]:
    extension (a: A)
        def innerShape(oPrevShape: Option[PipeShape]): Option[PositionOp[PipeShape]]
