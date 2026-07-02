/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
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

class FlowOnlyHorizontalForm_15544(using DisplayUnits, Locale):

    import AddFlowOnlyPipeElement_15544.*
    import SetFlowOnlyPipeProp_15544.*
    import FlowOnlyChannelTopologyOp_15544.*

    // import defaultable.given
    private given horizontal_form: HorizontalFormCommonInstances = HorizontalFormCommonInstances()
    import horizontal_form.{*, given}

    private val vv: ValidateVarCommonInstances = ValidateVarCommonInstances()

    // SetProp

    given horizontal_form_SetInnerShape: Form[SetInnerShape] =
        autoDeriveAndOverwriteFieldNames[SetInnerShape]

    given horizontal_form_SetInnerShapePreventSectionGeometryChangeAuto
        : Form[SetInnerShapePreventSectionGeometryChangeAuto] =
        autoDeriveAndOverwriteFieldNames[SetInnerShapePreventSectionGeometryChangeAuto]

    given horizontal_form_SetRoughness: Form[SetRoughness] =
        given Form[QtyD[Meter]] = horizontal_form_Roughness
        given Form[Roughness]   = Form.formConversionOpaque[Roughness, QtyD[Meter]]
        autoDeriveAndOverwriteFieldNames[SetRoughness]

    // Material 15544 V2

    given horizontal_form_Material_15544_V2: Form[Material_15544_V2] =
        // Import ShowUsingLocale and extension methods for Material_15544_V2
        import Material_15544_V2.{given, *}

        // Provide form for Roughness (opaque type over QtyD[Meter])
        given Form[QtyD[Meter]] = horizontal_form_Roughness
        given Form[Roughness]   =
            Form.formConversionOpaque[Roughness, QtyD[Meter]]
                .withFieldName(I18N.terms.roughness)

        // Provide Defaultable
        given Defaultable[Material_15544_V2] =
            defaultable_15544.defaultable_material_15544_v2

        // Provide ValidateVar
        given ValidateVar[Roughness]         = vv.roughness.valid_whenStrictlyPositive
        given ValidateVar[Material_15544_V2] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[Material_15544_V2]

        FormDerivation.forSelectionWithDefaultValue_usingSelectInput[Material_15544_V2, Roughness]   (
            selectOptions    = Material_15544_V2.values,
            getDefaultValue  = _.roughness,
            withDefaultValue = _.withRoughness(_),
            getId            = _.name
        )

    // Material 15544 V1

    given horizontal_form_Material_15544_V1: Form[Material_15544_V1] =
        import Material_15544_V1.given
        given ValidateVar[Material_15544_V1] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[Material_15544_V1]
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId[Material_15544_V1](Material_15544_V1.values.toList)

    given horizontal_form_SetMaterial: Form[SetMaterial] =
        autoDeriveAndOverwriteFieldNames[SetMaterial]

    given horizontal_form_SetNumberOfFlows: Form[SetNumberOfFlows] =
        import ValidateVarCommonInstances.validOption_always.given
        given Form[Int]       = FormDerivation.forInt
        given Form[NbOfFlows] = Form.formConversionOpaque[NbOfFlows, Int]
        autoDeriveAndOverwriteFieldNames[SetNumberOfFlows]

    // AddElement

    // helper with string field always validated
    inline def autoDeriveAndOverwriteFieldNames_AddElement_Subtype[A](using
        inline m: Mirror.Of[A]
    ): Form[A] =
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

    given horizontal_form_AddSharpeAngle_0_to_180: Form[AddSharpeAngle_0_to_180] =
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddSharpeAngle_0_to_180]

    given horizontal_form_AddCircularArc_60: Form[AddCircularArc_60] =
        autoDeriveAndOverwriteFieldNames_DC_Subtype[AddCircularArc_60]

    given horizontal_form_AddSectionShapeChange: Form[AddSectionShapeChange] =
        given Form[String] = horizontal_form.string_emptyAsDefault_alwaysValid
        autoDeriveAndOverwriteFieldNames[AddSectionShapeChange]

    given horizontal_form_AddFlowResistance: Form[AddFlowResistance] =
        given Form[OptionOfEither[AreaInCm2, PipeShape]] =
            horizontal_form_Either_AreaInCm2_or_PipeShape
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddFlowResistance]

    given horizontal_form_AddPressureDiff: Form[AddPressureDiff] =
        given Form[QtyD[Pascal]] = horizontal_form_QtyD_Pascal
        autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddPressureDiff]

    // Renders only newInnerShape; name + RDI + badge are composed by the panel (customFormNode)
    private def splitMergeForm[A](
        getShape: A => PipeShape,
        setShape: (A, PipeShape) => A
    )(using
        Defaultable[A],
        Defaultable[Option[AbsoluteDirection]],
        Defaultable[PipeShape],
        ValidateVar[A]
    ): Form[A] =
        val d = summon[Defaultable[A]]
        Form.makeFor[A](d): (v, fc) =>
            val shapeVar = v.zoomLazy(getShape)(setShape)
            Form[PipeShape].render(shapeVar, fc)

    given horizontal_form_SplitSingleFlowIntoTwoFlowsWith90DegTurn: Form[SplitSingleFlowIntoTwoFlowsWith90DegTurn] =
        import defaultable.pipeShapeInner
        given Defaultable[Option[AbsoluteDirection]]                = Defaultable(None)
        given ValidateVar[SplitSingleFlowIntoTwoFlowsWith90DegTurn] =
            ValidateVarCommonInstances.valid_always
                .given_ValidateVar_AlwaysValid[SplitSingleFlowIntoTwoFlowsWith90DegTurn]
        splitMergeForm(_.newInnerShape, (a, s) => a.copy(newInnerShape = s))

    given horizontal_form_MergeTwoFlowsIntoSingleWith90DegTurn: Form[MergeTwoFlowsIntoSingleWith90DegTurn] =
        import defaultable.pipeShapeInner
        given Defaultable[Option[AbsoluteDirection]]            = Defaultable(None)
        given ValidateVar[MergeTwoFlowsIntoSingleWith90DegTurn] =
            ValidateVarCommonInstances.valid_always
                .given_ValidateVar_AlwaysValid[MergeTwoFlowsIntoSingleWith90DegTurn]
        splitMergeForm(_.newInnerShape, (a, s) => a.copy(newInnerShape = s))

end FlowOnlyHorizontalForm_15544
