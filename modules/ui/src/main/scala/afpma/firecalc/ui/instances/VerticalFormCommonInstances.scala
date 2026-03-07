/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.ζ

import afpma.firecalc.payments.shared.i18n.implicits.I18N_PaymentsShared

import afpma.firecalc.ui.daisyui.DaisyUIInputs.DoubleFieldsetLabelAndInput
import afpma.firecalc.ui.daisyui.DaisyUIInputs.FieldsetLabelAndContent
import afpma.firecalc.ui.daisyui.DaisyUIInputs.FieldsetLegendWithContent
import afpma.firecalc.ui.daisyui.DaisyUIInputs.SelectAndOptionsOnly
import afpma.firecalc.ui.daisyui.DaisyUIVerticalForm
import afpma.firecalc.ui.formgen.ConditionalFor
import afpma.firecalc.ui.formgen.Defaultable
import afpma.firecalc.ui.formgen.ValidateVar
import afpma.firecalc.ui.components.FireboxCatalogSelectComponent
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.models.BillableCountry
import afpma.firecalc.ui.models.BillableCustomerType
import afpma.firecalc.ui.models.BillingLanguage
import afpma.firecalc.ui.models.door15aFireboxesSignal
import afpma.firecalc.ui.models.stove_params_var
import afpma.firecalc.ui.models.firebox_var

import cats.Show

import com.raquo.airstream.state.Var

import coulomb.*

import scala.deriving.Mirror

import io.taig.babel.Locale

