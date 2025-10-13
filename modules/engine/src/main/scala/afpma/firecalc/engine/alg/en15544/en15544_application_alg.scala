/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import cats.data.*

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.Standard
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application.ComputeAt
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.Inputs as en13384_Inputs
import afpma.firecalc.engine.models.en13384.std.ReferenceTemperatures
import afpma.firecalc.engine.models.en13384.typedefs.PressureRequirements_13384
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en13384.typedefs.TemperatureRequirements_EN13384
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.standard.MecaFlu_Error

object EN15544_V_2023_Application_Alg:
    type ErrorGen = MCalc_Error    

trait EN15544_V_2023_Application_Alg[+_Inputs <: Inputs[?]] extends Standard:

    val inputs: _Inputs

    export EN15544_V_2023_Application_Alg.{ErrorGen}

    type VNel[A] = ValidatedNel[ErrorGen, A]

    type PSect = afpma.firecalc.engine.models.en13384.pipedescr.PipeElDescr | afpma.firecalc.engine.models.en15544.pipedescr.PipeElDescr

    val formulas: EN15544_V_2023_Formulas_Alg
    // export formulas.*

    // en13384 dependencies
    lazy val en13384_formulas: EN13384_1_A1_2019_Formulas_Alg
    given EN13384_1_A1_2019_Formulas_Alg = en13384_formulas

    abstract class EN13384_For15544_Application(
        override val formulas: EN13384_1_A1_2019_Formulas_Alg,
        override val inputs: en13384_Inputs,
    )
        extends EN13384_1_A1_2019_Common_Application (
            formulas,
            inputs
        )
    {
        override lazy val last_known_density_before_connector_pipe = 
            computeAt match
                case ComputeAt.Mean         => flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_density_mean)
                case ComputeAt.Middle       => flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_density_middle)
    
        override lazy val last_known_velocity_before_connector_pipe = 
            computeAt match
                case ComputeAt.Mean         => flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_velocity_mean)
                case ComputeAt.Middle       => flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_velocity_middle)
    }

    lazy val en13384_application: EN13384_For15544_Application

    lazy val en13384_fluegas_σ_CO2_dry_nominal: Percentage
    lazy val en13384_fluegas_σ_CO2_dry_lowest: Option[Percentage]
    lazy val en13384_fluegas_σ_H2O_nominal: Option[Percentage]
    lazy val en13384_fluegas_σ_H2O_lowest :  Option[Percentage]

    /** flue gas percentages (if specified by constructor) */
    lazy val en13384_heatingAppliance_fluegas = HeatingAppliance.FlueGas(
        co2_dry_perc_nominal    = en13384_fluegas_σ_CO2_dry_nominal,
        co2_dry_perc_reduced     = en13384_fluegas_σ_CO2_dry_lowest,
        h2o_perc_nominal        = en13384_fluegas_σ_H2O_nominal,
        h2o_perc_reduced         = en13384_fluegas_σ_H2O_lowest 
    )

    /** inputs for underlying EN 13384 Application (parts in relation to EN 15544)*/
    lazy val en13384_inputs: en13384_Inputs

    
    /** heating appliance as given as input to EN13384  */
    def en13384_heatingAppliance_input: VNelString[HeatingAppliance]

    /** heating appliance once applied in EN13384 (not the same as the input version, we need 13384 to be applied) */
    def en13384_heatingAppliance_final: VNelString[HeatingAppliance]

    def m_B: m_B
    // TODO: add constraints ? in signature ? in other objects ? AllTermConstraintsFor ?

    def m_B_min: Option[m_B_min]

    // type K_V <: Dimensionless
    // def K_V_calc: (c_P, PipeSection, Temperature, Temperature, m_G, σ_CO2) => K_V

    val fluegas_σ_CO2_dry_nominal: σ_CO2
    val fluegas_σ_CO2_dry_lowest : Option[σ_CO2]
    val wood_σ_H2O: σ_H2O

    // def t_connector_pipe: t_connector_pipe
    // def ts_connector_pipe_section(
    //     sect: ConnectorPipe.Section
    // ): Temperatures[FOp, TempD[Celsius]]

    def P_n: P_n // input
    def t_n: t_n // input


    // Section "1", "Scope"
    def injectors_air_velocity: OneOffOrNotApplicable[WithParams_15544[Velocity]]
    def validate_injectors_air_velocity: OneOffOrNotApplicable[WithParams_15544[ValidatedNel[FireboxError, Unit]]]

    // Section "4.3.1.2", "Firebox surface"
    def U_BR: OneOffOrNotApplicable[U_BR]

    def O_BR: O_BR

    // Section "4.3.1.3", "Firebox base"
    def A_BR_min: A_BR
    def A_BR_max: OneOffOrNotApplicable[A_BR]
    def A_BR: OneOffOrNotApplicable[A_BR]

    // Section "4.3.1.4", "Firebox height"
    def H_BR: OneOffOrNotApplicable[H_BR]

    // Section "4.3.2", "Calculated flue pipe length"
    def L_Z_calculated: L_N

    // Section "4.3.3", "Minimum flue pipe length"
    def table_1_Factor_a_or_b: Option[Table_1_Factor_a_or_b]
    def L_Z_min: OneOffOrNotApplicable[VNel[L_N]]

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
    def m_G: LoadOp[m_G]
    
    def m_L: LoadOp[m_L]

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
    def t_fluepipe(L_Z: QtyD[Meter]): t_fluepipe
    def t_fluepipe_mean(lz1: QtyD[Meter], lz2: QtyD[Meter]): VNel[t_fluepipe]

    // Section "4.8.4", "Flue gas temperature in the connector pipe"
    def t_connector_pipe_mean: WithParams_15544[t_connector_pipe_mean]

    // Section "4.8.5",

    // Flue gas temperature at chimney entrance mean flue gas
    // temperature of the chimney and temperature of the chimney wall
    // at the top of the chimney
    def t_chimney_entrance  : WithParams_15544[t_chimney_entrance]
    def t_chimney_mean      : WithParams_15544[t_chimney_mean]
    def t_chimney_out       : WithParams_15544[t_chimney_out]
    def t_chimney_wall_top  : WithParams_15544[t_chimney_wall_top]

    // Section "4.9.4", Calculation of static friction


    // Section "4.9.4.3", Friction coefficient


    // Section "4.9.5, Calculation of the resistance due to direction change (p_u)


    // Section "4.10.1", Pressure requirement

    def Σ_p_R_and_Σ_p_u: WithParams_15544[ValidatedNel[MecaFlu_Error, Pressure]]
    def Σ_p_h: WithParams_15544[ValidatedNel[MecaFlu_Error, Pressure]]

    def pressureRequirement_EN15544: WithParams_15544[ValidatedNel[MecaFlu_Error, PressureRequirement]]

    // Section "4.10.2, Dew point condition

    // Section "4.10.3", Efficiency of the combustion
    
    def η: WithParams_15544[η]
    def t_F: WithParams_15544[t_F]

    // Section "4.10.4"

    def required_delivery_pressure: WithParams_15544[ValidatedNel[MecaFlu_Error, RequiredDeliveryPressure]]
    def t_fluepipe_end: WithParams_15544[TCelsius]

    def flue_gas_triple_of_variates: WithParams_15544[ValidatedNel[MecaFlu_Error, FlueGasTripleOfVariates]]

    def estimated_output_temperatures: WithParams_15544[EstimatedOutputTemperatures]

    // EN13384 variables

    // TODO: P_Z, P_Zmax, P_Ze, P_Zemax, P_W, P_W_max, P_B
    def pressureRequirements_EN13384: WithParams_13384[ValidatedNel[MecaFlu_Error, PressureRequirements_13384]]
    def temperatureRequirements_EN13384: WithParams_13384[TemperatureRequirements_EN13384]

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

    given params_13384_to_15544: (p13384: Params_13384, diff: Params_15544_Minus_Params_13384) => Params_15544 = p13384
    given params_15544_to_13384: (p: Params_15544) => Params_13384 = p
    given params_15544_to_diff: (p: Params_15544) => Params_15544_Minus_Params_13384 = EmptyTuple
    
    given loadQty_from_Params_15544: (p: Params_15544) => LoadQty = p._2
    given pressReq_from_Params_15544: (p: Params_15544) => DraftCondition = p._1
    // export Params_13384.pressReq_from_Params_13384

    def airIntake_PipeResult     : WithParams_13384[PipeResultE]
    def combustionAir_PipeResult : WithParams_15544[PipeResultE]
    def firebox_PipeResult       : WithParams_15544[PipeResultE]
    def flue_PipeResult          : WithParams_15544[PipeResultE]
    def connector_PipeResult     : WithParams_15544[PipeResultE]
    def chimney_PipeResult       : WithParams_15544[PipeResultE]

    protected def pipesResult_15544_VNelS: WithParams_15544[PipesResult_15544_VNelString]

    // outputs
    val techSpecs: TechnicalSpecficiations
    def reference_temperatures: ReferenceTemperatures
    def outputs: WithParams_15544[Outputs]

    def validateFluePipeShape(): VNel[Unit]
    def validateVelocitiesInPipes(): WithParams_15544[VNel[Unit]]
    def validatePressureRequirements_EN15544(): WithParams_15544[ValidatedNel[MecaFlu_Error, Unit]]
    def validateChimneyWallTempIsAbove45DegreesCelsius(): WithParams_15544[ValidatedNel[MecaFlu_Error, Unit]]
    def validateEfficiencyIsAboveMinEfficiency(): WithParams_15544[ValidatedNel[MecaFlu_Error, Unit]]
    def validateCitedConstraints(): WithParams_15544[ValidatedNel[MecaFlu_Error, Unit]]
    def validateFireboxType(): WithParams_15544[ValidatedNel[FireboxError, Unit]]

    val runValidationAtParams: Params_15544
    def validateResults: VNel[Unit]

    def efficiencies_values: EfficienciesValues
    def emissions_and_efficiency_values: EmissionsAndEfficiencyValues
    def η_s: WithParams_15544[Percentage]

end EN15544_V_2023_Application_Alg
