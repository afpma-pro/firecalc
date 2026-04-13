/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

import cats.data.NonEmptyList
import cats.data.Validated

/** Opaque validation function type: A => VNelString[Unit]. */
opaque type ValidateVar[A] = A => VNelString[Unit]

object ValidateVar:

    def apply[A](using ev: ValidateVar[A]): ValidateVar[A] = ev

    def make[A](f: A => VNelString[Unit]): ValidateVar[A] = f

    def forEither[L: ValidateVar, R: ValidateVar]: ValidateVar[Either[L, R]] =
        (ei: Either[L, R]) =>
            ei match
                case Left(l)  => ValidateVar[L].validate(l)
                case Right(r) => ValidateVar[R].validate(r)

    def forOptionEitherFromOptions[L, R](using
        vvol: ValidateVar[Option[L]],
        vvor: ValidateVar[Option[R]]
    ): ValidateVar[Option[Either[L, R]]] =
        ValidateVar.make: olr =>
            olr match
                case None           => VNelString.validUnit
                case Some(Left(l))  => vvol.validate(Some(l))
                case Some(Right(r)) => vvor.validate(Some(r))

    def forOptionEither[L, R](using
        vvl: ValidateVar[L],
        vvr: ValidateVar[R]
    ): ValidateVar[Option[Either[L, R]]] =
        ValidateVar.make: olr =>
            olr match
                case None           => VNelString.validUnit
                case Some(Left(l))  => vvl.validate(l)
                case Some(Right(r)) => vvr.validate(r)

    def forList[A: ValidateVar]: ValidateVar[List[A]] =
        import cats.syntax.all.*
        (xs: List[A]) =>
            val vva              = ValidateVar[A]
            val zero             = Vector.empty[NonEmptyList[String]]
            val indexedErrorsVec = (xs
                .mapWithIndex((x, i) => (x, i)))
                .foldLeft(zero): (acc, el) =>
                    val (a, idx) = el
                    vva.validate(a) match
                        case Validated.Invalid(errs) =>
                            val indexedErrors = errs.map(err => s"[$idx]: $err")
                            acc.appended(indexedErrors)
                        case _                       => acc
            if (indexedErrorsVec.size > 0)
                VNelString.invalidUnsafe(indexedErrorsVec.toList.map(_.toList).flatten)
            else
                VNelString.valid        (()                                           )

    def valid[A]: ValidateVar[A] =
        _ => VNelString.validUnit

    def validWhen[A](cond: A => Boolean)(err: A => String): ValidateVar[A] =
        a => VNelString.validUnitWhen(a)(cond)(err(a))

    def validOption_WhenDefinedAnd[A](
        cond: A => Boolean
    )(err: A => String)(using fm: FormMessages): ValidateVar[Option[A]] =
        oa =>
            oa match
                case Some(a) => VNelString.validUnitWhenOption(oa)(cond)(err(a))
                case None    => VNelString.invalidOne(fm.valueIsUndefined)

    def invalidOne[A](err: A => String): ValidateVar[A] =
        a => VNelString.invalidOne(err(a))

    def invalid[A](nel: A => NonEmptyList[String]): ValidateVar[A] =
        a => VNelString.invalid(nel(a))

    def invalidUnsafe[A](errs: A => List[String]): ValidateVar[A] =
        a => VNelString.invalidUnsafe(errs(a))

    extension [A](vv: ValidateVar[A])
        def validate(a: A): VNelString[Unit] = vv(a)
        def unwrap                 : A => VNelString[Unit] = vv
        def contramap[B](f: B => A): ValidateVar[B]        =
            (b: B) => vv(f(b))

        def toOption_WithNoneAsInvalid(using fm: FormMessages): ValidateVar[Option[A]] =
            (oa: Option[A]) =>
                oa match
                    case Some(a) => vv(a)
                    case None    => VNelString.invalidOne(fm.valueIsUndefined)

        def toOption_WithNoneAsValid: ValidateVar[Option[A]] =
            (oa: Option[A]) =>
                oa match
                    case Some(a) => vv(a)
                    case None    => VNelString.validUnit

        def toLeft_WithRightAsAlwaysValid[R]: ValidateVar[Either[A, R]] =
            (ei: Either[A, R]) =>
                ei match
                    case Left(a)  => vv(a)
                    case Right(_) => VNelString.validUnit

        def toRight_WithLeftAsAlwaysValid[L]: ValidateVar[Either[L, A]] =
            (ei: Either[L, A]) =>
                ei match
                    case Left(_)  => VNelString.validUnit
                    case Right(a) => vv(a)

    extension [A](vvoa: ValidateVar[Option[A]])
        def contramapOpt[B](f: B => A): ValidateVar[Option[B]] =
            vvoa.contramap[Option[B]](ob => ob.map(f))
        def flatten                   : ValidateVar[A]         =
            vvoa.contramap[A](a => Some(a))

    extension [A](a: A)
        def validateVarValue(using validate: ValidateVar[A]): VNelString[Unit] =
            validate(a)

    object Option:
        def apply[A](using ev: ValidateVar[Option[A]]): ValidateVar[Option[A]] = ev
