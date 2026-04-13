/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.coulomb

import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import _root_.coulomb.*
import _root_.coulomb.syntax.*
import afpma.laminar.form.*

/** NumericFormValue instances for coulomb quantity types. */
object CoulombFormInstances:

    // =========================================================================
    // NumericFormValue[QtyD[U]] — quantity with unit display
    // =========================================================================

    given numericFormValueForQtyD[U: SUnit]: NumericFormValue[QtyD[U]] with
        def toDouble  (q: QtyD[U]): Double  = q.value
        def fromDouble(d: Double ): QtyD[U] = d.withUnit[U]
        def unitDisplays: List[UnitDisplay] =
            val su = SUnit[U]
            List(UnitDisplay(label = su.showUnitFull, abbreviation = su.showUnit))

    // =========================================================================
    // NumericFormValue[TempD[U]] — temperature with unit display
    // =========================================================================

    given numericFormValueForTempD[U: SUnit]: NumericFormValue[TempD[U]] with
        def toDouble  (t: TempD[U]): Double   = t.value
        def fromDouble(d: Double  ): TempD[U] = d.withTemperature[U]
        def unitDisplays: List[UnitDisplay] =
            val su = SUnit[U]
            List(UnitDisplay(label = su.showUnitFull, abbreviation = su.showUnit))

    // =========================================================================
    // Convenience methods for coulomb types (migration aliases)
    // =========================================================================

    import afpma.laminar.form.derivation.FormDerivation

    /** Form[QtyD[U]] from NumericFormValue — wraps Option variant with Defaultable. */
    def forQtyD[U: SUnit](using d: Defaultable[QtyD[U]], vv: ValidateVar[Option[QtyD[U]]]): Form[QtyD[U]] =
        FormDerivation.mkFromOptionFor_UseDefaultableIfEmptyInput(FormDerivation.forNumericFormValue[QtyD[U]])

    /** Form[Option[QtyD[U]]] — None-as-default. */
    def forOptionQtyD_default[U: SUnit]()(using vv: ValidateVar[Option[QtyD[U]]]): Form[Option[QtyD[U]]] =
        FormDerivation.forNumericFormValue[QtyD[U]]

    /** Form[TempD[U]] from NumericFormValue. */
    def forTempD[U: SUnit](using d: Defaultable[TempD[U]], vv: ValidateVar[Option[TempD[U]]]): Form[TempD[U]] =
        FormDerivation.mkFromOptionFor_UseDefaultableIfEmptyInput(FormDerivation.forNumericFormValue[TempD[U]])

    /** Form[Option[TempD[U]]] — None-as-default. */
    def forOptionTempD_default[U: SUnit]()(using vv: ValidateVar[Option[TempD[U]]]): Form[Option[TempD[U]]] =
        FormDerivation.forNumericFormValue[TempD[U]]

    /** Form[QtyD[U]] with validation — None is invalid. */
    def forValidatedQtyD_NoneAsDefault[U: SUnit]()(using
        d : Defaultable[QtyD[U]],
        vv: ValidateVar[Option[QtyD[U]]]
    ): Form[QtyD[U]] =
        FormDerivation.mkValidatedFromOptionFor_NoneAsDefault(
            foa = FormDerivation.forNumericFormValue[QtyD[U]]
        )

    // =========================================================================
    // Linked Var extension — bidirectional sync for unit-typed Vars
    // =========================================================================

    extension [A](form: Form[Option[A]])
        /**
         * Creates a new Form that bidirectionally syncs with a linked Var.
         *
         * Useful when a form field should stay in sync with another Var,
         * such as a field in a parent model.
         */
        def withLinkedVar(linkedVar: Var[Option[A]]): Form[Option[A]] =
            new Form[Option[A]]:
                def defaultable                                                       = form.defaultable
                def validateVar                                                       = form.validateVar
                def render(v: Var[Option[A]], config: FormConfig)(using FormRenderer) =
                    val syncToLinkedVar = v.signal.distinct.changes
                        .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                        .collect { case Some(x) => Some(x) }
                        .withCurrentValueOf(linkedVar.signal)
                        .collect { case (newVal, curVal) if newVal != curVal => newVal } --> linkedVar.writer

                    val syncFromLinkedVar = linkedVar.signal.distinct.changes
                        .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                        .withCurrentValueOf(v.signal)
                        .collect { case (newVal, curVal) if newVal != curVal => newVal } --> v.writer

                    form.render(v, config)
                        .amend(syncToLinkedVar, syncFromLinkedVar)
