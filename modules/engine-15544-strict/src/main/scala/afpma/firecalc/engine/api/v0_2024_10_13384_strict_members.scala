/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.all.ThermalPipeDescr_13384
import afpma.firecalc.engine.impl.en13384.EN13384_FlowOnlyAirIntake_Assembly
import afpma.firecalc.engine.impl.en13384.EN13384_ThermalAirIntake_Assembly
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

/**
 * EN 13384 composition adapters for strict mode — thin wiring only.
 *
 * Architectural invariant: EN 13384 computation logic lives in engine-13384-strict
 * ([[EN13384_FlowOnlyAirIntake_Assembly]], [[EN13384_ThermalAirIntake_Assembly]]).
 * These adapters only bridge v0_2024_10_core.StoveProjectDescr_13384_Alg to those
 * assembly traits and fix the abstract type members.  Do not add computation here.
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

        def connectorPipeDescr: Seq[ThermalPipeDescr_13384]
        def chimneyPipeDescr  : Seq[ThermalPipeDescr_13384]

        private lazy val pipeChain = PipeChain_13384.build(
            PipeChain_13384.Descriptors(connectorPipeDescr, chimneyPipeDescr)
        )

        override lazy val connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe] =
            pipeChain.connectorPipe
        override lazy val chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]   = pipeChain.chimneyPipe
