/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v1.*
import afpma.firecalc.dto.v2.*
import afpma.firecalc.dto.v3.*

import coulomb.syntax.*

import io.taig.babel.Language
import io.taig.babel.Locale

object SchemaGenerators:

    /**
     * Minimal valid FireCalcYAML_V1 instance.
     * Uses realistic pipes extracted from fdim/exercices/en15544_strict/strict_ex01_colonne_ascendante
     */
    def minimalV1: FireCalcYAML_V1 =
        FireCalcYAML_V1(
            version = FireCalc_Version(1),
            locale = Locale(Language("en")),
            display_units = DisplayUnits.SI,
            standard_or_computation_method =
                StandardOrComputationMethod.EN_15544_2023,
            project_description = ProjectDescr(
                reference = "TEST-001",
                date = "2025-01-01",
                country = Country.France
            ),
            local_conditions = LocalConditions.default,
            stove_params = StoveParams.fromMaxLoadAndStoragePeriod(
                maximum_load = 20.kg,
                heating_cycle = 12.hours,
                min_efficiency = 80.percent,
                facing_type = FacingType.WithoutAirGap
            ),
            air_intake_descr = Seq(),
            firebox = Firebox_V1.Traditional(
                heat_output_reduced = HeatOutputReduced.NotDefined,
                firebox_depth = 40.cm,
                firebox_width = 50.cm,
                firebox_height = 60.cm,
                pressure_loss_coefficient_from_door = 0.5.withUnit[1],
                total_air_intake_surface_area_on_door = 0.01.m2,
                glass_width = 30.cm,
                glass_height = 40.cm
            ),
            flue_pipe_descr = Seq(),
            connector_pipe_descr = Seq(),
            chimney_pipe_descr = Seq()
        )

    /**
     * Minimal valid FireCalcYAML_V2 instance.
     * Uses realistic pipes extracted from fdim/exercices/en15544_strict/strict_ex02_carneau_descendant
     */
    def minimalV2: FireCalcYAML_V2 =
        FireCalcYAML_V2(
            version = FireCalc_Version(2),
            locale = Locale(Language("en")),
            display_units = DisplayUnits.SI,
            standard_or_computation_method =
                StandardOrComputationMethod.EN_15544_2023,
            project_description = ProjectDescr(
                reference = "TEST-001",
                date = "2025-01-01",
                country = Country.France
            ),
            local_conditions = LocalConditions.default,
            stove_params = StoveParams.fromMaxLoadAndStoragePeriod(
                maximum_load = 20.kg,
                heating_cycle = 12.hours,
                min_efficiency = 80.percent,
                facing_type = FacingType.WithoutAirGap
            ),
            air_intake_descr = Seq(),
            firebox = Firebox_V2.Traditional(
                heat_output_reduced = HeatOutputReduced.NotDefined,
                firebox_depth = 40.cm,
                firebox_width = 50.cm,
                firebox_height = 60.cm,
                pressure_loss_coefficient_from_door = 0.5.withUnit[1],
                total_air_intake_surface_area_on_door = 0.01.m2,
                glass_width = 30.cm,
                glass_height = 40.cm,
                height_of_first_row_of_air_injectors = 5.cm
            ),
            flue_pipe_descr = Seq(),
            connector_pipe_descr = Seq(),
            chimney_pipe_descr = Seq()
        )

    /**
     * Minimal valid FireCalcYAML_V3 instance.
     * Uses realistic pipes extracted from fdim/exercices/en15544_strict/strict_ex03_cas_pratique (with V2 types)
     */
    def minimalV3: FireCalcYAML_V3 =
        FireCalcYAML_V3(
            version = FireCalc_Version(3),
            locale = Locale(Language("en")),
            display_units = DisplayUnits.SI,
            standard_or_computation_method =
                StandardOrComputationMethod.EN_15544_2023,
            project_description = ProjectDescr(
                reference = "TEST-001",
                date = "2025-01-01",
                country = Country.France
            ),
            local_conditions = LocalConditions.default,
            stove_params = StoveParams.fromMaxLoadAndStoragePeriod(
                maximum_load = 20.kg,
                heating_cycle = 12.hours,
                min_efficiency = 80.percent,
                facing_type = FacingType.WithoutAirGap
            ),
            air_intake_descr = Seq(),
            firebox = Firebox_V2.Traditional(
                heat_output_reduced = HeatOutputReduced.NotDefined,
                firebox_depth = 40.cm,
                firebox_width = 50.cm,
                firebox_height = 60.cm,
                pressure_loss_coefficient_from_door = 0.5.withUnit[1],
                total_air_intake_surface_area_on_door = 0.01.m2,
                glass_width = 30.cm,
                glass_height = 40.cm,
                height_of_first_row_of_air_injectors = 5.cm
            ),
            flue_pipe_descr = Seq(),
            connector_pipe_descr = Seq(),
            chimney_pipe_descr = Seq()
        )

end SchemaGenerators
