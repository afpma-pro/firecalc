/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

/**
 * Conditional form field visibility.
 *
 * Given a condition source of type C, determines whether a field of type A
 * should be shown, and optionally extracts an initial A value from C.
 */
trait ConditionalFor[C, A]:
    def check: C => Boolean
    def deref: C => Option[A] = _ => None

object ConditionalFor:
    def apply[C, A](f: C => Boolean): ConditionalFor[C, A] = new ConditionalFor[C, A]:
        def check: C => Boolean = f

    def apply[C, A](f: C => Boolean, d: C => Option[A]): ConditionalFor[C, A] = new ConditionalFor[C, A]:
        def check         : C => Boolean   = f
        override def deref: C => Option[A] = d