class VerticalFormCommonInstances(using DisplayUnits, Locale):
    
    import SUnits.given
    // import defaultable.given
    import hastranslations.given

    private given dual: DualCommonInstances = new DualCommonInstances()
    import dual.given

    private val vv: ValidateVarCommonInstances = ValidateVarCommonInstances()

    type DF[A]    = DaisyUIVerticalForm[A]

    private given horizontal_form: HorizontalFormCommonInstances = HorizontalFormCommonInstances()

    // factory helper

    inline def autoDeriveAndOverwriteFieldNames[A](using inline m: Mirror.Of[A]): DaisyUIVerticalForm[A] =
        DaisyUIVerticalForm
            .autoDerived[A](using m)
            .autoOverwriteFieldNames

    // BasicTypes

    val boolean_trueAsDefault_alwaysValid: DaisyUIVerticalForm[Boolean] =
        import defaultable.boolean.asTrue
        import ValidateVarCommonInstances.boolean.valid_Always
        DaisyUIVerticalForm.forBoolean

    val boolean_falseAsDefault_alwaysValid: DaisyUIVerticalForm[Boolean] =
        import defaultable.boolean.asFalse
        import ValidateVarCommonInstances.boolean.valid_Always
        DaisyUIVerticalForm.forBoolean

    val string_emptyAsDefault_alwaysValid: DaisyUIVerticalForm[String] =
        import defaultable.string.empty
        import ValidateVarCommonInstances.string.validOption_Always
        DaisyUIVerticalForm.forString

    val int_emptyAsDefault_alwaysValid: DaisyUIVerticalForm[Int] =
        import defaultable.int.empty
        import ValidateVarCommonInstances.int.validOption_Always
        DaisyUIVerticalForm.forInt

    val double_emptyAsDefault_alwaysValid: DaisyUIVerticalForm[Double] =
        import defaultable.double.empty
        import ValidateVarCommonInstances.double.validOption_Always
        DaisyUIVerticalForm.forDouble

    // QtyD

    given given_QtyD_Centimer: Defaultable[QtyD[Centimeter]] => DaisyUIVerticalForm[QtyD[Centimeter]] =
        import vv.centimeter.validOption_whenStrictlyPositive
        DaisyUIVerticalForm.forQtyD[Centimeter](using SUnits.sunit_Centimeter)

    given given_QtyD_Hour: Defaultable[QtyD[Hour]] => DaisyUIVerticalForm[QtyD[Hour]] =
        import vv.hour.validOption_whenStrictlyPositive
        DaisyUIVerticalForm.forQtyD[Hour]

    given given_QtyD_Kilogram: Defaultable[QtyD[Kilogram]] => DaisyUIVerticalForm[QtyD[Kilogram]] =
        import vv.kilogram.validOption_whenStrictlyPositive
        DaisyUIVerticalForm.forQtyD[Kilogram]

    given given_QtyD_Kilowatt: Defaultable[QtyD[Kilo * Watt]] => DaisyUIVerticalForm[QtyD[Kilo * Watt]] =
        import vv.kilowatt.validOption_whenStrictlyPositive
        DaisyUIVerticalForm.forQtyD[Kilo * Watt]

    given given_Option_QtyD_Kilowatt: DaisyUIVerticalForm[Option[QtyD[Kilo * Watt]]] =
        import vv.kilowatt.validOption_whenStrictlyPositive
        DaisyUIVerticalForm.forOptionQtyD_default[Kilo * Watt]

    given given_QtyD_Percent: Defaultable[QtyD[Percent]] => DaisyUIVerticalForm[QtyD[Percent]] =
        import vv.percent.validOption_whenPositive
        DaisyUIVerticalForm.forQtyD[Percent]

    val vertical_form_Option_QtyD_Percent: DaisyUIVerticalForm[Option[QtyD[Percent]]] =
        import vv.percent.validOption_whenPositive
        DaisyUIVerticalForm.forOptionQtyD_default[Percent]

    val vertical_form_Option_QtyD_Kilogram: DaisyUIVerticalForm[Option[QtyD[Kilogram]]] =
        import vv.kilogram.validOption_whenStrictlyPositive
        DaisyUIVerticalForm.forOptionQtyD_default[Kilogram]

    given given_QtyD_Dimensionless: Defaultable[QtyD[1]] => DaisyUIVerticalForm[QtyD[1]] =
        import vv.unitless.validOption_whenPositive
        DaisyUIVerticalForm.forQtyD[1]

    val vertical_form_Option_QtyD_Dimensionless: DaisyUIVerticalForm[Option[QtyD[1]]] =
        import vv.unitless.validOption_whenPositive
        DaisyUIVerticalForm.forOptionQtyD_default[1]

    given given_TCelsius: Defaultable[TCelsius] => DaisyUIVerticalForm[TCelsius] =
        import vv.temp.celsius.validOption_whenPositive
        DaisyUIVerticalForm.forTempD[Celsius]

    val vertical_form_Option_TCelsius: DaisyUIVerticalForm[Option[TCelsius]] =
        import vv.temp.celsius.validOption_whenPositive
        DaisyUIVerticalForm.forOptionTempD_default[Celsius]

    val vertical_form_EmissionValueU: DaisyUIVerticalForm[EmissionValueU] =
        given ValidateVar[Option[QtyD[Milli * Gram / (Meter ^ 3)]]] = ValidateVar.valid
        given Defaultable[QtyD[Milli * Gram / (Meter ^ 3)]] = Defaultable(0.0.mg_per_Nm3)
        given DaisyUIVerticalForm[QtyD[Milli * Gram / (Meter ^ 3)]] =
            DaisyUIVerticalForm.forQtyD[Milli * Gram / (Meter ^ 3)](using SUnits.sunit_MilligramPerNm3)
        DaisyUIVerticalForm.formConversionOpaque[EmissionValueU, QtyD[Milli * Gram / (Meter ^ 3)]]

    val given_HeatOutputReduced_NotDefined_Or_Tested
        : DaisyUIVerticalForm[HeatOutputReduced.NotDefined_Or_Tested] =
        val fromTypeTestDefault
            : HeatOutputReduced.NotDefined_Or_Tested = HeatOutputReduced.FromTypeTest(0.0.kW)
        given Defaultable[HeatOutputReduced.NotDefined_Or_Tested] = Defaultable(
            HeatOutputReduced.NotDefined
        )
        DaisyUIVerticalForm.mk_AlwaysValid[HeatOutputReduced.NotDefined_Or_Tested]: (va, _) =>
            FieldsetLabelAndContent(
                label = I18N.en15544.terms.P_n_reduced.name,
                SelectAndOptionsOnly.fromShow[HeatOutputReduced.NotDefined_Or_Tested](
                    selectedVar           = va,
                    labelAsDisabledOption = None,
                    options = Seq(
                        HeatOutputReduced.NotDefined,
                        fromTypeTestDefault
                    )
                )
            )

    val vertical_form_Length_cm: DF[Length] =
        import vv.meter.valid_whenStrictlyPositive
        import defaultable.qty_d.meter.zero
        given_dual_Length_cm.form_DaisyUIVerticalForm

    val vertical_form_Length_mm_cm: DF[Length] =
        import vv.meter.valid_whenStrictlyPositive
        import defaultable.qty_d.meter.zero
        given_dual_Length_mm_cm.form_DaisyUIVerticalForm

    // Business logic types

    // alias
    def I18N_COS(using Locale) = I18N.local_conditions.chimney_termination

    given given_Address: DF[Address] =
        given DaisyUIVerticalForm[String] = string_emptyAsDefault_alwaysValid
        DaisyUIVerticalForm.autoDerived[Address].autoOverwriteFieldNames

    // Area : cm2
    given vertical_form_AreaInCm2: DF[AreaInCm2] =
        import defaultable.qty_d.area_in_cm2.zero
        import vv.area_in_cm2.valid_whenStrictlyPositive
        given_dual_Area_cm2_or_in2.form_DaisyUIVerticalForm

    // Area : cm2 + m2
    given vertical_form_Area_cm2_m2: DF[Area] =
        import defaultable.qty_d.area.zero
        import vv.area.valid_whenStrictlyPositive
        given_dual_Area_cm2_m2_or_in2.form_DaisyUIVerticalForm
            .withFieldName(I18N.terms.area)

    // Firebox

    given given_Firebox_Traditional: DF[Firebox.Traditional] =
        given DaisyUIVerticalForm[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
            horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal.toVerticalForm
        given DaisyUIVerticalForm[Length]                                                         = vertical_form_Length_cm
        given DaisyUIVerticalForm[Area]                                                           = vertical_form_Area_cm2_m2

        // zeta = 0.3 by default
        given DaisyUIVerticalForm[QtyD[1]] =
            vertical_form_zeta_as_QtyD(Defaultable(0.3.unitless))
                .withFieldName(I18N.firebox.traditional.pressure_loss_coefficient_from_door)
                .showFieldName

        DaisyUIVerticalForm.autoDerived[Firebox.Traditional].autoOverwriteFieldNames

    private enum Version  :
        case Version_1, Version_2
    private object Version:

        type Version_1 = Version_1.type
        type Version_2 = Version_2.type

        given c1: Conversion[
            Either[Version_1, Version_2],
            Either["Version 1", "Version 2"]
        ] = {
            case Left(_)  => Left("Version 1")
            case Right(_) => Right("Version 2")
        }
        given c2: Conversion[
            Either["Version 1", "Version 2"],
            Either[Version_1, Version_2]
        ] = {
            case Left(_)  => Left(Version.Version_1)
            case Right(_) => Right(Version.Version_2)
        }

        given c3: Conversion[
            Version,
            Either["Version 1", "Version 2"]
        ] = {
            case Version_1 => Left("Version 1")
            case Version_2 => Right("Version 2")
        }
        given c4: Conversion[
            Either["Version 1", "Version 2"],
            Version
        ] = {
            case Left(_)  => Version_1
            case Right(_) => Version_2
        }

        given ValidateVar[Version]         = ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[Version]
        given DaisyUIVerticalForm[Version] = DaisyUIVerticalForm
            .forEnumOrSumTypeLike_UsingShowAsId[Version](Version.values.toList)
        given Show[Version]                = Show.show:
            case Version.Version_1 => "Version 1"
            case Version.Version_2 => "Version 2"

    private given given_Ecolabeled_Version: DaisyUIVerticalForm[Version] =
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId[Version](
            options = List(
                Version.Version_1,
                Version.Version_2
            )
        )
        // DaisyUIVerticalForm.eitherAsSelectWithOptions[Version.Version_1, Version.Version_2]("Version")

    given DaisyUIVerticalForm[Either["Version 1", "Version 2"]] =
        DaisyUIVerticalForm.formConversionOpaque[
            Either["Version 1", "Version 2"],
            Version
        ]

    private def ecolabeled(default_version: Version): DF[Firebox.Ecolabeled] =
        import com.raquo.laminar.api.enrichSource
        val version_var: Var[Version] = Var(default_version)

        // update and bind version_var from app state
        val binder = firebox_var.signal
            .mapLazy {
                case eco: Firebox.Ecolabeled =>
                    eco.version match
                        case Left("Version 1")  => Some(Version.Version_1)
                        case Right("Version 2") => Some(Version.Version_2)
                case _ => None
            }
            .changes
            .filter(_.isDefined)
            .map(_.get) --> version_var.writer

        given ctx_Length: DF[Length] = vertical_form_Length_cm

        given DaisyUIVerticalForm[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
            horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal.toVerticalForm

        given ConditionalFor[Version, PipeShape] = ConditionalFor:
            case Version.Version_1 => false
            case Version.Version_2 => true

        import defaultable.qty_d.meter.zero

        given DF[PipeShape] =
            horizontal_form.horizontal_form_PipeShape.toVerticalForm.showFieldName

        given DF[Option[PipeShape]] =
            DaisyUIVerticalForm.conditionalOn[Version, PipeShape](version_var, extraBinders = Seq(binder))

        DaisyUIVerticalForm.autoDerived[Firebox.Ecolabeled].autoOverwriteFieldNames

    given given_AFPMA_PRSE: DF[Firebox.AFPMA_PRSE] =
        given DF[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
            horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal.toVerticalForm
        given DF[Int]                                                            = int_emptyAsDefault_alwaysValid
        // given DF[Boolean]                                                        = boolean_trueAsDefault_alwaysValid
        given DF[PipeShape]                                                      = horizontal_form.horizontal_form_PipeShape.toVerticalForm.hideFieldName
            .wrappedInto(c =>
                FieldsetLabelAndContent  (
                    label   = I18N.firebox.afpma_prse.outside_air_conduit_shape,
                    content = c
                )
            )

        given DF[Length]                     = vertical_form_Length_cm
        given DF[OutsideAirLocationInHeater] = DaisyUIVerticalForm.mk_AlwaysValid: (va, _) =>
            FieldsetLabelAndContent(
                label = I18N.firebox.afpma_prse.outside_air_location_in_heater,
                SelectAndOptionsOnly.single(
                    va.now(),
                    asDisabled = false
                )
            )

        DaisyUIVerticalForm.autoDerived[Firebox.AFPMA_PRSE].autoOverwriteFieldNames

    given Locale => DaisyUIVerticalForm[BillingLanguage] =
        import ValidateVarCommonInstances.billingLanguage.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = BillingLanguage.values.toList,
            updateFieldName = _ => Some("-BILLING LANGUAGE-")
        )

    // TODO: handle specific national standard like ONORM 8303 ?
    given given_TestStandard: DF[TestStandard] =
        import ValidateVarCommonInstances.testStandard.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options         = List(TestStandard.EN_15250, TestStandard.EN_13229),
            updateFieldName = _ => Some(I18N.firebox.single_tested.test_standard)  // TODO: handle National standard (e.g. ÖNORM B 8303)
        )

    given given_Firebox_SingleTested: DF[Firebox.SingleTested] =
        import defaultable.qty_d.zeroWithUnit
        given defaultable_TCelsius: Defaultable[TCelsius] = defaultable.given_TCelsius
        given DF[TestStandard]                           = given_TestStandard
        given DF[String]                                 = string_emptyAsDefault_alwaysValid
        given DF[Length]                                 = vertical_form_Length_cm
        given DF[Boolean]                                = boolean_trueAsDefault_alwaysValid
        given percDF: DF[Percentage]                     = given_QtyD_Percent
        given optPerc: DF[Option[Percentage]]            = vertical_form_Option_QtyD_Percent
        given massDF: DF[Mass]                           = given_QtyD_Kilogram
        given optMass: DF[Option[Mass]]                  = vertical_form_Option_QtyD_Kilogram
        given dimDF: DF[Dimensionless]                   = given_QtyD_Dimensionless
        given optDim: DF[Option[Dimensionless]]          = vertical_form_Option_QtyD_Dimensionless
        given tempDF: DF[TCelsius]                       = given_TCelsius
        given optTemp: DF[Option[TCelsius]]              = vertical_form_Option_TCelsius
        given DF[HeatOutputReduced.NotDefined_Or_Tested] = given_HeatOutputReduced_NotDefined_Or_Tested
        given DF[EmissionValueU]                         = vertical_form_EmissionValueU
        DaisyUIVerticalForm.autoDerived[Firebox.SingleTested].autoOverwriteFieldNames

    given given_Firebox_Door15aFirebox_Catalog: DF[Firebox.Door15aFirebox_Catalog] =
        import vv.kilogram.validOption_whenStrictlyPositive
        import defaultable.qty_d.kilogram.ten
        import defaultable.qty_d.centimeter.zero

        // Defaultable for Percent (needed by given_QtyD_Percent context param)
        given Defaultable[QtyD[Percent]] = defaultable.qty_d.zeroWithUnit[Percent]

        // Create a zoomed Var for maximum_load that syncs with stove_params
        val maximumLoadVar: Var[Option[Mass]] =
            stove_params_var.zoomLazy(_.maximum_load)((sp, m) =>
                if (m == sp.maximum_load) sp
                else if (m.isDefined) sp.with_mB(m.get) else sp
            )

        // Standard field definitions
        given DF[String]                           = string_emptyAsDefault_alwaysValid
        given DF[Length]                           = vertical_form_Length_cm
        given DF[QtyD[Centimeter]]                 = given_QtyD_Centimer
        given percDF: DF[Percentage]               = given_QtyD_Percent
        given optPerc: DF[Option[Percentage]]      = vertical_form_Option_QtyD_Percent

        // Optional centimeter (sb_min, sb_max)
        val vertical_form_Option_QtyD_Centimeter: DF[Option[QtyD[Centimeter]]] =
            import vv.centimeter.validOption_whenStrictlyPositive
            DaisyUIVerticalForm.forOptionQtyD_default[Centimeter]
        given optCm: DF[Option[QtyD[Centimeter]]] = vertical_form_Option_QtyD_Centimeter

        // Custom Option[Mass] field with bidirectional sync to stove_params using generic method
        val optMassLinkedVal: DF[Option[Mass]] = DaisyUIVerticalForm.mkFromUnderlyingWithLinkedVar[Kilogram](
            underlying = vertical_form_Option_QtyD_Kilogram,
            linkedVar = maximumLoadVar
        )
        given optMassDF: DF[Option[Mass]] = optMassLinkedVal

        // PipeShape (expectedAirIntakePipeShape)
        given DF[PipeShape] = horizontal_form.horizontal_form_PipeShape.toVerticalForm

        // HeatOutputReduced full enum (pn_reduced)
        given DF[HeatOutputReduced] =
            given Defaultable[HeatOutputReduced] = Defaultable(HeatOutputReduced.NotDefined)
            DaisyUIVerticalForm.mk_AlwaysValid[HeatOutputReduced]: (va, _) =>
                FieldsetLabelAndContent(
                    label = I18N.en15544.terms.P_n_reduced.name,
                    SelectAndOptionsOnly.fromShow[HeatOutputReduced](
                        selectedVar           = va,
                        labelAsDisabledOption = None,
                        options = Seq(
                            HeatOutputReduced.NotDefined,
                            HeatOutputReduced.HalfOfNominal.makeWithoutValue,
                            HeatOutputReduced.FromTypeTest(0.0.kW)
                        )
                    )
                )

        // Area (glass_area)
        given DF[Area] = vertical_form_Area_cm2_m2

        // EmissionsAndEfficiencyValues_DTO (emissions_values)
        given DF[EmissionsAndEfficiencyValues_DTO] =
            // PolluantName select
            given DF[PolluantName] =
                import ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid
                given Defaultable[PolluantName] = Defaultable(PolluantName.CO)
                DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId[PolluantName](
                    options = PolluantName.values.toList
                )
            // Option[EmissionValueU]: map through Option[QtyD] with unit display
            given DF[Option[EmissionValueU]] =
                given ValidateVar[Option[QtyD[Milli * Gram / (Meter ^ 3)]]] = ValidateVar.valid
                val underlying: DF[Option[QtyD[Milli * Gram / (Meter ^ 3)]]] =
                    DaisyUIVerticalForm.forOptionQtyD_default[Milli * Gram / (Meter ^ 3)](using SUnits.sunit_MilligramPerNm3)
                underlying.bimap[Option[EmissionValueU]](_.map(summon[Conversion[QtyD[Milli * Gram / (Meter ^ 3)], EmissionValueU]].apply(_)))(_.map(_.unwrap))
            // TestEmissionValue_DTO
            given DF[TestEmissionValue_DTO] =
                DaisyUIVerticalForm.autoDerived[TestEmissionValue_DTO].autoOverwriteFieldNames
            // TestReport
            given DF[TestReport] =
                DaisyUIVerticalForm.autoDerived[TestReport].autoOverwriteFieldNames
            // List[TestReport]
            given DF[List[TestReport]] =
                DaisyUIVerticalForm.forList_WithEphemeralIds[TestReport]
            // EmissionValues_DTO
            given DF[EmissionValues_DTO] =
                DaisyUIVerticalForm.autoDerived[EmissionValues_DTO].autoOverwriteFieldNames
            DaisyUIVerticalForm.autoDerived[EmissionsAndEfficiencyValues_DTO].autoOverwriteFieldNames

        val autoDerivedForm = DaisyUIVerticalForm.autoDerived[Firebox.Door15aFirebox_Catalog].autoOverwriteFieldNames
        val d               = autoDerivedForm.defaultable_instance
        given ValidateVar[Firebox.Door15aFirebox_Catalog] = autoDerivedForm.validate_var

        DaisyUIVerticalForm
            .makeFor[Firebox.Door15aFirebox_Catalog](d): (v, fc) =>
                import com.raquo.laminar.api.L.*
                val modal = FireboxCatalogSelectComponent(
                    entriesSignal = door15aFireboxesSignal,
                    onSelect      = Observer(v.set)
                )
                div(
                    button(
                        cls     := "btn btn-secondary btn-sm mb-2",
                        I18N_UI.catalog.select_from_catalog,
                        onClick --> { _ => modal.open() }
                    ),
                    autoDerivedForm.render(v, fc),
                    modal.node
                )
            .withFieldName(I18N.firebox_names.door_15a_firebox)

    given given_Firebox: DF[Firebox] =
        // given DF[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
        //     horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal.toVerticalForm

        given DF[Firebox.Traditional]             = given_Firebox_Traditional
        given DF[Firebox.AFPMA_PRSE]              = given_AFPMA_PRSE
        given DF[Firebox.Ecolabeled]              = ecolabeled(default_version = Version.Version_1)
        given DF[Firebox.SingleTested]            = given_Firebox_SingleTested
        given DF[Firebox.Door15aFirebox_Catalog]  = given_Firebox_Door15aFirebox_Catalog

        DaisyUIVerticalForm
            .autoDerived[Firebox]
            .autoOverwriteFieldNames
            .wrappedInto(c =>
                FieldsetLegendWithContent  (
                    legendOpt   = Some(I18N.firebox.typ),
                    content     = c,
                    bgClass     = "bg-base-200",
                    borderClass = "border-base-300"
                )
            )

    given given_FacingType: Locale => DaisyUIVerticalForm[FacingType] =
        import ValidateVarCommonInstances.FacingType.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = FacingType.values.toList,
            updateFieldName = _ => Some(I18N.technical_specifications.facing_type)
        )

    given given_Country: Locale => DaisyUIVerticalForm[Country] =
        import ValidateVarCommonInstances.country.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = Country.values.toList,
            updateFieldName = _ => Some(I18N.address.country)
        )

    given given_BillableCountry: Locale => DaisyUIVerticalForm[BillableCountry] =
        import ValidateVarCommonInstances.billableCountry.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = BillableCountry.values.toList,
            updateFieldName = _ => Some(I18N.address.country)
        )

    given given_Customer: Locale => DaisyUIVerticalForm[Customer] =
        given DaisyUIVerticalForm[String] = string_emptyAsDefault_alwaysValid
        DaisyUIVerticalForm.autoDerived[Customer].autoOverwriteFieldNames

    given given_BillableCustomerType: Locale => DaisyUIVerticalForm[BillableCustomerType] =
        import ValidateVarCommonInstances.billableCustomerType.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = BillableCustomerType.values.toList,
            updateFieldName = _ => Some(I18N_PaymentsShared.billing_info.customer_type)
        )

    given given_ChimneyHeightAboveRidgeline: Locale => DaisyUIVerticalForm[ChimneyHeightAboveRidgeline] =
        import ValidateVarCommonInstances.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = ChimneyHeightAboveRidgeline.values.toList,
            updateFieldName = _ => Some(I18N_COS.chimney_location_on_roof.chimney_height_above_ridgeline.explain)
        )

    given given_HorizontalDistanceBetweenChimneyAndAdjacentBuildings
        : Locale => DaisyUIVerticalForm[HorizontalDistanceBetweenChimneyAndAdjacentBuildings] =
        import ValidateVarCommonInstances.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = HorizontalDistanceBetweenChimneyAndAdjacentBuildings.values.toList,
            updateFieldName = _ =>
                Some(I18N_COS.adjacent_buildings.horizontal_distance_between_chimney_and_adjacent_buildings.explain)
        )

    given given_HorizontalDistanceBetweenChimneyAndRidgelineBis
        : Locale => DaisyUIVerticalForm[HorizontalDistanceBetweenChimneyAndRidgelineBis] =
        import ValidateVarCommonInstances.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = HorizontalDistanceBetweenChimneyAndRidgelineBis.values.toList,
            updateFieldName = _ =>
                Some(I18N_COS.chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline_bis.explain)
        )

    given given_HorizontalDistanceBetweenChimneyAndRidgeline
        : Locale => DaisyUIVerticalForm[HorizontalDistanceBetweenChimneyAndRidgeline] =
        import ValidateVarCommonInstances.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = HorizontalDistanceBetweenChimneyAndRidgeline.values.toList,
            updateFieldName =
                _ => Some(I18N_COS.chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline.explain)
        )

    given given_InnerConstructionMaterial: Locale => DaisyUIVerticalForm[InnerConstructionMaterial] =
        import ValidateVarCommonInstances.innerConstructionMaterial.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = InnerConstructionMaterial.values.toList,
            updateFieldName = _ => Some(I18N.technical_specifications.inner_construction_material)
        )

    given given_HorizontalAngleBetweenChimneyAndAdjacentBuildings
        : Locale => DaisyUIVerticalForm[HorizontalAngleBetweenChimneyAndAdjacentBuildings] =
        import ValidateVarCommonInstances.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = HorizontalAngleBetweenChimneyAndAdjacentBuildings.values.toList,
            updateFieldName =
                _ => Some(I18N_COS.adjacent_buildings.horizontal_angle_between_chimney_and_adjacent_buildings.explain)
        )

    given given_OutsideAirIntakeAndChimneyLocations
        : Locale => DaisyUIVerticalForm[OutsideAirIntakeAndChimneyLocations] =
        import ValidateVarCommonInstances.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = OutsideAirIntakeAndChimneyLocations.values.toList,
            updateFieldName =
                _ => Some(I18N_COS.chimney_location_on_roof.outside_air_intake_and_chimney_locations.explain)
        )

    given given_ProjectDescr: Locale => DaisyUIVerticalForm[ProjectDescr] =
        given DaisyUIVerticalForm[String] = string_emptyAsDefault_alwaysValid
        DaisyUIVerticalForm.autoDerived[ProjectDescr].autoOverwriteFieldNames

    given given_Slope: Locale => DaisyUIVerticalForm[Slope] =
        import ValidateVarCommonInstances.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = Slope.values.toList,
            updateFieldName = _ => Some(I18N_COS.chimney_location_on_roof.slope.explain)
        )

    given given_SizingMethod: Locale => DaisyUIVerticalForm[SizingMethod] =
        import ValidateVarCommonInstances.sizingMethod.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = SizingMethod.values.toList,
            updateFieldName = _ => Some(I18N.technical_specifications.sizing_method)
        )

    given given_VerticalAngleBetweenChimneyAndAdjacentBuildings
        : Locale => DaisyUIVerticalForm[VerticalAngleBetweenChimneyAndAdjacentBuildings] =
        import ValidateVarCommonInstances.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = VerticalAngleBetweenChimneyAndAdjacentBuildings.values.toList,
            updateFieldName =
                _ => Some(I18N_COS.adjacent_buildings.vertical_angle_between_chimney_and_adjacent_buildings.explain)
        )

    // Zeta ζ

    def vertical_form_zeta(d: Defaultable[QtyD[1]]): DaisyUIVerticalForm[ζ] =
        // import defaultable.zeta
        // import validatevar.unitless.validOption_whenPositive
        // given DaisyUIVerticalForm[QtyD[1]] = DaisyUIVerticalForm.forQtyD[1]
        // DaisyUIVerticalForm.formConversionOpaque[ζ, QtyD[1]]
        // import defaultable.zeta
        DaisyUIVerticalForm
            .mkFromComponentOption_AlwaysValid[ζ] { case (voz, _) =>
                val vod = voz.bimap[Option[Double]](_.map(_.value))(_.map(_.unitless: ζ))
                DoubleFieldsetLabelAndInput(
                    Some(I18N.firebox.traditional.pressure_loss_coefficient_from_door),
                    vod
                )
            // LabelledNumberInputWithUnitAndTooltip(
            //     vod,
            //     labelEnd = Some("ζ")
            // )
            }(using Defaultable[ζ](d.default))

    given vertical_form_ζ: Locale => DaisyUIVerticalForm[ζ] =
        vertical_form_zeta(defaultable.zeta)

    // Zeta ζ as QtyD[1]

    def vertical_form_zeta_as_QtyD(d: Defaultable[QtyD[1]]): DaisyUIVerticalForm[QtyD[1]] =
        // import defaultable.zeta
        // import validatevar.unitless.validOption_whenPositive
        // given DaisyUIVerticalForm[QtyD[1]] = DaisyUIVerticalForm.forQtyD[1]
        // DaisyUIVerticalForm.formConversionOpaque[ζ, QtyD[1]]
        // import defaultable.zeta
        DaisyUIVerticalForm
            .mkFromComponentOption_AlwaysValid[QtyD[1]] { case (voq, _) =>
                val vod = voq.bimap[Option[Double]](_.map(_.value))(_.map(_.unitless))
                DoubleFieldsetLabelAndInput(
                    Some(I18N.firebox.traditional.pressure_loss_coefficient_from_door),
                    vod
                )
            // LabelledNumberInputWithUnitAndTooltip(
            //     vod,
            //     labelEnd = Some("ζ")
            // )
            }(using Defaultable[ζ](d.default))

end VerticalFormCommonInstances
