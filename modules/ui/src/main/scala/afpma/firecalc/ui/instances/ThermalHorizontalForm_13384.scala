/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.NbOfFlows

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.components.AppendLayersComponent
import afpma.firecalc.ui.daisyui.DaisyUIHorizontalForm
import afpma.firecalc.ui.formgen.*

import coulomb.*
import coulomb.policy.standard.given

import scala.annotation.nowarn
import scala.deriving.Mirror

import io.taig.babel.Locale
import afpma.firecalc.ui.daisyui.DaisyUIVerticalForm

class ThermalHorizontalForm_13384(using DisplayUnits, Locale):

    import AddThermalPipeElement_13384.*
    import SetThermalPipeProp_13384.*

    // import defaultable.given
    private given horizontal_form: HorizontalFormCommonInstances = HorizontalFormCommonInstances()
    import horizontal_form.{*, given}

    private val vv: ValidateVarCommonInstances = ValidateVarCommonInstances()

    // AddElement

    given horizontal_form_SetPropertiesInBatch: DaisyUIHorizontalForm[SetPropertiesInBatch] =
        import defaultable_13384.incr_descr_en13384.defaultable_Seq_SetSingleProp
        // given DaisyUIHorizontalForm[String] = string_emptyAsDefault_alwaysValid

        // List[SetSingleProp] should be a global vertical form
        // containing horizontal form instances for each "SetSingleProp" element
        given df_list: DaisyUIVerticalForm[List[SetSingleProp]] =
            DaisyUIVerticalForm
                .forList_WithEphemeralIds[SetSingleProp](using horizontal_form_SetSingleProp.toVerticalForm)

        // Convert from List -> Seq
        given seqForm: DaisyUIVerticalForm[Seq[SetSingleProp]] =
            DaisyUIVerticalForm.formConversionOpaque[Seq[SetSingleProp], List[SetSingleProp]](using
                df_list,
                _.toList,
                _.toSeq
            )

        val d: Defaultable[SetPropertiesInBatch] = summon[Defaultable[SetPropertiesInBatch]]
        given vv_batch: ValidateVar[SetPropertiesInBatch] = ValidateVar.valid

        val stringForm: DaisyUIHorizontalForm[String] = string_emptyAsDefault_alwaysValid

        DaisyUIHorizontalForm.makeFor[SetPropertiesInBatch](d): (v, fc) =>
            import afpma.firecalc.ui.instances.ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid
            import afpma.firecalc.ui.components.SetPropertiesInBatchFormComponent
            import afpma.firecalc.ui.models.pipePresetsSignal

            val titleVar   = v.zoomLazy(_.batch_name)((spb, name) => spb.copy(batch_name = name))
            val contentVar = v.zoomLazy(_.props)((spb, props) => spb.copy(props = props))

            val titleElement   = stringForm.render(titleVar, fc)
            val contentElement = seqForm.render(contentVar, fc)

            SetPropertiesInBatchFormComponent(
                v             = v,
                entriesSignal = pipePresetsSignal,
                titleEl       = titleElement,
                contentEl     = contentElement
            ).node

    given horizontal_form_LinedFlue: DaisyUIHorizontalForm[LinedFlue] =
        import defaultable_13384.incr_descr_en13384.given
        val d: Defaultable[LinedFlue] = summon[Defaultable[LinedFlue]]
        given ValidateVar[LinedFlue] = ValidateVar.valid

        DaisyUIHorizontalForm.makeFor[LinedFlue](d): (v, fc) =>
            import com.raquo.laminar.api.L.*

            given vv_spb: ValidateVar[SetPropertiesInBatch] =
                ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[SetPropertiesInBatch]
            given vv_asd: ValidateVar[AirSpaceDetailed] =
                ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[AirSpaceDetailed]

            val linerForm  = horizontal_form_SetPropertiesInBatch
            val casingForm = horizontal_form_SetPropertiesInBatch

            val linerVar  = v.zoomLazy(_.liner)((lf, l) => lf.copy(liner = l))
            val airVar    = v.zoomLazy(_.air_space)((lf, a) => lf.copy(air_space = a))
            val casingVar = v.zoomLazy(_.casing)((lf, c) => lf.copy(casing = c))

            // --- Bidirectional linking: airspace width ↔ casing inner shape ---

            import afpma.firecalc.ui.LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS
            import afpma.firecalc.ui.components.InfoDialog
            import coulomb.syntax.*

            val infoDialog = InfoDialog()

            def linerOuterShape(liner: SetPropertiesInBatch): Option[PipeShape] =
                liner.props.extractInnerShape.map(is => liner.props.extractLayers.compute_outer_shape(is))

            // Adjust the casing inner shape so its smallest dimension matches requiredMinDim.
            // Preserves the existing shape type; only changes the smallest side.
            def adjustCasingInnerShape(existing: Option[PipeShape], requiredMinDim: Length): PipeShape =
                existing match
                    case Some(Circle(_))                                      => Circle(requiredMinDim)
                    case Some(Square(_))                                      => Square(requiredMinDim)
                    case Some(Rectangle(a, b)) if a.value == b.value          => Rectangle(requiredMinDim, requiredMinDim)
                    case Some(Rectangle(a, b)) if a.value < b.value           => Rectangle(requiredMinDim, b)
                    case Some(Rectangle(a, b))                                => Rectangle(a, requiredMinDim)
                    case None                                                 => Square(requiredMinDim)

            def upsertInnerShape(props: Seq[SetSingleProp], newShape: PipeShape): Seq[SetSingleProp] =
                if props.exists(_.isInstanceOf[SetInnerShape]) then
                    props.map { case _: SetInnerShape => SetInnerShape(newShape); case other => other }
                else
                    SetInnerShape(newShape) +: props

            // Derived signal: liner's outer shape (inner shape expanded through wall layers)
            val linerOuterSig: Signal[Option[PipeShape]] =
                linerVar.signal.map(linerOuterShape)

            // Binder 1: (linerOuter + airWidth) → casing inner shape
            val airToCasingBinder =
                linerOuterSig.combineWith(airVar.signal)
                    .distinct
                    .changes
                    .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                    .map {
                        case (Some(los), AirSpaceDetailed_V2.WithAirSpace_V2(width, _, _)) =>
                            val requiredMinDim = (los.dh.toUnit[Meter].value + 2.0 * width.toUnit[Meter].value).withUnit[Meter]
                            Some(requiredMinDim)
                        case _ => None
                    }
                    .withCurrentValueOf(casingVar.signal.map(_.props.extractInnerShape))
                    .map { case (optDim, curCasingInner) =>
                        optDim.map(dim => (adjustCasingInnerShape(curCasingInner, dim), curCasingInner))
                    }
                    .collect { case Some((newShape, curShape)) if !curShape.contains(newShape) => newShape }
                    --> Observer[PipeShape](newShape =>
                        casingVar.update(c => c.copy(props = upsertInnerShape(c.props, newShape)))
                        infoDialog.show(I18N.set_prop.LinedFlue_sync_casing)
                    )

            // Binder 2: (linerOuter + casing inner shape) → air width
            // Observe the full casingVar (not just extractInnerShape) to ensure
            // changes to PipeShape dimensions propagate even through nested zooms.
            val casingToAirBinder =
                linerOuterSig.combineWith(casingVar.signal)
                    .distinct
                    .changes
                    .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                    .map { case (los, casing) =>
                        (los, casing.props.extractInnerShape) match
                            case (Some(l), Some(cis)) =>
                                val dh = cis match
                                    case Rectangle(min, b) if min < b   => min
                                    case Rectangle(a, min) if min < a   => min
                                    case other                          => other.dh
                                val airWidthMeters = (dh.toUnit[Meter].value - l.dh.toUnit[Meter].value) / 2.0
                                if airWidthMeters > 0 then Some(airWidthMeters.withUnit[Meter])
                                else None
                            case _ => None
                    }
                    .distinct
                    .withCurrentValueOf(airVar.signal)
                    .collect {
                        case (Some(newWidth), AirSpaceDetailed_V2.WithAirSpace_V2(curWidth, _, _))
                            if math.abs(newWidth.toUnit[Meter].value - curWidth.toUnit[Meter].value) >= 0.001 => 
                                // equality means diff less than 1mm.
                                // should be enough to prevent looping because of floating computations
                                newWidth

                    }
                    --> Observer[Length](newWidth =>
                        airVar.update {
                            case AirSpaceDetailed_V2.WithAirSpace_V2(_, dir, vo) =>
                                AirSpaceDetailed_V2.WithAirSpace_V2(newWidth, dir, vo)
                            case other => other
                        }
                        infoDialog.show(I18N.set_prop.LinedFlue_sync_airspace)
                    )

            div(
                airToCasingBinder,
                casingToAirBinder,
                infoDialog.node,
                h4(cls := "font-semibold text-sm mb-1", I18N.set_prop.LinedFlue_liner),
                linerForm.render(linerVar, fc),
                h4(cls := "font-semibold text-sm mb-1 mt-2", I18N.en13384.air_space_detailed),
                horizontal_form_AirSpaceDetailed.render(airVar, fc),
                h4(cls := "font-semibold text-sm mb-1 mt-2", I18N.set_prop.LinedFlue_casing),
                casingForm.render(casingVar, fc)
            )

    given horizontal_form_SetSingleProp: DaisyUIHorizontalForm[SetSingleProp] =
        DaisyUIHorizontalForm.splitViaMatchingOnly[SetSingleProp]
    
    given horizontal_form_SetInnerShape: DaisyUIHorizontalForm[SetInnerShape] =
        autoDeriveAndOverwriteFieldNames[SetInnerShape]

    given horizontal_form_SetOuterShape: DaisyUIHorizontalForm[SetOuterShape] =
        autoDeriveAndOverwriteFieldNames[SetOuterShape]

    given horizontal_form_SetThickness: DaisyUIHorizontalForm[SetThickness] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Thickness
        autoDeriveAndOverwriteFieldNames[SetThickness]

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
        autoDeriveAndOverwriteFieldNames[SetMaterial]

    given horizontal_form_SetLayer: DaisyUIHorizontalForm[SetLayer] =
        given DaisyUIHorizontalForm[QtyD[Meter]]         = horizontal_form_Thickness
        autoDeriveAndOverwriteFieldNames[SetLayer]

    given horizontal_form_SetLayers: DaisyUIHorizontalForm[SetLayers] =
        // import defaultable.given_AppendLayerDescr
        given DaisyUIHorizontalForm[List[AppendLayerDescr]] =
            import ValidateVarCommonInstances.valid_always.given
            DaisyUIHorizontalForm.forList_fromComponent: appnd_layers_var =>
                AppendLayersComponent(appnd_layers_var).node
        autoDeriveAndOverwriteFieldNames[SetLayers]

    given horizontal_form_SetAirSpaceAfterLayers: DaisyUIHorizontalForm[SetAirSpaceAfterLayers] =
        autoDeriveAndOverwriteFieldNames[SetAirSpaceAfterLayers]

    given horizontal_form_SetPipeLocation: DaisyUIHorizontalForm[SetPipeLocation] =
        autoDeriveAndOverwriteFieldNames[SetPipeLocation]

    given horizontal_form_SetDuctType: DaisyUIHorizontalForm[SetDuctType] =
        autoDeriveAndOverwriteFieldNames[SetDuctType]

    given horizontal_form_SetNumberOfFlows: DaisyUIHorizontalForm[SetNumberOfFlows] =
        import ValidateVarCommonInstances.validOption_always.given
        given DaisyUIHorizontalForm[Int]       = DaisyUIHorizontalForm.forInt
        given DaisyUIHorizontalForm[NbOfFlows] = DaisyUIHorizontalForm.formConversionOpaque[NbOfFlows, Int]
        autoDeriveAndOverwriteFieldNames[SetNumberOfFlows]

    // AddElement

    // helper with string field always validated
    inline def autoDeriveAndOverwriteFieldNames_AddElement_Subtype[A](using inline m: Mirror.Of[A]): DaisyUIHorizontalForm[A] =
        @nowarn given DaisyUIHorizontalForm[String] = horizontal_form.string_emptyAsDefault_alwaysValid
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
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddAngleAdjustable]

    given horizontal_form_AddSharpeAngle_0_to_90: DaisyUIHorizontalForm[AddSharpeAngle_0_to_90] =
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSharpeAngle_0_to_90]

    given horizontal_form_AddSharpeAngle_0_to_90_Unsafe: DaisyUIHorizontalForm[AddSharpeAngle_0_to_90_Unsafe] =
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSharpeAngle_0_to_90_Unsafe]

    given horizontal_form_AddSmoothCurve_90: DaisyUIHorizontalForm[AddSmoothCurve_90] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSmoothCurve_90]

    given horizontal_form_AddSmoothCurve_90_Unsafe: DaisyUIHorizontalForm[AddSmoothCurve_90_Unsafe] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSmoothCurve_90_Unsafe]

    given horizontal_form_AddSmoothCurve_60: DaisyUIHorizontalForm[AddSmoothCurve_60] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSmoothCurve_60]

    given horizontal_form_AddSmoothCurve_60_Unsafe: DaisyUIHorizontalForm[AddSmoothCurve_60_Unsafe] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSmoothCurve_60_Unsafe]

    given horizontal_form_AddElbows_2x45: DaisyUIHorizontalForm[AddElbows_2x45] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddElbows_2x45]

    given horizontal_form_AddElbows_3x30: DaisyUIHorizontalForm[AddElbows_3x30] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddElbows_3x30]

    given horizontal_form_AddElbows_4x22p5: DaisyUIHorizontalForm[AddElbows_4x22p5] =
        given DaisyUIHorizontalForm[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddElbows_4x22p5]

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

    @nowarn
    given horizontal_form_AppendLayerDescr: DaisyUIHorizontalForm[AppendLayerDescr] =
        given DaisyUIHorizontalForm[QtyD[Meter]]     = horizontal_form_Length_mm_cm
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
        : DaisyUIHorizontalForm[AirSpaceDetailed.VentilDirection.UndefinedDir] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection.UndefinedDir]

    given horizontal_form_AirSpaceDetailed_VentilDirection_SameDirAsFlueGas
        : DaisyUIHorizontalForm[AirSpaceDetailed.VentilDirection.SameDirAsFlueGas] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection.SameDirAsFlueGas]

    given horizontal_form_AirSpaceDetailed_VentilDirection_OppositeDirOfFlueGas
        : DaisyUIHorizontalForm[AirSpaceDetailed.VentilDirection.OppositeDirOfFlueGas] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection.OppositeDirOfFlueGas]

    given horizontal_form_AirSpaceDetailed_VentilDirection: DaisyUIHorizontalForm[AirSpaceDetailed.VentilDirection] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilDirection]

    // Ventil Openings

    given horizontal_form_AirSpaceDetailed_VentilOpenings_NoOpening: DaisyUIHorizontalForm[AirSpaceDetailed.VentilOpenings.NoOpening] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings.NoOpening]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_AnnularAreaFullyOpened
        : DaisyUIHorizontalForm[AirSpaceDetailed.VentilOpenings.AnnularAreaFullyOpened] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings.AnnularAreaFullyOpened]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_PartiallyOpened_InAccordanceWith_DTU_24_1
        : DaisyUIHorizontalForm[AirSpaceDetailed.VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1]

    given horizontal_form_AirSpaceDetailed_VentilOpenings: DaisyUIHorizontalForm[AirSpaceDetailed.VentilOpenings] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.VentilOpenings]

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

end ThermalHorizontalForm_13384
