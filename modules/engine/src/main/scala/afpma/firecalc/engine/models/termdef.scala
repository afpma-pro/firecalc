/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import scala.math.Ordering.Implicits.*

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import afpma.firecalc.engine.models.TermConstraint.*

import afpma.firecalc.i18n.Localized
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.units.coulombutils.showP
import io.taig.babel.Locale

// TermDef

sealed class TermDefDetails[Q](
    val name: Localized[String],
    val description: Localized[String],
)

object TermDefDetails:
    def apply[Q](using instance: TermDefDetails[Q]): TermDefDetails[Q] = instance

    def apply[Q](
        name: Localized[String],
        description: Localized[String],
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
        symbol: String,
    ): TermDef[Q] =
        TermDef(symbol, source = "")

    // conversions
    def fromTermDefToTermDefOption[A]
        : Conversion[TermDef[A], TermDef[Option[A]]] =
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
enum TermConstraintError[O](val o: O):
    // Backward compatible: errors with hardcoded strings (store Show instance, compute message on demand)
    case MinError(min: O, override val o: O, showO: Show[O])
        extends TermConstraintError(o)

    case MaxError(max: O, override val o: O, showO: Show[O])
        extends TermConstraintError(o)

    case GenericError(override val o: O, failMsgString: String)
        extends TermConstraintError(o)

    // NEW: Typed errors with i18n support (message computed on-demand with Locale)
    case TypedError(override val o: O, error: Any, showError: Any => Locale ?=> String)
        extends TermConstraintError(o)

    // Locale-aware error message generation
    def failMsg(using Locale): String = this match
        case MinError(min, value, showO) =>
            given Show[O] = showO
            I18N.errors.term_constraint_min_error(value.showP, min.showP)
        case MaxError(max, value, showO) =>
            given Show[O] = showO
            I18N.errors.term_constraint_max_error(value.showP, max.showP)
        case GenericError(_, msg) =>
            msg
        case TypedError(_, error, showFn) =>
            showFn(error)

sealed abstract class TermConstraint[O](
    // val termName: String,
    // termNameLatex: String,
    // val descr: String,
    // constraintLatex: String,
    val source: String,
    val validate: O => TermConstraint.ValidatedResult[O]
)

object TermConstraint:

    opaque type ValidatedResult[O] = ValidatedNel[TermConstraintError[O], O]

    extension [O](o: O) def validResult: ValidatedResult[O] = o.validNel
    extension [O](tce: TermConstraintError[O])
        def invalidResult: ValidatedResult[O] = tce.invalidNel

    object ValidatedResult:

        extension [O](vr: ValidatedResult[O])
            def isValid = vr.isValid
            def isInvalid = vr.isInvalid

        extension [O](vr: ValidatedResult[O])
            def unwrap: ValidatedNel[TermConstraintError[O], O] = vr
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
                ): ValidatedResult[U] =
                    (x, y) match {
                        case (Valid(_), Valid(_))       => u.validNel
                        case (i @ Invalid(_), Valid(_)) => i
                        case (Valid(_), i @ Invalid(_)) => i
                        case (i1 @ Invalid(_), i2 @ Invalid(_)) =>
                            i1.combine(i2)
                    }
            }

    end ValidatedResult

    // instances for show
    given showValidatedResult: [Q] => (td: TermDef[Q], tdd: TermDefDetails[Q], loc: Locale) => Show[ValidatedResult[Q]] =
        showValidatedResultUsingSymbol[Q](using td.symbol, tdd.name, loc)

    given showValidatedResultUsingSymbol: [Q] => (
        symbol: String,
        name: String,
        loc: Locale
    ) => Show[ValidatedResult[Q]] = Show.show(o =>
        o match
            case Invalid(nel) =>
                given Locale = loc
                val errorsShow = showTermConstraintErrorNel[Q].show(nel)
                s"$errorsShow"
            case Valid(_) =>
                s"Valid '${symbol}' - $name"
    )

    def showTermConstraintErrorNel[O](using Locale): Show[NonEmptyList[TermConstraintError[O]]] =
        Show.show(nel =>
            nel.toList
                .map(tces => tces.failMsg)
                .mkString(" & ")
        )

    // ShowConstraint
    opaque type ShowConstraint[O] = Show[O]
    object ShowConstraint:
        def show[O](f: O => String): ShowConstraint[O] = Show.show(f)

    extension [O](sc: ShowConstraint[O]) def show(o: O): String = sc.show(o)

    // ShowConstraint instances & helpers

    // helpers
    private val showSource = Show.show[String](src => s"(cf $src)")

    // subtypes of TermConstraint

    case class Min[O: {Show, Ordering}](
        min: O,
        override val source: String
    ) extends TermConstraint[O](
            source,
            validate = u =>
                if (min <= u) u.validResult
                else TermConstraintError.MinError(min, u, summon[Show[O]]).invalidResult
        )

    object Min:
        def apply[Q: {Show, Ordering}](min: Q): Min[Q] =
            Min[Q](min, "")

    case class Max[Q: {Show, Ordering}](
        max: Q,
        override val source: String
    ) extends TermConstraint[Q](
            source,
            validate = u =>
                if (max >= u) u.validResult
                else TermConstraintError.MaxError(max, u, summon[Show[Q]]).invalidResult
        )

    object Max:
        def apply[Q: {Show, Ordering}](max: Q): Max[Q] =
            Max[Q](max, "")

    case class Generic[Q](
        value: Q,
        override val source: String,
        isValid: Q => Either[String, Q]
    ) extends TermConstraint[Q](
            source,
            validate = q =>
                isValid(q) match
                    case Right(q) => q.validResult
                    case Left(errMsg) =>
                        TermConstraintError.GenericError(value, errMsg).invalidResult
        )

    object Generic:

        def apply[Q](
            value: Q,
            isValid: Q => Either[String, Q]
        ): Generic[Q] =
            Generic[Q](
                value,
                "",
                isValid
            )

    // NEW: Typed error variant with i18n support
    case class GenericTyped[Q, E](
        value: Q,
        override val source: String,
        isValid: Q => Either[E, Q]
    )(using showE: ShowUsingLocale[E]) extends TermConstraint[Q](
            source,
            validate = q =>
                isValid(q) match
                    case Right(q) => q.validResult
                    case Left(error) =>
                        val showFn: Any => Locale ?=> String = (e: Any) => showE.show(e.asInstanceOf[E])
                        TermConstraintError.TypedError(value, error, showFn).invalidResult
        )

    object GenericTyped:
        def apply[Q, E: ShowUsingLocale](
            value: Q,
            isValid: Q => Either[E, Q]
        ): GenericTyped[Q, E] =
            GenericTyped[Q, E](value, "", isValid)


    // instances
    given showConstraintMin: [O] => ShowConstraint[Min[O]] =
        ShowConstraint.show[Min[O]](c =>
            s"${c.min} ${showSource.show(c.source)}"
        )

    given showConstraintMax: [O] => ShowConstraint[Max[O]] =
        ShowConstraint.show[Max[O]](c =>
            s"${c.max} ${showSource.show(c.source)}"
        )

    given showConstraintGeneric: [O] => ShowConstraint[Generic[O]] =
        ShowConstraint.show[Generic[O]](c =>
            s"${showSource.show(c.source)}"
        )



