/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.alg.en13384.HasTypeMembers_13384_Alg
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.MCalc_Error

import cats.data.ValidatedNel

// ─── Architectural invariant ─────────────────────────────────────────────────
// EN 13384 API ownership lives here in engine-13384-common.
//
// This trait defines the pure EN 13384 project description algebra
// (StoveProjectDescr_13384_Alg) without any EN 15544 dependency.
//
// EN 15544 modules (engine-15544-common) compose this trait via
//   trait v0_2024_10_core extends v0_2024_10_13384_core
// to bridge StoveProjectDescr_Alg (LocalizedAlg, project metadata) with
// the EN 13384 algebra, and to define StoveProjectDescr_15544_Alg on top.
//
// Leaf modules (engine-15544-strict, engine-15544-mce) mix in composition
// adapters that extend v0_2024_10_13384_core directly — they only need
// EN 13384 concepts, not the full EN 15544 API surface.
// ─────────────────────────────────────────────────────────────────────────────
trait v0_2024_10_13384_core:

    trait StoveProjectDescr_13384_Alg extends HasTypeMembers_13384_Alg:
        self =>

        /** Project metadata — needed for identification and country-level defaults. */
        def project: ProjectDescr

        def typeOfAppliance: TypeOfAppliance

        /** Abstract connector pipe type — fixed to concrete type in leaf modules. */
        type ConnectorPipe

        /** Abstract chimney pipe type — fixed to concrete type in leaf modules. */
        type ChimneyPipe

        def fuelType: FuelType

        def flueGasCondition: FlueGasCondition

        def localConditions: LocalConditions

        def en13384NationalAcceptedData: NationalAcceptedData =
            NationalAcceptedData.noOverride

        def airIntakePipe: ValidatedNel[IncrementalValidation_Error, AirIntakePipe_Module.PipeCanBe]

        def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]

        def chimneyPipe: ValidatedNel[IncrementalValidation_Error, ChimneyPipe]

        def en13384_pipesVNel: ValidatedNel[IncrementalValidation_Error, Pipes_13384]

        def heatingAppliance: ValidatedNel[MCalc_Error, HeatingAppliance]
