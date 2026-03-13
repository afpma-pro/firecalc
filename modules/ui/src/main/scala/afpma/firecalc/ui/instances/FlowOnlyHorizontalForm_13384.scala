/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AirSpaceDetailed_V2.VentilDirection
import afpma.firecalc.dto.all.AirSpaceDetailed_V2.VentilOpenings
import afpma.firecalc.dto.common.NbOfFlows

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.daisyui.DaisyUIHorizontalForm
import afpma.firecalc.ui.formgen.*

import coulomb.*
import coulomb.policy.standard.given

import scala.annotation.nowarn
import scala.deriving.Mirror

import io.taig.babel.Locale

class FlowOnlyHorizontalForm_13384(using DisplayUnits, Locale):

    import AddFlowOnlyPipeElement_13384.*
    import SetFlowOnlyPipeProp_13384.*

    private given horizontal_form: HorizontalFormCommonInstances = HorizontalFormCommonInstances()
    import horizontal_form.{*, given}

    private val vv: ValidateVarCommonInstances = ValidateVarCommonInstances()

    // AddElement

    given horizontal_form_SetInnerShape: DaisyUIHorizontalForm[SetInnerShape] =
        autoDeriveAndOverwriteFieldNames[SetInnerShape]

    given horizontal_form_SetRoughness: DaisyUIHorizontalForm[SetRoughness] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Roughness
        given DaisyUIHorizontalForm[Roughness]   = DaisyUIHorizontalForm.formConversionOpaque[Roughness, QtyD[Meter]]
        autoDeriveAndOverwriteFieldNames[SetRoughness]

    // Material_13384_V2

    given horizontal_form_Material_13384_V2: DaisyUIHorizontalForm[Material_13384_V2] =
        // Import ShowUsingLocale and extension methods for Material_13384_V2
        import Material_13384_V2.{given, *}

        // Provide form for Roughness (opaque type over QtyD[Meter])
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Roughness
        given DaisyUIHorizontalForm[Roughness]   =
            DaisyUIHorizontalForm
                .formConversionOpaque[Roughness, QtyD[Meter]]
                .withFieldName(I18N.terms.roughness)

        // Provide Defaultable
        given Defaultable[Material_13384_V2] =
            defaultable_13384.defaultable_Material_13384_v2

        // Provide ValidateVar
        given ValidateVar[Roughness]         = vv.roughness.valid_whenStrictlyPositive
        given ValidateVar[Material_13384_V2] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[Material_13384_V2]

        DaisyUIHorizontalForm.forSelectionWithDefaultValue_usingSelectInput[Material_13384_V2, Roughness]   (
            selectOptions    = Material_13384_V2.values,
            getDefaultValue  = _.roughness,
            withDefaultValue = _.withRoughness(_),
            getId            = _.name
        )

    // Material_13384_V1

    given horizontal_form_Material_13384_V1: DaisyUIHorizontalForm[Material_13384_V1] =
        import Material_13384_V1.given
        given ValidateVar[Material_13384_V1] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[Material_13384_V1]
        DaisyUIHorizontalForm
            .forEnumOrSumTypeLike_UsingShowAsId[Material_13384_V1](Material_13384_V1.values.toList)

    given horizontal_form_SetMaterial: DaisyUIHorizontalForm[SetMaterial] =
        given DaisyUIHorizontalForm[Material_13384] = horizontal_form_Material_13384_V2
        autoDeriveAndOverwriteFieldNames[SetMaterial]

    given horizontal_form_SetNumberOfFlows: DaisyUIHorizontalForm[SetNumberOfFlows] =
        import ValidateVarCommonInstances.validOption_always.given
        given DaisyUIHorizontalForm[Int]       = DaisyUIHorizontalForm.forInt
        given DaisyUIHorizontalForm[NbOfFlows] = DaisyUIHorizontalForm.formConversionOpaque[NbOfFlows, Int]
        autoDeriveAndOverwriteFieldNames[SetNumberOfFlows]

    given horizontal_form_SetInitialDirection: DaisyUIHorizontalForm[SetInitialDirection] =
        autoDeriveAndOverwriteFieldNames[SetInitialDirection]

    // AddElement

    // helper with string field always validated
    inline def autoDeriveAndOverwriteFieldNames_AddElement_Subtype[A](using inline m: Mirror.Of[A]): DaisyUIHorizontalForm[A] =
        @nowarn given DaisyUIHorizontalForm[String] = horizontal_form.string_emptyAsDefault_alwaysValid
        autoDeriveAndOverwriteFieldNames[A]

    // Like above but suppresses the finalDir field — finalDir is set via the DirectionBadge dropdown.
    // Both places must be updated together when adding a new DC subtype.
    inline def autoDeriveAndOverwriteFieldNames_DC_Subtype[A](using inline m: Mirror.Of[A]): DaisyUIHorizontalForm[A] =
        import com.raquo.laminar.api.L.span
        @nowarn given DaisyUIHorizontalForm[String] = horizontal_form.string_emptyAsDefault_alwaysValid
        given ValidateVar[Option[FinalDirection]] =
            ValidateVarCommonInstances.validOption_always.given_ValidateVarOption_AlwaysValid[FinalDirection]
        @nowarn given DaisyUIHorizontalForm[Option[FinalDirection]] =
            DaisyUIHorizontalForm.makeFor[Option[FinalDirection]](Defaultable(None))((_, _) => span())
        autoDeriveAndOverwriteFieldNames[A]

    given horizontal_form_AddSectionSlopped: DaisyUIHorizontalForm[AddSectionSlopped] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionSlopped]

    given horizontal_form_AddSectionHorizontal: DaisyUIHorizontalForm[AddSectionHorizontal] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionHorizontal]

    given horizontal_form_AddSectionVertical: DaisyUIHorizontalForm[AddSectionVertical] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionVertical]

    given horizontal_form_AddAngleAdjustable: DaisyUIHorizontalForm[AddAngleAdjustable] =
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddAngleAdjustable]

    given horizontal_form_AddSharpeAngle_0_to_90: DaisyUIHorizontalForm[AddSharpeAngle_0_to_90] =
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSharpeAngle_0_to_90]

    given horizontal_form_AddSharpeAngle_0_to_90_Unsafe: DaisyUIHorizontalForm[AddSharpeAngle_0_to_90_Unsafe] =
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSharpeAngle_0_to_90_Unsafe]

    given horizontal_form_AddSmoothCurve_90: DaisyUIHorizontalForm[AddSmoothCurve_90] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSmoothCurve_90]

    given horizontal_form_AddSmoothCurve_90_Unsafe: DaisyUIHorizontalForm[AddSmoothCurve_90_Unsafe] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSmoothCurve_90_Unsafe]

    given horizontal_form_AddSmoothCurve_60: DaisyUIHorizontalForm[AddSmoothCurve_60] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSmoothCurve_60]

    given horizontal_form_AddSmoothCurve_60_Unsafe: DaisyUIHorizontalForm[AddSmoothCurve_60_Unsafe] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSmoothCurve_60_Unsafe]

    given horizontal_form_AddElbows_2x45: DaisyUIHorizontalForm[AddElbows_2x45] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddElbows_2x45]

    given horizontal_form_AddElbows_3x30: DaisyUIHorizontalForm[AddElbows_3x30] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddElbows_3x30]

    given horizontal_form_AddElbows_4x22p5: DaisyUIHorizontalForm[AddElbows_4x22p5] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddElbows_4x22p5]

    given horizontal_form_AddSectionDecrease: DaisyUIHorizontalForm[AddSectionDecrease] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionDecrease]

    given horizontal_form_AddSectionIncrease: DaisyUIHorizontalForm[AddSectionIncrease] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionIncrease]

    given horizontal_form_AddFlowResistance: DaisyUIHorizontalForm[AddFlowResistance] =
        given DaisyUIHorizontalForm[OptionOfEither[AreaInCm2, PipeShape]] =
            horizontal_form_Either_AreaInCm2_or_PipeShape
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddFlowResistance]

    // AmbiantAirTemperatureSet

    given horizontal_form_AmbiantAirTemperatureSet: DaisyUIHorizontalForm[AmbiantAirTemperatureSet] =
        given DaisyUIHorizontalForm[Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius]] = horizontal_form_TuTemperature_Or_TCelsius
        autoDeriveAndOverwriteFieldNames[AmbiantAirTemperatureSet]

    // AppendLayerDescr

    given horizontal_form_FromLambda: DaisyUIHorizontalForm[AppendLayerDescr.FromLambda] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromLambda]

    given horizontal_form_FromLambdaUsingThickness: DaisyUIHorizontalForm[AppendLayerDescr.FromLambdaUsingThickness] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromLambdaUsingThickness]

    given horizontal_form_FromThermalResistanceUsingThickness
        : DaisyUIHorizontalForm[AppendLayerDescr.FromThermalResistanceUsingThickness] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromThermalResistanceUsingThickness]

    given horizontal_form_FromThermalResistance: DaisyUIHorizontalForm[AppendLayerDescr.FromThermalResistance] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromThermalResistance]

    given horizontal_form_AirSpaceUsingOuterShape: DaisyUIHorizontalForm[AppendLayerDescr.AirSpaceUsingOuterShape] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.AirSpaceUsingOuterShape]

    given horizontal_form_AirSpaceUsingThickness: DaisyUIHorizontalForm[AppendLayerDescr.AirSpaceUsingThickness] =
        given DaisyUIHorizontalForm[QtyD[Meter]]     = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.AirSpaceUsingThickness]

    given horizontal_form_AppendLayerDescr: DaisyUIHorizontalForm[AppendLayerDescr] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr]

    // AirSpaceDetailed

    given horizontal_form_AirSpaceDetailed: DaisyUIHorizontalForm[AirSpaceDetailed] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed]

    given horizontal_form_AirSpaceDetailed_WithoutAirSpace: DaisyUIHorizontalForm[AirSpaceDetailed.WithoutAirSpace_V2] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.WithoutAirSpace_V2]

    given horizontal_form_AirSpaceDetailed_WithAirSpace: DaisyUIHorizontalForm[AirSpaceDetailed.WithAirSpace_V2] =
        given DaisyUIHorizontalForm[QtyD[Meter]]     = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.WithAirSpace_V2]

    // PipeLocation.AreaName

    given horizontal_form_PipeLocation_AreaName_BoilerRoom       : DaisyUIHorizontalForm[PipeLocation.AreaName.BoilerRoom]        =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.BoilerRoom]
    given horizontal_form_PipeLocation_AreaName_HeatedArea       : DaisyUIHorizontalForm[PipeLocation.AreaName.HeatedArea]        =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.HeatedArea]
    given horizontal_form_PipeLocation_AreaName_UnheatedInside   : DaisyUIHorizontalForm[PipeLocation.AreaName.UnheatedInside]    =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.UnheatedInside]
    given horizontal_form_PipeLocation_AreaName_OutsideOrExterior: DaisyUIHorizontalForm[PipeLocation.AreaName.OutsideOrExterior] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.OutsideOrExterior]
    given horizontal_form_PipeLocation_AreaName_CustomArea       : DaisyUIHorizontalForm[PipeLocation.AreaName.CustomArea]        =
        given ValidateVar[Option[String]] = ValidateVarCommonInstances.string.validOption_Always
        given DaisyUIHorizontalForm[String] = DaisyUIHorizontalForm.forString
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.CustomArea]

    given horizontal_form_PipeLocation_AreaName: DaisyUIHorizontalForm[PipeLocation.AreaName] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName]

    // DuctType

    given horizontal_form_DuctType_NonConcentricDuctsHighThermalResistance
        : DaisyUIHorizontalForm[DuctType.NonConcentricDuctsHighThermalResistance] =
        autoDeriveAndOverwriteFieldNames[DuctType.NonConcentricDuctsHighThermalResistance]

    given horizontal_form_DuctType_NonConcentricDuctsLowThermalResistance
        : DaisyUIHorizontalForm[DuctType.NonConcentricDuctsLowThermalResistance] =
        autoDeriveAndOverwriteFieldNames[DuctType.NonConcentricDuctsLowThermalResistance]

    given horizontal_form_DuctType_ConcentricDucts: DaisyUIHorizontalForm[DuctType.ConcentricDucts] =
        autoDeriveAndOverwriteFieldNames[DuctType.ConcentricDucts]

    given horizontal_form_DuctType: DaisyUIHorizontalForm[DuctType] =
        autoDeriveAndOverwriteFieldNames[DuctType]

    given horizontal_form_PipeLocation: DaisyUIHorizontalForm[PipeLocation] =
        autoDeriveAndOverwriteFieldNames[PipeLocation]

    given horizontal_form_PipeLocation_BoilerRoom: DaisyUIHorizontalForm[PipeLocation.BoilerRoom] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.BoilerRoom]

    given horizontal_form_PipeLocation_HeatedArea: DaisyUIHorizontalForm[PipeLocation.HeatedArea] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.HeatedArea]

    given horizontal_form_PipeLocation_UnheatedInside: DaisyUIHorizontalForm[PipeLocation.UnheatedInside] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.UnheatedInside]

    given horizontal_form_PipeLocation_OutsideOrExterior: DaisyUIHorizontalForm[PipeLocation.OutsideOrExterior] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.OutsideOrExterior]

    given horizontal_form_PipeLocation_CustomArea: DaisyUIHorizontalForm[PipeLocation.CustomArea] =
        given DaisyUIHorizontalForm[Boolean] = horizontal_form.boolean_falseAsDefault_alwaysValid
        autoDeriveAndOverwriteFieldNames[PipeLocation.CustomArea]

    // Ventil Direction

    given horizontal_form_AirSpaceDetailed_VentilDirection_UndefinedDir
        : DaisyUIHorizontalForm[VentilDirection.UndefinedDir] =
        autoDeriveAndOverwriteFieldNames[VentilDirection.UndefinedDir]

    given horizontal_form_AirSpaceDetailed_VentilDirection_SameDirAsFlueGas
        : DaisyUIHorizontalForm[VentilDirection.SameDirAsFlueGas] =
        autoDeriveAndOverwriteFieldNames[VentilDirection.SameDirAsFlueGas]

    given horizontal_form_AirSpaceDetailed_VentilDirection_OppositeDirOfFlueGas
        : DaisyUIHorizontalForm[VentilDirection.OppositeDirOfFlueGas] =
        autoDeriveAndOverwriteFieldNames[VentilDirection.OppositeDirOfFlueGas]

    given horizontal_form_AirSpaceDetailed_VentilDirection: DaisyUIHorizontalForm[VentilDirection] =
        autoDeriveAndOverwriteFieldNames[VentilDirection]

    // Ventil Openings

    given horizontal_form_AirSpaceDetailed_VentilOpenings_NoOpening: DaisyUIHorizontalForm[VentilOpenings.NoOpening] =
        autoDeriveAndOverwriteFieldNames[VentilOpenings.NoOpening]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_AnnularAreaFullyOpened
        : DaisyUIHorizontalForm[VentilOpenings.AnnularAreaFullyOpened] =
        autoDeriveAndOverwriteFieldNames[VentilOpenings.AnnularAreaFullyOpened]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_PartiallyOpened_InAccordanceWith_DTU_24_1
        : DaisyUIHorizontalForm[VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1] =
        autoDeriveAndOverwriteFieldNames[VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1]

    given horizontal_form_AirSpaceDetailed_VentilOpenings: DaisyUIHorizontalForm[VentilOpenings] =
        autoDeriveAndOverwriteFieldNames[VentilOpenings]

    // TuTemperature

    given horizontal_form_TuTemperature_Or_TCelsius: DaisyUIHorizontalForm[Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius]] =
        given Defaultable[TCelsius] =
            defaultable.tcelsius // or tuo default value of a specific Country / global setting ?
        given DaisyUIHorizontalForm[TCelsius]                                = horizontal_form.horizontal_form_TCelsius
        given DaisyUIHorizontalForm[AmbiantAirTemperatureSet.UseTuoOverride] =
            autoDeriveAndOverwriteFieldNames[AmbiantAirTemperatureSet.UseTuoOverride]
        DaisyUIHorizontalForm.eitherAsSelectWithOptions[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius](
            I18N.en13384._ambiant_air_temperature.short
        )

end FlowOnlyHorizontalForm_13384
