/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.ui.daisyui.UnitAwareInputs

import cats.Functor
import cats.Id
import cats.syntax.all.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.annotation.nowarn

import _root_.coulomb.conversion.UnitConversion
import afpma.laminar.form.*
import afpma.laminar.form.FormRenderer
import afpma.laminar.form.derivation.FormDerivation.DISABLED_SIG

/**
 * Dual[A] helps build Form, Encoder and Decoder instances for a type A
 * using another "dual" representation D for which we provide Form, Encoder, Decoder instances
 *
 * Example: QtyD[Meter] is dual of InputQtyD[Meter, Inch]
 *
 * This type is helpful when we can't or (don't want to) modify the initial type A
 * It provided an ad-hoc mechanism to generate Form, Encoder, Decoder instances without touching type A
 */
trait DualQtyDF[F[_], UF: SUnit, UI: SUnit](using
    ucfi: UnitConversion[Double, UF, UI],
    ucif: UnitConversion[Double, UI, UF]
):
    self =>

    given Functor[F] = scala.compiletime.deferred
    def currentOValueToCurrentFValue(od: Option[Double], defaultIfNone: QFinal, currentUnit: SUnit[?]): F[Double]
    def currentFValueToCurrentOValue(fv: F[Double], currentUnit       : SUnit[?]                     ): Option[Double]

    protected def optionToOptionF[A](oa : Option[A]   ): Option[F[A]]
    protected def flattenOptionF[A] (ofa: Option[F[A]]): Option[A]

    type QFinal = F[QtyD[UF]]
    type QInit  = F[QtyD[UI]]

    def showFinalQty(qf: QtyD[UF]): String = SUnit[UF].showQtyDInstance.show(qf)

    case class AllowedSUnit[u](
        su  : SUnit[u],
        ucfx: UnitConversion[Double, UF, u],
        ucxf: UnitConversion[Double, u, UF]
    ):
        def makeQFinal_FromQCurrent(cfqu: F[QtyD[u]]): QFinal =
            cfqu.map(cqu => cqu.toUnit[UF](using ucxf))

        def makeQFinal_FromCurrentFValue(cfv: F[Double]): QFinal =
            val cfqu = cfv.map(cv => su.makeQtyD(cv))
            makeQFinal_FromQCurrent(cfqu)

        def makeCurrentQtyD_FromFinalQty(qf: QtyD[UF]): QtyD[u] =
            qf.toUnit[u](using ucfx)

        def makeCurrentValue_FromFinalQty(qf: QtyD[UF]): Double =
            makeCurrentQtyD_FromFinalQty(qf).value

    var allowed_sunits: Vector[AllowedSUnit[?]] =
        Vector(AllowedSUnit(SUnit[UI], ucfi, ucif))

    protected def getAllowedSUnit(su: SUnit[?]): AllowedSUnit[su.Unit] =
        allowed_sunits
            .find(_.su == su)
            .getOrElse(
                throw new IllegalStateException(
                    s"'${su.showUnitFull}' is a unit that is not references in the list: expecting one of ${allowed_sunits.map(_.su.showUnitFull)}"
                )
            )
            .asInstanceOf[AllowedSUnit[su.Unit]]

    def appendAllowed[U](using su: SUnit[U], ucfu: UnitConversion[Double, UF, U], ucuf: UnitConversion[Double, U, UF]) =
        allowed_sunits = allowed_sunits.appended(AllowedSUnit[U](su, ucfu, ucuf))
        this

    private def splitAndMakeVarsFor(
        finalVar: Var[QFinal]
    )(using d: Defaultable[QFinal]): (Var[Option[Double]], Var[SUnit[?]], List[SUnit[?]], Seq[Binder[HtmlElement]]) =

        val curr_sunit_var: Var[SUnit[?]] = Var(SUnit[UI])

        // create an async var with bidirectional link to hold current value "ofv" (double with 'current' unit)
        val (curr_ofvalue_var, bidir_async_binders) =
            VarSync.makeOptionVarFromVar_BiDirAsync_Tuple1[QFinal, F[Double]]    (
                finalVar,
                f     = (fq: QFinal) =>
                    val csu = curr_sunit_var.now()
                    fq.map(getAllowedSUnit(csu).makeCurrentValue_FromFinalQty)
                ,
                f_inv = (curr_v: F[Double]) =>
                    val csu = curr_sunit_var.now()
                    getAllowedSUnit(csu).makeQFinal_FromCurrentFValue(curr_v)
            )

        val curr_ovalue_var = curr_ofvalue_var.bimap(flattenOptionF)(optionToOptionF)

        val curr_sunit_to_qfinal_binder =
            curr_sunit_var.signal.distinct
                .withCurrentValueOf(curr_ovalue_var)
                .map: t =>
                    val (csu, cov) = t
                    val cfv = currentOValueToCurrentFValue(cov, d.default, csu)
                    getAllowedSUnit(csu).makeQFinal_FromCurrentFValue(cfv)
                .changes --> finalVar.writer

        // render underlying Form for Value and SUnit
        val sunits = allowed_sunits.map(_.su).toList

        val binders =
            bidir_async_binders
                :+ curr_sunit_to_qfinal_binder

        (curr_ovalue_var, curr_sunit_var, sunits, binders)

    def form(
        disabled: Signal[Boolean] = DISABLED_SIG
    )(using
        d   : Defaultable[QFinal],
        vvqf: ValidateVar[QFinal]
    ): Form[QFinal] =
        new Form[QFinal]:
            val defaultable = d
            lazy val validateVar    = vvqf
            @nowarn def render(
                finalVar  : Var[QFinal],
                formConfig: FormConfig
            )(using renderer: FormRenderer): L.HtmlElement =
                // Floating labels are a renderer concern — ask the renderer directly.
                val withFloatingLabel = renderer.usesFloatingLabels && formConfig.showFieldName
                val (curr_ovalue_var, curr_sunit_var, sunits, binders) = splitAndMakeVarsFor(finalVar)
                UnitAwareInputs
                    .NumberInputWithUnitsAndFloatingLabelAndTooltipValidation     (
                        curr_ovalue_var,
                        fieldNameOpt      = formConfig.shownFieldName,
                        withFloatingLabel = withFloatingLabel,
                        sunitsVar         = Var(sunits), // should be dynamic if config changes (SI or imperial)
                        sunitCurrentVar   = curr_sunit_var,
                        validate          = (cov, csu) =>
                            val curr_fv = currentOValueToCurrentFValue(cov, d.default, csu)
                            val qf      = getAllowedSUnit(csu).makeQFinal_FromCurrentFValue(curr_fv)
                            vvqf.validate(qf),
                        disabled          = disabled
                    )
                    .amend(binders)
            end render
    end form

