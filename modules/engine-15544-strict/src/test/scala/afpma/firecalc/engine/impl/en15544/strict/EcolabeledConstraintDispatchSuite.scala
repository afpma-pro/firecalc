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

import afpma.firecalc.engine.alg.en15544.ConstraintContext
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraintsResolver
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled_V1
import afpma.firecalc.engine.models.en15544.typedefs.*

import io.taig.babel.Locale
import io.taig.babel.Languages

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class EcolabeledConstraintDispatchSuite extends AnyFreeSpec with Matchers:

    private def stubEcolabeledDTO(
        heightOfFirstRowOfAirInjectors                    : Length
    ): Firebox.Ecolabeled =
        Firebox.Ecolabeled(
            heat_output_reduced                     = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            version                                 = Left("Version 1"),
            air_intake_shape                        = None,
            firebox_depth                           = 0.54.meters,
            firebox_width                           = 0.54.meters,
            firebox_height                          = 0.813.meters,
            height_of_first_row_of_air_injectors    = heightOfFirstRowOfAirInjectors,
            door_opening_width                      = 0.52.meters,
            glass_width                             = 0.50.meters,
            glass_height                            = 0.40.meters,
            ash_pit_height                          = 0.08.meters,
            air_manifold_height                     = 0.11.meters,
            firebox_floor_thickness                 = 0.08.meters,
            firebox_inner_wall_thickness            = 0.06.meters,
            firebox_outer_wall_thickness            = 0.06.meters,
            air_column_thickness                    = 0.035.meters,
            width_between_two_air_columns_sides     = 0.045.meters,
            width_between_two_air_columns_rear      = 0.045.meters,
            reinforcement_bars_offset_in_corners_R1 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R2 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R3 = 0.045.meters,
            injector_height                         = 0.008.meters
        )

    private def stubStoveParams(maxLoad: Double): StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = maxLoad.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    private def buildEngineState(
        firebox                            : Firebox.Ecolabeled,
        stoveParams                        : StoveParams
    ): FireCalcYAML_V7 =
        FireCalcYAML_V7(
            locale                         = Locale(Languages.Fr),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = ProjectDescr(
                reference = CasType_15544_C3.project.reference,
                date      = CasType_15544_C3.project.date,
                country   = CasType_15544_C3.project.country
            ),
            local_conditions               = CasType_15544_C3.localConditions,
            stove_params                   = stoveParams,
            air_intake_descr               = CasType_15544_C3.conduit_air_descr,
            firebox                        = firebox,
            post_firebox_pipes             = PostFireboxPipes(
                initialDirection = PostFireboxInitialDirection.default,
                initialPosition  = PostFireboxInitialPosition(0.m, 0.m, 0.m),
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot     (CasType_15544_C3.accumulateur_descr        ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(CasType_15544_C3.conduit_raccordement_descr),
                    PostFireboxPipeDescrSlot.ChimneySlot  (CasType_15544_C3.conduit_fumees_descr      )
                )
            )
        )

    private def loadApp(yaml: FireCalcYAML): EN15544_Strict_Application =
        val loader = FireCalcYAML_Loader(yaml)
        val appV   = loader.make_en15544_Strict_Application
        appV.isValid shouldBe true
        appV.toOption.get

    "Ecolabeled constraint dispatch through resolver" - {

        "resolver returns Ecolabeled instance with m_B min=6kg (not base 10kg)" in {
            val fb          = Ecolabeled_V1(
                pn_reduced                                      = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
                h11_profondeurDuFoyer                           = 54.cm,
                h12_largeurDuFoyer                              = 54.cm,
                h13_hauteurDuFoyer                              = 81.3.cm,
                h70_largeurPorteDansMaconnerie                  = 52.cm,
                h71_largeurVitre                                = 50.cm,
                h72_hauteurVitre                                = 40.cm,
                h74_hauteur_de_cendrier_AF                      = 8.cm,
                h75_hauteurArriveeConduitAir_DessousSoleFoyer_W = 11.cm,
                h76_epaisseurSole                               = 8.cm,
                h77_epaisseurParoiInterneFoyer_D1               = 6.cm,
                epaisseurParoiExterneFoyer_D2                   = 6.cm,
                h78_largeurEspaceInterparoisDuFoyer_S           = 3.5.cm,
                h79_largeurRenfortMedianLateraux                = 4.5.cm,
                h80_largeurRenfortMedianArriere                 = 4.5.cm,
                r1                                              = 4.5.cm,
                r2                                              = 4.5.cm,
                r3                                              = 4.5.cm,
                h82_hauteurDesInjecteurs_Z                      = 0.8.cm,
                h83_hauteurEntreLaSoleEtLe1erInjecteur_X        = 10.cm
            )
            val constraints = FireboxConstraintsResolver.resolve(fb)
            val ctx         = ConstraintContext(
                m_B                            = 8.kg,
                O_BR                           = 500.cm2,
                FLOOR_DEPTH_TO_WIDTH_MIN_RATIO = 0.5,
                FLOOR_DEPTH_TO_WIDTH_MAX_RATIO = 2.0,
                A_BR_min                       = Some(500.cm2),
                A_BR_max                       = Some(2000.cm2),
                A_BR                           = 1000.cm2,
                H_BR_min                       = None,
                H_BR                           = 81.3.cm,
                n_min                          = 78.percent
            )
            val resolved    = constraints.m_B_constraints(fb, ctx)
            val hasMin6kg   = resolved.exists {
                case Some(tc: TermConstraint.Min[?]) => tc.min == 6.kg
                case _                               => false
            }
            hasMin6kg shouldBe true
        }

        "custom constraint: h83 < 5cm surfaces error through application layer" in {
            val app    = loadApp(
                buildEngineState(stubEcolabeledDTO(heightOfFirstRowOfAirInjectors = 3.cm), stubStoveParams(26.0))
            )
            val result = app.validateResultsExceptEmissionsValues(Country.France)
            result.isValid shouldBe false
        }
    }
