/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import scala.deriving.Mirror

import cats.Show

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.ζ

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.daisyui.DaisyUIInputs.DoubleFieldsetLabelAndInput
import afpma.firecalc.ui.daisyui.DaisyUIInputs.FieldsetLabelAndContent
import afpma.firecalc.ui.daisyui.DaisyUIInputs.FieldsetLegendWithContent
import afpma.firecalc.ui.daisyui.DaisyUIInputs.SelectAndOptionsOnly
import afpma.firecalc.ui.daisyui.DaisyUIVerticalForm
import afpma.firecalc.ui.formgen.ConditionalFor
import afpma.firecalc.ui.formgen.Defaultable
import afpma.firecalc.ui.formgen.ValidateVar
import afpma.firecalc.ui.models.firebox_var

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*

import com.raquo.airstream.state.Var
import io.taig.babel.Locale
import afpma.firecalc.ui.models.BillableCountry
import afpma.firecalc.ui.models.BillingLanguage
import afpma.firecalc.ui.models.BillableCustomerType
import afpma.firecalc.payments.shared.i18n.implicits.I18N_PaymentsShared

object vertical_form:
    
    import dual.given
    import SUnits.given
    // import defaultable.given
    import hastranslations.given

    type DF[A] = DaisyUIVerticalForm[A]
    type LDF[A] = Locale ?=> DaisyUIVerticalForm[A]
    type CtxDF[A] = DisplayUnits ?=> Locale ?=> DaisyUIVerticalForm[A]

    // factory helper
    
    inline def autoDeriveAndOverwriteFieldNames[A](using inline m: Mirror.Of[A], l: Locale): DaisyUIVerticalForm[A] = 
        DaisyUIVerticalForm
            .autoDerived[A](using m)
            .autoOverwriteFieldNames

    // BasicTypes

    val boolean_trueAsDefault_alwaysValid: DaisyUIVerticalForm[Boolean] = 
        import defaultable.boolean.asTrue
        import validatevar.boolean.valid_Always
        DaisyUIVerticalForm.forBoolean

    val boolean_falseAsDefault_alwaysValid: DaisyUIVerticalForm[Boolean] = 
        import defaultable.boolean.asFalse
        import validatevar.boolean.valid_Always
        DaisyUIVerticalForm.forBoolean

    val string_emptyAsDefault_alwaysValid: DaisyUIVerticalForm[String] = 
        import defaultable.string.empty
        import validatevar.string.validOption_Always
        DaisyUIVerticalForm.forString

    val int_emptyAsDefault_alwaysValid: DaisyUIVerticalForm[Int] = 
        import defaultable.int.empty
        import validatevar.int.validOption_Always
        DaisyUIVerticalForm.forInt

    val double_emptyAsDefault_alwaysValid: DaisyUIVerticalForm[Double] = 
        import defaultable.double.empty
        import validatevar.double.validOption_Always
        DaisyUIVerticalForm.forDouble

    // QtyD

    given given_QtyD_Hour: Defaultable[QtyD[Hour]] => DaisyUIVerticalForm[QtyD[Hour]] = 
        import validatevar.hour.validOption_whenPositive
        DaisyUIVerticalForm.forQtyD[Hour]


    given given_QtyD_Kilogram: Defaultable[QtyD[Kilogram]] => DaisyUIVerticalForm[QtyD[Kilogram]] = 
        import validatevar.kilogram.validOption_whenPositive
        DaisyUIVerticalForm.forQtyD[Kilogram]

    given given_QtyD_Kilowatt: Defaultable[QtyD[Kilo * Watt]] => DaisyUIVerticalForm[QtyD[Kilo * Watt]] = 
        import validatevar.kilowatt.validOption_whenPositive
        DaisyUIVerticalForm.forQtyD[Kilo * Watt]

    given given_Option_QtyD_Kilowatt: DaisyUIVerticalForm[Option[QtyD[Kilo * Watt]]] = 
        import validatevar.kilowatt.validOption_whenPositive
        DaisyUIVerticalForm.forOptionQtyD_default[Kilo * Watt]

    given given_QtyD_Percent: Defaultable[QtyD[Percent]] => DaisyUIVerticalForm[QtyD[Percent]] = 
        import validatevar.percent.validOption_whenPositive
        DaisyUIVerticalForm.forQtyD[Percent]

    val vertical_form_Length_cm: CtxDF[Length] = 
        import validatevar.meter.valid_whenPositive
        import defaultable.qty_d.meter.zero
        given_dual_Length_cm.form_DaisyUIVerticalForm

    val vertical_form_Length_mm_cm: CtxDF[Length] = 
        import validatevar.meter.valid_whenPositive
        import defaultable.qty_d.meter.zero
        given_dual_Length_mm_cm.form_DaisyUIVerticalForm

    // Business logic types

    // alias
    def I18N_COS(using Locale) = I18N.local_conditions.chimney_termination

    given given_Address: LDF[Address] = 
        given DaisyUIVerticalForm[String] = string_emptyAsDefault_alwaysValid
        DaisyUIVerticalForm.autoDerived[Address]
        .autoOverwriteFieldNames

    // Area : cm2

    given vertical_form_AreaInCm2: CtxDF[AreaInCm2] = 
        import defaultable.qty_d.area_in_cm2.zero
        import validatevar.area_in_cm2.valid_whenPositive
        given_dual_Area_cm2_or_in2.form_DaisyUIVerticalForm

    // Area : cm2 + m2

    given vertical_form_Area_cm2_m2: CtxDF[Area] = 
        import defaultable.qty_d.area.zero
        import validatevar.area.valid_whenPositive
        given_dual_Area_cm2_m2_or_in2.form_DaisyUIVerticalForm
        .withFieldName(I18N.terms.area)

    // Firebox

    given given_Firebox_Traditional: CtxDF[Firebox.Traditional] = 
        given DaisyUIVerticalForm[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
            horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal.toVerticalForm
        given DaisyUIVerticalForm[Length] = vertical_form_Length_cm
        given DaisyUIVerticalForm[Area] = vertical_form_Area_cm2_m2
        
        // zeta = 0.3 by default
        given DaisyUIVerticalForm[QtyD[1]] = 
            vertical_form_zeta_as_QtyD(Defaultable(0.3.unitless))
            .withFieldName(I18N.firebox.traditional.pressure_loss_coefficient_from_door)
            .showFieldName
        
        DaisyUIVerticalForm.autoDerived[Firebox.Traditional]
        .autoOverwriteFieldNames

    private enum Version:
        case Version_1, Version_2
    private object Version:
        
        type Version_1 = Version_1.type
        type Version_2 = Version_2.type
        
        given c1: Conversion[
            Either[Version_1, Version_2],
            Either["Version 1", "Version 2"]
        ] = {
            case Left(_) => Left("Version 1")
            case Right(_) => Right("Version 2")
        }
        given c2: Conversion[
            Either["Version 1", "Version 2"],
            Either[Version_1, Version_2]
        ] = {
            case Left(_) => Left(Version.Version_1)
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
            Version,
        ] = {
            case Left(_) => Version_1
            case Right(_) => Version_2
        }
        
        given ValidateVar[Version] = validatevar.valid_always.given_ValidateVar_AlwaysValid[Version]
        given DaisyUIVerticalForm[Version] = DaisyUIVerticalForm
            .forEnumOrSumTypeLike_UsingShowAsId[Version](Version.values.toList)
        given Show[Version] = Show.show:
            case Version.Version_1 => "Version 1"
            case Version.Version_2 => "Version 2"

    private given given_Ecolabeled_Version: DaisyUIVerticalForm[Version] = 
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId[Version](
            options = List(
                Version.Version_1,
                Version.Version_2,
            )
        )
        // DaisyUIVerticalForm.eitherAsSelectWithOptions[Version.Version_1, Version.Version_2]("Version")

    given DaisyUIVerticalForm[Either["Version 1", "Version 2"]] = 
        DaisyUIVerticalForm.formConversionOpaque[
            Either["Version 1", "Version 2"],
            Version
        ]

    private def ecolabeled(default_version: Version): CtxDF[Firebox.EcoLabeled] = 
        import com.raquo.laminar.api.enrichSource
        val version_var: Var[Version] = Var(default_version)
        
        // update and bind version_var from app state
        val binder = firebox_var.signal
            .mapLazy {
                case eco: Firebox.EcoLabeled => eco.version match
                    case Left("Version 1") => Some(Version.Version_1)
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
            horizontal_form.horizontal_form_PipeShape.toVerticalForm
            .showFieldName

        given DF[Option[PipeShape]] = 
            DaisyUIVerticalForm.conditionalOn[Version, PipeShape](version_var, extraBinders = Seq(binder))

        DaisyUIVerticalForm.autoDerived[Firebox.EcoLabeled].autoOverwriteFieldNames
    
    given given_AFPMA_PRSE: CtxDF[Firebox.AFPMA_PRSE] = 
        given DF[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
            horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal.toVerticalForm
        given DF[Int] = int_emptyAsDefault_alwaysValid
        given DF[PipeShape] = horizontal_form.horizontal_form_PipeShape
            .toVerticalForm
            .hideFieldName
            .wrappedInto(c => FieldsetLabelAndContent(
                label = I18N.firebox.afpma_prse.outside_air_conduit_shape,
                content = c
            ))

        given DF[Length] = vertical_form_Length_cm
        given DF[Firebox.AFPMA_PRSE.OutsideAirLocationInHeater] = DaisyUIVerticalForm.mk_AlwaysValid: (va, _) =>
            FieldsetLabelAndContent(
                label = I18N.firebox.afpma_prse.outside_air_location_in_heater,
                SelectAndOptionsOnly.single(
                    va.now(), 
                    asDisabled = false,
                )
            )

        DaisyUIVerticalForm.autoDerived[Firebox.AFPMA_PRSE].autoOverwriteFieldNames

    given Locale => DaisyUIVerticalForm[BillingLanguage] =
        import validatevar.billingLanguage.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = BillingLanguage.values.toList,
            updateFieldName = _ => Some("-BILLING LANGUAGE-")
        )

    given given_Firebox: CtxDF[Firebox] = 
        // given DF[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] =
        //     horizontal_form.given_HeatOutputReduced_NotDefined_or_HalfOfNominal.toVerticalForm
        
        given DF[Firebox.Traditional] = given_Firebox_Traditional
        given DF[Firebox.AFPMA_PRSE]  = given_AFPMA_PRSE
        given DF[Firebox.EcoLabeled]  = ecolabeled(default_version = Version.Version_1)
        
        DaisyUIVerticalForm.autoDerived[Firebox]
        .autoOverwriteFieldNames
        .wrappedInto(c =>
            FieldsetLegendWithContent(
                legendOpt = Some(I18N.firebox.typ),
                content = c
            )
        )

    given given_FacingType: Locale => DaisyUIVerticalForm[FacingType] =
        import validatevar.FacingType.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = FacingType.values.toList,
            updateFieldName = _ => Some(I18N.technical_specifications.facing_type)
        )

    given given_Country: Locale => DaisyUIVerticalForm[Country] =
        import validatevar.country.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = Country.values.toList,
            updateFieldName = _ => Some(I18N.address.country),
        )

    given given_BillableCountry: Locale => DaisyUIVerticalForm[BillableCountry] =
        import validatevar.billableCountry.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = BillableCountry.values.toList,
            updateFieldName = _ => Some(I18N.address.country),
        )

    given given_Customer: Locale => DaisyUIVerticalForm[Customer] =
        given DaisyUIVerticalForm[String] = string_emptyAsDefault_alwaysValid
        DaisyUIVerticalForm.autoDerived[Customer]
        .autoOverwriteFieldNames

    given given_BillableCustomerType: Locale => DaisyUIVerticalForm[BillableCustomerType] =
        import validatevar.billableCustomerType.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = BillableCustomerType.values.toList,
            updateFieldName = _ => Some(I18N_PaymentsShared.billing_info.customer_type)
        )

    given given_ChimneyHeightAboveRidgeline: Locale => DaisyUIVerticalForm[ChimneyHeightAboveRidgeline] = 
        import validatevar.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = ChimneyHeightAboveRidgeline.values.toList,
            updateFieldName = _ => Some(I18N_COS.chimney_location_on_roof.chimney_height_above_ridgeline.explain)
        )

    given given_HorizontalDistanceBetweenChimneyAndAdjacentBuildings: Locale => DaisyUIVerticalForm[HorizontalDistanceBetweenChimneyAndAdjacentBuildings] = 
        import validatevar.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = HorizontalDistanceBetweenChimneyAndAdjacentBuildings.values.toList,
            updateFieldName = _ => Some(I18N_COS.adjacent_buildings.horizontal_distance_between_chimney_and_adjacent_buildings.explain)
        )

    given given_HorizontalDistanceBetweenChimneyAndRidgelineBis: Locale => DaisyUIVerticalForm[HorizontalDistanceBetweenChimneyAndRidgelineBis] = 
        import validatevar.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = HorizontalDistanceBetweenChimneyAndRidgelineBis.values.toList,
            updateFieldName = _ => Some(I18N_COS.chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline_bis.explain)
        )

    given given_HorizontalDistanceBetweenChimneyAndRidgeline: Locale => DaisyUIVerticalForm[HorizontalDistanceBetweenChimneyAndRidgeline] = 
        import validatevar.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = HorizontalDistanceBetweenChimneyAndRidgeline.values.toList,
            updateFieldName = _ => Some(I18N_COS.chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline.explain)
        )

    given given_InnerConstructionMaterial: Locale => DaisyUIVerticalForm[InnerConstructionMaterial] =
        import validatevar.innerConstructionMaterial.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = InnerConstructionMaterial.values.toList,
            updateFieldName = _ => Some(I18N.technical_specifications.inner_construction_material)
        )

    given given_HorizontalAngleBetweenChimneyAndAdjacentBuildings: Locale => DaisyUIVerticalForm[HorizontalAngleBetweenChimneyAndAdjacentBuildings] = 
        import validatevar.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = HorizontalAngleBetweenChimneyAndAdjacentBuildings.values.toList,
            updateFieldName = _ => Some(I18N_COS.adjacent_buildings.horizontal_angle_between_chimney_and_adjacent_buildings.explain)
        )

    given given_OutsideAirIntakeAndChimneyLocations: Locale => DaisyUIVerticalForm[OutsideAirIntakeAndChimneyLocations] = 
        import validatevar.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = OutsideAirIntakeAndChimneyLocations.values.toList,
            updateFieldName = _ => Some(I18N_COS.chimney_location_on_roof.outside_air_intake_and_chimney_locations.explain)
        )

    given given_ProjectDescr: Locale => DaisyUIVerticalForm[ProjectDescr] = 
        given DaisyUIVerticalForm[String] = string_emptyAsDefault_alwaysValid
        DaisyUIVerticalForm.autoDerived[ProjectDescr]
        .autoOverwriteFieldNames

    given given_Slope: Locale => DaisyUIVerticalForm[Slope] = 
        import validatevar.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = Slope.values.toList,
            updateFieldName = _ => Some(I18N_COS.chimney_location_on_roof.slope.explain)
        )

    given given_SizingMethod: Locale => DaisyUIVerticalForm[SizingMethod] =
        import validatevar.sizingMethod.valid_Always
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = SizingMethod.values.toList,
            updateFieldName = _ => Some(I18N.technical_specifications.sizing_method)
        )

    given given_VerticalAngleBetweenChimneyAndAdjacentBuildings: Locale => DaisyUIVerticalForm[VerticalAngleBetweenChimneyAndAdjacentBuildings] = 
        import validatevar.valid_always.given
        DaisyUIVerticalForm.forEnumOrSumTypeLike_UsingShowAsId(
            options = VerticalAngleBetweenChimneyAndAdjacentBuildings.values.toList,
            updateFieldName = _ => Some(I18N_COS.adjacent_buildings.vertical_angle_between_chimney_and_adjacent_buildings.explain)
        )

    // Zeta ζ

    def vertical_form_zeta(d: Defaultable[QtyD[1]]): Locale ?=> DaisyUIVerticalForm[ζ] = 
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
                vod,
            )
            // LabelledNumberInputWithUnitAndTooltip(
            //     vod,
            //     labelEnd = Some("ζ")
            // )
        }(using Defaultable[ζ](d.default))
    
    given vertical_form_ζ: Locale => DaisyUIVerticalForm[ζ] =
        vertical_form_zeta(defaultable.zeta)

    // Zeta ζ as QtyD[1]

    def vertical_form_zeta_as_QtyD(d: Defaultable[QtyD[1]]): Locale ?=> DaisyUIVerticalForm[QtyD[1]] = 
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
                vod,
            )
            // LabelledNumberInputWithUnitAndTooltip(
            //     vod,
            //     labelEnd = Some("ζ")
            // )
        }(using Defaultable[ζ](d.default))



end vertical_form