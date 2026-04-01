/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.formgen

trait ConditionalFor[C, A]:
    def check: C => Boolean
    def deref: C => Option[A] = _ => None

object ConditionalFor:
    def apply[C, A](f: C => Boolean) = new ConditionalFor[C, A]:
        def check: C => Boolean = f

    def apply[C, A](f: C => Boolean, d: C => Option[A]) = new ConditionalFor[C, A]:
        def check: C => Boolean = f
        override def deref: C => Option[A] = d
