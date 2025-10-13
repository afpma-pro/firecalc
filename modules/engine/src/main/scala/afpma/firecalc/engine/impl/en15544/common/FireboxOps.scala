/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import afpma.firecalc.engine.models.en15544.std.*

trait FireboxOps:
    extension (firebox: Firebox_15544)

        def ifOneOff[A, B](orElse: B)(
            f: OneOff => A
        ): A | B =
            firebox match
                case i: OneOff => f(i)
                case _         => orElse

        def ifOneOff_[A](f: OneOff => A): A | Unit =
            ifOneOff(orElse = ())(f)

        def ifTested[A, B](orElse: B)(f: Tested => A): A | B =
            firebox match
                case i: Tested => f(i)
                case _         => orElse

        def ifTested_[A](f: Tested => A): A | Unit =
            ifTested(orElse = ())(f)

        def ifNotTested[A, B](orElse: B)(f: => A): A | B =
            firebox match
                case _: Tested => orElse
                case _         => f

        def ifNotTested_[A](f: => A): A | Unit =
            ifNotTested(orElse = ())(f)

        def whenOneOff[A](f: OneOff => A): OneOffOrNotApplicable[A] = 
            firebox match
                case i: OneOff  => OneOffOrNotApplicable.oneOff(f(i))
                case _: Tested  => OneOffOrNotApplicable.notApplicable