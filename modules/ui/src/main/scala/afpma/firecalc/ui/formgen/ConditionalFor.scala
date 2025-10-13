/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.formgen

trait ConditionalFor[C, A]:
    def check: C => Boolean

object ConditionalFor:
    def apply[C, A](f: C => Boolean) = new ConditionalFor[C, A]:
        def check: C => Boolean = f
