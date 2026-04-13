/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

import cats.data.NonEmptyList
import cats.data.Validated
import cats.syntax.validated.*

/** Validated[NonEmptyList[String], A] — inlined from engine-kernel to avoid the dependency. */
type VNelString[A] = Validated[NonEmptyList[String], A]

object VNelString:

    def validUnitWhenOption[A](oa: Option[A])(cond: A => Boolean)(err: String): VNelString[Unit] = oa match
        case Some(a) => validUnitWhen(a)(cond)(err)
        case None    => NonEmptyList.one("no value").invalid

    def validUnitWhen[A](a: A)(cond: A => Boolean)(err: String): VNelString[Unit] =
        if (cond(a)) Validated.Valid(())
        else NonEmptyList.one(err).invalid

    def invalidOne[A](err: String): VNelString[A] =
        Validated.Invalid(NonEmptyList.one(err))

    def invalid[A](nel: NonEmptyList[String]): VNelString[A] =
        Validated.Invalid(nel)

    def invalidUnsafe[A](errs: List[String]): VNelString[A] =
        Validated.Invalid(NonEmptyList.fromListUnsafe(errs))

    def valid[A](a: A): VNelString[A] =
        Validated.Valid(a)

    val validUnit: VNelString[Unit] = valid(())
