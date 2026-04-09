/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances
import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.utils.OptionOfEither
import afpma.firecalc.utils.OptionOfEither.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.gtypedefs.*

import afpma.laminar.form.Form
import afpma.laminar.form.derivation.FormDerivation
import afpma.firecalc.ui.daisyui.DaisyUIInputs.FieldsetLabelAndContent
import afpma.firecalc.ui.daisyui.DaisyUIInputs.SelectAndOptionsOnly
import afpma.laminar.form.*
import afpma.laminar.form.coulomb.CoulombFormInstances
import afpma.laminar.form.Form.*

import _root_.coulomb.*
import _root_.coulomb.policy.standard.given

import scala.deriving.Mirror

import io.taig.babel.Locale

class HorizontalFormCommonInstances(using DisplayUnits, Locale):

    import FormDerivation.given
    import SUnits.given
    // import defaultable.given
    
    given dual: DualCommonInstances = new DualCommonInstances()
    import dual.given

    private val vv: ValidateVarCommonInstances = ValidateVarCommonInstances()

    // factory helper
    inline def autoDeriveAndOverwriteFieldNames[A](using inline m: Mirror.Of[A]): Form[A] =
        import hastranslations.given
        import afpma.laminar.form.derivation.FormDerivation.autoOverwriteFieldNames
        FormDerivation
            .derived[A](using m)
            .autoOverwriteFieldNames

    // BasicTypes

    val boolean_trueAsDefault_alwaysValid: Form[Boolean] =
        import defaultable.boolean.asTrue
        import ValidateVarCommonInstances.boolean.valid_Always
        FormDerivation.forBoolean

    val boolean_falseAsDefault_alwaysValid: Form[Boolean] =
        import defaultable.boolean.asFalse
        import ValidateVarCommonInstances.boolean.valid_Always
        FormDerivation.forBoolean

    val string_emptyAsDefault_alwaysValid: Form[String] =
        import defaultable.string.empty
        import ValidateVarCommonInstances.string.validOption_Always
        FormDerivation.forString

    val int_emptyAsDefault_alwaysValid: Form[Int] =
        import defaultable.int.empty
        import ValidateVarCommonInstances.int.validOption_Always
        FormDerivation.forInt

    val double_emptyAsDefault_alwaysValid: Form[Double] =
        import defaultable.double.empty
        import ValidateVarCommonInstances.double.validOption_Always
        FormDerivation.forDouble

    // AreaInCm2 + Option[AreaInCm2]

    given horizontal_form_AreaInCm2: DisplayUnits => Form[AreaInCm2] =
        import defaultable.qty_d.area_in_cm2.zero
        import vv.area_in_cm2.valid_whenStrictlyPositive
        given_dual_Area_cm2_or_in2.form_horizontal()

    given horizontal_form_Option_AreaInCm2: DisplayUnits => Form[Option[AreaInCm2]] =
        import defaultable.qty_d.option.area_in_cm2.zero
        import vv.area_in_cm2.validOption_whenStrictlyPositive
        given_dual_Option_Area.form_horizontal()

    // given horizontal_form_AddFlowResistance: Locale => DaisyUI5HorizontalForm[AddFlowResistance] =
    //     DaisyUI5HorizontalForm.derived[AddFlowResistance]
    //     .autoOverwriteFieldNames

    // given horizontal_form_AddSection: Locale => DaisyUI5HorizontalForm[AddSection] =
    //     import defaultable.qty_d.meter.zero
    //     DaisyUI5HorizontalForm.derived[AddSection]
    //     .autoOverwriteFieldNames

    // given horizontal_form_AddSectionHorizontal: Locale => DaisyUI5HorizontalForm[AddSectionHorizontal] =
    //     import defaultable.qty_d.meter.zero
    //     DaisyUI5HorizontalForm.derived[AddSectionHorizontal]
    //     .autoOverwriteFieldNames

    // given horizontal_form_AddSectionVertical: Locale => DaisyUI5HorizontalForm[AddSectionVertical] =
    //     import defaultable.qty_d.meter.zero
    //     DaisyUI5HorizontalForm.derived[AddSectionVertical]
    //     .autoOverwriteFieldNames

    // PipeShape

    given horizontal_form_PipeShape_Circle: DisplayUnits => Locale => Form[PipeShape.Circle] =
        import defaultable.qty_d.meter.zero
        import vv.meter.valid_whenStrictlyPositive
        given Form[QtyD[Meter]] = given_dual_Length_mm_cm.form_horizontal()
        autoDeriveAndOverwriteFieldNames[PipeShape.Circle]

    given horizontal_form_PipeShape_Square: DisplayUnits => Locale => Form[PipeShape.Square] =
        import defaultable.qty_d.meter.zero
        import vv.meter.valid_whenStrictlyPositive
        given Form[QtyD[Meter]] = given_dual_Length_mm_cm.form_horizontal()
        autoDeriveAndOverwriteFieldNames[PipeShape.Square]

    given horizontal_form_PipeShape_Rectangle: DisplayUnits => Locale => Form[PipeShape.Rectangle] =
        import defaultable.qty_d.meter.zero
        import vv.meter.valid_whenStrictlyPositive
        given Form[QtyD[Meter]] = given_dual_Length_mm_cm.form_horizontal()
        autoDeriveAndOverwriteFieldNames[PipeShape.Rectangle]

    given horizontal_form_PipeShape: DisplayUnits => Locale => Form[PipeShape] =
        autoDeriveAndOverwriteFieldNames[PipeShape]

    // HeatOutputReduced

    given given_HeatOutputReduced_NotDefined_or_HalfOfNominal: Locale
        => Form[
            HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal
        ] =
        val halfOfNominalDefault                                                          = HeatOutputReduced.HalfOfNominal.makeWithoutValue
        given Defaultable[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] = Defaultable(
            halfOfNominalDefault
        )
        FormDerivation
            .mk_AlwaysValid[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal]: (vpn, _) =>
                FieldsetLabelAndContent  (
                    label   = I18N.en15544.terms.P_n_reduced.name,
                    content =
                        SelectAndOptionsOnly.fromShow[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal]          (
                            selectedVar           = vpn,
                            labelAsDisabledOption = None,
                            options               = Seq(HeatOutputReduced.NotDefined, halfOfNominalDefault)
                        )
                )

    // QtyD[Degree] = Angle

    given horizontal_form_Angle: Form[Angle] =
        import defaultable.qty_d.angle.ninety
        import vv.angle.validOption_whenPositive
        CoulombFormInstances.forQtyD[Degree]

    // QtyD[Kilo * Watt]

    given horizontal_form_Kilowatt: Form[QtyD[Kilo * Watt]] =
        import defaultable.qty_d.kilowatt.zero
        import vv.kilowatt.validOption_whenStrictlyPositive
        CoulombFormInstances.forQtyD[Kilo * Watt]

    given horizontal_form_Option_Kilowatt: Form[Option[QtyD[Kilo * Watt]]] =
        import vv.kilowatt.validOption_whenStrictlyPositive
        CoulombFormInstances.forOptionQtyD_default[Kilo * Watt]()

    // QtyD[Meter]

    val horizontal_form_QtyD_Meter: Form[QtyD[Meter]] =
        import ValidateVarCommonInstances.valid_always.given
        import defaultable.qty_d.meter.zero
        CoulombFormInstances.forQtyD[Meter]

    val horizontal_form_Length_cm_m: Form[QtyD[Meter]] =
        import ValidateVarCommonInstances.valid_always.given
        import defaultable.qty_d.meter.zero
        given_dual_Length_cm_m.form_horizontal()

    val horizontal_form_Length_mm_cm: Form[QtyD[Meter]] =
        import ValidateVarCommonInstances.valid_always.given
        import defaultable.qty_d.meter.zero
        given_dual_Length_mm_cm.form_horizontal()

    val horizontal_form_QtyD_Pascal: Form[QtyD[Pascal]] =
        import ValidateVarCommonInstances.valid_always.given
        import defaultable.qty_d.pascal.zero
        CoulombFormInstances.forQtyD[Pascal]

    given horizontal_form_QtyD_SquareMeterKelvinPerWatt: Form[SquareMeterKelvinPerWatt] =
        given Defaultable[SquareMeterKelvinPerWatt] = defaultable.thermalResistance.map(x => x)
        import vv.square_meter_kelvin_per_watt.validOption_whenStrictlyPositive
        CoulombFormInstances.forValidatedQtyD_NoneAsDefault[(Meter ^ 2) * Kelvin / Watt]()

    given horizontal_form_QtyD_Unitless: Form[QtyD[1]] =
        given zeta_defaultable: Defaultable[QtyD[1]] = defaultable.zeta.map(z => z: QtyD[1])
        import vv.unitless.validOption_whenPositive
        CoulombFormInstances.forQtyD[1]

    given horizontal_form_QtyD_Watt_per_MeterKelvin: Form[QtyD[Watt / (Meter * Kelvin)]] =
        given Defaultable[QtyD[Watt / (Meter * Kelvin)]] = defaultable.thermalConductivity.map(x => x)
        import vv.watt_per_meter_kelvin.validOption_whenStrictlyPositive
        CoulombFormInstances.forValidatedQtyD_NoneAsDefault[Watt / (Meter * Kelvin)]()

    // Roughness

    val horizontal_form_Roughness: Form[QtyD[Meter]] =
        import defaultable.given_Roughness
        import vv.meter.valid_whenStrictlyPositive
        given_dual_Roughness.form_horizontal()
            .withFieldName(I18N.terms.roughness)

    given horizontal_form_TCelsius: Locale => Form[TCelsius] =
        import defaultable.given_TCelsius
        import vv.temp.celsius.validOption_whenPositive
        CoulombFormInstances
            .forTempD[Celsius]
            .withFieldName(I18N.terms.temperature_celsius)

    given horizontal_form_Either_AreaInCm2_or_PipeShape
        : DisplayUnits => Locale => Form[OptionOfEither[AreaInCm2, PipeShape]] =
        // import defaultable.pipeShapeInner
        // import defaultable.qty_d.area_in_cm2.zero
        import afpma.firecalc.ui.instances.defaultable.qty_d.area_in_cm2.zero

        import vv.area_in_cm2.valid_whenStrictlyPositive
        given Form[PipeShape]  = horizontal_form_PipeShape
        given Form[AreaInCm2]  = given_dual_Area_cm2_or_in2.form_horizontal()
            .withFieldName(I18N.terms.area)

        import afpma.laminar.form.derivation.{OptionOfEither as DerivOOE, NoneOfEither as DerivNone, SomeLeft as DerivSL, SomeRight as DerivSR}
        FormDerivation.optionOfEither[AreaInCm2, PipeShape](
            noneLabel  = "-",
            leftLabel  = I18N.terms.area,
            rightLabel = I18N.terms.pipe_shape._self
        ).bimap[OptionOfEither[AreaInCm2, PipeShape]](derivOoe => derivOoe match
            case DerivNone    => NoneOfEither
            case DerivSL(l)   => SomeLeft(l)
            case DerivSR(r)   => SomeRight(r)
        )(utilsOoe => utilsOoe match
            case NoneOfEither => DerivNone
            case SomeLeft(l)  => DerivSL(l)
            case SomeRight(r) => DerivSR(r)
        )

    // ThermalConductivity

    given horizontal_form_ThermalConductivity: Form[ThermalConductivity.Type] =
        // given DaisyUI5Defaultable[QtyD[Watt / (Meter * Kelvin)]] = defaultable.thermalConductivity.map(x => x)
        Form.formConversionOpaque[ThermalConductivity.Type, QtyD[Watt / (Meter * Kelvin)]]

    // Thickness

    def horizontal_form_Thickness: Form[QtyD[Meter]] =
        import defaultable.qty_d.meter.zero
        // import validatevar.meter.validOption_whenStrictlyPositive
        import vv.meter.valid_whenStrictlyPositive
        // CoulombFormInstances.forValidatedQtyD_NoneAsDefault[Meter]()
        given_dual_Thickness.form_horizontal()

    // Zeta ζ

    val horizontal_form_zeta: Form[ζ] = horizontal_form_ζ
    given horizontal_form_ζ : Form[ζ] =
        given zeta_defaultable: Defaultable[QtyD[1]] = defaultable.zeta.map(z => z: QtyD[1])
        import vv.unitless.validOption_whenPositive
        given Form[QtyD[1]] = CoulombFormInstances.forQtyD[1]
        Form.formConversionOpaque[ζ, QtyD[1]]

    // AreaHeatingStatus

    given horizontal_form_AreaHeatingStatus: Locale => Form[AreaHeatingStatus] =
        autoDeriveAndOverwriteFieldNames[AreaHeatingStatus]

    given horizontal_form_AreaHeatingStatus_Heated: Locale => Form[AreaHeatingStatus.Heated] =
        autoDeriveAndOverwriteFieldNames[AreaHeatingStatus.Heated]

    given horizontal_form_AreaHeatingStatus_NotHeated: Locale => Form[AreaHeatingStatus.NotHeated] =
        autoDeriveAndOverwriteFieldNames[AreaHeatingStatus.NotHeated]

    // AzimuthDirection — select over named cases

    given horizontal_form_AzimuthDirection: Locale => Form[AzimuthDirection] =
        import cats.Show
        import afpma.firecalc.ui.i18n.implicits.I18N_UI
        given Show[AzimuthDirection] = Show.show:
            case AzimuthDirection.Rear       => I18N_UI.direction_badge.cardinal_rear
            case AzimuthDirection.RearRight  => s"${I18N_UI.direction_badge.cardinal_rear}-${I18N_UI.direction_badge.cardinal_right}"
            case AzimuthDirection.Right      => I18N_UI.direction_badge.cardinal_right
            case AzimuthDirection.FrontRight => s"${I18N_UI.direction_badge.cardinal_front}-${I18N_UI.direction_badge.cardinal_right}"
            case AzimuthDirection.Front      => I18N_UI.direction_badge.cardinal_front
            case AzimuthDirection.FrontLeft  => s"${I18N_UI.direction_badge.cardinal_front}-${I18N_UI.direction_badge.cardinal_left}"
            case AzimuthDirection.Left       => I18N_UI.direction_badge.cardinal_left
            case AzimuthDirection.RearLeft   => s"${I18N_UI.direction_badge.cardinal_rear}-${I18N_UI.direction_badge.cardinal_left}"
            case AzimuthDirection.Custom(az) => s"${az.value}\u00b0"
        given Defaultable[AzimuthDirection] = Defaultable(AzimuthDirection.Rear)
        given ValidateVar[AzimuthDirection] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[AzimuthDirection]
        FormDerivation
            .forEnumOrSumTypeLike_UsingShowAsId[AzimuthDirection](AzimuthDirection.namedCases)

    // InclinationDirection — select over named cases

    given horizontal_form_InclinationDirection: Locale => Form[InclinationDirection] =
        import cats.Show
        import afpma.firecalc.ui.i18n.implicits.I18N_UI
        given Show[InclinationDirection] = Show.show:
            case InclinationDirection.Up         => I18N_UI.direction_badge.cardinal_up
            case InclinationDirection.Down       => I18N_UI.direction_badge.cardinal_down
            case InclinationDirection.Horizontal => I18N_UI.direction_badge.cardinal_horizontal
            case InclinationDirection.Custom(el) => s"${el.value}\u00b0"
        given Defaultable[InclinationDirection] = Defaultable(InclinationDirection.Up)
        given ValidateVar[InclinationDirection] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[InclinationDirection]
        FormDerivation
            .forEnumOrSumTypeLike_UsingShowAsId[InclinationDirection](InclinationDirection.namedCases)

    // SetInitialDirection — shared rendering helper for all pipe types.
    // Each pipe-specific HorizontalForm class calls this with zoomed Vars.

    def renderInitialDirectionForm(
        azVar  : com.raquo.airstream.state.Var[AzimuthDirection],
        inclVar: com.raquo.airstream.state.Var[InclinationDirection]
    )(using FormRenderer): com.raquo.laminar.api.L.HtmlElement =
        import com.raquo.laminar.api.L.*
        import afpma.firecalc.ui.i18n.implicits.I18N_UI
        import afpma.firecalc.ui.components.CustomDirectionDialog
        import com.raquo.airstream.core.Observer

        given ValidateVar[AzimuthDirection] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[AzimuthDirection]
        given ValidateVar[InclinationDirection] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[InclinationDirection]

        val azForm   = horizontal_form_AzimuthDirection.render(azVar, FormConfig(fieldName = Some(I18N.terms.azimuth)))
        val inclForm = horizontal_form_InclinationDirection.render(inclVar, FormConfig(fieldName = Some(I18N.terms.inclination)))

        val dialog = CustomDirectionDialog(
            onApply = Observer[(AzimuthDirection, InclinationDirection)]: (az, incl) =>
                azVar.set(az)
                inclVar.set(incl)
        )

        // Badge showing custom degree values when not a named case
        val isCustom = azVar.signal
            .combineWith(inclVar.signal)
            .map: (az, incl) =>
                az.isInstanceOf[AzimuthDirection.Custom] || incl.isInstanceOf[InclinationDirection.Custom]

        val customBadge = span(
            cls := "badge badge-ghost badge-sm font-mono text-xs",
            display <-- isCustom.map(if _ then "inline-flex" else "none"),
            child.text <-- azVar.signal.combineWith(inclVar.signal).map: (az, incl) =>
                val azDeg   = AzimuthDirection.toDegrees(az)
                val inclDeg = InclinationDirection.toDegrees(incl)
                s"${azDeg}\u00b0 / ${inclDeg}\u00b0"
        )

        val customBtn = button(
            cls := "btn btn-xs btn-outline",
            tpe := "button",
            I18N_UI.direction_badge.custom_btn,
            onClick --> { _ => dialog.open(azVar.now(), inclVar.now()) }
        )

        div(
            cls := "flex flex-row gap-1 items-center",
            div(cls := "flex-auto", azForm),
            div(cls := "flex-auto", inclForm),
            customBadge,
            customBtn,
            dialog.node
        )

end HorizontalFormCommonInstances
