/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.units.coulombutils.*

trait HasVerticalElev[A]:
    extension (a: A) def verticalElev: Length

object HasVerticalElev:
    def from[A](f: A => Length) = new HasVerticalElev[A]:
        extension (a: A) def verticalElev: Length = f(a)

