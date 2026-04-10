/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.Inputs_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.en13384.Inputs_13384_WithThermalAirIntake
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType
import afpma.firecalc.engine.standard.InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType
import afpma.firecalc.engine.standard.VNelMcalcErr

import cats.data.ValidatedNel
import cats.syntax.all.*

/**
 * EN 13384 flow-only air-intake computation — logic owned by engine-13384-strict.
 *
 * Standalone trait: no dependency on v0_2024_10_core.
 * Leaf modules in engine-15544-* compose this with StoveProjectDescr_13384_Alg.
 */
trait EN13384_FlowOnlyAirIntake_Assembly extends HasTypeMembers_13384_WithFlowOnlyAirIntake:

    def typeOfAppliance            : TypeOfAppliance
    def fuelType                   : FuelType
    def flueGasCondition           : FlueGasCondition
    def localConditions            : LocalConditions
    def en13384NationalAcceptedData: NationalAcceptedData

    def airIntakePipe: ValidatedNel[IncrementalValidation_Error, AirIntakePipe_Module.PipeCanBe]
    def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe_Module.PipeCanBe]
    def chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe_Module.PipeCanBe]

    def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384] =
        (
            airIntakePipe,
            connectorPipe,
            chimneyPipe
        ).mapN: (_airIntake, _connector, _chimney) =>
            new Pipes_13384_WithFlowOnlyAirIntake:
                override val airIntake: FlowOnlyAirIntakePipe_13384 = _airIntake
                override val connector: ConnectorPipe               = _connector
                override val chimney  : ChimneyPipe                 = _chimney

    def inputsVNel: VNelMcalcErr[Inputs_13384] =
        val checkApplianceAndFuel: VNelMcalcErr[Unit] =
            (typeOfAppliance, fuelType) match
                case (TypeOfAppliance.Pellets, FuelType.Pellets                ) =>
                    ().validNel // OK
                case (TypeOfAppliance.Pellets, ft @ FuelType.WoodLog30pHumidity) =>
                    InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType.invalidNel
                case (TypeOfAppliance.WoodLogs, FuelType.Pellets               ) =>
                    InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType.invalidNel
                case (TypeOfAppliance.WoodLogs, _                              ) =>
                    ().validNel // OK
        (
            checkApplianceAndFuel,
            en13384_pipesVNel
        ).mapN: (_, pipes) =>
            Inputs_13384_WithFlowOnlyAirIntake(
                pipes,
                en13384NationalAcceptedData,
                fuelType,
                localConditions,
                flueGasCondition
            )

    lazy val en13384_appl: VNelMcalcErr[EN13384_1_A1_2019_Common_Application] = inputsVNel.map: i =>
        val f = new EN13384_1_A1_2019_Formulas
        EN13384_WithFlowOnlyAirIntake_Application.make(f, i)

/**
 * EN 13384 thermal air-intake computation — logic owned by engine-13384-strict.
 *
 * Standalone trait: no dependency on v0_2024_10_core.
 * Leaf modules in engine-15544-* compose this with StoveProjectDescr_13384_Alg.
 */
trait EN13384_ThermalAirIntake_Assembly extends HasTypeMembers_13384_WithThermalAirIntake:

    def typeOfAppliance            : TypeOfAppliance
    def fuelType                   : FuelType
    def flueGasCondition           : FlueGasCondition
    def localConditions            : LocalConditions
    def en13384NationalAcceptedData: NationalAcceptedData

    def airIntakePipe: ValidatedNel[IncrementalValidation_Error, AirIntakePipe_Module.PipeCanBe]
    def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe_Module.PipeCanBe]
    def chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe_Module.PipeCanBe]

    def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384] =
        (
            airIntakePipe,
            connectorPipe,
            chimneyPipe
        ).mapN: (_airIntake, _connector, _chimney) =>
            new Pipes_13384_WithThermalAirIntake:
                override val airIntake: ThermalAirIntakePipe_13384 = _airIntake
                override val connector: ConnectorPipe              = _connector
                override val chimney  : ChimneyPipe                = _chimney

    def en13384_inputsVNel: VNelMcalcErr[Inputs_13384] =
        val checkApplianceAndFuel: VNelMcalcErr[Unit] =
            (typeOfAppliance, fuelType) match
                case (TypeOfAppliance.Pellets, FuelType.Pellets                ) =>
                    ().validNel // OK
                case (TypeOfAppliance.Pellets, ft @ FuelType.WoodLog30pHumidity) =>
                    InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType.invalidNel
                case (TypeOfAppliance.WoodLogs, FuelType.Pellets               ) =>
                    InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType.invalidNel
                case (TypeOfAppliance.WoodLogs, _                              ) =>
                    ().validNel // OK
        (
            checkApplianceAndFuel,
            en13384_pipesVNel
        ).mapN: (_, pipes) =>
            Inputs_13384_WithThermalAirIntake(
                pipes,
                en13384NationalAcceptedData,
                fuelType,
                localConditions,
                flueGasCondition
            )

    lazy val en13384_appl: VNelMcalcErr[EN13384_1_A1_2019_Common_Application] = en13384_inputsVNel.map: i =>
        val f = new EN13384_1_A1_2019_Formulas
        EN13384_WithThermalAirIntake_Application.make(f, i)
