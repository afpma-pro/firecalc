/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import afpma.firecalc.units.coulombutils.*

trait InsideOrOutside:
    def isInside: Boolean
    def isOutside: Boolean = !isInside

object InsideOrOutside:
    trait Inside extends InsideOrOutside:
        def isInside: Boolean = true
    trait Outside extends InsideOrOutside:
        def isInside: Boolean = false

    trait Helper[L]:

        def iterator: IterableOnce[(L, InsideOrOutside)]
        def getLengthFn: L => QtyD[Meter]

        def fullyInside: Boolean = iterator.iterator.forall(_._2.isInside)
        def fullyOutside: Boolean = iterator.iterator.forall(_._2.isOutside)

        private def lengthInside: QtyD[Meter] =
            iterator.iterator.filter(_._2.isInside).map(t => getLengthFn(t._1).value).sum.meters
        private def lengthOutside: QtyD[Meter] =
            iterator.iterator.filter(_._2.isOutside).map(t => getLengthFn(t._1).value).sum.meters
        private def totalLength: QtyD[Meter] = (lengthInside.value + lengthOutside.value).meters

        def ratioInside: Dimensionless = (lengthInside.value / totalLength.value).ea
        def ratioOutside: Dimensionless = (lengthOutside.value / totalLength.value).ea

