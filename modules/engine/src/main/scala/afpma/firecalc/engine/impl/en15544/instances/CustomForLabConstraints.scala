/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Traditional.CustomForLab

/** EN 15544 constraints for [[CustomForLab]] fireboxes.
 *
 * Extends the default constraint set.
 */
given customForLabConstraints: FireboxConstraints[CustomForLab] =
    new FireboxConstraints_Strict[CustomForLab] {}
