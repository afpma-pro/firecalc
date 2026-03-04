/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.showP

import afpma.firecalc.i18n.Localized
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.TermConstraint.*
import afpma.firecalc.engine.standard.*

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import scala.math.Ordering.Implicits.*

import io.taig.babel.Locale

// TermDef

sealed class TermDefDetails[Q](
    val name       : Localized[String],
    val description: Localized[String]
)

object TermDefDetails:
    def apply[Q](using instance: TermDefDetails[Q]): TermDefDetails[Q] = instance

    def apply[Q](
        name       : Localized[String],
        description: Localized[String]
    ): TermDefDetails[Q] =
        new TermDefDetails(name, description)

    // conversions
    // def fromTermDefDetailsToTermDefDetailsOption[A]
    //     : Conversion[TermDefDetails[A], TermDefDetails[Option[A]]] =
    //     (td: TermDefDetails[A]) =>
    //         new TermDefDetails[Option[A]](
    //             name = td.name,
    //             description = td.description,
    //         )

    // given fromTermDefDetailsToTermDefDetailsValidatedNel[E: Show, A]
    //     : Conversion[TermDefDetails[A], TermDefDetails[ValidatedNel[E, A]]] =
    //     (td: TermDefDetails[A]) =>
    //         new TermDefDetails[ValidatedNel[E, A]](
    //             name = td.name,
    //             description = td.description,
    //         )

sealed class TermDef[Q](
    val symbol: String,
    val source: String
)

object TermDef {

    def apply[Q](using instance: TermDef[Q]): TermDef[Q] = instance

    def apply[Q](
        symbol: String,
        source: String
    ): TermDef[Q] =
        new TermDef(symbol, source)

    def apply[Q](
        symbol: String
    ): TermDef[Q] =
        TermDef(symbol, source = "")

    // conversions
    def fromTermDefToTermDefOption[A]: Conversion[TermDef[A], TermDef[Option[A]]] =
        (td: TermDef[A]) =>
            new TermDef[Option[A]](
                symbol = td.symbol,
                source = td.source
            )

    given fromTermDefToTermDefValidatedNel: [E, A] => Conversion[TermDef[A], TermDef[ValidatedNel[E, A]]] =
        (td: TermDef[A]) =>
            new TermDef[ValidatedNel[E, A]](
                symbol = td.symbol,
                source = td.source
            )

}

// TermConstraint
enum TermConstraintError[O: TermDefDetails](val o: O):

    case MinError(min: O, override val o: O, showO: Show[O])(using TermDefDetails[O]) extends TermConstraintError(o)

    case MaxError(max: O, override val o: O, showO: Show[O])(using TermDefDetails[O]) extends TermConstraintError(o)

    case GenericError(override val o: O, failMsgString: String)(using TermDefDetails[O]) extends TermConstraintError(o)

    case TypedError(override val o: O, error: Any, showError: Any => Locale ?=> String)(using TermDefDetails[O])
        extends TermConstraintError(o)

    // Locale-aware error message generation
    def failMsg(using Locale): String = this match
        case MinError(min, value, showO)  =>
            given Show[O] = showO
            I18N.errors.term_constraint_min_error(TermDefDetails[O].name, value.showP, min.showP)
        case MaxError(max, value, showO)  =>
            given Show[O] = showO
            I18N.errors.term_constraint_max_error(TermDefDetails[O].name, value.showP, max.showP)
        case GenericError(_, msg)         =>
            s"${TermDefDetails[O].name} ${msg}"
        case TypedError(_, error, showFn) =>
            showFn(error)

sealed abstract class TermConstraint[O](
    val source  : String,
    val validate: O => TermConstraint.ValidatedResult[O]
)

