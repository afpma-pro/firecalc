/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.daisyui.DaisyUIVerticalForm
import afpma.firecalc.ui.formgen.*
import afpma.firecalc.ui.instances.*

import com.raquo.laminar.api.L.Signal

import coulomb.*

import io.taig.babel.Locale

object StoveParamsUI:

    // val mB_instances = QtyD_Instances[Kilogram]()(using sunit_Kilogram)
    // val pn_instances = QtyD_Instances[Kilo * Watt]()(using sunit_Kilowatt)
    // val tn_instances = QtyD_Instances[Hour]()(using sunit_Hour)
    // val nmin_instances = QtyD_Instances[Percent]()(using sunit_Percent)

    // import QtyD_Instances.given

    given default_StoveParams: Defaultable[StoveParams]:
        def default = StoveParams.fromMaxLoadAndStoragePeriod(
            maximum_load   = default_mB.default,
            heating_cycle  = default_tn.default,
            min_efficiency = default_nmin.default,
            facing_type    = FacingType.WithoutAirGap
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
    import hastranslations.given

    private given dual: DualCommonInstances = new DualCommonInstances()

    private given vertical_form: VerticalFormCommonInstances = new VerticalFormCommonInstances()
    import vertical_form.given

    private val vv: ValidateVarCommonInstances = ValidateVarCommonInstances()

    type DF[A] = DaisyUIVerticalForm[A]

    given conditionalFor_mB: ConditionalFor[StoveParams, QtyD[Kilogram]] =
        ConditionalFor(_.sizing_method == SizingMethod.MaxLoad)

    given conditionalFor_pn: ConditionalFor[StoveParams, QtyD[Kilo * Watt]] =
        ConditionalFor(_.sizing_method == SizingMethod.NominalHeatOutput)

    // Engine-computed values used as activation defaults when switching sizing method.
    // Since results_en15544_strict_sig is debounced, at switch time it still holds the
    // previous computation — giving us the derived value to carry over seamlessly.
    private val computedMbSignal: Signal[Option[QtyD[Kilogram]]]    =
        results_en15544_strict_sig.map(_.toOption.map(_.m_B))
    private val computedPnSignal: Signal[Option[QtyD[Kilo * Watt]]] =
        results_en15544_strict_sig.map(_.toOption.map(_.P_n))

    given form_option_mB: DaisyUIVerticalForm[Option[QtyD[Kilogram]]] =
        import vv.kilogram.valid_whenStrictlyPositive
        given DF[QtyD[Kilogram]] = dual.given_dual_Kilogram.form_DaisyUIVerticalForm
        DaisyUIVerticalForm
            .conditionalOn[StoveParams, QtyD[Kilogram]](
                stove_params_var,
                activationDefault = computedMbSignal
            )
            .withFieldName(I18N.en15544.terms.m_B.name)

    given form_option_pn: DaisyUIVerticalForm[Option[QtyD[Kilo * Watt]]] =
        import vv.kilowatt.valid_whenStrictlyPositive
        given DF[Power] = dual.given_dual_Power.form_DaisyUIVerticalForm
        DaisyUIVerticalForm
            .conditionalOn[StoveParams, QtyD[Kilo * Watt]](
                stove_params_var,
                activationDefault = computedPnSignal
            )
            .withFieldName(I18N.en15544.terms.P_n.name)

    given DaisyUIVerticalForm[StoveParams] =
        given DaisyUIVerticalForm[Option[Power]] = form_option_pn
        DaisyUIVerticalForm.autoDerived[StoveParams].autoOverwriteFieldNames

    lazy val _form = stove_params_var.as_HtmlElement

end StoveParamsUI
