/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.given
import afpma.firecalc.i18n.utils.*
import afpma.firecalc.i18n.utils.macros.*

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.instances.*

import cats.syntax.all.*

import com.raquo.laminar.api.L.*

import afpma.laminar.form.*
import afpma.laminar.form.Form
import afpma.laminar.form.Form.*
import afpma.laminar.form.daisyui.DaisyUIVertical
import afpma.laminar.form.derivation.FormDerivation
import afpma.laminar.form.i18n.FormI18nExtensions.autoOverwriteFieldNames
import io.circe.*
import io.circe.generic.semiauto.*
import io.taig.babel.Locale
import magnolia1.Transl

inline def I(f: I18nData => String) = tPath[I18nData](f)

object LocalConditionsUI:

    import circe.given

    def make()(using Locale, DisplayUnits): LocalConditionsUI = LocalConditionsUI()

    // LocalConditions_UIClone so we can have three different forms and customize layout

    @Transl(I(_.headers.local_conditions))
    case class LocalConditions_UIClone(
        @Transl(I(_.local_conditions.altitude))
        altitude      : QtyD[Meter],
        @Transl(I(_.local_conditions.coastal_region))
        coastal_region: Boolean
    )

    object LocalConditions_UIClone:
        def fromOriginal(lc: LocalConditions)                                      : LocalConditions_UIClone =
            LocalConditions_UIClone      (
                altitude       = lc.altitude,
                coastal_region = lc.coastal_region
            )
        def updateOriginal(lcOrig: LocalConditions, clone: LocalConditions_UIClone): LocalConditions         =
            lcOrig.copy      (
                altitude       = clone.altitude,
                coastal_region = clone.coastal_region
            )

    given Defaultable[LocalConditions_UIClone]:
        def default =
            val lc_default = defaultable_LocalConditions.default
            LocalConditions_UIClone      (
                altitude       = lc_default.altitude,
                coastal_region = lc_default.coastal_region
            )

    given Decoder[LocalConditions_UIClone] = deriveDecoder[LocalConditions_UIClone]
    given Encoder[LocalConditions_UIClone] = deriveEncoder[LocalConditions_UIClone]

    given defaultable_LocalConditions: Defaultable[LocalConditions]:
        def default = LocalConditions.default