object TermConstraint:

    opaque type ValidatedResult[O] = ValidatedNel[TermConstraintError[O], O]

    extension [O](o  : O                     ) def validResult  : ValidatedResult[O] = o.validNel
    extension [O](tce: TermConstraintError[O]) def invalidResult: ValidatedResult[O] = tce.invalidNel

    // extension [U](constraints: Seq[Option[TermConstraint[U]]])
    //     def checkAll(u: U): Seq[ValidatedResult[U]] =
    //         constraints.flatten.map(_.validate(u))

    //     def checkAllOpt(ou: Option[U]): Seq[Option[ValidatedResult[U]]] =
    //         ou match
    //             case Some(u) =>
    //                 constraints.map:
    //                     case Some(tc) => tc.validate(u).some
    //                     case None     => None
    //             case None    =>
    //                 constraints.flatten.map(_ => None)

    //     def checkAllAndCombine(u: U): ValidatedResult[U] =
    //         given Monoid[ValidatedResult[U]] = ValidatedResult.mkMonoid[U](u)
    //         checkAll(u).combineAll

    //     def checkAllAndCombineWhenDefined(ou: Option[U]): Option[ValidatedResult[U]] =
    //         ou.map(checkAllAndCombine)

    object ValidatedResult:

        extension [O](vr: ValidatedResult[O])
            def isValid   = vr.isValid
            def isInvalid = vr.isInvalid

        extension [O](vr: ValidatedResult[O])
            def unwrap: ValidatedNel[TermConstraintError[O], O] = vr

            def leftMapDeep[E](f: TermConstraintError[O] => E): ValidatedNel[E, O] =
                vr.leftMap(_.map(f))

            def foldToErrDeep[E](f: TermConstraintError[O] => E): List[E] =
                if (vr.isInvalid)
                    vr.swap.toOption.get.map(f).toList
                else
                    Nil

            def foldToErr: Option[List[InvalidConstraint]] =
                if (vr.isInvalid)
                    Some(vr.swap.toOption.get.map(InvalidConstraint(_)).toList)
                else
                    None

            def showInvalidConstraintErrors: Option[List[TermConstraintError[O]]] =
                if (vr.isInvalid)
                    Some(vr.swap.toOption.get.toList)
                else
                    None

        extension [O: {TermDef, TermDefDetails}](vr: ValidatedResult[O])

            def showIfInvalid(using Locale): Option[String] =
                given svr: Show[ValidatedResult[O]] =
                    TermConstraint.showValidatedResult[O]
                if (vr.isInvalid)
                    val term_name = TermDefDetails[O].name.show
                    Some(s"$term_name: ${svr.show(vr)}")
                else None

            def show(using Locale): String =
                given svr: Show[ValidatedResult[O]] =
                    TermConstraint.showValidatedResult[O]
                svr.show(vr)

        def mkMonoid[U](u: U): Monoid[ValidatedResult[U]] =
            new Monoid[ValidatedResult[U]] {

                given Semigroup[U] = new Semigroup[U] {
                    def combine(x: U, y: U): U =
                        assert(x == y, s"combining $x and $y: should be equal")
                        x
                }

                def empty: ValidatedResult[U] = u.validNel
                def combine(
                    x: ValidatedResult[U],
                    y: ValidatedResult[U]
                )        : ValidatedResult[U] =
                    (x, y) match {
                        case (Valid(_), Valid(_)              ) => u.validNel
                        case (i @ Invalid(_), Valid(_)        ) => i
                        case (Valid(_), i @ Invalid(_)        ) => i
                        case (i1 @ Invalid(_), i2 @ Invalid(_)) =>
                            i1.combine(i2)
                    }
            }

    end ValidatedResult

    // instances for show
    given showValidatedResult
        : [Q] => (td: TermDef[Q], tdd: TermDefDetails[Q], loc: Locale) => Show[ValidatedResult[Q]] =
        showValidatedResultUsingSymbol[Q](using td.symbol, tdd.name, loc)

    given showValidatedResultUsingSymbol: [Q] => (
        symbol: String,
        name  : String,
        loc   : Locale
    ) => Show[ValidatedResult[Q]] = Show.show(o =>
        o match
            case Invalid(nel) =>
                given Locale   = loc
                val errorsShow = showTermConstraintErrorNel[Q].show(nel)
                s"$errorsShow"
            case Valid(_)     =>
                s"Valid '${symbol}' - $name"
    )

    def showTermConstraintErrorNel[O](using Locale): Show[NonEmptyList[TermConstraintError[O]]] =
        Show.show(nel =>
            nel.toList
                .map(tces => tces.failMsg)
                .mkString(" & ")
        )

    // helpers
    Show.show[String](src => s"(cf $src)")

    // subtypes of TermConstraint

    case class Min[O: {Show, Ordering, TermDefDetails}](
        min                : O,
        override val source: String
    ) extends TermConstraint[O](
            source,
            validate = u =>
                if (min <= u) u.validResult
                else TermConstraintError.MinError(min, u, summon[Show[O]]).invalidResult
        )

    object Min:
        def apply[Q: {Show, Ordering, TermDefDetails}](min: Q): Min[Q] =
            Min[Q](min, "")

    case class Max[Q: {Show, Ordering, TermDefDetails}](
        max                : Q,
        override val source: String
    ) extends TermConstraint[Q](
            source,
            validate = u =>
                if (max >= u) u.validResult
                else TermConstraintError.MaxError(max, u, summon[Show[Q]]).invalidResult
        )

    object Max:
        def apply[Q: {Show, Ordering, TermDefDetails}](max: Q): Max[Q] =
            Max[Q](max, "")

    case class Generic[Q: TermDefDetails](
        value              : Q,
        override val source: String,
        isValid            : Q => Either[String, Q]
    ) extends TermConstraint[Q](
            source,
            validate = q =>
                isValid(q) match
                    case Right(q)     => q.validResult
                    case Left(errMsg) =>
                        TermConstraintError.GenericError(value, errMsg).invalidResult
        )

    object Generic:

        def apply[Q: TermDefDetails](
            value  : Q,
            isValid: Q => Either[String, Q]
        ): Generic[Q] =
            Generic[Q](
                value,
                "",
                isValid
            )

    case class GenericTyped[Q, E](
        value              : Q,
        override val source: String,
        isValid            : Q => Either[E, Q]
    )                            (using showE: ShowUsingLocale[E], tdd: TermDefDetails[Q])
        extends TermConstraint[Q](
            source,
            validate = q =>
                isValid(q) match
                    case Right(q)    => q.validResult
                    case Left(error) =>
                        val showFn: Any => Locale ?=> String = (e: Any) => showE.show(e.asInstanceOf[E])
                        TermConstraintError.TypedError(value, error, showFn).invalidResult
        )

    object GenericTyped:
        def apply[Q: TermDefDetails, E: ShowUsingLocale](
            value  : Q,
            isValid: Q => Either[E, Q]
        ): GenericTyped[Q, E] =
            GenericTyped[Q, E](value, "", isValid)

