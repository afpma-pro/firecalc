/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import scala.annotation.tailrec

import cats.*

trait FireboxHelper_15544:
    
    opaque type OneOffOrNotApplicable[A] = WhenOneOff.Result[A] | WhenTested.NotApplicable
    object OneOffOrNotApplicable:
        def oneOff[A](a: A): OneOffOrNotApplicable[A] = WhenOneOff.Result(a)
        def notApplicable[A]: OneOffOrNotApplicable[A] = WhenTested.NotApplicable
        extension [A](x: OneOffOrNotApplicable[A])
            def toOption: Option[A] = x match
                case WhenOneOff.Result(a) => Some(a)
                case WhenTested.NotApplicable => None

    opaque type TestedOrNotApplicable[A] = WhenTested.Result[A] | WhenOneOff.NotApplicable
    object TestedOrNotApplicable:
        def tested[A](a: A): TestedOrNotApplicable[A] = WhenTested.Result(a)
        def notApplicable[A]: TestedOrNotApplicable[A] = WhenOneOff.NotApplicable

    given Monad[OneOffOrNotApplicable]:
        def pure[A](x: A): OneOffOrNotApplicable[A] = WhenOneOff.Result(x)

        @tailrec
        def tailRecM[A, B](a: A)(f: A => OneOffOrNotApplicable[Either[A, B]]): OneOffOrNotApplicable[B] = 
            f(a) match
                case WhenOneOff.Result(Left(nextA))  => tailRecM(nextA)(f)
                case WhenOneOff.Result(Right(b))     => WhenOneOff.Result(b)
                case WhenTested.NotApplicable        => WhenTested.NotApplicable

        def flatMap[A, B](fa: OneOffOrNotApplicable[A])(f: A => OneOffOrNotApplicable[B]): OneOffOrNotApplicable[B] =
            fa match
                case WhenOneOff.Result(a)       => f(a)
                case WhenTested.NotApplicable   => WhenTested.NotApplicable

    given Monad[TestedOrNotApplicable]:
        def pure[A](x: A): TestedOrNotApplicable[A] = WhenTested.Result(x)

        @tailrec
        def tailRecM[A, B](a: A)(f: A => TestedOrNotApplicable[Either[A, B]]): TestedOrNotApplicable[B] = 
            f(a) match
                case WhenTested.Result(Left(nextA))  => tailRecM(nextA)(f)
                case WhenTested.Result(Right(b))     => WhenTested.Result(b)
                case WhenOneOff.NotApplicable        => WhenOneOff.NotApplicable

        def flatMap[A, B](fa: TestedOrNotApplicable[A])(f: A => TestedOrNotApplicable[B]): TestedOrNotApplicable[B] =
            fa match
                case WhenTested.Result(a)       => f(a)
                case WhenOneOff.NotApplicable   => WhenOneOff.NotApplicable

    sealed trait OneOffOrTestedResult[+A]
    sealed trait WhenOneOff[+A] extends OneOffOrTestedResult[A]
    object WhenOneOff:
        extension [A](a: A)
            def wrapWhenOneOff: WhenOneOff.Result[A] = Result(a)
        case class Result[A](a: A) extends WhenOneOff[A]
        case object NotApplicable extends WhenOneOff[Nothing]
        type NotApplicable = NotApplicable.type

    sealed trait WhenTested[+A] extends OneOffOrTestedResult[A]
    object WhenTested:
        extension [A](a: A)
            def wrapWhenTested: WhenTested.Result[A] = Result(a)
        case class Result[A](a: A) extends WhenTested[A]
        case object NotApplicable extends WhenTested[Nothing]
        type NotApplicable = NotApplicable.type