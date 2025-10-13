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
        autoDeriveAndOverwriteFieldNames[SetMaterial]

    given horizontal_form_SetLayer: CtxDF[SetLayer] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Thickness
        given DaisyUIHorizontalForm[ThermalConductivity] = horizontal_form_ThermalConductivity
        DaisyUIHorizontalForm.autoDerived[SetLayer]
        .autoOverwriteFieldNames
        // autoDeriveAndOverwriteFieldNames[SetLayer]

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

    given horizontal_form_SetDuctType: LocDF[SetDuctType] = 
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
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.AirSpaceUsingOuterShape]
    
    given horizontal_form_AirSpaceUsingThickness: CtxDF[AppendLayerDescr.AirSpaceUsingThickness] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.AirSpaceUsingThickness]

    
    given horizontal_form_AppendLayerDescr: CtxDF[AppendLayerDescr] = 
        given DF[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr]

    // AirSpaceDetailed

    given horizontal_form_AirSpaceDetailed: CtxDF[AirSpaceDetailed] = 
        given DF[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed]

    given horizontal_form_AirSpaceDetailed_WithoutAirSpace: CtxDF[AirSpaceDetailed.WithoutAirSpace] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.WithoutAirSpace]

    given horizontal_form_AirSpaceDetailed_WithAirSpace: CtxDF[AirSpaceDetailed.WithAirSpace] = 
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.WithAirSpace]

    // PipeLocation

    // import cats.implicits.toShow

    
    // TODO: OTHER APPROACH TO TRY TO REORDER PIPE LOCATION TYPE
    // private def makeIdsForSelectOptions[A: Show](options: Seq[A]): List[(String, A)] = 
    //     options.map(a => (a.show, a)).toList
    
    // given horizontal_form_PipeLocation: LocDF[PipeLocation] =
    //     import defaultable.given_TuTemperature
    //     given ValidateVar[PipeLocation] = 
    //         validatevar.valid_always.given_ValidateVar_AlwaysValid[PipeLocation]

        // DaisyUIHorizontalForm.forEnumOrSumTypeLike_UsingShowAsId[PipeLocation](
        //     options         = List(
        //         PipeLocation.BoilerRoom,
        //         PipeLocation.HeatedArea,
        //         PipeLocation.UnheatedInside,
        //         PipeLocation.OutsideOrExterior,
        //     ),
        //     // options = PipeLocation.AreaHeatingStatus.values,
        //     updateFieldName = _ => Some(I18N.area_heating_status._self)
        // )


        // import PipeLocation.show_PipeLocation
        // given _show: Show[PipeLocation] = show_PipeLocation
        // val options: List[PipeLocation] = List(
        //     PipeLocation.BoilerRoom,
        //     PipeLocation.HeatedArea,
        //     PipeLocation.UnheatedInside,
        //     PipeLocation.OutsideOrExterior,
        // )
        // DaisyUIHorizontalForm.mkFromComponent: (v, fc) =>
        //     val ids = makeIdsForSelectOptions[PipeLocation](options)
        //     def getById(id: String): PipeLocation = ids
        //         .find(x => x._1 == id)
        //         .getOrElse(throw new Exception("unexpected error: can not get element back"))
        //         ._2
        //     SelectFieldsetLabelAndInput(
        //         labelOpt = Some(I18N.area_heating_status._self),
        //         selectedVar = v,
        //         options = options,
        //         show = _show.show,
        //         makeId = _show.show,
        //         getById = getById,
        //         isOptional = false,
        //     )

    given horizontal_form_PipeLocation: LocDF[PipeLocation] =
        given ValidateVar[Option[String]] = validatevar.string.validOption_Always
        given DF[String] = DaisyUIHorizontalForm.forString
        given DF[Boolean] = horizontal_form.boolean_trueAsDefault_alwaysValid
        autoDeriveAndOverwriteFieldNames[PipeLocation]

    given horizontal_form_PipeLocation_BoilerRoom: LocDF[PipeLocation.BoilerRoom] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.BoilerRoom]

    given horizontal_form_PipeLocation_HeatedArea: LocDF[PipeLocation.HeatedArea] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.HeatedArea]

    given horizontal_form_PipeLocation_UnheatedInside: LocDF[PipeLocation.UnheatedInside] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.UnheatedInside]

    given horizontal_form_PipeLocation_OutsideOrExterior: LocDF[PipeLocation.OutsideOrExterior] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.OutsideOrExterior]

    given horizontal_form_PipeLocation_CustomArea: LocDF[PipeLocation.CustomArea] =
        given DaisyUIHorizontalForm[String] = horizontal_form.string_emptyAsDefault_alwaysValid
        given DaisyUIHorizontalForm[Boolean] = horizontal_form.boolean_falseAsDefault_alwaysValid
        autoDeriveAndOverwriteFieldNames[PipeLocation.CustomArea]

    // Ventil Direction

    given horizontal_form_AirSpaceDetailed_VentilDirection_UndefinedDir: CtxDF[AirSpaceDetailed.VentilDirection.UndefinedDir] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection.UndefinedDir]

    given horizontal_form_AirSpaceDetailed_VentilDirection_SameDirAsFlueGas: CtxDF[AirSpaceDetailed.VentilDirection.SameDirAsFlueGas] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection.SameDirAsFlueGas]

    given horizontal_form_AirSpaceDetailed_VentilDirection_InverseDirAsFlueGas: CtxDF[AirSpaceDetailed.VentilDirection.OppositeDirOfFlueGas] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection.OppositeDirOfFlueGas]

    // Ventil Openings

    given horizontal_form_AirSpaceDetailed_VentilOpenings_ZeroOpenings: CtxDF[AirSpaceDetailed.VentilOpenings.NoOpening] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings.NoOpening]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_FullAnnularSurface: CtxDF[AirSpaceDetailed.VentilOpenings.AnnularAreaFullyOpened] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings.AnnularAreaFullyOpened]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_Partial_DTU_24_1: CtxDF[AirSpaceDetailed.VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1] = 
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1]

    // TuTemperature

    given horizontal_form_TuTemperature_Or_TCelsius: CtxDF[Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius]] =
        given Defaultable[TCelsius] = defaultable.tcelsius // or tuo default value of a specific Country / global setting ?
        given DaisyUIHorizontalForm[TCelsius] = horizontal_form.horizontal_form_TCelsius
        given DaisyUIHorizontalForm[AmbiantAirTemperatureSet.UseTuoOverride] = autoDeriveAndOverwriteFieldNames[AmbiantAirTemperatureSet.UseTuoOverride]
        DaisyUIHorizontalForm.eitherAsSelectWithOptions[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius](
            I18N.en13384._ambiant_air_temperature.short
        )

end horizontal_form_13384
