/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.engine.impl.en13384.EN13384_FlowOnlyAirIntake_Assembly
import afpma.firecalc.engine.impl.en13384.EN13384_ThermalAirIntake_Assembly
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

/**
 * EN 13384 composition traits for strict mode.
 *
 * Computation logic is owned by [[EN13384_FlowOnlyAirIntake_Assembly]] and
 * [[EN13384_ThermalAirIntake_Assembly]] in engine-13384-strict.
 * These adapters only bridge v0_2024_10_core.StoveProjectDescr_13384_Alg
 * to those computation traits and fix the abstract type members.
 */
trait v0_2024_10_13384_strict_members extends v0_2024_10_core:

    trait StoveProjectDescr_13384_WithFlowOnlyAirIntake_Alg
        extends StoveProjectDescr_13384_Alg
        with EN13384_FlowOnlyAirIntake_Assembly:

        type ConnectorPipe = ConnectorPipe_Module.PipeCanBe
        type ChimneyPipe   = ChimneyPipe_Module.PipeCanBe

    trait StoveProjectDescr_13384_WithThermalAirIntake_Alg
        extends StoveProjectDescr_13384_Alg
        with EN13384_ThermalAirIntake_Assembly:

        type ConnectorPipe = ConnectorPipe_Module.PipeCanBe
        type ChimneyPipe   = ChimneyPipe_Module.PipeCanBe

    trait WithPipeChain_13384:
        self: StoveProjectDescr_13384_Alg =>

        type ConnectorPipe = ConnectorPipe_Module.PipeCanBe
        type ChimneyPipe   = ChimneyPipe_Module.PipeCanBe

        def connectorPipeDescr: Seq[ConnectorPipe_Module.incremental.IncrDescr]
        def chimneyPipeDescr  : Seq[ChimneyPipe_Module.incremental.IncrDescr]

        private lazy val pipeChain = PipeChain_13384.build(
            PipeChain_13384.Descriptors(connectorPipeDescr, chimneyPipeDescr)
        )

        override lazy val connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe] =
            pipeChain.connectorPipe
        override lazy val chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]   = pipeChain.chimneyPipe
