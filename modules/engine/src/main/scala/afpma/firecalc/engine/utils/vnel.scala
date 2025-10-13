/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import scala.annotation.nowarn

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

extension [E, A](e: Either[E, A])
    def toValidatedNel: ValidatedNel[E, A] =
        e.fold(_.invalidNel[A], _.validNel[E])
    
extension [A](e: Either[Throwable, A])
    def getOrThrow: A                   = mapOrThrow(identity)
    def mapOrThrow[B](f: A => B): B     = e.fold(e => throw e, f)
    def mapShow[B](showErr: Throwable => String, show: A => String): String = e.fold(showErr, show)

def ApplyVNel[E] = Apply[[X] =>> ValidatedNel[E, X]]

// given applicativeVNel[E: Semigroup]
//     : Applicative[[X] =>> ValidatedNel[E, X]] =
//     new Applicative[[X] =>> ValidatedNel[E, X]] {
//         def ap[A, B](
//             f: ValidatedNel[E, A => B]
//         )(fa: ValidatedNel[E, A]): ValidatedNel[E, B] =
//             (fa, f) match {
//                 case (Valid(a), Valid(fab))     => Valid(fab(a))
//                 case (i @ Invalid(_), Valid(_)) => i
//                 case (Valid(_), i @ Invalid(_)) => i
//                 case (Invalid(e1), Invalid(e2)) =>
//                     Invalid(Semigroup[NonEmptyList[E]].combine(e1, e2))
//             }

//         def pure[A](x: A): ValidatedNel[E, A] = x.validNel[E]
//     }

given semigroupNel: [E] => Semigroup[NonEmptyList[E]] =
    SemigroupK[NonEmptyList].algebra[E]

extension [E, A](vnec: ValidatedNec[E, A])
    def asValidatedNel: ValidatedNel[E, A] =
        vnec.swap.map(_.toNonEmptyList).swap

extension [E, A](vnel: ValidatedNel[E, A])
    def asValidatedNel[EE](using
        conv: Conversion[E, EE]
    ): ValidatedNel[EE, A] =
        vnel.swap.map(nel => nel.map(conv)).swap

    // def zip[B](vnb: ValidatedNel[E, B]): ValidatedNel[E, (A, B)] =
    //     given SE: Semigroup[NonEmptyList[E]] = semigroupNel[E]
    //     (vnel, vnb) match
    //         case (Valid(a), Valid(b))       => Valid((a, b))
    //         case (Valid(_), i @ Invalid(_)) => i
    //         catPse (i @ Invalid(_), Valid(_)) => i
    //         case (Invalid(na), Invalid(nb)) =>
    //             SE.combine(na, nb).invalid[(A, B)]

    def expandErrorTo[EE >: E]: ValidatedNel[EE, A] =
        vnel.leftMap(nel => nel.map(e => e: EE))

extension [E: Show, A](@nowarn vnel: ValidatedNel[E, A])

    private def _showVNel = (nele: NonEmptyList[E]) => 
        s"""|${nele.map(_.show).toList.mkString("", "\n", "\n")}
            |""".stripMargin

    def getOrThrow: A = mapOrThrow(identity)

    def mapOrThrow[B](f: A => B): B = 
        vnel.fold(
            nele => throw new Error(_showVNel(nele)),
            f
        )

    def mapShow[B](errPrefix: String)(show: A => String): String = 
        vnel.fold(err => s"ERROR // ${errPrefix} :\n${_showVNel(err)}", show)

    def asVNelString: VNelString[A] =
        vnel.swap.map(_.map(_.show)).swap

type VNelString[A] = Validated[NonEmptyList[String], A]
object VNelString:

    // builder methods
    
    def validUnitWhenOption[A](oa: Option[A])(cond: A => Boolean)(err: String): VNelString[Unit] = oa match
        case Some(a) => 
            validUnitWhen(a)(cond)(err)
        case None => NonEmptyList.one("aucune valeur").invalid

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

    val validUnit = valid(())

    // extractors
    
    sealed trait Status[+A]
    case class Valid[A](a: A) extends Status[A]
    case class InvalidOne(err: String) extends Status[Nothing]
    case class InvalidMany(errors: List[String]) extends Status[Nothing]

    def unapply[A](vn: VNelString[A]): Option[Status[A]] = Some(vn match
        case Validated.Valid(a) => Valid(a)
        case Validated.Invalid(nel) => nel match
            case NonEmptyList(head, Nil)  => InvalidOne(head)
            case nel @ NonEmptyList(_, _) => InvalidMany(nel.toList)
    )

    object Errors:
        def unapply[A](vn: VNelString[A]): Option[(List[String])] = vn match
            case Invalid(nel @ NonEmptyList(_, _)) => Some(nel.toList)
            case _ => None

extension [A](vnsa: VNelString[A])
    def getOrThrow: A = mapOrThrow(identity)
    def mapOrThrow[B](f: A => B): B = 
        vnsa.fold(
            nels => throw new Error(
                s"""|VNelString error :
                    |${nels.toList.mkString("\t-", "\n\t-", "\n")}
                    |""".stripMargin),
            f
        )