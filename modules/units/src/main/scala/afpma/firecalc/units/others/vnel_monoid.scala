/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.units.others

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import algebra.instances.all.given
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}

object vnel_monoid:
    given semigroupVNel: [E, A] => Semigroup[ValidatedNel[E, A]] =
        SemigroupK[[X] =>> ValidatedNel[E, X]].algebra[A]

    def mkMonoidSumForVNelQtyD[E, U](zero: QtyD[U]): Monoid[ValidatedNel[E, QtyD[U]]] = 
        Monoid.instance(zero.validNel, {
            case (Valid(q1), Valid(q2))       => Valid(q1 + q2)
            case (Valid(_), i @ Invalid(_))   => i
            case (i @ Invalid(_), Valid(_))   => i
            case (Invalid(na), Invalid(nb))   => 
                NonEmptyList.fromListUnsafe(na.toList ++ nb.toList).invalid


        })