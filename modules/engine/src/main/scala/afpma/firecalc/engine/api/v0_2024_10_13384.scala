/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas
import afpma.firecalc.engine.impl.en13384.EN13384_WithFlowOnlyAirIntake_Application
import afpma.firecalc.engine.impl.en13384.EN13384_WithThermalAirIntake_Application
import afpma.firecalc.engine.impl.en13384.HasTypeMembers_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.impl.en13384.HasTypeMembers_13384_WithThermalAirIntake
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_WithThermalAirIntake
import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType
import afpma.firecalc.engine.standard.InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.VNelMcalcErr

import cats.data.ValidatedNel
import cats.syntax.all.*

/** EN 13384 concrete wiring traits (split from v0_2024_10). */
trait v0_2024_10_13384_members extends v0_2024_10_core:

    trait StoveProjectDescr_13384_WithFlowOnlyAirIntake_Alg
        extends StoveProjectDescr_13384_Alg
        with HasTypeMembers_13384_WithFlowOnlyAirIntake:
        self =>

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
            // ensure type of appliance matches with fuel type
            val checkApplianceAndFuel: VNelMcalcErr[Unit] =
                (typeOfAppliance, fuelType) match
                    case (afpma.firecalc.dto.v4.TypeOfAppliance.Pellets, FuelType.Pellets                ) =>
                        ().validNel // OK
                    case (afpma.firecalc.dto.v4.TypeOfAppliance.Pellets, ft @ FuelType.WoodLog30pHumidity) =>
                        InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType.invalidNel
                    case (afpma.firecalc.dto.v4.TypeOfAppliance.WoodLogs, FuelType.Pellets               ) =>
                        InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType.invalidNel
                    case (afpma.firecalc.dto.v4.TypeOfAppliance.WoodLogs, _                              ) =>
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

        lazy val en13384_appl: VNelMcalcErr[EN13384_WithFlowOnlyAirIntake_Application] = inputsVNel.map: i =>
            val f = new EN13384_1_A1_2019_Formulas
            EN13384_WithFlowOnlyAirIntake_Application.make(f, i)

    trait StoveProjectDescr_13384_WithThermalAirIntake_Alg
        extends StoveProjectDescr_13384_Alg
        with HasTypeMembers_13384_WithThermalAirIntake:
        self =>

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
            // ensure type of appliance matches with fuel type
            val checkApplianceAndFuel: VNelMcalcErr[Unit] =
                (typeOfAppliance, fuelType) match
                    case (afpma.firecalc.dto.v4.TypeOfAppliance.Pellets, FuelType.Pellets                ) =>
                        ().validNel // OK
                    case (afpma.firecalc.dto.v4.TypeOfAppliance.Pellets, ft @ FuelType.WoodLog30pHumidity) =>
                        InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType.invalidNel
                    case (afpma.firecalc.dto.v4.TypeOfAppliance.WoodLogs, FuelType.Pellets               ) =>
                        InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType.invalidNel
                    case (afpma.firecalc.dto.v4.TypeOfAppliance.WoodLogs, _                              ) =>
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

        lazy val en13384_appl: VNelMcalcErr[EN13384_WithThermalAirIntake_Application] = en13384_inputsVNel.map: i =>
            val f = new EN13384_1_A1_2019_Formulas
            EN13384_WithThermalAirIntake_Application.make(f, i)

    trait WithPipeChain_13384:
        self: StoveProjectDescr_13384_Alg =>

        def connectorPipeDescr: Seq[ConnectorPipe_Module.incremental.IncrDescr]
        def chimneyPipeDescr  : Seq[ChimneyPipe_Module.incremental.IncrDescr]

        private lazy val pipeChain = PipeChain_13384.build(
            PipeChain_13384.Descriptors(connectorPipeDescr, chimneyPipeDescr)
        )

        override lazy val connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe] =
            pipeChain.connectorPipe
        override lazy val chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]   = pipeChain.chimneyPipe
