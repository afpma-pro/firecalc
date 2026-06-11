/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.PostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxInitialDirection
import afpma.firecalc.dto.v7.PostFireboxInitialPosition
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application

import io.taig.babel.Locale
import io.taig.babel.Languages

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class TraditionalFireboxConstraintDispatchSuite extends AnyFreeSpec with Matchers:

    private def stubTraditionalDTO(
        totalAirIntakeSurfaceAreaOnDoor                  : Double
    ): Firebox.Traditional =
        Firebox.Traditional(
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            firebox_depth                         = 0.44.meters,
            firebox_width                         = 0.42.meters,
            firebox_height                        = 0.78.meters,
            height_of_lowest_opening              = 0.05.meters,
            pressure_loss_coefficient_from_door   = 0.3.unitless,
            total_air_intake_surface_area_on_door = totalAirIntakeSurfaceAreaOnDoor.cm2,
            glass_width                           = 0.15.meters,
            glass_height                          = 0.20.meters
        )

    private def stubStoveParams(maxLoad: Double): StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = maxLoad.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    private def buildEngineState(
        firebox                            : Firebox.Traditional,
        stoveParams                        : StoveParams
    ): FireCalcYAML_V7 =
        FireCalcYAML_V7(
            locale                         = Locale(Languages.Fr),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = ProjectDescr(
                reference = ExampleProject_15544.project.reference,
                date      = ExampleProject_15544.project.date,
                country   = ExampleProject_15544.project.country
            ),
            local_conditions               = ExampleProject_15544.localConditions,
            stove_params                   = stoveParams,
            air_intake_descr               = ExampleProject_15544.conduit_air_descr,
            firebox                        = firebox,
            post_firebox_pipes             = PostFireboxPipes(
                initialDirection = PostFireboxInitialDirection.default,
                initialPosition  = PostFireboxInitialPosition(0.m, 0.m, 0.m),
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot     (ExampleProject_15544.accumulateur_descr        ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(ExampleProject_15544.conduit_raccordement_descr),
                    PostFireboxPipeDescrSlot.ChimneySlot  (ExampleProject_15544.conduit_fumees_descr      )
                )
            )
        )

    private def loadApp(yaml: FireCalcYAML): EN15544_Strict_Application =
        val loader = FireCalcYAML_Loader(yaml)
        val appV   = loader.make_en15544_Strict_Application
        appV.isValid shouldBe true
        appV.toOption.get

    "Traditional firebox constraint dispatch through resolver" - {

        "firebox_custom_constraints override: tiny h67 triggers injector velocity error" in {
            val app    = loadApp(
                buildEngineState(
                    stubTraditionalDTO(totalAirIntakeSurfaceAreaOnDoor = 1.0),
                    stubStoveParams   (18.5                                 )
                )
            )
            val result = app.validateResultsExceptEmissionsValues(Country.France)
            result.isValid shouldBe false
        }
    }
