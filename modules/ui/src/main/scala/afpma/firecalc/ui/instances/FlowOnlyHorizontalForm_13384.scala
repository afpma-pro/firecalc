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

import scala.annotation.nowarn
import scala.deriving.Mirror

import _root_.coulomb.*
import _root_.coulomb.policy.standard.given
import afpma.laminar.form.*
import afpma.laminar.form.Form
import afpma.laminar.form.Form.*
import afpma.laminar.form.derivation.FormDerivation
import io.taig.babel.Locale

class FlowOnlyHorizontalForm_13384(using DisplayUnits, Locale):

    import AddFlowOnlyPipeElement_13384.*
    import SetFlowOnlyPipeProp_13384.*

    private given horizontal_form: HorizontalFormCommonInstances = HorizontalFormCommonInstances()
    import horizontal_form.{*, given}

    private val vv: ValidateVarCommonInstances = ValidateVarCommonInstances()

    // AddElement

    given horizontal_form_SetInnerShape: Form[SetInnerShape] =
        autoDeriveAndOverwriteFieldNames[SetInnerShape]

    given horizontal_form_SetRoughness: Form[SetRoughness] =
        given Form[QtyD[Meter]] = horizontal_form_Roughness
        given Form[Roughness]   = Form.formConversionOpaque[Roughness, QtyD[Meter]]
        autoDeriveAndOverwriteFieldNames[SetRoughness]

    // Material_13384_V2

    given horizontal_form_Material_13384_V2: Form[Material_13384_V2] =
        // Import ShowUsingLocale and extension methods for Material_13384_V2
        import Material_13384_V2.{given, *}

        // Provide form for Roughness (opaque type over QtyD[Meter])
        given Form[QtyD[Meter]] = horizontal_form_Roughness
        given Form[Roughness]   =
            Form.formConversionOpaque[Roughness, QtyD[Meter]]
                .withFieldName(I18N.terms.roughness)

        // Provide Defaultable
        given Defaultable[Material_13384_V2] =
            defaultable_13384.defaultable_Material_13384_v2

        // Provide ValidateVar
        given ValidateVar[Roughness]         = vv.roughness.valid_whenStrictlyPositive
        given ValidateVar[Material_13384_V2] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[Material_13384_V2]

        FormDerivation.forSelectionWithDefaultValue_usingSelectInput[Material_13384_V2, Roughness]   (
            selectOptions    = Material_13384_V2.values,
            getDefaultValue  = _.roughness,
            withDefaultValue = _.withRoughness(_),
            getId            = _.name
        )

    // Material_13384_V1

    given horizontal_form_Material_13384_V1: Form[Material_13384_V1] =
        import Material_13384_V1.given
        given ValidateVar[Material_13384_V1] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[Material_13384_V1]
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId[Material_13384_V1](Material_13384_V1.values.toList)

    given horizontal_form_SetMaterial: Form[SetMaterial] =
        given Form[Material_13384] = horizontal_form_Material_13384_V2
        autoDeriveAndOverwriteFieldNames[SetMaterial]

    given horizontal_form_SetNumberOfFlows: Form[SetNumberOfFlows] =
        import ValidateVarCommonInstances.validOption_always.given
        given Form[Int]       = FormDerivation.forInt
        given Form[NbOfFlows] = Form.formConversionOpaque[NbOfFlows, Int]
        autoDeriveAndOverwriteFieldNames[SetNumberOfFlows]

    given horizontal_form_SetInitialDirection: Form[SetInitialDirection] =
        given Defaultable[SetInitialDirection] =
            Defaultable(SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up))
        given ValidateVar[SetInitialDirection] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[SetInitialDirection]
        Form.makeFor[SetInitialDirection](summon[Defaultable[SetInitialDirection]]): (variable, _) =>
            val azVar   = variable.zoomLazy(_.azimuth)((sid, az) => sid.copy(azimuth = az))
            val inclVar = variable.zoomLazy(_.inclination)((sid, incl) => sid.copy(inclination = incl))
            horizontal_form.renderInitialDirectionForm(azVar, inclVar)

    given horizontal_form_SetInitialPosition: Form[SetInitialPosition] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames[SetInitialPosition]

    given horizontal_form_SetFinalPosition: Form[SetFinalPosition] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames[SetFinalPosition]

    // AddElement

    // helper with string field always validated
    inline def autoDeriveAndOverwriteFieldNames_AddElement_Subtype[A](using inline m: Mirror.Of[A]): Form[A] =
        @nowarn given Form[String] = horizontal_form.string_emptyAsDefault_alwaysValid
        autoDeriveAndOverwriteFieldNames[A]

    // Like above but suppresses the absDir field — absDir is set via the DirectionBadge dropdown.
    // Both places must be updated together when adding a new DC subtype.
    inline def autoDeriveAndOverwriteFieldNames_DC_Subtype[A](using inline m: Mirror.Of[A]): Form[A] =
        import com.raquo.laminar.api.L.span
        @nowarn given Form[String]                    = horizontal_form.string_emptyAsDefault_alwaysValid
        given ValidateVar[Option[AbsoluteDirection]]  =
            ValidateVarCommonInstances.validOption_always.given_ValidateVarOption_AlwaysValid[AbsoluteDirection]
        @nowarn given Form[Option[AbsoluteDirection]] =
            Form.makeFor[Option[AbsoluteDirection]](Defaultable(None))((_, _) => span())
        autoDeriveAndOverwriteFieldNames[A]

    given horizontal_form_AddSectionSlopped: Form[AddSectionSlopped] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionSlopped]

    given horizontal_form_AddSectionHorizontal: Form[AddSectionHorizontal] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionHorizontal]

    given horizontal_form_AddSectionVertical: Form[AddSectionVertical] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionVertical]

    given horizontal_form_AddAngleAdjustable: Form[AddAngleAdjustable] =
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddAngleAdjustable]

    given horizontal_form_AddSharpeAngle_0_to_90: Form[AddSharpeAngle_0_to_90] =
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSharpeAngle_0_to_90]

    given horizontal_form_AddSharpeAngle_0_to_90_Unsafe: Form[AddSharpeAngle_0_to_90_Unsafe] =
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSharpeAngle_0_to_90_Unsafe]

    given horizontal_form_AddSmoothCurve_90: Form[AddSmoothCurve_90] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSmoothCurve_90]

    given horizontal_form_AddSmoothCurve_90_Unsafe: Form[AddSmoothCurve_90_Unsafe] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSmoothCurve_90_Unsafe]

    given horizontal_form_AddSmoothCurve_60: Form[AddSmoothCurve_60] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSmoothCurve_60]

    given horizontal_form_AddSmoothCurve_60_Unsafe: Form[AddSmoothCurve_60_Unsafe] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSmoothCurve_60_Unsafe]

    given horizontal_form_AddElbows_2x45: Form[AddElbows_2x45] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddElbows_2x45]

    given horizontal_form_AddElbows_3x30: Form[AddElbows_3x30] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddElbows_3x30]

    given horizontal_form_AddElbows_4x22p5: Form[AddElbows_4x22p5] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddElbows_4x22p5]

    given horizontal_form_AddSectionDecrease: Form[AddSectionDecrease] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionDecrease]

    given horizontal_form_AddSectionIncrease: Form[AddSectionIncrease] =
        given Form[QtyD[Meter]] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionIncrease]

    given horizontal_form_AddFlowResistance: Form[AddFlowResistance] =
        given Form[OptionOfEither[AreaInCm2, PipeShape]] =
            horizontal_form_Either_AreaInCm2_or_PipeShape
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddFlowResistance]

    // AmbiantAirTemperatureSet

    given horizontal_form_AmbiantAirTemperatureSet: Form[AmbiantAirTemperatureSet] =
        given Form[Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius]] =
            horizontal_form_TuTemperature_Or_TCelsius
        autoDeriveAndOverwriteFieldNames[AmbiantAirTemperatureSet]

    // AppendLayerDescr

    given horizontal_form_FromLambda: Form[AppendLayerDescr.FromLambda] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromLambda]

    given horizontal_form_FromLambdaUsingThickness: Form[AppendLayerDescr.FromLambdaUsingThickness] =
        given Form[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromLambdaUsingThickness]

    given horizontal_form_FromThermalResistanceUsingThickness
        : Form[AppendLayerDescr.FromThermalResistanceUsingThickness] =
        given Form[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromThermalResistanceUsingThickness]

    given horizontal_form_FromThermalResistance: Form[AppendLayerDescr.FromThermalResistance] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.FromThermalResistance]

    given horizontal_form_AirSpaceUsingOuterShape: Form[AppendLayerDescr.AirSpaceUsingOuterShape] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.AirSpaceUsingOuterShape]

    given horizontal_form_AirSpaceUsingThickness: Form[AppendLayerDescr.AirSpaceUsingThickness] =
        given Form[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr.AirSpaceUsingThickness]

    given horizontal_form_AppendLayerDescr: Form[AppendLayerDescr] =
        autoDeriveAndOverwriteFieldNames[AppendLayerDescr]

    // AirSpaceDetailed

    given horizontal_form_AirSpaceDetailed: Form[AirSpaceDetailed] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed]

    given horizontal_form_AirSpaceDetailed_WithoutAirSpace: Form[AirSpaceDetailed.WithoutAirSpace_V2] =
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.WithoutAirSpace_V2]

    given horizontal_form_AirSpaceDetailed_WithAirSpace: Form[AirSpaceDetailed.WithAirSpace_V2] =
        given Form[QtyD[Meter]] = horizontal_form_Length_mm_cm
        autoDeriveAndOverwriteFieldNames[AirSpaceDetailed.WithAirSpace_V2]

    // PipeLocation.AreaName

    given horizontal_form_PipeLocation_AreaName_BoilerRoom       : Form[PipeLocation.AreaName.BoilerRoom]        =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.BoilerRoom]
    given horizontal_form_PipeLocation_AreaName_HeatedArea       : Form[PipeLocation.AreaName.HeatedArea]        =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.HeatedArea]
    given horizontal_form_PipeLocation_AreaName_UnheatedInside   : Form[PipeLocation.AreaName.UnheatedInside]    =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.UnheatedInside]
    given horizontal_form_PipeLocation_AreaName_OutsideOrExterior: Form[PipeLocation.AreaName.OutsideOrExterior] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.OutsideOrExterior]
    given horizontal_form_PipeLocation_AreaName_CustomArea       : Form[PipeLocation.AreaName.CustomArea]        =
        given ValidateVar[Option[String]] = ValidateVarCommonInstances.string.validOption_Always
        given Form[String]                = FormDerivation.forString
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName.CustomArea]

    given horizontal_form_PipeLocation_AreaName: Form[PipeLocation.AreaName] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.AreaName]

    // DuctType

    given horizontal_form_DuctType_NonConcentricDuctsHighThermalResistance
        : Form[DuctType.NonConcentricDuctsHighThermalResistance] =
        autoDeriveAndOverwriteFieldNames[DuctType.NonConcentricDuctsHighThermalResistance]

    given horizontal_form_DuctType_NonConcentricDuctsLowThermalResistance
        : Form[DuctType.NonConcentricDuctsLowThermalResistance] =
        autoDeriveAndOverwriteFieldNames[DuctType.NonConcentricDuctsLowThermalResistance]

    given horizontal_form_DuctType_ConcentricDucts: Form[DuctType.ConcentricDucts] =
        autoDeriveAndOverwriteFieldNames[DuctType.ConcentricDucts]

    given horizontal_form_DuctType: Form[DuctType] =
        autoDeriveAndOverwriteFieldNames[DuctType]

    given horizontal_form_PipeLocation: Form[PipeLocation] =
        autoDeriveAndOverwriteFieldNames[PipeLocation]

    given horizontal_form_PipeLocation_BoilerRoom: Form[PipeLocation.BoilerRoom] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.BoilerRoom]

    given horizontal_form_PipeLocation_HeatedArea: Form[PipeLocation.HeatedArea] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.HeatedArea]

    given horizontal_form_PipeLocation_UnheatedInside: Form[PipeLocation.UnheatedInside] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.UnheatedInside]

    given horizontal_form_PipeLocation_OutsideOrExterior: Form[PipeLocation.OutsideOrExterior] =
        autoDeriveAndOverwriteFieldNames[PipeLocation.OutsideOrExterior]

    given horizontal_form_PipeLocation_CustomArea: Form[PipeLocation.CustomArea] =
        given Form[Boolean] = horizontal_form.boolean_falseAsDefault_alwaysValid
        autoDeriveAndOverwriteFieldNames[PipeLocation.CustomArea]

    // Ventil Direction

    given horizontal_form_AirSpaceDetailed_VentilDirection_UndefinedDir: Form[VentilDirection.UndefinedDir] =
        autoDeriveAndOverwriteFieldNames[VentilDirection.UndefinedDir]

    given horizontal_form_AirSpaceDetailed_VentilDirection_SameDirAsFlueGas: Form[VentilDirection.SameDirAsFlueGas] =
        autoDeriveAndOverwriteFieldNames[VentilDirection.SameDirAsFlueGas]

    given horizontal_form_AirSpaceDetailed_VentilDirection_OppositeDirOfFlueGas
        : Form[VentilDirection.OppositeDirOfFlueGas] =
        autoDeriveAndOverwriteFieldNames[VentilDirection.OppositeDirOfFlueGas]

    given horizontal_form_AirSpaceDetailed_VentilDirection: Form[VentilDirection] =
        autoDeriveAndOverwriteFieldNames[VentilDirection]

    // Ventil Openings

    given horizontal_form_AirSpaceDetailed_VentilOpenings_NoOpening: Form[VentilOpenings.NoOpening] =
        autoDeriveAndOverwriteFieldNames[VentilOpenings.NoOpening]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_AnnularAreaFullyOpened
        : Form[VentilOpenings.AnnularAreaFullyOpened] =
        autoDeriveAndOverwriteFieldNames[VentilOpenings.AnnularAreaFullyOpened]

    given horizontal_form_AirSpaceDetailed_VentilOpenings_PartiallyOpened_InAccordanceWith_DTU_24_1
        : Form[VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1] =
        autoDeriveAndOverwriteFieldNames[VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1]

    given horizontal_form_AirSpaceDetailed_VentilOpenings: Form[VentilOpenings] =
        autoDeriveAndOverwriteFieldNames[VentilOpenings]

    // TuTemperature

    given horizontal_form_TuTemperature_Or_TCelsius: Form[Either[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius]] =
        given Defaultable[TCelsius] =
            defaultable.tcelsius // or tuo default value of a specific Country / global setting ?
        given Form[TCelsius]                                = horizontal_form.horizontal_form_TCelsius
        given Form[AmbiantAirTemperatureSet.UseTuoOverride] =
            autoDeriveAndOverwriteFieldNames[AmbiantAirTemperatureSet.UseTuoOverride]
        FormDerivation.eitherAsSelectWithOptions[AmbiantAirTemperatureSet.UseTuoOverride, TCelsius](
            I18N.en13384._ambiant_air_temperature.short
        )

end FlowOnlyHorizontalForm_13384
