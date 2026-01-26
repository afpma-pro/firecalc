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
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_Alg
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.ReferenceTemperatures
import afpma.firecalc.engine.models.en13384.typedefs.PressureRequirements_13384
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en13384.typedefs.TemperatureRequirements_13384
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.dto.common.DuctType

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.standard.MecaFlu_Error

object EN15544_V_2023_Application_Alg:
    type ErrorGen = MCalc_Error    

trait EN15544_V_2023_Application_Alg extends Standard with HasTypeMembers_15544_Alg:
    self =>

    lazy val inputs: Inputs_15544

    export EN15544_V_2023_Application_Alg.{ErrorGen}

    type VNel[A] = ValidatedNel[ErrorGen, A]

    type PSect = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.PipeElDescr | afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.PipeElDescr

    val formulas: EN15544_V_2023_Formulas_Alg
    // export formulas.*

    // en13384 dependencies
    lazy val en13384_formulas: EN13384_1_A1_2019_Formulas_Alg
    given EN13384_1_A1_2019_Formulas_Alg = en13384_formulas

    def en13384_inputs_pipes: Pipes_13384

    def en13384_inputs_fuelType: FuelType = FuelType.WoodLog30pHumidity

    def en13384_inputs_flueGasCondition: FlueGasCondition = FlueGasCondition.Dry_NonCondensing

    abstract class EN13384_For_15544_Application(
        override val formulas: EN13384_1_A1_2019_Formulas_Alg,
    )
        extends EN13384_1_A1_2019_Common_Application(formulas)
    {
        override type AirIntakePipe_Module_T  = self.AirIntakePipe_Module_T
        override val AirIntakePipe_Module     = self.AirIntakePipe_Module

        override type Pipes_13384 = self.Pipes_13384

        override type Inputs_13384 = self.Inputs_13384

        override lazy val inputs = en13384_inputs

        override lazy val last_known_density_before_connector_pipe = 
            computeAt match
                case ComputeAt.Mean         => flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_density_mean)
                case ComputeAt.Middle       => flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_density_middle)
    
        override lazy val last_known_velocity_before_connector_pipe = 
            computeAt match
                case ComputeAt.Mean         => flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_velocity_mean)
                case ComputeAt.Middle       => flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_velocity_middle)

        // 7.8.4
        // Températures moyennes pour le calcul de pression

        /** température moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en K */
        override def T_mB =
            // flow only pipes are necessarily considered non concentric
            // otherwise we would have to compute thermal variations
            val dt = DuctType.NonConcentricDuctsHighThermalResistance 
            val tl: afpma.firecalc.engine.models.en13384.typedefs.T_L = T_L
            // val debug = s"T_L = $tl (ep = ${DraftCondition.summon})"
            // scala.scalajs.js.Dynamic.global.console.log(debug)
            // println(debug)
            formulas.T_mB_calc(dt, tl).withSectionTyp(AirIntakePipeT)
    }

    lazy val en13384_application: EN13384_For_15544_Application

    lazy val en13384_fluegas_σ_CO2_dry_nominal: Percentage
    lazy val en13384_fluegas_σ_CO2_dry_lowest: Option[Percentage]
    lazy val en13384_fluegas_σ_H2O_nominal: Option[Percentage]
    lazy val en13384_fluegas_σ_H2O_lowest :  Option[Percentage]

    /** flue gas percentages (if specified by constructor) */
    lazy val en13384_heatingAppliance_fluegas = HeatingAppliance.FlueGas(
        co2_dry_perc_nominal    = en13384_fluegas_σ_CO2_dry_nominal,
        co2_dry_perc_reduced    = en13384_fluegas_σ_CO2_dry_lowest,
        h2o_perc_nominal        = en13384_fluegas_σ_H2O_nominal,
        h2o_perc_reduced        = en13384_fluegas_σ_H2O_lowest 
    )

    /** inputs for underlying EN 13384 Application (parts in relation to EN 15544)*/
    lazy val en13384_inputs: Inputs_13384

    /** heating appliance as given as input to EN13384  */
    def en13384_heatingAppliance_input: VNelMcalcErr[HeatingAppliance]

    /** heating appliance once applied in EN13384 (not the same as the input version, we need 13384 to be applied) */
    def en13384_heatingAppliance_final: VNelMcalcErr[HeatingAppliance]

    def m_B: m_B
    def m_B_min: Option[m_B_min]

    //type K_V <: Dimensionless
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
    
    // helper method
    def getOrThrow_forLoadOp[A](
        opvnelmcalcerr: LoadOp[VNelMcalcErr[A]],
        ifNone: MCalc_Error,
    )(using olq: Option[LoadQty]): A = 
        opvnelmcalcerr(using olq) match
            case None    => throw new IllegalStateException(ifNone.toString)
            case Some(vnelmcalca) => 
                vnelmcalca match
                    case Validated.Valid(a)             => a
                    case Validated.Invalid(nelmcalcerr) => throw new IllegalStateException(nelmcalcerr.toList.mkString("\n"))

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
    def t_fluepipe(L_Z: QtyD[Meter]): t_fluepipe
    def t_fluepipe_mean(lz1: QtyD[Meter], lz2: QtyD[Meter]): VNel[t_fluepipe]

    // Section "4.8.4", "Flue gas temperature in the connector pipe"
    def t_connector_pipe_mean: WithParams_15544[VNelMcalcErr[t_connector_pipe_mean]]

    // Section "4.8.5",

    // Flue gas temperature at chimney entrance mean flue gas
    // temperature of the chimney and temperature of the chimney wall
    // at the top of the chimney
    def t_chimney_entrance  : WithParams_15544[VNelMcalcErr[t_chimney_entrance]]
    def t_chimney_mean      : WithParams_15544[VNelMcalcErr[t_chimney_mean]]
    def t_chimney_out       : WithParams_15544[VNelMcalcErr[t_chimney_out]]
    def t_chimney_wall_top  : WithParams_15544[VNelMcalcErr[t_chimney_wall_top]]

    // Section "4.9.4", Calculation of static friction


    // Section "4.9.4.3", Friction coefficient


    // Section "4.9.5, Calculation of the resistance due to direction change (p_u)


    // Section "4.10.1", Pressure requirement

    def Σ_p_R_and_Σ_p_u: WithParams_15544[VNelMcalcErr[Pressure]]
    def Σ_p_h: WithParams_15544[VNelMcalcErr[Pressure]]

    def pressureRequirement_EN15544: WithParams_15544[VNelMcalcErr[PressureRequirement]]

    // Section "4.10.2, Dew point condition

    // Section "4.10.3", Efficiency of the combustion
    
    def η: WithParams_15544[VNelMcalcErr[η]]
    def t_F: WithParams_15544[VNelMcalcErr[t_F]]

    // Section "4.10.4"

    def required_delivery_pressure: WithParams_15544[VNelMcalcErr[RequiredDeliveryPressure]]
    def t_fluepipe_end: WithParams_15544[VNelMcalcErr[TCelsius]]

    def flue_gas_triple_of_variates: WithParams_15544[VNelMcalcErr[FlueGasTripleOfVariates]]

    def estimated_output_temperatures: WithParams_15544[EstimatedOutputTemperatures]

    // EN13384 variables

    // TODO: P_Z, P_Zmax, P_Ze, P_Zemax, P_W, P_W_max, P_B
    def pressureRequirements_EN13384: WithParams_13384[VNelMcalcErr[PressureRequirements_13384]]
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

    given params_13384_to_15544: (p13384: Params_13384, diff: Params_15544_Minus_Params_13384) => Params_15544 = p13384
    given params_15544_to_13384: (p: Params_15544) => Params_13384 = p
    given params_15544_to_diff: (p: Params_15544) => Params_15544_Minus_Params_13384 = EmptyTuple
    
    given loadQty_from_Params_15544: (p: Params_15544) => LoadQty = p._2
    given pressReq_from_Params_15544: (p: Params_15544) => DraftCondition = p._1
    // export Params_13384.pressReq_from_Params_13384

    def airIntake_PipeResult     : WithParams_13384[VNelMcalcErr[PipeResult]]
    def combustionAir_PipeResult : WithParams_15544[VNelMcalcErr[PipeResult]]
    def firebox_PipeResult       : WithParams_15544[VNelMcalcErr[PipeResult]]
    def flue_PipeResult          : WithParams_15544[VNelMcalcErr[PipeResult]]
    def connector_PipeResult     : WithParams_15544[VNelMcalcErr[PipeResult]]
    def chimney_PipeResult       : WithParams_15544[VNelMcalcErr[PipeResult]]

    protected def pipesResult_15544_VNelS: WithParams_15544[PipesResult_15544_VNelString]

    // outputs
    val techSpecs: TechnicalSpecficiations
    def reference_temperatures: ReferenceTemperatures
    def outputs: WithParams_15544[Outputs]

    def validateFluePipeShape(): VNel[Unit]
    def validateVelocitiesInPipes(): WithParams_15544[VNel[Unit]]
    def validatePressureRequirements_EN15544(): WithParams_15544[VNelMcalcErr[Unit]]
    def validateChimneyWallTempIsAbove45DegreesCelsius(): WithParams_15544[VNelMcalcErr[Unit]]
    def validateEfficiencyIsAboveMinEfficiency(): WithParams_15544[VNelMcalcErr[Unit]]
    def validateCitedConstraints(): WithParams_15544[VNelMcalcErr[Unit]]
    def validateFireboxType(): WithParams_15544[ValidatedNel[FireboxError, Unit]]

    val runValidationAtParams: Params_15544
    def validateResults: VNel[Unit]

    def efficiencies_values: EfficienciesValues
    def emissions_and_efficiency_values: EmissionsAndEfficiencyValues
    def η_s: WithParams_15544[VNelMcalcErr[Percentage]]

end EN15544_V_2023_Application_Alg
