/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances
import scala.annotation.nowarn
import scala.deriving.Mirror

import afpma.firecalc.engine.models.gtypedefs.*

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.components.AppendLayersComponent
import afpma.firecalc.ui.daisyui.DaisyUIHorizontalForm
import afpma.firecalc.ui.daisyui.DaisyUIHorizontalForm.autoOverwriteFieldNames
import afpma.firecalc.ui.formgen.*

import afpma.firecalc.dto.all.*
import AddElement_13384.*
import SetProp_13384.*
import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import io.taig.babel.Locale


object horizontal_form_13384:

    type CtxDF[A] = DisplayUnits ?=> Locale ?=> DaisyUIHorizontalForm[A]
    type LocDF[A] = Locale ?=> DaisyUIHorizontalForm[A]
    type DF[A] = DaisyUIHorizontalForm[A]
    
    // import defaultable.given
    import hastranslations.given
    import horizontal_form.{*, given}

    // AddElement

    given horizontal_form_SetInnerShape: CtxDF[SetInnerShape] = 
        given DaisyUIHorizontalForm[PipeShape] = horizontal_form_PipeShape
        autoDeriveAndOverwriteFieldNames[SetInnerShape]

    given horizontal_form_SetOuterShape: CtxDF[SetOuterShape] = 
        given DaisyUIHorizontalForm[PipeShape] = horizontal_form_PipeShape
        autoDeriveAndOverwriteFieldNames[SetOuterShape]

    given horizontal_form_SetThickness: CtxDF[SetThickness] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Thickness
        autoDeriveAndOverwriteFieldNames[SetThickness]

    given horizontal_form_SetRoughness: CtxDF[SetRoughness] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Roughness
        given DaisyUIHorizontalForm[Roughness] = DaisyUIHorizontalForm.formConversionOpaque[Roughness, QtyD[Meter]]
        autoDeriveAndOverwriteFieldNames[SetRoughness]

    given horizontal_form_Material_13384: CtxDF[Material_13384] = 
        import Material_13384.given
        given ValidateVar[Material_13384] = 
            validatevar.valid_always.given_ValidateVar_AlwaysValid[Material_13384]
        DaisyUIHorizontalForm
        .forEnumOrSumTypeLike_UsingShowAsId[Material_13384](Material_13384.values.toList)

    given horizontal_form_SetMaterial: CtxDF[SetMaterial] = 
        given DF[Material_13384] = horizontal_form_Material_13384
        autoDeriveAndOverwriteFieldNames[SetMaterial]

    given horizontal_form_SetLayer: CtxDF[SetLayer] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Thickness
        given DaisyUIHorizontalForm[ThermalConductivity] = horizontal_form_ThermalConductivity
        autoDeriveAndOverwriteFieldNames[SetLayer]

    given horizontal_form_SetLayers: CtxDF[SetLayers] = 
        // import defaultable.given_AppendLayerDescr
        given DaisyUIHorizontalForm[List[AppendLayerDescr]] = 
            import validatevar.valid_always.given
            DaisyUIHorizontalForm.forList_fromComponent: appnd_layers_var =>
                AppendLayersComponent(appnd_layers_var).node
        autoDeriveAndOverwriteFieldNames[SetLayers]

    given horizontal_form_SetAirSpaceAfterLayers: CtxDF[SetAirSpaceAfterLayers] = 
        given DF[AirSpaceDetailed] = horizontal_form_AirSpaceDetailed
        autoDeriveAndOverwriteFieldNames[SetAirSpaceAfterLayers]

    given horizontal_form_SetPipeLocation: CtxDF[SetPipeLocation] = 
        given DF[PipeLocation] = horizontal_form_PipeLocation
        autoDeriveAndOverwriteFieldNames[SetPipeLocation]

    given horizontal_form_SetDuctType: CtxDF[SetDuctType] = 
        given DF[DuctType] = horizontal_form_DuctType
        autoDeriveAndOverwriteFieldNames[SetDuctType]

    given horizontal_form_SetNumberOfFlows: CtxDF[SetNumberOfFlows] = 
        import validatevar.validOption_always.given
        given DaisyUIHorizontalForm[Int] = DaisyUIHorizontalForm.forInt
        given DaisyUIHorizontalForm[NbOfFlows] = DaisyUIHorizontalForm.formConversionOpaque[NbOfFlows, Int]
        autoDeriveAndOverwriteFieldNames[SetNumberOfFlows]

    // AddElement

    // helper with string field always validated
    inline def autoDeriveAndOverwriteFieldNames_AddElement_Subtype[A](using inline m: Mirror.Of[A]): LocDF[A] = 
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

    given horizontal_form_AddAngleAdjustable: CtxDF[AddAngleAdjustable] = 
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddAngleAdjustable]

    given horizontal_form_AddSharpeAngle_0_to_90: CtxDF[AddSharpeAngle_0_to_90] = 
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSharpeAngle_0_to_90]

    given horizontal_form_AddSharpeAngle_0_to_90_Unsafe: CtxDF[AddSharpeAngle_0_to_90_Unsafe] = 
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSharpeAngle_0_to_90_Unsafe]

    given horizontal_form_AddSmoothCurve_90: CtxDF[AddSmoothCurve_90] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSmoothCurve_90]

    given horizontal_form_AddSmoothCurve_90_Unsafe: CtxDF[AddSmoothCurve_90_Unsafe] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSmoothCurve_90_Unsafe]

    given horizontal_form_AddSmoothCurve_60: CtxDF[AddSmoothCurve_60] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSmoothCurve_60]

    given horizontal_form_AddSmoothCurve_60_Unsafe: CtxDF[AddSmoothCurve_60_Unsafe] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSmoothCurve_60_Unsafe]

    given horizontal_form_AddElbows_2x45: CtxDF[AddElbows_2x45] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddElbows_2x45]

    given horizontal_form_AddElbows_3x30: CtxDF[AddElbows_3x30] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddElbows_3x30]

    given horizontal_form_AddElbows_4x22p5: CtxDF[AddElbows_4x22p5] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddElbows_4x22p5]

    given horizontal_form_AddSectionDecrease: CtxDF[AddSectionDecrease] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionDecrease]

    given horizontal_form_AddSectionIncrease: CtxDF[AddSectionIncrease] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionIncrease]

    given horizontal_form_AddFlowResistance: CtxDF[AddFlowResistance] = 
        given DaisyUIHorizontalForm[OptionOfEither[AreaInCm2, PipeShape]] = horizontal_form_Either_AreaInCm2_or_PipeShape
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddFlowResistance]

    // AmbiantAirTemperatureSet

    given horizontal_form_AmbiantAirTemperatureSet: CtxDF[AmbiantAirTemperatureSet] = 
        given DF[Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius]] = horizontal_form_TuTemperature_Or_TCelsius
        autoDeriveAndOverwriteFieldNames[AmbiantAirTemperatureSet]

    // AppendLayerDescr

    given horizontal_form_FromLambda: CtxDF[AppendLayerDescr.FromLambda] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromLambda]
    
    given horizontal_form_FromLambdaUsingThickness: CtxDF[AppendLayerDescr.FromLambdaUsingThickness] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromLambdaUsingThickness]
    
    given horizontal_form_FromThermalResistanceUsingThickness: CtxDF[AppendLayerDescr.FromThermalResistanceUsingThickness] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromThermalResistanceUsingThickness]
    
    given horizontal_form_FromThermalResistance: CtxDF[AppendLayerDescr.FromThermalResistance] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromThermalResistance]
    
    given horizontal_form_AirSpaceUsingOuterShape: CtxDF[AppendLayerDescr.AirSpaceUsingOuterShape] =
        given DF[VentilDirection] = horizontal_form_AirSpaceDetailed_VentilDirection
        given DF[VentilOpenings] = horizontal_form_AirSpaceDetailed_VentilOpenings
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.AirSpaceUsingOuterShape]
    
    given horizontal_form_AirSpaceUsingThickness: CtxDF[AppendLayerDescr.AirSpaceUsingThickness] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_mm_cm
        given DF[VentilDirection] = horizontal_form_AirSpaceDetailed_VentilDirection
        given DF[VentilOpenings] = horizontal_form_AirSpaceDetailed_VentilOpenings
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.AirSpaceUsingThickness]

    
    given horizontal_form_AppendLayerDescr: CtxDF[AppendLayerDescr] = 
        given DF[QtyD[Meter]] = horizontal_form_Length_mm_cm
        given DF[VentilDirection] = horizontal_form_AirSpaceDetailed_VentilDirection
        given DF[VentilOpenings] = horizontal_form_AirSpaceDetailed_VentilOpenings
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr]

    // AirSpaceDetailed

    given horizontal_form_AirSpaceDetailed: CtxDF[AirSpaceDetailed] = 
        given DF[QtyD[Meter]] = horizontal_form_Length_mm_cm
        given DF[VentilDirection] = horizontal_form_AirSpaceDetailed_VentilDirection
        given DF[VentilOpenings] = horizontal_form_AirSpaceDetailed_VentilOpenings
        // be explicit
        given DF[AirSpaceDetailed.WithoutAirSpace] = horizontal_form_AirSpaceDetailed_WithoutAirSpace
        given DF[AirSpaceDetailed.WithAirSpace] = horizontal_form_AirSpaceDetailed_WithAirSpace
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed]

    given horizontal_form_AirSpaceDetailed_WithoutAirSpace: CtxDF[AirSpaceDetailed.WithoutAirSpace] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.WithoutAirSpace]

    given horizontal_form_AirSpaceDetailed_WithAirSpace: CtxDF[AirSpaceDetailed.WithAirSpace] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_mm_cm
        given DF[VentilDirection] = horizontal_form_AirSpaceDetailed_VentilDirection
        given DF[VentilOpenings] = horizontal_form_AirSpaceDetailed_VentilOpenings
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.WithAirSpace]

    // PipeLocation.AreaName

    given horizontal_form_PipeLocation_AreaName_BoilerRoom: CtxDF[PipeLocation.AreaName.BoilerRoom] = 
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.BoilerRoom]
    given horizontal_form_PipeLocation_AreaName_HeatedArea: CtxDF[PipeLocation.AreaName.HeatedArea] = 
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.HeatedArea]
    given horizontal_form_PipeLocation_AreaName_UnheatedInside: CtxDF[PipeLocation.AreaName.UnheatedInside] = 
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.UnheatedInside]
    given horizontal_form_PipeLocation_AreaName_OutsideOrExterior: CtxDF[PipeLocation.AreaName.OutsideOrExterior] = 
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.OutsideOrExterior]
    given horizontal_form_PipeLocation_AreaName_CustomArea: CtxDF[PipeLocation.AreaName.CustomArea] = 
        given ValidateVar[Option[String]] = validatevar.string.validOption_Always
        given DF[String] = DaisyUIHorizontalForm.forString
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.CustomArea]

    given horizontal_form_PipeLocation_AreaName: CtxDF[PipeLocation.AreaName] =
        // be explicit
        given DF[PipeLocation.AreaName.BoilerRoom] = horizontal_form_PipeLocation_AreaName_BoilerRoom
        given DF[PipeLocation.AreaName.HeatedArea] = horizontal_form_PipeLocation_AreaName_HeatedArea
        given DF[PipeLocation.AreaName.UnheatedInside] = horizontal_form_PipeLocation_AreaName_UnheatedInside
        given DF[PipeLocation.AreaName.OutsideOrExterior] = horizontal_form_PipeLocation_AreaName_OutsideOrExterior
        given DF[PipeLocation.AreaName.CustomArea] = horizontal_form_PipeLocation_AreaName_CustomArea
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName]

    // DuctType

    given horizontal_form_DuctType_NonConcentricDuctsHighThermalResistance: CtxDF[DuctType.NonConcentricDuctsHighThermalResistance] = 
        autoDeriveAndOverwriteFieldNames[DuctType.NonConcentricDuctsHighThermalResistance]

    given horizontal_form_DuctType_NonConcentricDuctsLowThermalResistance: CtxDF[DuctType.NonConcentricDuctsLowThermalResistance] = 
        autoDeriveAndOverwriteFieldNames[DuctType.NonConcentricDuctsLowThermalResistance]

    given horizontal_form_DuctType_ConcentricDucts: CtxDF[DuctType.ConcentricDucts] = 
        autoDeriveAndOverwriteFieldNames[DuctType.ConcentricDucts]

    given horizontal_form_DuctType: CtxDF[DuctType] = 
        autoDeriveAndOverwriteFieldNames[DuctType]

    given horizontal_form_PipeLocation: CtxDF[PipeLocation] =
        import defaultable.given_TuTemperature
        given DF[Boolean] = horizontal_form.boolean_trueAsDefault_alwaysValid
        given ValidateVar[PipeLocation] = validatevar.pipeLocation.valid_Always
        given DF[AmbiantAirTemperatureSet] = horizontal_form_AmbiantAirTemperatureSet
        given DF[PipeLocation.AreaName] = horizontal_form_PipeLocation_AreaName
        // be explicit
        given DF[PipeLocation.BoilerRoom] = horizontal_form_PipeLocation_BoilerRoom
        given DF[PipeLocation.HeatedArea] = horizontal_form_PipeLocation_HeatedArea
        given DF[PipeLocation.UnheatedInside] = horizontal_form_PipeLocation_UnheatedInside
        given DF[PipeLocation.OutsideOrExterior] = horizontal_form_PipeLocation_OutsideOrExterior
        given DF[PipeLocation.CustomArea] = horizontal_form_PipeLocation_CustomArea
        autoDeriveAndOverwriteFieldNames[PipeLocation]

    given horizontal_form_PipeLocation_BoilerRoom: LocDF[PipeLocation.BoilerRoom] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.BoilerRoom]

    given horizontal_form_PipeLocation_HeatedArea: LocDF[PipeLocation.HeatedArea] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.HeatedArea]

    given horizontal_form_PipeLocation_UnheatedInside: LocDF[PipeLocation.UnheatedInside] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.UnheatedInside]

    given horizontal_form_PipeLocation_OutsideOrExterior: LocDF[PipeLocation.OutsideOrExterior] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.OutsideOrExterior]

    given horizontal_form_PipeLocation_CustomArea: CtxDF[PipeLocation.CustomArea] =
        given DF[String] = horizontal_form.string_emptyAsDefault_alwaysValid
        given DF[Boolean] = horizontal_form.boolean_falseAsDefault_alwaysValid
        given DF[AmbiantAirTemperatureSet] = horizontal_form_AmbiantAirTemperatureSet
        given DF[PipeLocation.AreaName] = horizontal_form_PipeLocation_AreaName
        autoDeriveAndOverwriteFieldNames[PipeLocation.CustomArea]

    // Ventil Direction

    given horizontal_form_AirSpaceDetailed_VentilDirection_UndefinedDir: CtxDF[AirSpaceDetailed.VentilDirection.UndefinedDir] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection.UndefinedDir]

    given horizontal_form_AirSpaceDetailed_VentilDirection_SameDirAsFlueGas: CtxDF[AirSpaceDetailed.VentilDirection.SameDirAsFlueGas] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection.SameDirAsFlueGas]

    given horizontal_form_AirSpaceDetailed_VentilDirection_OppositeDirOfFlueGas: CtxDF[AirSpaceDetailed.VentilDirection.OppositeDirOfFlueGas] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection.OppositeDirOfFlueGas]

    given horizontal_form_AirSpaceDetailed_VentilDirection: CtxDF[AirSpaceDetailed.VentilDirection] = 
        // be explicit 
        given DF[AirSpaceDetailed.VentilDirection.UndefinedDir] = horizontal_form_AirSpaceDetailed_VentilDirection_UndefinedDir
        given DF[AirSpaceDetailed.VentilDirection.SameDirAsFlueGas] = horizontal_form_AirSpaceDetailed_VentilDirection_SameDirAsFlueGas
        given DF[AirSpaceDetailed.VentilDirection.OppositeDirOfFlueGas] = horizontal_form_AirSpaceDetailed_VentilDirection_OppositeDirOfFlueGas
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection]

    // Ventil Openings

    given horizontal_form_AirSpaceDetailed_VentilOpenings_NoOpening: CtxDF[AirSpaceDetailed.VentilOpenings.NoOpening] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings.NoOpening]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_AnnularAreaFullyOpened: CtxDF[AirSpaceDetailed.VentilOpenings.AnnularAreaFullyOpened] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings.AnnularAreaFullyOpened]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_PartiallyOpened_InAccordanceWith_DTU_24_1: CtxDF[AirSpaceDetailed.VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1]

    given horizontal_form_AirSpaceDetailed_VentilOpenings: CtxDF[AirSpaceDetailed.VentilOpenings] = 
        // be explicit 
        given DF[AirSpaceDetailed.VentilOpenings.NoOpening] = horizontal_form_AirSpaceDetailed_VentilOpenings_NoOpening
        given DF[AirSpaceDetailed.VentilOpenings.AnnularAreaFullyOpened] = horizontal_form_AirSpaceDetailed_VentilOpenings_AnnularAreaFullyOpened
        given DF[AirSpaceDetailed.VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1] = horizontal_form_AirSpaceDetailed_VentilOpenings_PartiallyOpened_InAccordanceWith_DTU_24_1
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings]

    // TuTemperature

    given horizontal_form_TuTemperature_Or_TCelsius: CtxDF[Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius]] =
        given Defaultable[TCelsius] = defaultable.tcelsius // or tuo default value of a specific Country / global setting ?
        given DaisyUIHorizontalForm[TCelsius] = horizontal_form.horizontal_form_TCelsius
        given DaisyUIHorizontalForm[AmbiantAirTemperatureSet.UseTuoOverride] = autoDeriveAndOverwriteFieldNames[AmbiantAirTemperatureSet.UseTuoOverride]
        DaisyUIHorizontalForm.eitherAsSelectWithOptions[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius](
            I18N.en13384._ambiant_air_temperature.short
        )

end horizontal_form_13384
