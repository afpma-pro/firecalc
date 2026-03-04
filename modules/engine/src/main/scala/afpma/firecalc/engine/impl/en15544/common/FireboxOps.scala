/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import afpma.firecalc.engine.models.en15544.std.*

trait FireboxOps:
    extension (firebox: Firebox_15544)

        def ifSingleTested[A, B](orElse: B)(f: SingleTested => A): A | B =
            firebox match
                case i: SingleTested => f(i)
                case _ => orElse

        def ifSingleTested_[A](f: SingleTested => A): A | Unit =
            ifSingleTested(orElse = ())(f)

        def ifNotSingleTested[A, B](orElse: B)(f: => A): A | B =
            firebox match
                case _: SingleTested => orElse
                case _ => f

        def ifNotSingleTested_[A](f: => A): A | Unit =
            ifNotSingleTested(orElse = ())(f)