case class LocalConditionsUI()(using Locale, DisplayUnits) extends Component:

    import LocalConditionsUI.*
    import defaultable.chimney_termination.given
    
    private given vertical_form: VerticalFormCommonInstances = new VerticalFormCommonInstances()
    import vertical_form.given

    private val vv: ValidateVarCommonInstances = ValidateVarCommonInstances()

    given dual: DualCommonInstances = new DualCommonInstances()
    given FormRenderer = DaisyUIVertical

    private val FC_I18N_COS = I18N.local_conditions.chimney_termination

    // ChimneyLocationOnRoof

    given c_wr1: ConditionalFor[ChimneyLocationOnRoof, HorizontalDistanceBetweenChimneyAndRidgeline] = ConditionalFor(
        _.chimney_height_above_ridgeline == ChimneyHeightAboveRidgeline.LessThan40cm
    )

    given c_wr2: ConditionalFor[ChimneyLocationOnRoof, Slope] = ConditionalFor(wr =>
        c_wr1.check(wr) &&
            wr.horizontal_distance_between_chimney_and_ridgeline == HorizontalDistanceBetweenChimneyAndRidgeline.LessThan2m30.some
    )

    given c_wr3: ConditionalFor[ChimneyLocationOnRoof, OutsideAirIntakeAndChimneyLocations] = ConditionalFor(wr =>
        c_wr2.check(wr) &&
            wr.slope == Slope.From25DegTo40Deg.some
    )

    given c_wr4: ConditionalFor[ChimneyLocationOnRoof, HorizontalDistanceBetweenChimneyAndRidgelineBis] =
        ConditionalFor(wr =>
            c_wr3.check(wr) &&
                wr.outside_air_intake_and_chimney_locations == OutsideAirIntakeAndChimneyLocations.OnDifferentSidesOfRidgeline.some
        )

    given form_HorizontalDistanceBetweenChimneyAndRidgeline
        : Form[Option[HorizontalDistanceBetweenChimneyAndRidgeline]] =
        FormDerivation
            .conditionalOn[ChimneyLocationOnRoof, HorizontalDistanceBetweenChimneyAndRidgeline](
                chimney_location_on_roof_var
            )
            .withFieldName(
                FC_I18N_COS.chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline.explain
            )

    given form_Slope: Form[Option[Slope]] =
        given Form[Slope] = vertical_form.given_Slope
        FormDerivation
            .conditionalOn[ChimneyLocationOnRoof, Slope](chimney_location_on_roof_var)
            .withFieldName(FC_I18N_COS.chimney_location_on_roof.slope.explain)

    given form_OutsideAirIntakeAndChimneyLocations: Form[Option[OutsideAirIntakeAndChimneyLocations]] =
        FormDerivation
            .conditionalOn[ChimneyLocationOnRoof, OutsideAirIntakeAndChimneyLocations](chimney_location_on_roof_var)
            .withFieldName(FC_I18N_COS.chimney_location_on_roof.outside_air_intake_and_chimney_locations.explain)

    given form_HorizontalDistanceBetweenChimneyAndRidgelineBis
        : Form[Option[HorizontalDistanceBetweenChimneyAndRidgelineBis]] =
        FormDerivation
            .conditionalOn[ChimneyLocationOnRoof, HorizontalDistanceBetweenChimneyAndRidgelineBis](
                chimney_location_on_roof_var
            )
            .withFieldName(
                FC_I18N_COS.chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline_bis.explain
            )

    given form_ChimneyLocationOnRoof: Form[ChimneyLocationOnRoof] =
        FormDerivation
            .derived[ChimneyLocationOnRoof]
            .withFieldName(
                I18N.local_conditions.chimney_termination.chimney_location_on_roof.explain
            )

    // AdjacentBuildings

    given c_wao1
        : ConditionalFor[AdjacentBuildings, HorizontalAngleBetweenChimneyAndAdjacentBuildings] = ConditionalFor(wao =>
        wao.horizontal_distance_between_chimney_and_adjacent_buildings == HorizontalDistanceBetweenChimneyAndAdjacentBuildings.LessThan15m &&
            wao.vertical_angle_between_chimney_and_adjacent_buildings != VerticalAngleBetweenChimneyAndAdjacentBuildings.LessThan10DegOverHorizon.some
    )

    given c_wao2
        : ConditionalFor[AdjacentBuildings, VerticalAngleBetweenChimneyAndAdjacentBuildings] = ConditionalFor(wao =>
        (wao.horizontal_distance_between_chimney_and_adjacent_buildings == HorizontalDistanceBetweenChimneyAndAdjacentBuildings.LessThan15m &&
            wao.horizontal_angle_between_chimney_and_adjacent_buildings == None)
            ||
                (wao.horizontal_distance_between_chimney_and_adjacent_buildings == HorizontalDistanceBetweenChimneyAndAdjacentBuildings.LessThan15m &&
                    wao.horizontal_angle_between_chimney_and_adjacent_buildings == HorizontalAngleBetweenChimneyAndAdjacentBuildings.MoreThan30Deg.some)
    )

    given form_HorizontalAngleBetweenChimneyAndAdjacentBuildings
        : Form[Option[HorizontalAngleBetweenChimneyAndAdjacentBuildings]] =
        FormDerivation
            .conditionalOn[AdjacentBuildings, HorizontalAngleBetweenChimneyAndAdjacentBuildings](adjacent_buildings_var)
            .withFieldName(
                FC_I18N_COS.adjacent_buildings.horizontal_angle_between_chimney_and_adjacent_buildings.explain
            )

    given form_VerticalAngleBetweenChimneyAndAdjacentBuildings
        : Form[Option[VerticalAngleBetweenChimneyAndAdjacentBuildings]] =
        given Form[VerticalAngleBetweenChimneyAndAdjacentBuildings] = vertical_form.given_VerticalAngleBetweenChimneyAndAdjacentBuildings
        FormDerivation
            .conditionalOn[AdjacentBuildings, VerticalAngleBetweenChimneyAndAdjacentBuildings](adjacent_buildings_var)
            .withFieldName(FC_I18N_COS.adjacent_buildings.vertical_angle_between_chimney_and_adjacent_buildings.explain)

    given form_AdjacentBuildings: Form[AdjacentBuildings] =
        FormDerivation
            .derived[AdjacentBuildings]
            .withFieldName(
                I18N.local_conditions.chimney_termination.adjacent_buildings.explain
            )

    // ChimneyTermination

    given hasTransl_ChimneyTermination: HasTranslatedFieldsWithValues[ChimneyTermination] =
        HasTranslatedFieldsWithValues.makeFor[ChimneyTermination, I18nData](using I18N)

    given form_ChimneyTermination: Form[ChimneyTermination] =
        FormDerivation.derived[ChimneyTermination].autoOverwriteFieldNames.hideFieldName

    // z_geodetical_height

    given form_z_geodetical_height: DisplayUnits => Form[QtyD[Meter]] =
        import defaultable.given_z_geodetical_height
        import vv.meter.valid_whenPositive
        dual.given_dual_z_geodetical_height.form()
            .withFieldName(I18N.local_conditions.altitude)

    // LocalConditions Clone

    given hasTransl_LocalConditions_UIClone: HasTranslatedFieldsWithValues[LocalConditions_UIClone] =
        HasTranslatedFieldsWithValues.makeFor[LocalConditions_UIClone, I18nData](using I18N)

    given Form[LocalConditions_UIClone] =
        given Form[Boolean] = vertical_form.boolean_trueAsDefault_alwaysValid
        FormDerivation.derived[LocalConditions_UIClone].autoOverwriteFieldNames

    lazy val local_conditions_clone_var =
        local_conditions_var.zoomLazy(LocalConditions_UIClone.fromOriginal)(LocalConditions_UIClone.updateOriginal)

    lazy val chimney_location_on_roof_var =
        local_conditions_var.zoomLazy(_.chimney_termination.chimney_location_on_roof): (lc, wr) =>
            lc.copy(chimney_termination = lc.chimney_termination.copy(chimney_location_on_roof = wr))

    lazy val adjacent_buildings_var =
        local_conditions_var.zoomLazy(_.chimney_termination.adjacent_buildings): (lc, wr) =>
            lc.copy(chimney_termination = lc.chimney_termination.copy(adjacent_buildings = wr))

    lazy val local_conditions_clone_ui   = local_conditions_clone_var.as_HtmlElement
    lazy val chimney_location_on_roof_ui = chimney_location_on_roof_var.as_HtmlElement
    lazy val adjacent_buildings_ui       = adjacent_buildings_var.as_HtmlElement

    val node = div(
        cls := "flex flex-row items-start justify-center",
        div(cls := "flex-auto flex justify-center", div(cls := "flex-none", local_conditions_clone_ui)  ),
        div(cls := "flex-auto flex justify-center", div(cls := "flex-none", chimney_location_on_roof_ui)),
        div(cls := "flex-auto flex justify-center", div(cls := "flex-none", adjacent_buildings_ui)      )
    )

    // lazy val _form = local_conditions_var.as_DaisyUI5_HtmlElement
