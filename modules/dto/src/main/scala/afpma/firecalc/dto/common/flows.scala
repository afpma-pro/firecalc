/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import cats.Show

import coulomb.*
import coulomb.syntax.*

import afpma.firecalc.units.coulombutils.*

opaque type NbOfFlows <: Int = Int
object NbOfFlows:
    def apply(i: Int): NbOfFlows = i
    given Conversion[Int, NbOfFlows] = identity
    extension (n: NbOfFlows) def unwrap: Int = n

    given Show[NbOfFlows] = Show.show(n => s"$n")

    extension (x: 1)
        def flow: NbOfFlows = 1

    extension (x: Int)
        def flows: NbOfFlows = x

    extension (nf: NbOfFlows)
        def asQty: QtyD[1] = nf.toDouble.withUnit[1]