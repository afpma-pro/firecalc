/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues
import afpma.firecalc.i18n.I18nData
import afpma.firecalc.i18n.implicits.given

import io.taig.babel.Locale
import afpma.firecalc.payments.shared.i18n.I18nData_PaymentsShared

object hastranslations:

    inline given autoDerivedFor_I18nData: [A] => Locale => HasTranslatedFieldsWithValues[A] =
        HasTranslatedFieldsWithValues.makeFor[A, I18nData]

    object forModule_PaymentsShared:
        import afpma.firecalc.payments.shared.i18n.I18nData_PaymentsShared
        import afpma.firecalc.payments.shared.i18n.implicits.given

        inline given autoDerivedFor_I18nData_PaymentsShared: [A] => Locale => HasTranslatedFieldsWithValues[A] =
            HasTranslatedFieldsWithValues.makeFor[A, I18nData_PaymentsShared]

    // TOOD: can we generate an instance for any class ? any case class using Mirror ?
    // so we don't have to generate them manually ?
    // or is it better to keep more control ?
    // => DONE using autoDerivedForI18nData

    // given hastranslations_SetInnerShape: Locale => HasTranslatedFieldsWithValues[SetInnerShape] =
    //     HasTranslatedFieldsWithValues.makeFor[SetInnerShape, I18nData]

    // given hastranslations_SetOuterShape: Locale => HasTranslatedFieldsWithValues[SetOuterShape] =
    //     HasTranslatedFieldsWithValues.makeFor[SetOuterShape, I18nData]

    // given hastranslations_SetThickness: Locale => HasTranslatedFieldsWithValues[SetThickness] =
    //     HasTranslatedFieldsWithValues.makeFor[SetThickness, I18nData]

    // given hastranslations_SetRoughness: Locale => HasTranslatedFieldsWithValues[SetRoughness] =
    //     HasTranslatedFieldsWithValues.makeFor[SetRoughness, I18nData]

    // given hastranslations_SetMaterial: Locale => HasTranslatedFieldsWithValues[SetMaterial] =
    //     HasTranslatedFieldsWithValues.makeFor[SetMaterial, I18nData]

    // given hastranslations_SetLayer: Locale => HasTranslatedFieldsWithValues[SetLayer] =
    //     HasTranslatedFieldsWithValues.makeFor[SetLayer, I18nData]

    // given hastranslations_SetLayers: Locale => HasTranslatedFieldsWithValues[SetLayers] =
    //     HasTranslatedFieldsWithValues.makeFor[SetLayers, I18nData]

    // given hastranslations_SetAirSpaceAfterLayers: Locale => HasTranslatedFieldsWithValues[SetAirSpaceAfterLayers] =
    //     HasTranslatedFieldsWithValues.makeFor[SetAirSpaceAfterLayers, I18nData]

    // given hastranslations_SetPipeLocation: Locale => HasTranslatedFieldsWithValues[SetPipeLocation] =
    //     HasTranslatedFieldsWithValues.makeFor[SetPipeLocation, I18nData]

    // given hastranslations_SetDuctType: Locale => HasTranslatedFieldsWithValues[SetDuctType] =
    //     HasTranslatedFieldsWithValues.makeFor[SetDuctType, I18nData]

    // given hastranslations_SetNumberOfFlows: Locale => HasTranslatedFieldsWithValues[SetNumberOfFlows] =
    //     HasTranslatedFieldsWithValues.makeFor[SetNumberOfFlows, I18nData]

    // given hastranslations_AddSection: Locale => HasTranslatedFieldsWithValues[AddSection] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSection, I18nData]

    // given hastranslations_AddSectionHorizontal: Locale => HasTranslatedFieldsWithValues[AddSectionHorizontal] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSectionHorizontal, I18nData]

    // given hastranslations_AddSectionVertical: Locale => HasTranslatedFieldsWithValues[AddSectionVertical] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSectionVertical, I18nData]

    // given hastranslations_AddAngleAdjustable: Locale => HasTranslatedFieldsWithValues[AddAngleAdjustable] =
    //     HasTranslatedFieldsWithValues.makeFor[AddAngleAdjustable, I18nData]

    // given hastranslations_AddSharpeAngle_0_to_90: Locale => HasTranslatedFieldsWithValues[AddSharpeAngle_0_to_90] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSharpeAngle_0_to_90, I18nData]

    // given hastranslations_AddSharpeAngle_0_to_90_Unsafe: Locale => HasTranslatedFieldsWithValues[AddSharpeAngle_0_to_90_Unsafe] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSharpeAngle_0_to_90_Unsafe, I18nData]

    // given hastranslations_AddSmoothCurve_90: Locale => HasTranslatedFieldsWithValues[AddSmoothCurve_90] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSmoothCurve_90, I18nData]

    // given hastranslations_AddSmoothCurve_90_Unsafe: Locale => HasTranslatedFieldsWithValues[AddSmoothCurve_90_Unsafe] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSmoothCurve_90_Unsafe, I18nData]

    // given hastranslations_AddSmoothCurve_60: Locale => HasTranslatedFieldsWithValues[AddSmoothCurve_60] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSmoothCurve_60, I18nData]

    // given hastranslations_AddSmoothCurve_60_Unsafe: Locale => HasTranslatedFieldsWithValues[AddSmoothCurve_60_Unsafe] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSmoothCurve_60_Unsafe, I18nData]

    // given hastranslations_AddElbows_2x45: Locale => HasTranslatedFieldsWithValues[AddElbows_2x45] =
    //     HasTranslatedFieldsWithValues.makeFor[AddElbows_2x45, I18nData]

    // given hastranslations_AddElbows_3x30: Locale => HasTranslatedFieldsWithValues[AddElbows_3x30] =
    //     HasTranslatedFieldsWithValues.makeFor[AddElbows_3x30, I18nData]

    // given hastranslations_AddElbows_4x22p5: Locale => HasTranslatedFieldsWithValues[AddElbows_4x22p5] =
    //     HasTranslatedFieldsWithValues.makeFor[AddElbows_4x22p5, I18nData]

    // given hastranslations_AddSectionDecrease: Locale => HasTranslatedFieldsWithValues[AddSectionDecrease] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSectionDecrease, I18nData]

    // given hastranslations_AddSectionIncrease: Locale => HasTranslatedFieldsWithValues[AddSectionIncrease] =
    //     HasTranslatedFieldsWithValues.makeFor[AddSectionIncrease, I18nData]

    // given hastranslations_AddFlowResistance: Locale => HasTranslatedFieldsWithValues[AddFlowResistance] =
    //     HasTranslatedFieldsWithValues.makeFor[AddFlowResistance, I18nData]

    // AppendLayerDescr

    // given hastranslations_AppendLayerDescr: Locale => HasTranslatedFieldsWithValues[AppendLayerDescr] =
    //     HasTranslatedFieldsWithValues.makeFor[AppendLayerDescr, I18nData]


end hastranslations