// AllTermConstraints


trait AllTermConstraintsFactory:
    def initConstraintsFor[U]: TermDef[U] ?=> AllTermConstraints[U] =
        initConstraintsFor[U](id = s"${TermDef[U].symbol}_constraints")

    def initConstraintsFor[U](id: String = ""): AllTermConstraints[U]

object AllTermConstraintsFactory:
    def make: AllTermConstraintsFactory =
        new AllTermConstraintsFactory {

            def initConstraintsFor[U](
                id: String = ""
            ): AllTermConstraints[U] =
                new AllTermConstraints[U]:

                    import scala.collection.mutable
                    import TermConstraint.ValidatedResult

                    type OTC = Option[TermConstraint[U]]
                    type EL = OTC

                    private val _tconstraints: mutable.ListBuffer[EL] =
                        synchronized { new mutable.ListBuffer() }

                    def getAll: Seq[EL] =
                        synchronized { _tconstraints.toSeq }

                    def append(tc: TermConstraint[U]): Unit =
                        appendOpt(Some(tc)) // lazy

                    def appendOpt(otc: OTC): Unit =
                        synchronized { _tconstraints.append(otc) }

                    def checkAll: U ?=> Seq[ValidatedResult[U]] =
                        checkAllFromValue(summon[U])

                    private def checkAllFromValue(u: U): Seq[ValidatedResult[U]] =
                        _tconstraints.toList.flatten.map(_.validate(u))

                    def checkAllOpt: Option[U] ?=> Seq[Option[ValidatedResult[U]]] =
                        summon[Option[U]] match
                            case Some(u) =>
                                _tconstraints.toList.map:
                                    case Some(tc)   => tc.validate(u).some
                                    case None       => None
                            case None => 
                                _tconstraints.toList.flatten.map(_ => None)

                    private def checkAllFromValueAndCombine(
                        u: U
                    ): ValidatedResult[U] =
                        val sequenced = checkAllFromValue(u)
                        given Monoid[ValidatedResult[U]] =
                            ValidatedResult.mkMonoid[U](u)
                        sequenced.combineAll

                    def checkAllAndCombine: U ?=> ValidatedResult[U] =
                        val u = summon[U]
                        checkAllFromValueAndCombine(u)

                    def checkAllAndCombineWhenDefined
                        : Option[U] ?=> Option[ValidatedResult[U]] =
                        val ou = summon[Option[U]]
                        ou match {
                            case Some(u) =>
                                Some(checkAllFromValueAndCombine(u))
                            case None => 
                                None
                        }

        }
trait AllTermConstraints[U]:
    
    import TermConstraint.ValidatedResult

    def getAll: Seq[Option[TermConstraint[U]]]
    def append(tc: TermConstraint[U]): Unit
    def appendOpt(otc: Option[TermConstraint[U]]): Unit
    def checkAll: U ?=> Seq[ValidatedResult[U]]
    def checkAllOpt: Option[U] ?=> Seq[Option[ValidatedResult[U]]]
    def checkAllAndCombine: U ?=> ValidatedResult[U]
    def checkAllAndCombineWhenDefined: Option[U] ?=> Option[ValidatedResult[U]]

object AllTermConstraints:

    def constraintsFor[U]
        : AllTermConstraints[U] ?=> AllTermConstraints[U] =
        summon[AllTermConstraints[U]]

case class CheckableConstraint[U](
    value: Option[U],
    alltc: AllTermConstraints[U]
)(using val td: TermDef[U], val tdd: TermDefDetails[U], val sh: Show[U]) {
    val termDef: TermDef[U] = td
    val termDefDetails: TermDefDetails[U] = tdd
    val vresultOption: Option[ValidatedResult[U]] = 
        alltc.checkAllAndCombineWhenDefined(using value)
}

object CheckableConstraint:
    
    def make[U: {TermDef, TermDefDetails, Show}](u: U)(using alltcs: AllTermConstraints[U]): CheckableConstraint[U] = 
        CheckableConstraint(Some(u), alltcs)

    def makeOption[U: {TermDef, TermDefDetails, Show}](ou: Option[U])(using alltcs: AllTermConstraints[U]): CheckableConstraint[U] = 
        CheckableConstraint(ou, alltcs)

    