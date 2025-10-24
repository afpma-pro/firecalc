/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances
import scala.deriving.Mirror

import afpma.firecalc.engine.models.gtypedefs.*

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.daisyui.DaisyUIHorizontalForm
import afpma.firecalc.ui.daisyui.DaisyUIInputs.FieldsetLabelAndContent
import afpma.firecalc.ui.daisyui.DaisyUIInputs.SelectAndOptionsOnly
import afpma.firecalc.ui.formgen.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.utils.OptionOfEither
import afpma.firecalc.utils.OptionOfEither.*
import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import io.taig.babel.Locale

object horizontal_form:

    type DF[A]    = DaisyUIHorizontalForm[A]
    type CtxDF[A] = DisplayUnits ?=> Locale ?=> DaisyUIHorizontalForm[A]
    type LocDF[A] = Locale ?=> DaisyUIHorizontalForm[A]

    import DaisyUIHorizontalForm.given
    import SUnits.given
    // import defaultable.given
    import dual.given

    // factory helper
    // inline def autoDeriveAndOverwriteFieldNames[A](using inline m: Mirror.Of[A], l: Locale): DaisyUIHorizontalForm[A] = 
    inline def autoDeriveAndOverwriteFieldNames[A](using inline m: Mirror.Of[A]): LocDF[A] = 
        import hastranslations.given
        import afpma.firecalc.ui.daisyui.DaisyUIHorizontalForm.autoOverwriteFieldNames
        DaisyUIHorizontalForm
            .derived[A](using m)
            .autoOverwriteFieldNames

    // BasicTypes

    val boolean_trueAsDefault_alwaysValid: DaisyUIHorizontalForm[Boolean] = 
        import defaultable.boolean.asTrue
        import validatevar.boolean.valid_Always
        DaisyUIHorizontalForm.forBoolean

    val boolean_falseAsDefault_alwaysValid: DaisyUIHorizontalForm[Boolean] = 
        import defaultable.boolean.asFalse
        import validatevar.boolean.valid_Always
        DaisyUIHorizontalForm.forBoolean

    val string_emptyAsDefault_alwaysValid: DaisyUIHorizontalForm[String] = 
        import defaultable.string.empty
        import validatevar.string.validOption_Always
        DaisyUIHorizontalForm.forString

    val int_emptyAsDefault_alwaysValid: DaisyUIHorizontalForm[Int] = 
        import defaultable.int.empty
        import validatevar.int.validOption_Always
        DaisyUIHorizontalForm.forInt
    
    val double_emptyAsDefault_alwaysValid: DaisyUIHorizontalForm[Double] = 
        import defaultable.double.empty
        import validatevar.double.validOption_Always
        DaisyUIHorizontalForm.forDouble

    // AreaInCm2 + Option[AreaInCm2]

    given horizontal_form_AreaInCm2: DisplayUnits => DaisyUIHorizontalForm[AreaInCm2] = 
        import defaultable.qty_d.area_in_cm2.zero
        import validatevar.area_in_cm2.valid_whenPositive
        given_dual_Area_cm2_or_in2.form_DaisyUIHorizontalForm

    given horizontal_form_Option_AreaInCm2: DisplayUnits => DaisyUIHorizontalForm[Option[AreaInCm2]] = 
        import defaultable.qty_d.option.area_in_cm2.zero
        import validatevar.area_in_cm2.validOption_whenPositive
        given_dual_Option_Area.form_DaisyUIHorizontalForm

    // given horizontal_form_AddFlowResistance: Locale => DaisyUI5HorizontalForm[AddFlowResistance] =
    //     DaisyUI5HorizontalForm.autoDerived[AddFlowResistance]
    //     .autoOverwriteFieldNames

    // given horizontal_form_AddSection: Locale => DaisyUI5HorizontalForm[AddSection] =
    //     import defaultable.qty_d.meter.zero
    //     DaisyUI5HorizontalForm.autoDerived[AddSection]
    //     .autoOverwriteFieldNames

    // given horizontal_form_AddSectionHorizontal: Locale => DaisyUI5HorizontalForm[AddSectionHorizontal] =
    //     import defaultable.qty_d.meter.zero
    //     DaisyUI5HorizontalForm.autoDerived[AddSectionHorizontal]
    //     .autoOverwriteFieldNames

    // given horizontal_form_AddSectionVertical: Locale => DaisyUI5HorizontalForm[AddSectionVertical] =
    //     import defaultable.qty_d.meter.zero
    //     DaisyUI5HorizontalForm.autoDerived[AddSectionVertical]
    //     .autoOverwriteFieldNames

    // PipeShape
    
    given horizontal_form_PipeShape_Circle: DisplayUnits => Locale => DaisyUIHorizontalForm[PipeShape.Circle] =
        import defaultable.qty_d.meter.zero
        import validatevar.meter.valid_whenPositive
        given DF[QtyD[Meter]] = given_dual_Length_mm_cm.form_DaisyUIHorizontalForm
        autoDeriveAndOverwriteFieldNames[PipeShape.Circle]
    
    given horizontal_form_PipeShape_Square: DisplayUnits => Locale => DaisyUIHorizontalForm[PipeShape.Square] =
        import defaultable.qty_d.meter.zero
        import validatevar.meter.valid_whenPositive
        given DF[QtyD[Meter]] = given_dual_Length_mm_cm.form_DaisyUIHorizontalForm
        autoDeriveAndOverwriteFieldNames[PipeShape.Square]
    
    given horizontal_form_PipeShape_Rectangle: DisplayUnits => Locale => DaisyUIHorizontalForm[PipeShape.Rectangle] =
        import defaultable.qty_d.meter.zero
        import validatevar.meter.valid_whenPositive
        given DF[QtyD[Meter]] = given_dual_Length_mm_cm.form_DaisyUIHorizontalForm
        autoDeriveAndOverwriteFieldNames[PipeShape.Rectangle]

    given horizontal_form_PipeShape: DisplayUnits => Locale => DaisyUIHorizontalForm[PipeShape] =
        autoDeriveAndOverwriteFieldNames[PipeShape]
    
    // HeatOutputReduced

    given given_HeatOutputReduced_NotDefined_or_HalfOfNominal: Locale => DaisyUIHorizontalForm[
        HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal
    ] =
        val halfOfNominalDefault = HeatOutputReduced.HalfOfNominal.makeWithoutValue
        given Defaultable[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] = Defaultable(halfOfNominalDefault)
        DaisyUIHorizontalForm
            .mkFromComponent_AlwaysValid[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal]: (vpn, _) =>
                FieldsetLabelAndContent(
                    label = I18N.en15544.terms.P_n_reduced.name,
                    content = 
                        SelectAndOptionsOnly.fromShow[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal](
                            selectedVar = vpn,
                            labelAsDisabledOption = None,
                            options = Seq(HeatOutputReduced.NotDefined, halfOfNominalDefault)
                        )
                )

    // QtyD[Degree] = Angle

    given horizontal_form_Angle: DaisyUIHorizontalForm[Angle] = 
        import defaultable.qty_d.angle.zero
        import validatevar.angle.validOption_whenPositive
        DaisyUIHorizontalForm.forQtyD[Degree]

    // QtyD[Kilo * Watt]

    given horizontal_form_Kilowatt: DaisyUIHorizontalForm[QtyD[Kilo * Watt]] = 
        import defaultable.qty_d.kilowatt.zero
        import validatevar.kilowatt.validOption_whenPositive
        DaisyUIHorizontalForm.forQtyD[Kilo * Watt]

    given horizontal_form_Option_Kilowatt: DaisyUIHorizontalForm[Option[QtyD[Kilo * Watt]]] = 
        import validatevar.kilowatt.validOption_whenPositive
        DaisyUIHorizontalForm.forOptionQtyD_default[Kilo * Watt]

    // QtyD[Meter]

    val horizontal_form_QtyD_Meter: Locale ?=> DaisyUIHorizontalForm[QtyD[Meter]] =
        import validatevar.valid_always.given
        import defaultable.qty_d.meter.zero
        DaisyUIHorizontalForm.forQtyD[Meter]

    val horizontal_form_Length_cm_m: DisplayUnits ?=> Locale ?=> DaisyUIHorizontalForm[QtyD[Meter]] =
        import validatevar.valid_always.given
        import defaultable.qty_d.meter.zero
        given_dual_Length_cm_m.form_DaisyUIHorizontalForm

    val horizontal_form_Length_mm_cm: DisplayUnits ?=> Locale ?=> DaisyUIHorizontalForm[QtyD[Meter]] =
        import validatevar.valid_always.given
        import defaultable.qty_d.meter.zero
        given_dual_Length_mm_cm.form_DaisyUIHorizontalForm

    val horizontal_form_QtyD_Pascal: DisplayUnits ?=> Locale ?=> DaisyUIHorizontalForm[QtyD[Pascal]] =
        import validatevar.valid_always.given
        import defaultable.qty_d.pascal.zero
        DaisyUIHorizontalForm.forQtyD[Pascal]

    given horizontal_form_QtyD_SquareMeterKelvinPerWatt: DaisyUIHorizontalForm[SquareMeterKelvinPerWatt] =
        given Defaultable[SquareMeterKelvinPerWatt] = defaultable.thermalResistance.map(x => x)
        import validatevar.square_meter_kelvin_per_watt.validOption_whenPositive
        DaisyUIHorizontalForm.forValidatedQtyD_NoneAsDefault[(Meter ^ 2) * Kelvin / Watt]()(
            using given_forOptionQtyD_default
        )

    given horizontal_form_QtyD_Unitless: DaisyUIHorizontalForm[QtyD[1]] =
        given zeta_defaultable: Defaultable[QtyD[1]] = defaultable.zeta.map(z => z: QtyD[1])
        import validatevar.unitless.validOption_whenPositive
        DaisyUIHorizontalForm.forQtyD[1]
    
    given horizontal_form_QtyD_Watt_per_MeterKelvin: DaisyUIHorizontalForm[QtyD[Watt / (Meter * Kelvin)]] =
        given Defaultable[QtyD[Watt / (Meter * Kelvin)]] = defaultable.thermalConductivity.map(x => x)
        import validatevar.watt_per_meter_kelvin.validOption_whenPositive
        DaisyUIHorizontalForm.forValidatedQtyD_NoneAsDefault[Watt / (Meter * Kelvin)]()(
            using given_forOptionQtyD_default
        )

    // Roughness

    def horizontal_form_Roughness: DaisyUIHorizontalForm[QtyD[Meter]] =
        import defaultable.given_Roughness
        import validatevar.meter.valid_whenPositive
        given_dual_Roughness.form_DaisyUIHorizontalForm

    given horizontal_form_TCelsius: Locale => DaisyUIHorizontalForm[TCelsius] =
        import defaultable.given_TCelsius
        import validatevar.temp.celsius.validOption_whenPositive
        DaisyUIHorizontalForm.forTempD[Celsius]
        .withFieldName(I18N.terms.temperature_celsius)

    given horizontal_form_Either_AreaInCm2_or_PipeShape: DisplayUnits => Locale => DaisyUIHorizontalForm[OptionOfEither[AreaInCm2, PipeShape]] = 
        // import defaultable.pipeShapeInner
        // import defaultable.qty_d.area_in_cm2.zero
        import afpma.firecalc.ui.instances.defaultable.qty_d.area_in_cm2.zero
        import afpma.firecalc.ui.instances.validatevar.area_in_cm2.valid_whenPositive

        given DF[PipeShape] = horizontal_form_PipeShape
        given DF[AreaInCm2] = given_dual_Area_cm2_or_in2
            .form_DaisyUIHorizontalForm
            .withFieldName(I18N.terms.area)

        DaisyUIHorizontalForm.optionOfEither[AreaInCm2, PipeShape](
            noneLabel   = "-",
            leftLabel   = I18N.terms.area,
            rightLabel  = I18N.terms.pipe_shape._self,
        )

    // ThermalConductivity

    given horizontal_form_ThermalConductivity: DaisyUIHorizontalForm[ThermalConductivity.Type] =
        // given DaisyUI5Defaultable[QtyD[Watt / (Meter * Kelvin)]] = defaultable.thermalConductivity.map(x => x)
        DaisyUIHorizontalForm.formConversionOpaque[ThermalConductivity.Type, QtyD[Watt / (Meter * Kelvin)]]

    // Thickness

    def horizontal_form_Thickness: DisplayUnits ?=> Locale ?=> DaisyUIHorizontalForm[QtyD[Meter]] =
        import defaultable.qty_d.meter.zero
        // import validatevar.meter.validOption_whenPositive
        import validatevar.meter.valid_whenPositive
        // DaisyUIHorizontalForm.forValidatedQtyD_NoneAsDefault[Meter]()(
        //     using given_forOptionQtyD_default
        // )
        dual.given_dual_Thickness.form_DaisyUIHorizontalForm

    // Zeta ζ

    val horizontal_form_zeta: DaisyUIHorizontalForm[ζ] = horizontal_form_ζ
    given horizontal_form_ζ: DaisyUIHorizontalForm[ζ] =
        given zeta_defaultable: Defaultable[QtyD[1]] = defaultable.zeta.map(z => z: QtyD[1])
        import validatevar.unitless.validOption_whenPositive
        given DaisyUIHorizontalForm[QtyD[1]] = DaisyUIHorizontalForm.forQtyD[1]
        DaisyUIHorizontalForm.formConversionOpaque[ζ, QtyD[1]]

    // AreaHeatingStatus

    given horizontal_form_AreaHeatingStatus: Locale => DaisyUIHorizontalForm[AreaHeatingStatus] =
        autoDeriveAndOverwriteFieldNames[AreaHeatingStatus]
    
    given horizontal_form_AreaHeatingStatus_Heated: Locale => DaisyUIHorizontalForm[AreaHeatingStatus.Heated] =
        autoDeriveAndOverwriteFieldNames[AreaHeatingStatus.Heated]
    
    given horizontal_form_AreaHeatingStatus_NotHeated: Locale => DaisyUIHorizontalForm[AreaHeatingStatus.NotHeated] =
        autoDeriveAndOverwriteFieldNames[AreaHeatingStatus.NotHeated]
        
end horizontal_form