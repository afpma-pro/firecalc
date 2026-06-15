/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.generators.base.*
import afpma.firecalc.dto.v3.Material_15544_V2
import afpma.firecalc.dto.v7.FlowOnlyPipeDescr_15544_V4
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4.*
import afpma.firecalc.dto.v7.FlowOnlyChannelTopologyOp_15544_V4
import org.scalacheck.Gen

/**
 * Generators for V7 FlowOnlyPipeDescr_15544_V4.
 *
 * Key difference from V3: `SetNumberOfFlows` lives in
 * `FlowOnlyChannelTopologyOp_15544_V4`, not in `SetFlowOnlyPipeProp_15544_V4`.
 */
trait SetFlowOnlyPipeProp_15544_V4_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators:

    def genSetInnerShape_FlowOnly_15544_V4: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    def genSetRoughness_FlowOnly_15544_V4: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    def genSetMaterial_FlowOnly_15544_V4: Gen[SetMaterial] =
        Gen.oneOf(
            Gen.const(SetMaterial(Material_15544_V2.TuyauxEnChamotte())),
            Gen.const(SetMaterial(Material_15544_V2.BlocsDeChamotte() ))
        )

    // ── ChannelTopologyOp ───────────────────────────────────────────────

    def genSetNumberOfFlows_FlowOnly_15544_V4: Gen[FlowOnlyChannelTopologyOp_15544_V4.SetNumberOfFlows] =
        Gen.choose(1, 4).map(n => FlowOnlyChannelTopologyOp_15544_V4.SetNumberOfFlows(NbOfFlows(n)))

    // ── PipeTrackingOp: removed in V7 (SetInitialDirection, SetInitialPosition, SetFinalPosition) ──
    // These generators were removed because the corresponding descriptors no longer exist in V7.
    // Initial direction/position now come from wrapper types (FramedPostFireboxPipes, FramedAirIntakePipes).

    // ── Composite: any SetFlowOnlyPipeProp_15544_V4 ─────────────────────

    def genSetFlowOnlyPipeProp_15544_V4: Gen[SetFlowOnlyPipeProp_15544_V4] =
        Gen.oneOf(
            genSetInnerShape_FlowOnly_15544_V4,
            genSetRoughness_FlowOnly_15544_V4,
            genSetMaterial_FlowOnly_15544_V4
        )

    // ── Composite: realistic sequence for a pipe ────────────────────────

    def genFlowOnlyPipeDescr_15544_V4_Seq: Gen[Seq[FlowOnlyPipeDescr_15544_V4]] =
        for
            innerShape <- genSetInnerShape_FlowOnly_15544_V4
            material   <- genSetMaterial_FlowOnly_15544_V4
            roughness  <- genSetRoughness_FlowOnly_15544_V4
            maybeFlows <- Gen.option(genSetNumberOfFlows_FlowOnly_15544_V4)
        yield List[FlowOnlyPipeDescr_15544_V4](innerShape, material, roughness) ++ maybeFlows.toList

end SetFlowOnlyPipeProp_15544_V4_Generators
