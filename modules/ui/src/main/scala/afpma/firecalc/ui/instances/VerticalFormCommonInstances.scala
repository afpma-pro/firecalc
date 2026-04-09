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

import afpma.laminar.form.daisyui.DaisyUIInputs.DoubleFieldsetLabelAndInput
import afpma.laminar.form.daisyui.DaisyUIInputs.FieldsetLabelAndContent
import afpma.laminar.form.daisyui.DaisyUIInputs.FieldsetLegendWithContent
import afpma.laminar.form.daisyui.DaisyUIInputs.SelectAndOptionsOnly
import afpma.laminar.form.Form
import afpma.laminar.form.derivation.FormDerivation
import afpma.laminar.form.i18n.FormI18nExtensions.autoOverwriteFieldNames
import afpma.laminar.form.derivation.FormDerivation.given
import afpma.laminar.form.coulomb.CoulombFormInstances
import afpma.laminar.form.Form.*
import afpma.laminar.form.VarSync
import afpma.laminar.form.FormRenderer
import afpma.laminar.form.ConditionalFor
import afpma.laminar.form.Defaultable
import afpma.laminar.form.ValidateVar
import afpma.firecalc.ui.components.FireboxCatalogSelectComponent
import afpma.firecalc.ui.components.SingleTestedCatalogSelectComponent
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.models.BillableCountry
import afpma.firecalc.ui.models.BillableCustomerType
import afpma.firecalc.ui.models.BillingLanguage
import afpma.firecalc.ui.models.door15aFireboxesSignal
import afpma.firecalc.ui.models.singleTestedFireboxesSignal
import afpma.firecalc.ui.models.stove_params_var
import afpma.firecalc.ui.models.firebox_var
import afpma.firecalc.ui.LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS

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

    type DF[A]    = Form[A]

    private given horizontal_form: HorizontalFormCommonInstances = HorizontalFormCommonInstances()

    // factory helper

    inline def autoDeriveAndOverwriteFieldNames[A](using inline m: Mirror.Of[A]): Form[A] =
        FormDerivation
            .derived[A](using m)
            .autoOverwriteFieldNames

    // BasicTypes

    val boolean_trueAsDefault_alwaysValid: Form[Boolean] =
        import defaultable.boolean.asTrue
        import ValidateVarCommonInstances.boolean.valid_Always
        FormDerivation.forBoolean

    val boolean_falseAsDefault_alwaysValid: Form[Boolean] =
        import defaultable.boolean.asFalse
        import ValidateVarCommonInstances.boolean.valid_Always
        FormDerivation.forBoolean

    val string_emptyAsDefault_alwaysValid: Form[String] =
        import defaultable.string.empty
        import ValidateVarCommonInstances.string.validOption_Always
        FormDerivation.forString

    val int_emptyAsDefault_alwaysValid: Form[Int] =
        import defaultable.int.empty
        import ValidateVarCommonInstances.int.validOption_Always
        FormDerivation.forInt

    val double_emptyAsDefault_alwaysValid: Form[Double] =
        import defaultable.double.empty
        import ValidateVarCommonInstances.double.validOption_Always
        FormDerivation.forDouble

    // QtyD

    given given_QtyD_Centimer: Defaultable[QtyD[Centimeter]] => Form[QtyD[Centimeter]] =
        import vv.centimeter.validOption_whenStrictlyPositive
        CoulombFormInstances.forQtyD[Centimeter](using SUnits.sunit_Centimeter)

    given given_QtyD_Hour: Defaultable[QtyD[Hour]] => Form[QtyD[Hour]] =
        import vv.hour.validOption_whenStrictlyPositive
        CoulombFormInstances.forQtyD[Hour]

    val vertical_form_Option_QtyD_Minute: Form[Option[QtyD[Minute]]] =
        import vv.minute.validOption_whenStrictlyPositive
        CoulombFormInstances.forOptionQtyD_default[Minute]()

    given given_QtyD_Minute: Defaultable[QtyD[Minute]] => Form[QtyD[Minute]] =
        import vv.minute.validOption_whenStrictlyPositive
        CoulombFormInstances.forQtyD[Minute]

    given given_QtyD_Kilogram: Defaultable[QtyD[Kilogram]] => Form[QtyD[Kilogram]] =
        import vv.kilogram.validOption_whenStrictlyPositive
        CoulombFormInstances.forQtyD[Kilogram]

    given given_QtyD_Kilowatt: Defaultable[QtyD[Kilo * Watt]] => Form[QtyD[Kilo * Watt]] =
        import vv.kilowatt.validOption_whenStrictlyPositive
        CoulombFormInstances.forQtyD[Kilo * Watt]

    given given_Option_QtyD_Kilowatt: Form[Option[QtyD[Kilo * Watt]]] =
        import vv.kilowatt.validOption_whenStrictlyPositive
        CoulombFormInstances.forOptionQtyD_default[Kilo * Watt]()

    given given_QtyD_Percent: Defaultable[QtyD[Percent]] => Form[QtyD[Percent]] =
        import vv.percent.validOption_whenPositive
        CoulombFormInstances.forQtyD[Percent]

    val vertical_form_Option_QtyD_Percent: Form[Option[QtyD[Percent]]] =
        import vv.percent.validOption_whenPositive
        CoulombFormInstances.forOptionQtyD_default[Percent]()

    val vertical_form_Option_QtyD_Kilogram: Form[Option[QtyD[Kilogram]]] =
        import vv.kilogram.validOption_whenStrictlyPositive
        CoulombFormInstances.forOptionQtyD_default[Kilogram]()

    given given_QtyD_Dimensionless: Defaultable[QtyD[1]] => Form[QtyD[1]] =
        import vv.unitless.validOption_whenPositive
        CoulombFormInstances.forQtyD[1]

    val vertical_form_Option_QtyD_Dimensionless: Form[Option[QtyD[1]]] =
        import vv.unitless.validOption_whenPositive
        CoulombFormInstances.forOptionQtyD_default[1]()

    given given_TCelsius: Defaultable[TCelsius] => Form[TCelsius] =
        import vv.temp.celsius.validOption_whenPositive
        CoulombFormInstances.forTempD[Celsius]

    val vertical_form_Option_TCelsius: Form[Option[TCelsius]] =
        import vv.temp.celsius.validOption_whenPositive
        CoulombFormInstances.forOptionTempD_default[Celsius]()

    val vertical_form_EmissionValueU: Form[EmissionValueU] =
        given ValidateVar[Option[QtyD[Milli * Gram / (Meter ^ 3)]]] = ValidateVar.valid
        given Defaultable[QtyD[Milli * Gram / (Meter ^ 3)]] = Defaultable(0.0.mg_per_Nm3)
        given Form[QtyD[Milli * Gram / (Meter ^ 3)]] =
            CoulombFormInstances.forQtyD[Milli * Gram / (Meter ^ 3)](using SUnits.sunit_MilligramPerNm3)
        Form.formConversionOpaque[EmissionValueU, QtyD[Milli * Gram / (Meter ^ 3)]]

    val given_HeatOutputReduced_NotDefined_Or_Tested
        : Form[HeatOutputReduced.NotDefined_Or_Tested] =
        val fromTypeTestDefault
            : HeatOutputReduced.NotDefined_Or_Tested = HeatOutputReduced.FromTypeTest(0.0.kW)
        given Defaultable[HeatOutputReduced.NotDefined_Or_Tested] = Defaultable(
            HeatOutputReduced.NotDefined
        )
        FormDerivation.mk_AlwaysValid[HeatOutputReduced.NotDefined_Or_Tested]: (va, _) =>
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
        given_dual_Length_cm.form()

    val vertical_form_Length_mm_cm: DF[Length] =
        import vv.meter.valid_whenStrictlyPositive
        import defaultable.qty_d.meter.zero
        given_dual_Length_mm_cm.form()

    // Business logic types

    // alias
    def I18N_COS(using Locale) = I18N.local_conditions.chimney_termination

    given given_Address: DF[Address] =
        given Form[String] = string_emptyAsDefault_alwaysValid
        FormDerivation.derived[Address].autoOverwriteFieldNames

    // Area : cm2
    given vertical_form_AreaInCm2: DF[AreaInCm2] =
        import defaultable.qty_d.area_in_cm2.zero
        import vv.area_in_cm2.valid_whenStrictlyPositive
        given_dual_Area_cm2_or_in2.form()

    // Area : cm2 + m2
    given vertical_form_Area_cm2_m2: DF[Area] =
        import defaultable.qty_d.area.zero
        import vv.area.valid_whenStrictlyPositive
        given_dual_Area_cm2_m2_or_in2.form()
            .withFieldName(I18N.terms.area)

    // Firebox

    given given_Firebox_Traditional: DF[Firebox.Traditional] =
        given Form[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
            horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal
        given Form[Length]                                                         = vertical_form_Length_cm
        given Form[Area]                                                           = vertical_form_Area_cm2_m2

        // zeta = 0.3 by default
        given Form[QtyD[1]] =
            vertical_form_zeta_as_QtyD(Defaultable(0.3.unitless))
                .withFieldName(I18N.firebox.traditional.pressure_loss_coefficient_from_door)
                .showFieldName

        FormDerivation.derived[Firebox.Traditional].autoOverwriteFieldNames

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
        given Form[Version] = FormDerivation
            .forEnumOrSumTypeLike_UsingShowAsId[Version](Version.values.toList)
        given Show[Version]                = Show.show:
            case Version.Version_1 => "Version 1"
            case Version.Version_2 => "Version 2"

    private given given_Ecolabeled_Version: Form[Version] =
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId[Version](
            options = List(
                Version.Version_1,
                Version.Version_2
            )
        )
        // FormDerivation.eitherAsSelectWithOptions[Version.Version_1, Version.Version_2]("Version")

    given Form[Either["Version 1", "Version 2"]] =
        Form.formConversionOpaque[
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

        given Form[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
            horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal

        given ConditionalFor[Version, PipeShape] = ConditionalFor:
            case Version.Version_1 => false
            case Version.Version_2 => true


        given DF[PipeShape] =
            horizontal_form.horizontal_form_PipeShape.showFieldName

        import defaultable.qty_d.meter.zero // for Defaultable[QtyD[Meter]] needed by PipeShape
        given DF[Option[PipeShape]] =
            FormDerivation.conditionalOn[Version, PipeShape](version_var, extraBinders = Seq(binder))

        FormDerivation.derived[Firebox.Ecolabeled].autoOverwriteFieldNames

    given given_AFPMA_PRSE: DF[Firebox.AFPMA_PRSE] =
        given DF[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
            horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal
        given DF[Int]                                                            = int_emptyAsDefault_alwaysValid
        // given DF[Boolean]                                                        = boolean_trueAsDefault_alwaysValid
        given DF[PipeShape]                                                      = horizontal_form.horizontal_form_PipeShape.hideFieldName
            .wrappedInto(c =>
                FieldsetLabelAndContent  (
                    label   = I18N.firebox.afpma_prse.outside_air_conduit_shape,
                    content = c
                )
            )

        given DF[Length]                     = vertical_form_Length_cm
        given DF[OutsideAirLocationInHeater] = FormDerivation.mk_AlwaysValid: (va, _) =>
            FieldsetLabelAndContent(
                label = I18N.firebox.afpma_prse.outside_air_location_in_heater,
                SelectAndOptionsOnly.single(
                    va.now(),
                    asDisabled = false
                )
            )

        FormDerivation.derived[Firebox.AFPMA_PRSE].autoOverwriteFieldNames

    given Locale => Form[BillingLanguage] =
        import ValidateVarCommonInstances.billingLanguage.valid_Always
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = BillingLanguage.values.toList,
            updateFieldName = _ => Some("-BILLING LANGUAGE-")
        )

    // TODO: handle specific national standard like ONORM 8303 ?
    given given_TestStandard: DF[TestStandard] =
        import ValidateVarCommonInstances.testStandard.valid_Always
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId(
            options         = List(TestStandard.EN_15250, TestStandard.EN_13229),
            updateFieldName = _ => Some(I18N.firebox.single_tested.test_standard)  // TODO: handle National standard (e.g. ÖNORM B 8303)
        )

    given given_TypeOfAppliance: DF[TypeOfAppliance] =
        given Defaultable[TypeOfAppliance] = Defaultable(TypeOfAppliance.WoodLogs)
        given ValidateVar[TypeOfAppliance] = ValidateVar.valid
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId(TypeOfAppliance.values.toList)

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
        given minuteDF: DF[Option[QtyD[Minute]]]         = vertical_form_Option_QtyD_Minute
        given optMass: DF[Option[Mass]]                  = vertical_form_Option_QtyD_Kilogram
        given dimDF: DF[Dimensionless]                   = given_QtyD_Dimensionless
        given optDim: DF[Option[Dimensionless]]          = vertical_form_Option_QtyD_Dimensionless
        given tempDF: DF[TCelsius]                       = given_TCelsius
        given optTemp: DF[Option[TCelsius]]              = vertical_form_Option_TCelsius
        given DF[HeatOutputReduced.NotDefined_Or_Tested] = given_HeatOutputReduced_NotDefined_Or_Tested
        // EmissionsAndEfficiencyValues_DTO (emissions_values)
        given DF[EmissionsAndEfficiencyValues_DTO] =
            given DF[PolluantName] =
                import ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid
                given Defaultable[PolluantName] = Defaultable(PolluantName.CO)
                FormDerivation.forEnumOrSumTypeLike_UsingShowAsId[PolluantName](
                    options = PolluantName.values.toList
                )
            given DF[Option[EmissionValueU]] =
                given ValidateVar[Option[QtyD[Milli * Gram / (Meter ^ 3)]]] = ValidateVar.valid
                val underlying: DF[Option[QtyD[Milli * Gram / (Meter ^ 3)]]] =
                    CoulombFormInstances.forOptionQtyD_default[Milli * Gram / (Meter ^ 3)]()(using SUnits.sunit_MilligramPerNm3)
                underlying.bimap[Option[EmissionValueU]](_.map(summon[Conversion[QtyD[Milli * Gram / (Meter ^ 3)], EmissionValueU]].apply(_)))(_.map(_.unwrap))
            given DF[TestEmissionValue_DTO] =
                FormDerivation.derived[TestEmissionValue_DTO].autoOverwriteFieldNames
            given DF[TestReport] =
                FormDerivation.derived[TestReport].autoOverwriteFieldNames
            given DF[List[TestReport]] =
                given (TestReport => Int) = System.identityHashCode(_)
                FormDerivation.forList[TestReport, Int]
            given DF[EmissionValues_DTO] =
                FormDerivation.derived[EmissionValues_DTO].autoOverwriteFieldNames
            FormDerivation.derived[EmissionsAndEfficiencyValues_DTO].autoOverwriteFieldNames

        val autoDerivedForm = FormDerivation.derived[Firebox.SingleTested].autoOverwriteFieldNames
        val d               = autoDerivedForm.defaultable
        given ValidateVar[Firebox.SingleTested] = autoDerivedForm.validateVar

        Form.makeFor[Firebox.SingleTested](d): (v, fc) =>
            (renderer: FormRenderer) ?=>
                import com.raquo.laminar.api.L.*
                val modal = SingleTestedCatalogSelectComponent(
                    entriesSignal = singleTestedFireboxesSignal,
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
        .withFieldName(I18N.firebox_names.single_tested)

    given given_Firebox_Door15aFirebox_Catalog: DF[Firebox.Door15aFirebox_Catalog] =
        import defaultable.qty_d.centimeter.zero

        // Defaultable for Percent (needed by given_QtyD_Percent context param)
        given Defaultable[QtyD[Percent]] = defaultable.qty_d.zeroWithUnit[Percent]

        // Standard field definitions
        given DF[String]                           = string_emptyAsDefault_alwaysValid
        given DF[Length]                           = vertical_form_Length_cm
        given DF[QtyD[Centimeter]]                 = given_QtyD_Centimer
        given percDF: DF[Percentage]               = given_QtyD_Percent
        given optPerc: DF[Option[Percentage]]      = vertical_form_Option_QtyD_Percent

        // Optional centimeter (sb_min, sb_max)
        val vertical_form_Option_QtyD_Centimeter: DF[Option[QtyD[Centimeter]]] =
            import vv.centimeter.validOption_whenStrictlyPositive
            CoulombFormInstances.forOptionQtyD_default[Centimeter]()
        given optCm: DF[Option[QtyD[Centimeter]]] = vertical_form_Option_QtyD_Centimeter

        // Plain Option[Mass] — mb_min and mb_max are independent supplier constraints, no stove_params link.
        // load_size_nominal sync is handled explicitly in the makeFor lambda below.
        given optMassDF: DF[Option[Mass]] = vertical_form_Option_QtyD_Kilogram

        // PipeShape (actualAirIntakePipeShape)
        given DF[PipeShape] = horizontal_form.horizontal_form_PipeShape
        // List[PipeShape] (expectedAirIntakePipeShapes)
        given DF[List[PipeShape]] =
            given (PipeShape => Int) = System.identityHashCode(_)
            FormDerivation.forList[PipeShape, Int]

        // HeatOutputReduced full enum (pn_reduced)
        given DF[HeatOutputReduced] =
            given Defaultable[HeatOutputReduced] = Defaultable(HeatOutputReduced.NotDefined)
            FormDerivation.mk_AlwaysValid[HeatOutputReduced]: (va, _) =>
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
                given Defaultable[PolluantName] = Defaultable(PolluantName.CO)
                given ValidateVar[PolluantName] = ValidateVar.valid
                FormDerivation.forEnumOrSumTypeLike_UsingShowAsId[PolluantName](
                    options = PolluantName.values.toList
                )
            // Option[EmissionValueU]: map through Option[QtyD] with unit display
            given DF[Option[EmissionValueU]] =
                given ValidateVar[Option[QtyD[Milli * Gram / (Meter ^ 3)]]] = ValidateVar.valid
                val underlying: DF[Option[QtyD[Milli * Gram / (Meter ^ 3)]]] =
                    CoulombFormInstances.forOptionQtyD_default[Milli * Gram / (Meter ^ 3)]()(using SUnits.sunit_MilligramPerNm3)
                underlying.bimap[Option[EmissionValueU]](_.map(summon[Conversion[QtyD[Milli * Gram / (Meter ^ 3)], EmissionValueU]].apply(_)))(_.map(_.unwrap))
            // TestEmissionValue_DTO
            given DF[TestEmissionValue_DTO] =
                FormDerivation.derived[TestEmissionValue_DTO].autoOverwriteFieldNames
            // TestReport
            given DF[TestReport] =
                FormDerivation.derived[TestReport].autoOverwriteFieldNames
            // List[TestReport]
            given DF[List[TestReport]] =
                given (TestReport => Int) = System.identityHashCode(_)
                FormDerivation.forList[TestReport, Int]
            // EmissionValues_DTO
            given DF[EmissionValues_DTO] =
                FormDerivation.derived[EmissionValues_DTO].autoOverwriteFieldNames
            FormDerivation.derived[EmissionsAndEfficiencyValues_DTO].autoOverwriteFieldNames

        val autoDerivedForm = FormDerivation.derived[Firebox.Door15aFirebox_Catalog].autoOverwriteFieldNames
        val d               = autoDerivedForm.defaultable
        given ValidateVar[Firebox.Door15aFirebox_Catalog] = autoDerivedForm.validateVar

        Form.makeFor[Firebox.Door15aFirebox_Catalog](d): (v, fc) =>
            (renderer: FormRenderer) ?=>
                import com.raquo.laminar.api.L.*
                // Bidirectional sync
                // mb_min and mb_max are independent — they must NOT participate in this sync.
                val loadSizeNominalVar: Var[Option[Mass]] =
                    v.zoomLazy(_.load_size_nominal)((fb, m) => fb.copy(load_size_nominal = m))
                val maximumLoadVar: Var[Option[Mass]] =
                    stove_params_var.zoomLazy(_.maximum_load)((sp, m) =>
                        if (m == sp.maximum_load) sp
                        else if (m.isDefined) sp.with_mB(m.get) else sp
                    )
                val syncLoadToMax = loadSizeNominalVar.signal
                    .distinct.changes.debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                    .withCurrentValueOf(maximumLoadVar)
                    .collect { case (fv, lv) if fv != lv => fv } --> maximumLoadVar.writer
                val syncMaxToLoad = maximumLoadVar.signal
                    .distinct.changes.debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                    .withCurrentValueOf(loadSizeNominalVar)
                    .collect { case (lv, fv) if lv != fv => lv } --> loadSizeNominalVar.writer

                val modal = FireboxCatalogSelectComponent(
                    entriesSignal = door15aFireboxesSignal,
                    onSelect = Observer { entry =>
                        val ml = stove_params_var.now().maximum_load
                        v.set(if ml.isDefined then entry.copy(load_size_nominal = ml) else entry)
                    }
                )
                div(
                    syncLoadToMax,
                    syncMaxToLoad,
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
        //     horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal

        given DF[Firebox.Traditional]             = given_Firebox_Traditional
        given DF[Firebox.AFPMA_PRSE]              = given_AFPMA_PRSE
        given DF[Firebox.Ecolabeled]              = ecolabeled(default_version = Version.Version_1)
        given DF[Firebox.SingleTested]            = given_Firebox_SingleTested
        given DF[Firebox.Door15aFirebox_Catalog]  = given_Firebox_Door15aFirebox_Catalog

        FormDerivation
            .derived[Firebox]
            .withOnSubtypeSwitch { (prev, next) =>
                next match
                    case _: Firebox.SingleTested => next
                    case _ => next.withDimensions(prev.firebox_depth, prev.firebox_width, prev.firebox_height)
            }
            .autoOverwriteFieldNames
            .wrappedInto(c =>
                FieldsetLegendWithContent  (
                    legendOpt   = Some(I18N.firebox.typ),
                    content     = c,
                    bgClass     = "bg-base-200",
                    borderClass = "border-base-300"
                )
            )

    given given_FacingType: Locale => Form[FacingType] =
        import ValidateVarCommonInstances.FacingType.valid_Always
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = FacingType.values.toList,
            updateFieldName = _ => Some(I18N.technical_specifications.facing_type)
        )

    given given_Country: Locale => Form[Country] =
        import ValidateVarCommonInstances.country.valid_Always
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = Country.values.toList,
            updateFieldName = _ => Some(I18N.address.country)
        )

    given given_BillableCountry: Locale => Form[BillableCountry] =
        import ValidateVarCommonInstances.billableCountry.valid_Always
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = BillableCountry.values.toList,
            updateFieldName = _ => Some(I18N.address.country)
        )

    given given_Customer: Locale => Form[Customer] =
        given Form[String] = string_emptyAsDefault_alwaysValid
        FormDerivation.derived[Customer].autoOverwriteFieldNames

    given given_BillableCustomerType: Locale => Form[BillableCustomerType] =
        import ValidateVarCommonInstances.billableCustomerType.valid_Always
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = BillableCustomerType.values.toList,
            updateFieldName = _ => Some(I18N_PaymentsShared.billing_info.customer_type)
        )

    given given_ChimneyHeightAboveRidgeline: Locale => Form[ChimneyHeightAboveRidgeline] =
        import ValidateVarCommonInstances.valid_always.given
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = ChimneyHeightAboveRidgeline.values.toList,
            updateFieldName = _ => Some(I18N_COS.chimney_location_on_roof.chimney_height_above_ridgeline.explain)
        )

    given given_HorizontalDistanceBetweenChimneyAndAdjacentBuildings
       : Locale => Form[HorizontalDistanceBetweenChimneyAndAdjacentBuildings] =
        import ValidateVarCommonInstances.valid_always.given
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = HorizontalDistanceBetweenChimneyAndAdjacentBuildings.values.toList,
            updateFieldName = _ =>
                Some(I18N_COS.adjacent_buildings.horizontal_distance_between_chimney_and_adjacent_buildings.explain)
        )

    given given_HorizontalDistanceBetweenChimneyAndRidgelineBis
       : Locale => Form[HorizontalDistanceBetweenChimneyAndRidgelineBis] =
        import ValidateVarCommonInstances.valid_always.given
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = HorizontalDistanceBetweenChimneyAndRidgelineBis.values.toList,
            updateFieldName = _ =>
                Some(I18N_COS.chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline_bis.explain)
        )

    given given_HorizontalDistanceBetweenChimneyAndRidgeline
       : Locale => Form[HorizontalDistanceBetweenChimneyAndRidgeline] =
        import ValidateVarCommonInstances.valid_always.given
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = HorizontalDistanceBetweenChimneyAndRidgeline.values.toList,
            updateFieldName =
                _ => Some(I18N_COS.chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline.explain)
        )

    given given_InnerConstructionMaterial: Locale => Form[InnerConstructionMaterial] =
        import ValidateVarCommonInstances.innerConstructionMaterial.valid_Always
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = InnerConstructionMaterial.values.toList,
            updateFieldName = _ => Some(I18N.technical_specifications.inner_construction_material)
        )

    given given_HorizontalAngleBetweenChimneyAndAdjacentBuildings
       : Locale => Form[HorizontalAngleBetweenChimneyAndAdjacentBuildings] =
        import ValidateVarCommonInstances.valid_always.given
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = HorizontalAngleBetweenChimneyAndAdjacentBuildings.values.toList,
            updateFieldName =
                _ => Some(I18N_COS.adjacent_buildings.horizontal_angle_between_chimney_and_adjacent_buildings.explain)
        )

    given given_OutsideAirIntakeAndChimneyLocations
       : Locale => Form[OutsideAirIntakeAndChimneyLocations] =
        import ValidateVarCommonInstances.valid_always.given
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = OutsideAirIntakeAndChimneyLocations.values.toList,
            updateFieldName =
                _ => Some(I18N_COS.chimney_location_on_roof.outside_air_intake_and_chimney_locations.explain)
        )

    given given_ProjectDescr: Locale => Form[ProjectDescr] =
        given Form[String] = string_emptyAsDefault_alwaysValid
        FormDerivation.derived[ProjectDescr].autoOverwriteFieldNames

    given given_Slope: Locale => Form[Slope] =
        import ValidateVarCommonInstances.valid_always.given
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = Slope.values.toList,
            updateFieldName = _ => Some(I18N_COS.chimney_location_on_roof.slope.explain)
        )

    given given_SizingMethod: Locale => Form[SizingMethod] =
        import ValidateVarCommonInstances.sizingMethod.valid_Always
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = SizingMethod.values.toList,
            updateFieldName = _ => Some(I18N.technical_specifications.sizing_method)
        )

    given given_VerticalAngleBetweenChimneyAndAdjacentBuildings
       : Locale => Form[VerticalAngleBetweenChimneyAndAdjacentBuildings] =
        import ValidateVarCommonInstances.valid_always.given
        FormDerivation.forEnumOrSumTypeLike_UsingShowAsId        (
            options         = VerticalAngleBetweenChimneyAndAdjacentBuildings.values.toList,
            updateFieldName =
                _ => Some(I18N_COS.adjacent_buildings.vertical_angle_between_chimney_and_adjacent_buildings.explain)
        )

    // Zeta ζ

    def vertical_form_zeta(d: Defaultable[QtyD[1]]): Form[ζ] =
        given Defaultable[ζ] = Defaultable[ζ](d.default)
        FormDerivation.mk_AlwaysValid[ζ]: (variable, formConfig) =>
            (_: FormRenderer) ?=>
                val (optionVar, binders) = VarSync.makeOptionVarFromVar_BiDirAsync[ζ](variable)
                val vod = optionVar.bimap[Option[Double]](_.map(_.value))(_.map(_.unitless: ζ))
                DoubleFieldsetLabelAndInput(
                    Some(I18N.firebox.traditional.pressure_loss_coefficient_from_door),
                    vod
                ).amend(binders*)

    given vertical_form_ζ: Locale => Form[ζ] =
        vertical_form_zeta(defaultable.zeta)

    // Zeta ζ as QtyD[1]

    def vertical_form_zeta_as_QtyD(d: Defaultable[QtyD[1]]): Form[QtyD[1]] =
        given Defaultable[QtyD[1]] = Defaultable[QtyD[1]](d.default)
        FormDerivation.mk_AlwaysValid[QtyD[1]]: (variable, formConfig) =>
            (_: FormRenderer) ?=>
                val (optionVar, binders) = VarSync.makeOptionVarFromVar_BiDirAsync[QtyD[1]](variable)
                val vod = optionVar.bimap[Option[Double]](_.map(_.value))(_.map(_.unitless))
                DoubleFieldsetLabelAndInput(
                    Some(I18N.firebox.traditional.pressure_loss_coefficient_from_door),
                    vod
                ).amend(binders*)

end VerticalFormCommonInstances
