/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.utils

sealed trait OptionOfEither[+L, +R]
case object NoneOfEither extends OptionOfEither[Nothing, Nothing]
//     def unapply[L, R](ei: OptionOfEither[L, R]): Boolean = ei match
//         case NoneOfEither => true
//         case SomeLeft(l) => false
//         case SomeRight(r) => false

type NoneOfEither = NoneOfEither.type
case class SomeLeft[+L, +R](l: L) extends OptionOfEither[L, R]
case class SomeRight[+L, +R](r: R) extends OptionOfEither[L, R]

// object SomeLeft:
//     def unapply[L, R](ei: OptionOfEither[L, R]): Option[L] = ei match
//         case NoneOfEither => None
//         case SomeLeft(l) => Some(l)
//         case SomeRight(r) => None

// object SomeRight:
//     def unapply[L, R](ei: OptionOfEither[L, R]): Option[R] = ei match
//         case NoneOfEither => None
//         case SomeLeft(l) => None
//         case SomeRight(r) => Some(r)
    

object OptionOfEither:

    given [L, R] => Conversion[Option[Either[L, R]], OptionOfEither[L, R]] = 
        OptionOfEither.convertFromOptionEither

    def wrap[L, R](oe: Option[Either[L, R]]): OptionOfEither[L, R] = 
        convertFromOptionEither(oe)

    extension [L, R](self: OptionOfEither[L, R])
        def unwrap: Option[Either[L, R]] = 
            convertToOptionEither(self)

    def convertFromOptionEither[L, R](oelr: Option[Either[L, R]]): OptionOfEither[L, R] = 
        oelr match
            case Some(Left(l)) => SomeLeft(l)
            case Some(Right(r)) => SomeRight(r)
            case None => NoneOfEither

    def convertToOptionEither[L, R](in: OptionOfEither[L, R]): Option[Either[L, R]] =
        in match
            case NoneOfEither => None
            case SomeLeft(l) => Some(Left(l))
            case SomeRight(r) => Some(Right(r))