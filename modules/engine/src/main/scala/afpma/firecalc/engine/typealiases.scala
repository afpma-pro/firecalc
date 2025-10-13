/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition

type EpOp[X]        = DraftCondition ?=> X
type LoadOp[X]      = Option[LoadQty]  ?=> Option[X]