// AllTermConstraints - Immutable implementation

case class AllTermConstraints[U](constraints: Seq[Option[TermConstraint[U]]]):
    import TermConstraint.ValidatedResult

    def getAll: Seq[Option[TermConstraint[U]]] = constraints

    def checkAll(u: U): Seq[ValidatedResult[U]] =
        constraints.flatten.map(_.validate(u))

    def checkAllOpt(ou: Option[U]): Seq[Option[ValidatedResult[U]]] =
        ou match
            case Some(u) =>
                constraints.map:
                    case Some(tc) => tc.validate(u).some
                    case None     => None
            case None    =>
                constraints.flatten.map(_ => None)

    def checkAllAndCombine(u: U): ValidatedResult[U] =
        given Monoid[ValidatedResult[U]] = ValidatedResult.mkMonoid[U](u)
        checkAll(u).combineAll

    def checkAllAndCombineWhenDefined(ou: Option[U]): Option[ValidatedResult[U]] =
        ou.map(checkAllAndCombine)

object AllTermConstraints:
    def empty[U]: AllTermConstraints[U] = AllTermConstraints(Seq.empty)
    def fromSeq[U](otcs: Seq[Option[TermConstraint[U]]]): AllTermConstraints[U] = AllTermConstraints(otcs)

case class CheckableConstraint[U](
    value: Option[U],
    alltc: AllTermConstraints[U]
)                                (using val td: TermDef[U], val tdd: TermDefDetails[U], val sh: Show[U]) {
    val termDef       : TermDef[U]                 = td
    val termDefDetails: TermDefDetails[U]          = tdd
    val vresultOption : Option[ValidatedResult[U]] =
        alltc.checkAllAndCombineWhenDefined(value)
}

object CheckableConstraint:

    def make[U: {TermDef, TermDefDetails, Show}](
        u          : U,
        constraints: Seq[Option[TermConstraint[U]]]
    ): CheckableConstraint[U] =
        CheckableConstraint(Some(u), AllTermConstraints(constraints))

    def makeOption[U: {TermDef, TermDefDetails, Show}](
        ou         : Option[U],
        constraints: Seq[Option[TermConstraint[U]]]
    ): CheckableConstraint[U] =
        CheckableConstraint(ou, AllTermConstraints(constraints))
