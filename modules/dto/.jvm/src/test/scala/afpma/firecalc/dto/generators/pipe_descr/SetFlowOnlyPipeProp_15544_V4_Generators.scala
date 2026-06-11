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
import afpma.firecalc.dto.v7.FlowOnlyPipeTrackingOp_15544_V4

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

    // ── PipeTrackingOp (deprecated in V7) ───────────────────────────────

    def genSetInitialDirection_FlowOnly_15544_V4: Gen[FlowOnlyPipeTrackingOp_15544_V4.SetInitialDirection] =
        for
            azimuth     <- Gen.oneOf(
                afpma.firecalc.dto.v4.AzimuthDirection.Front,
                afpma.firecalc.dto.v4.AzimuthDirection.Right,
                afpma.firecalc.dto.v4.AzimuthDirection.Rear,
                afpma.firecalc.dto.v4.AzimuthDirection.Left
            )
            inclination <- Gen.oneOf(
                afpma.firecalc.dto.v4.InclinationDirection.Up,
                afpma.firecalc.dto.v4.InclinationDirection.Down,
                afpma.firecalc.dto.v4.InclinationDirection.Horizontal
            )
        yield FlowOnlyPipeTrackingOp_15544_V4.SetInitialDirection(azimuth, inclination)

    def genSetInitialPosition_FlowOnly_15544_V4: Gen[FlowOnlyPipeTrackingOp_15544_V4.SetInitialPosition] =
        for
            x <- Gen.choose(-10.0, 10.0).map(_.meters)
            y <- Gen.choose(-10.0, 10.0).map(_.meters)
            z <- Gen.choose(-10.0, 10.0).map(_.meters)
        yield FlowOnlyPipeTrackingOp_15544_V4.SetInitialPosition(x, y, z)

    def genSetFinalPosition_FlowOnly_15544_V4: Gen[FlowOnlyPipeTrackingOp_15544_V4.SetFinalPosition] =
        for
            x <- Gen.choose(-10.0, 10.0).map(_.meters)
            y <- Gen.choose(-10.0, 10.0).map(_.meters)
            z <- Gen.choose(-10.0, 10.0).map(_.meters)
        yield FlowOnlyPipeTrackingOp_15544_V4.SetFinalPosition(x, y, z)

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