object DualQtyDF:

    def makeForId[UF: SUnit, UI: SUnit](using
        ucfi: UnitConversion[Double, UF, UI],
        ucif: UnitConversion[Double, UI, UF]
    ): DualQtyDF[cats.Id, UF, UI] = new DualQtyDF[cats.Id, UF, UI]:

        given Functor[cats.Id] = Functor[cats.Id]

        protected def flattenOptionF[A] (ofa: Option[Id[A]]): Option[A]     = ofa
        protected def optionToOptionF[A](oa : Option[A]    ): Option[Id[A]] = oa

        /**
         * Convert current value to final value (using known current unit)
         *
         * @param od current underyling value with currentUnit
         * @param defaultIfNone default quantity to use if value is empty
         * @param currentUnit currently selected unit
         * @return value with final unit
         */
        def currentOValueToCurrentFValue(
            cov          : Option[Double],
            defaultIfNone: Id[QtyD[UF]],
            cu           : SUnit[?]
        ): Id[Double] =
            val coq        = cov.map(cu.makeQtyD)
            val coqDefault = defaultIfNone.map: fqDefault =>
                val casu = getAllowedSUnit(cu)
                casu.makeCurrentQtyD_FromFinalQty(fqDefault)
            coq.getOrElse(coqDefault).value

        def currentFValueToCurrentOValue(
            fv: Id[Double],
            cu: SUnit[?]
        ): Option[Double] =
            val cfu  = getAllowedSUnit(SUnit[UF])
            val cfq  = cfu.makeQFinal_FromCurrentFValue(fv)
            val casu = getAllowedSUnit(cu)
            val cv   = casu.makeCurrentValue_FromFinalQty(cfq)
            Some(cv)

    def makeForOption[UF: SUnit, UI: SUnit](using
        ucfi: UnitConversion[Double, UF, UI],
        ucif: UnitConversion[Double, UI, UF]
    ): DualQtyDF[Option, UF, UI] = new DualQtyDF[Option, UF, UI]:

        // given Functor[Option] = Functor[Option]

        protected def flattenOptionF[A] (ofa: Option[Option[A]]): Option[A]         = ofa.flatten
        protected def optionToOptionF[A](oa : Option[A]        ): Option[Option[A]] = Some(oa)

        def currentOValueToCurrentFValue(
            od           : Option[Double],
            defaultIfNone: Option[QtyD[UF]],
            currentUnit  : SUnit[?]
        ): Option[Double] =
            od

        def currentFValueToCurrentOValue(
            fv: Option[Double],
            cu: SUnit[?]
        ): Option[Double] =
            val cfu  = getAllowedSUnit(SUnit[UF])
            val cfq  = cfu.makeQFinal_FromCurrentFValue(fv)
            val casu = getAllowedSUnit(cu)
            cfq.map(casu.makeCurrentValue_FromFinalQty)

type DualQtyD[UF, UI]       = DualQtyDF[cats.Id, UF, UI]
type DualOptionQtyD[UF, UI] = DualQtyDF[Option, UF, UI]
