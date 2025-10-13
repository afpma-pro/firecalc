/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.daisyui.DaisyUIVerticalForm
import afpma.firecalc.ui.formgen.*
import afpma.firecalc.ui.instances.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*

import io.taig.babel.Locale

object StoveParamsUI:

    // val mB_instances = QtyD_Instances[Kilogram]()(using sunit_Kilogram)
    // val pn_instances = QtyD_Instances[Kilo * Watt]()(using sunit_Kilowatt)
    // val tn_instances = QtyD_Instances[Hour]()(using sunit_Hour)
    // val nmin_instances = QtyD_Instances[Percent]()(using sunit_Percent)

    // import QtyD_Instances.given

    given default_StoveParams: Defaultable[StoveParams]:
        def default = StoveParams.fromMaxLoadAndStoragePeriod(
            maximum_load       = default_mB.default,
            heating_cycle      = default_tn.default,
            min_efficiency     = default_nmin.default,
            facing_type        = FacingType.WithoutAirGap
        )

    given default_mB: Defaultable[QtyD[Kilogram]]:
        def default = 10.kg

    given default_pn: Defaultable[QtyD[Kilo * Watt]]:
        def default = 6.kW

    given default_tn: Defaultable[QtyD[Hour]]:
        def default = 8.hours

    given default_nmin: Defaultable[QtyD[Percent]]:
        def default = 78.percent
    
case class StoveParamsUI()(using Locale, DisplayUnits):

    import StoveParamsUI.given
    import vertical_form.{given_Option_QtyD_Kilowatt as _, given}
    import dual.given
    import hastranslations.given

    type DF[A] = DaisyUIVerticalForm[A]

    given conditionalFor_mB: ConditionalFor[StoveParams, QtyD[Kilogram]] = 
        ConditionalFor(_.sizing_method == SizingMethod.MaxLoad)
    
    given conditionalFor_pn: ConditionalFor[StoveParams, QtyD[Kilo * Watt]] = 
        ConditionalFor(_.sizing_method == SizingMethod.NominalHeatOutput)

    given form_option_mB: DaisyUIVerticalForm[Option[QtyD[Kilogram]]] = 
        import validatevar.kilogram.valid_whenPositive
        given DF[QtyD[Kilogram]] = given_dual_Kilogram.form_DaisyUIVerticalForm
        DaisyUIVerticalForm
        .conditionalOn[StoveParams, QtyD[Kilogram]](stove_params_var)
        .withFieldName(I18N.en15544.terms.m_B.name)

    given form_option_pn: DaisyUIVerticalForm[Option[QtyD[Kilo * Watt]]] = 
        import validatevar.kilowatt.valid_whenPositive
        given DF[Power] = given_dual_Power.form_DaisyUIVerticalForm
        DaisyUIVerticalForm.conditionalOn[StoveParams, QtyD[Kilo * Watt]](stove_params_var)
        .withFieldName(I18N.en15544.terms.P_n.name)

    given form_either_mB_or_pn: DaisyUIVerticalForm[Either[QtyD[Kilogram], QtyD[Kilo * Watt]]] = 
        DaisyUIVerticalForm.eitherFromOption(
            stove_params_var,
            convert = _.mB_or_pn,
            revert = (sp, mB_or_pn) => mB_or_pn match
                case Left(mB)  => sp.with_mB(mB)
                case Right(pn) => sp.with_pn(pn)
        )

    given DaisyUIVerticalForm[StoveParams] = 
        DaisyUIVerticalForm.autoDerived[StoveParams]
        .autoOverwriteFieldNames  

    lazy val _form = stove_params_var.as_HtmlElement

end StoveParamsUI
