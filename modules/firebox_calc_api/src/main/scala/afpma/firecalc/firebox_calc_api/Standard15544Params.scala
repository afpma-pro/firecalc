/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

final case class Standard15544Params(
    m_B  : Option[WireQty],
    /**
     * EN 15544 required minimum efficiency (`n_min`). Unit: percent. Optional:
     * absent (null) is a valid partial request (B.5-A). A `ConstraintContext`
     * can only be built when BOTH `m_B` and `n_min` are present, so when
     * `n_min` is absent no `ConstraintContext` is built and the `m_B`-dependent
     * constraints report `ConstraintOutcome.NotEvaluated` (`MissingMB`).
     */
    n_min: Option[WireQty]
)
