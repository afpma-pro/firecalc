/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.NbOfFlows

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.daisyui.DaisyUIHorizontalForm
import afpma.firecalc.ui.formgen.*

import coulomb.*
import coulomb.policy.standard.given

import scala.annotation.nowarn
import scala.deriving.Mirror

import io.taig.babel.Locale

object FlowOnlyHorizontalForm_15544:

    import AddFlowOnlyPipeElement_15544.*
    import SetFlowOnlyPipeProp_15544.*

    // import defaultable.given
    import horizontal_form.{*, given}

    type CtxDF[A] = DisplayUnits ?=> Locale ?=> DaisyUIHorizontalForm[A]

    // SetProp

    given horizontal_form_SetInnerShape: CtxDF[SetInnerShape] =
        given DaisyUIHorizontalForm[PipeShape] = horizontal_form_PipeShape
        autoDeriveAndOverwriteFieldNames[SetInnerShape]

    given horizontal_form_SetRoughness: CtxDF[SetRoughness] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Roughness
        given DaisyUIHorizontalForm[Roughness]   = DaisyUIHorizontalForm.formConversionOpaque[Roughness, QtyD[Meter]]
        autoDeriveAndOverwriteFieldNames[SetRoughness]

    // Material 15544 V2

    given horizontal_form_Material_15544_V2: CtxDF[Material_15544_V2] =
        // Import ShowUsingLocale and extension methods for Material_15544_V2
        import Material_15544_V2.{given, *}

        // Provide form for Roughness (opaque type over QtyD[Meter])
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Roughness
        given DaisyUIHorizontalForm[Roughness]   =
            DaisyUIHorizontalForm
                .formConversionOpaque[Roughness, QtyD[Meter]]
                .withFieldName(I18N.terms.roughness)

        // Provide Defaultable
        given Defaultable[Material_15544_V2] =
            defaultable_15544.defaultable_material_15544_v2

        // Provide ValidateVar
        given ValidateVar[Roughness]         = validatevar.roughness.valid_whenPositive
        given ValidateVar[Material_15544_V2] =
            validatevar.valid_always.given_ValidateVar_AlwaysValid[Material_15544_V2]

        DaisyUIHorizontalForm.forSelectionWithDefaultValue_usingSelectInput[Material_15544_V2, Roughness]   (
            selectOptions    = Material_15544_V2.values,
            getDefaultValue  = _.roughness,
            withDefaultValue = _.withRoughness(_),
            getId            = _.name
        )

    // Material 15544 V1

    given horizontal_form_Material_15544_V1: CtxDF[Material_15544_V1] =
        import Material_15544_V1.given
        given ValidateVar[Material_15544_V1] =
            validatevar.valid_always.given_ValidateVar_AlwaysValid[Material_15544_V1]
        DaisyUIHorizontalForm
            .forEnumOrSumTypeLike_UsingShowAsId[Material_15544_V1](Material_15544_V1.values.toList)

    given horizontal_form_SetMaterial: CtxDF[SetMaterial] =
        given DF[Material_15544] = horizontal_form_Material_15544_V2
        autoDeriveAndOverwriteFieldNames[SetMaterial]

    given horizontal_form_SetNumberOfFlows: CtxDF[SetNumberOfFlows] =
        import validatevar.validOption_always.given
        given DaisyUIHorizontalForm[Int]       = DaisyUIHorizontalForm.forInt
        given DaisyUIHorizontalForm[NbOfFlows] = DaisyUIHorizontalForm.formConversionOpaque[NbOfFlows, Int]
        autoDeriveAndOverwriteFieldNames[SetNumberOfFlows]

    // AddElement

    given horizontal_form_Option_Angle: DaisyUIHorizontalForm[Option[QtyD[Degree]]] =
        import afpma.firecalc.units.all.given
        // given Defaultable[Option[QtyD[Degree]]] = Defaultable(None)
        given ValidateVar[Option[QtyD[Degree]]] =
            validatevar.validOption_always.given_ValidateVarOption_AlwaysValid[QtyD[Degree]]
        DaisyUIHorizontalForm.forOptionQtyD_default[Degree]

    // helper with string field always validated
    inline def autoDeriveAndOverwriteFieldNames_AddElement_Subtype[A](using
        inline m: Mirror.Of[A],
        l       : Locale
    ): DaisyUIHorizontalForm[A] =
        @nowarn given DaisyUIHorizontalForm[String] = horizontal_form.string_emptyAsDefault_alwaysValid
        autoDeriveAndOverwriteFieldNames[A]

    given horizontal_form_AddSectionSlopped: CtxDF[AddSectionSlopped] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionSlopped]

    given horizontal_form_AddSectionHorizontal: CtxDF[AddSectionHorizontal] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionHorizontal]

    given horizontal_form_AddSectionVertical: CtxDF[AddSectionVertical] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionVertical]

    given horizontal_form_AddSharpeAngle_0_to_180: CtxDF[AddSharpeAngle_0_to_180] =
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSharpeAngle_0_to_180]

    given horizontal_form_AddCircularArc_60: CtxDF[AddCircularArc_60] =
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddCircularArc_60]

    given horizontal_form_AddSectionShapeChange: CtxDF[AddSectionShapeChange] =
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionShapeChange]

    given horizontal_form_AddFlowResistance: CtxDF[AddFlowResistance] =
        given DaisyUIHorizontalForm[OptionOfEither[AreaInCm2, PipeShape]] =
            horizontal_form_Either_AreaInCm2_or_PipeShape
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddFlowResistance]

    given horizontal_form_AddPressureDiff: CtxDF[AddPressureDiff] =
        given DaisyUIHorizontalForm[QtyD[Pascal]] = horizontal_form_QtyD_Pascal
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddPressureDiff]

end FlowOnlyHorizontalForm_15544
