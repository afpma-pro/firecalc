/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.ops.en13384.Pressures_13384.given
import afpma.firecalc.engine.standard.*

import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.catsSyntaxEither
import cats.syntax.all.catsSyntaxOptionId
import cats.syntax.all.catsSyntaxTuple2Semigroupal
import cats.syntax.all.catsSyntaxTuple4Semigroupal
import cats.syntax.all.catsSyntaxTuple5Semigroupal
import cats.syntax.all.catsSyntaxValidatedId

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.ops.standard.all.given

/**
 * Heating appliance + pipe result methods extracted from `EN15544_V_2023_Common_Application`.
 *
 * Covers the EN 13384 heating appliance bridge (pressures, input, final) and
 * pipe result accessors (air intake, combustion air, pressure/temperature requirements).
 */
trait EN15544_Common_HeatingAppliance { en15544: EN15544_V_2023_Common_Application =>

    // Section "4.10", "Operation control"

    // Section "4.10.2", "Dew point condition"

    // Section "4.10.3 – 4.10.4", "Efficiency and flue gas triple of variates"
    // Moved to CommonAtParams

    private def heatingAppliance_draft_min: VNelMcalcErr[Pressure] =
        atDraftMin_LoadNominal.required_delivery_pressure

    private def heatingAppliance_draft_max: VNelMcalcErr[Pressure] =
        given Params_13384 = Params_13384.DraftMin_LoadNominal
        val ap             = atDraftMin_LoadNominal
        (
            ap.Σ_p_R_and_Σ_p_u,
            ap.outputs.pipesResult_15544.accumulateErrors.map(_.Σ_ph_until_fluepipe_end: Pressure),
            airIntake_PipeResult.andThen                     (_.en13384_pr_all                   ),
            ap.outputs.pipesResult_15544.accumulateErrors.andThen: pr =>
                pr.connector match
                    case Some(c) => c.en13384_pr_all
                    case None    => 0.0.pascals.validNel,
            ap.outputs.pipesResult_15544.accumulateErrors.andThen(_.chimney.en13384_pr_all)
        )
            .mapN:
                (
                    Σ_p_R_and_Σ_p_u,
                    Σ_p_h_until_fluepipe_end,
                    `airIntake_pr_all`,
                    `connector_pr_all`,
                    `chimney_pr_all`
                ) =>
                    // tirage_max_for_en13384
                    (
                        // tirage max according to EN15544
                        1.05 * (Σ_p_R_and_Σ_p_u                                           )
                            // minus parts that en13384 will count as friction / resistances
                            -  (`airIntake_pr_all` + `connector_pr_all` + `chimney_pr_all`)
                            // minus parts with standing pressure that en13384 will not count
                            - Σ_p_h_until_fluepipe_end
                    )

    def en13384_heatingAppliance_pressures: VNelMcalcErr[HeatingAppliance.Pressures] =
        (
            heatingAppliance_draft_min,
            heatingAppliance_draft_max
        ).mapN: (draftMin, draftMax) =>
            if (draftMin > 0.0.pascals)
                HeatingAppliance.Pressures     (
                    underPressure      = UnderPressure.Negative,
                    flue_gas_draft_min = draftMin.some,
                    flue_gas_draft_max = draftMax.some,
                    flue_gas_pdiff_min = None,
                    flue_gas_pdiff_max = None
                )
            else
                HeatingAppliance.Pressures     (
                    underPressure      = UnderPressure.Positive,
                    flue_gas_draft_min = None,
                    flue_gas_draft_max = None,
                    flue_gas_pdiff_min =
                        (-draftMin).some, // (-) because inverted logic when chimney under positive pressure
                    flue_gas_pdiff_max =
                        (-draftMax).some  // (-) because inverted logic when chimney under positive pressure
                )

    def en13384_heatingAppliance_input: VNelMcalcErr[HeatingAppliance] =
        (
            en13384_heatingAppliance_efficiency,
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_pressures,
            en13384_heatingAppliance_temperatures
        )
            .mapN: (eff, powers, pressures, temperatures) =>
                HeatingAppliance(
                    inputs.design.firebox.reference.map(r => s"${r} + EN 15544"),
                    inputs.design.firebox.type_of_appliance,
                    eff,
                    en13384_heatingAppliance_fluegas,
                    powers,
                    temperatures,
                    en13384_heatingAppliance_massFlows,
                    pressures
                )

    def en13384_heatingAppliance_final: VNelMcalcErr[HeatingAppliance] =
        en13384_heatingAppliance_input.map(inp => en13384_application.heatingAppliance_final(using inp))

    // TODO: add this as examples or in documentation

    // val m_B_min_test: F[m_B_min] = pure(5.kilograms)
    // setConstraintFromQtyF[Mass, m_B](m_B_min_test, Min(_))

    def airIntake_PipeResult: WithParams_13384[VNelMcalcErr[PipeResult]] =
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_efficiency
        )
            .mapN:
                case (ha_pow, ha_eff) => (ha_pow, ha_eff)
            .andThen: (ha_pow, ha_eff) =>
                given HeatingAppliance.Powers     = ha_pow
                given HeatingAppliance.Efficiency = ha_eff
                en13384_application.airIntake_PipeResult.toValidatedNel

    final def combustionAir_PipeResult_whenEmpty: WithParams_15544[VNelMcalcErr[PipeResult]] =
        PipeResult
            .useless      (
                pt       = CombustionAirPipeT,
                pu       = 0.0.pascals,
                gas_temp = t_combustion_air
            )
            .validNel

    def combustionAir_PipeResult_whenExists(
        fd: CombustionAirPipe_Module.FullDescr
    ): WithParams_15544[VNelMcalcErr[PipeResult]]

    def pressureRequirements_EN13384: WithParams_13384[VNelMcalcErr[PressureRequirements_13384]] =
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_efficiency,
            en13384_heatingAppliance_pressures,
            en13384_heatingAppliance_temperatures
        ).mapN_andThen_impl:
            en13384_application.pressureRequirements

    override def temperatureRequirements_EN13384: WithParams_13384[VNelMcalcErr[TemperatureRequirements_13384]] =
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_temperatures,
            en13384_heatingAppliance_efficiency
        ).mapN_andThen_impl:
            en13384_application.temperatureRequirements.validNel

}
