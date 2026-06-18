/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.Country

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.Standard
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.ReferenceTemperatures
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import afpma.firecalc.engine.models.en13384.typedefs.PressureRequirements_13384
import afpma.firecalc.engine.models.en13384.typedefs.TemperatureRequirements_13384
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.standard.*

import cats.data.*

object EN15544_V_2023_Application_Alg:
    type ErrorGen = MCalc_Error

trait EN15544_V_2023_Application_Alg extends Standard with HasTypeMembers_15544_Alg:
    self =>

    lazy val inputs: Inputs_15544

    /**
     * The ordered post-firebox pipe descriptor slots from the DTO.
     *
     * Abstract — every concrete `Application` MUST provide this. Typical wiring is via
     * the `WithPipeChain_15544_*` trait family (e.g. `WithPipeChain_15544_MCE` produces
     * `[ThermalFlueSlot, ConnectorSlot, ChimneySlot]`); the `*_Application.make` factory
     * then forwards the trait's value into the Application instance.
     *
     * Previously had a default `[FlueSlot, ConnectorSlot, ChimneySlot]` which silently
     * masked wiring bugs (an Application that forgot to forward `pfbSlots` would inherit
     * the flow-only default and fail at runtime in MCE mode with "FlueSlot in flue
     * region"). Made abstract to force explicit wiring.
     */
    lazy val postFireboxPipeSlots: Seq[afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7]

    /**
     * Wrapper-level initial direction for post-firebox pipes (V7).
     * Used to seed the first pipe's initial frame in flueRegionPipeResults folds.
     * Defaults to None for backward compatibility.
     */
    def postFireboxInitialDirection: Option[afpma.firecalc.dto.common.PipeInitialDirection] = None

    /**
     * Wrapper-level initial position for post-firebox pipes (V7).
     * Defaults to None for backward compatibility.
     * Threaded alongside direction for consistency; position is not used for frame seeding.
     */
    def postFireboxInitialPosition: Option[afpma.firecalc.dto.common.Position3D] = None

    /**
     * Wrapper-level initial position for air intake pipes (V7).
     * Defaults to None for backward compatibility.
     * Resolved from AirIntakePosition.Auto modes in the loader.
     */
    def airIntakeInitialPosition: Option[afpma.firecalc.dto.common.Position3D] = None

    /**
     * Raw air intake pipe descriptors from the DTO.
     * Used by direction-reachability validation to walk the air intake bend chain.
     * Defaults to empty for engine variants that do not model an air intake.
     */
    def airIntakeDescriptors: Seq[afpma.firecalc.dto.v7.FlowOnlyPipeDescr_13384_V4] = Seq.empty

    export EN15544_V_2023_Application_Alg.{ErrorGen}

    type VNel[A] = ValidatedNel[ErrorGen, A]

    type PSect = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.PipeElDescr |
        afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.PipeElDescr

    val formulas: EN15544_V_2023_Formulas_Alg
    // export formulas.*

    // en13384 dependencies
    lazy val en13384_formulas: EN13384_1_A1_2019_Formulas_Alg
    given EN13384_1_A1_2019_Formulas_Alg = en13384_formulas

    def en13384_inputs_pipes: Pipes_13384

    def en13384_inputs_fuelType: FuelType = FuelType.WoodLog30pHumidity

    def en13384_inputs_flueGasCondition: FlueGasCondition = FlueGasCondition.Dry_NonCondensing

    /**
     * Abstract type for the EN13384 application used within EN15544.
     * Bound to the EN13384 algebra so the algebra layer stays pure.
     * Concrete implementations provide a subtype that extends the EN13384 impl.
     */
    type EN13384ForApp <: EN13384_1_A1_2019_Application_Alg

    lazy val en13384_application: EN13384ForApp

    lazy val en13384_fluegas_σ_CO2_dry_nominal: Percentage
    lazy val en13384_fluegas_σ_CO2_dry_lowest : Option[Percentage]
    lazy val en13384_fluegas_σ_H2O_nominal    : Option[Percentage]
    lazy val en13384_fluegas_σ_H2O_lowest     : Option[Percentage]

    /** flue gas percentages (if specified by constructor) */
    lazy val en13384_heatingAppliance_fluegas: afpma.firecalc.engine.models.en13384.std.HeatingAppliance.FlueGas =
        HeatingAppliance.FlueGas(
            co2_dry_perc_nominal = en13384_fluegas_σ_CO2_dry_nominal,
            co2_dry_perc_reduced = en13384_fluegas_σ_CO2_dry_lowest,
            h2o_perc_nominal     = en13384_fluegas_σ_H2O_nominal,
            h2o_perc_reduced     = en13384_fluegas_σ_H2O_lowest
        )

    /** inputs for underlying EN 13384 Application (parts in relation to EN 15544) */
    lazy val en13384_inputs: Inputs_13384

    /** heating appliance as given as input to EN13384 */
    def en13384_heatingAppliance_input: VNelMcalcErr[HeatingAppliance]

    /** heating appliance once applied in EN13384 (not the same as the input version, we need 13384 to be applied) */
    def en13384_heatingAppliance_final: VNelMcalcErr[HeatingAppliance]

    def m_B    : m_B
    def m_B_min: Option[m_B_min]

    // type K_V <: Dimensionless
    // def K_V_calc: (c_P, PipeSection, Temperature, Temperature, m_G, σ_CO2) => K_V

    val fluegas_σ_CO2_dry_nominal: σ_CO2
    val fluegas_σ_CO2_dry_lowest : Option[σ_CO2]
    val wood_σ_H2O               : σ_H2O

    // def t_connector_pipe: t_connector_pipe
    // def ts_connector_pipe_section(
    //     sect: ConnectorPipe.Section
    // ): Temperatures[FOp, TempD[Celsius]]

    def P_n: P_n // input
    def t_n: t_n // input

    // Section "1", "Scope"

    // Section "4.3.1", "Firebox sizing"

    def firebox_sizing: FireboxSizingAlg
    type FireboxSizingAlg <: FireboxSizing_15544_Alg

    // Section "4.3.2", "Calculated flue pipe length"
    def L_Z_calculated: L_N

    // Section "4.3.3", "Minimum flue pipe length"
    def table_1_Factor_a_or_b: Option[Table_1_Factor_a_or_b]
    def L_Z_min              : VNel[L_N]

    // Section "4.3.4", "Gas groove profile"
    def A_GS: A_GS

    // Section "4.4", "Calculation of the burning rate"
    def m_BU: m_BU

    // Section "4.5", "Fixing of the air ratio"
    def λ: λ

    // Section "4.6", "Combustion air flue gas"
    // Section "4.6.2", "Combustion air flow rate"
    def V_L: EpOp[LoadOp[V_L]]

    // Section "4.6.2.2", "Temperature correction"
    def f_t(t: TempD[Celsius]): f_t

    // Section "4.6.2.3", "Altitude correction"

    def z_geodetical_height: z_geodetical_height

    def f_s: f_s

    // Section "4.6.3", "Flue gas flow rate"
    def V_G(t: TempD[Celsius]): LoadOp[V_G]

    // Section "4.6.4", "Flue gas mass flow rate"

    // helper method
    def getOrThrow_forLoadOp[A](
        opvnelmcalcerr: LoadOp[VNelMcalcErr[A]],
        ifNone        : MCalc_Error
    )(using olq: Option[LoadQty]): A =
        opvnelmcalcerr(using olq) match
            case None             => throw new IllegalStateException(ifNone.toString)
            case Some(vnelmcalca) =>
                vnelmcalca match
                    case Validated.Valid(a)             => a
                    case Validated.Invalid(nelmcalcerr) =>
                        throw new IllegalStateException(nelmcalcerr.toList.mkString("\n"))

    def m_G: LoadOp[VNelMcalcErr[m_G]]

    def m_L: LoadOp[VNelMcalcErr[m_L]]

    // Section "4.7.1", "Combustion air density"
    def ρ_L: EpOp[ρ_L]

    // Section "4.7.2", "Flue gas density"
    def ρ_G(t: TempD[Celsius]): ρ_G

    // Section "4.8.1",
    // "Mean outside air temperature and combustion air temperature"
    def t_combustion_air: EpOp[t_combustion_air]

    // Section "4.8.2", "Mean firebox temperature"
    def t_BR: t_BR

    def t_burnout: t_burnout

    // Section "4.8.3", "Flue gas temperature in the flue pipe"
    def t_fluepipe     (L_Z: QtyD[Meter]                  ): t_fluepipe
    def t_fluepipe_mean(lz1: QtyD[Meter], lz2: QtyD[Meter]): VNel[t_fluepipe]

    // EN13384 variables

    // TODO: P_Z, P_Zmax, P_Ze, P_Zemax, P_W, P_W_max, P_B
    def pressureRequirements_EN13384   : WithParams_13384[VNelMcalcErr[PressureRequirements_13384]]
    def temperatureRequirements_EN13384: WithParams_13384[VNelMcalcErr[TemperatureRequirements_13384]]

    // VALIDATIONS
    def citedConstraints: CitedConstraints

    // RESULTS
    final type Params_15544 = Params_13384
    val Params_15544 = Params_13384
    type WithParams_15544[X] = Params_15544 ?=> X

    type Params_15544_Minus_Params_13384 = EmptyTuple

    // given mk_params_15544_from_args(using
    //     pReq: PressureRequirements, lq: LoadQty
    // ): Params_15544 = (ep, lq)

    given params_13384_to_15544: (p13384: Params_13384, diff: Params_15544_Minus_Params_13384) => Params_15544                    = p13384
    given params_15544_to_13384: (p     : Params_15544                                       ) => Params_13384                    = p
    given params_15544_to_diff : (p     : Params_15544                                       ) => Params_15544_Minus_Params_13384 = EmptyTuple

    given loadQty_from_Params_15544 : (p: Params_15544) => LoadQty        = p._2
    given pressReq_from_Params_15544: (p: Params_15544) => DraftCondition = p._1
    // export Params_13384.pressReq_from_Params_13384

    // ─── AtParams: params-dependent layer ───────────────────────────────

    /**
     * Inner trait grouping all declarations that depend on a fixed `Params_15544`.
     * Implementations provide lazy vals so that results are computed once per params combo.
     */
    trait AtParams:
        val params: Params_15544

        // Pipe results
        lazy val combustionAir_PipeResult: VNelMcalcErr[PipeResult]
        lazy val firebox_PipeResult      : VNelMcalcErr[PipeResult]
        lazy val connector_PipeResult    : VNelMcalcErr[PipeResult]
        lazy val chimney_PipeResult      : VNelMcalcErr[PipeResult]

        /**
         * All post-firebox pipe results as a tagged vector: [(PipeType, PipeResult)].
         * Generic N-pipe representation — the V6 slot vector is the canonical source.
         */
        lazy val postFireboxPipeResults: VNelMcalcErr[Vector[(PipeType, PipeResult)]]

        /**
         * Chain-aware "conceptual" accessors for the flue region.
         *
         * Strict/MCE override to bottom out at `flueRegionPipeResults.last`
         * (the HA-power-free Stage 1 result), decoupling flue-region temperature/pressure
         * reads from Stage 2 HA resolution and breaking the lazy-val initialization cycle.
         */
        lazy val conceptualFluePipeResult       : VNelMcalcErr[PipeResult]
        lazy val conceptualFlueRegionPipeResults: VNelMcalcErr[Vector[PipeResult]]

        // Derived temperatures (Section 4.8.4 – 4.8.5)
        lazy val t_connector_pipe_mean: VNelMcalcErr[t_connector_pipe_mean]
        lazy val t_chimney_entrance   : VNelMcalcErr[t_chimney_entrance]
        lazy val t_chimney_mean       : VNelMcalcErr[t_chimney_mean]
        lazy val t_chimney_out        : VNelMcalcErr[t_chimney_out]
        lazy val t_chimney_wall_top   : VNelMcalcErr[t_chimney_wall_top]

        // Pressures (Section 4.9 – 4.10.1)
        lazy val Σ_p_R_and_Σ_p_u            : VNelMcalcErr[Pressure]
        lazy val Σ_p_h                      : VNelMcalcErr[Pressure]
        lazy val pressureRequirement_EN15544: VNelMcalcErr[PressureRequirement]

        // Efficiency (Section 4.10.3)
        lazy val η  : VNelMcalcErr[η]
        lazy val t_F: VNelMcalcErr[t_F]
        lazy val η_s: VNelMcalcErr[Percentage]

        // Section 4.10.4
        lazy val required_delivery_pressure: VNelMcalcErr[RequiredDeliveryPressure]
        lazy val t_fluepipe_end            : VNelMcalcErr[TCelsius]

        lazy val flue_gas_triple_of_variates  : VNelMcalcErr[FlueGasTripleOfVariates]
        lazy val estimated_output_temperatures: EstimatedOutputTemperatures

        // Aggregated pipe results
        lazy val pipesResult_15544_VNelS: PipesResult_15544_VNelString

        // Outputs
        lazy val outputs: Outputs

        // Validations
        lazy val validateVelocitiesInFluePipe                  : VNelMcalcErr[Unit]
        lazy val validateVelocitiesInConnectorPipe             : VNelMcalcErr[Unit]
        lazy val validateVelocitiesInChimneyPipe               : VNelMcalcErr[Unit]
        lazy val validateVelocitiesInPipes                     : VNel[Unit]
        lazy val validatePressureRequirements_EN15544          : VNelMcalcErr[Unit]
        lazy val validateChimneyWallTempIsAboveCondensationTemp: VNelMcalcErr[Unit]
        lazy val validateEfficiencyIsAboveMinEfficiency        : VNelMcalcErr[Unit]
        def validateSeasonalEfficiency(countryCode: Country): VNelMcalcErr[Unit]
        lazy val validateCitedConstraints          : VNelMcalcErr[Unit]
        lazy val validateFireboxSpecificConstraints: ValidatedNel[FireboxError, Unit]
        lazy val fluePipeLengthBelowMinimumWarning : Option[FluePipeLengthBelowMinimum]
    end AtParams

    // ─── Pre-built AtParams instances ───────────────────────────────────

    /** Draft-min / nominal load — the primary computation used by the UI */
    lazy val atDraftMin_LoadNominal: AtParams

    /** Draft-min / reduced load — None when there is no m_B_min */
    lazy val atDraftMin_LoadMin: Option[AtParams]

    /** Draft-max / nominal load */
    lazy val atDraftMax_LoadNominal: AtParams

    /** Draft-max / reduced load — None when there is no m_B_min */
    lazy val atDraftMax_LoadMin: Option[AtParams]

    /** Alias to the most commonly used combo (draft-min, nominal load) */
    lazy val primary: AtParams = atDraftMin_LoadNominal

    // ─── Params-independent members kept on outer trait ──────────────────

    def airIntake_PipeResult: WithParams_13384[VNelMcalcErr[PipeResult]]

    // outputs
    val techSpecs: TechnicalSpecficiations
    def reference_temperatures: ReferenceTemperatures

    def validateFluePipeShape               (                    ): VNel[Unit]
    def validateResultsExceptEmissionsValues(countryCode: Country): VNel[Unit]

    def efficiencies_values            : EfficienciesValues
    def emissions_and_efficiency_values: EmissionsAndEfficiencyValues
    def check_emissions_and_efficiency_values_with_local_regulations(
        lreg: LocalRegulations
    ): List[LocalRegulations.ParamCheckResult[?]]

end EN15544_V_2023_Application_Alg
