/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.generators.base.*
import afpma.firecalc.dto.v3.Material_13384_V2
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_13384_V4
import afpma.firecalc.dto.v7.FlowOnlyPipeDescr_13384_V4
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_13384_V4
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_13384_V4.*

import org.scalacheck.Gen

/**
 * Generators for V7 FlowOnlyPipeDescr_13384_V4.
 *
 * Key difference from V3: `SetNumberOfFlows` lives in
 * `FlowOnlyChannelTopologyOp_13384_V4`, not in `SetFlowOnlyPipeProp_13384_V4`.
 */
trait SetFlowOnlyPipeProp_13384_V4_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators:

    def genSetInnerShape_FlowOnly_13384_V4: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    def genSetRoughness_FlowOnly_13384_V4: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    def genSetMaterial_FlowOnly_13384_V4: Gen[SetMaterial] =
        Gen.oneOf    (
            Gen.const(SetMaterial(Material_13384_V2.WeldedSteel()    )),
            Gen.const(SetMaterial(Material_13384_V2.Glass()          )),
            Gen.const(SetMaterial(Material_13384_V2.Plastic()        )),
            Gen.const(SetMaterial(Material_13384_V2.Aluminium()      )),
            Gen.const(SetMaterial(Material_13384_V2.ClayFlueLiners() )),
            Gen.const(SetMaterial(Material_13384_V2.Bricks()         )),
            Gen.const(SetMaterial(Material_13384_V2.SolderedMetal()  )),
            Gen.const(SetMaterial(Material_13384_V2.Concrete()       )),
            Gen.const(SetMaterial(Material_13384_V2.Fibrociment()    )),
            Gen.const(SetMaterial(Material_13384_V2.Masonry()        )),
            Gen.const(SetMaterial(Material_13384_V2.CorrugatedMetal()))
        )

    // ── ChannelTopologyOp ───────────────────────────────────────────────
    // SetNumberOfFlows is backend-forbidden (IsBackendForbidden) — excluded from
    // random generation. The new Split/Merge types are AddElement, not TopologyOp.

    // ── AddElement: Split/Merge ─────────────────────────────────────────

    def genSplitSingleFlowIntoTwoFlowsWith90DegTurn_13384_V4
        : Gen[AddFlowOnlyPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn] =
        for
            name          <- genSectionName
            absDir        <- Gen.option(genAbsoluteDirection)
            newInnerShape <- genPipeShape
        yield AddFlowOnlyPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn         (
            name          = name,
            absDir        = absDir,
            newInnerShape = newInnerShape
        )

    def genMergeTwoFlowsIntoSingleWith90DegTurn_13384_V4
        : Gen[AddFlowOnlyPipeElement_13384_V4.MergeTwoFlowsIntoSingleWith90DegTurn] =
        for
            name          <- genSectionName
            absDir        <- Gen.option(genAbsoluteDirection)
            newInnerShape <- genPipeShape
        yield AddFlowOnlyPipeElement_13384_V4.MergeTwoFlowsIntoSingleWith90DegTurn         (
            name          = name,
            absDir        = absDir,
            newInnerShape = newInnerShape
        )

    // ── PipeTrackingOp: removed in V7 (SetInitialDirection, SetInitialPosition, SetFinalPosition) ──
    // These generators were removed because the corresponding descriptors no longer exist in V7.
    // Initial direction/position now come from wrapper types (FramedPostFireboxPipes, FramedAirIntakePipes).

    // ── Composite: any SetFlowOnlyPipeProp_13384_V4 ─────────────────────

    def genSetFlowOnlyPipeProp_13384_V4: Gen[SetFlowOnlyPipeProp_13384_V4] =
        Gen.oneOf(
            genSetInnerShape_FlowOnly_13384_V4,
            genSetRoughness_FlowOnly_13384_V4,
            genSetMaterial_FlowOnly_13384_V4
        )

    // ── Composite: realistic sequence for a pipe ────────────────────────

    def genFlowOnlyPipeDescr_13384_V4_Seq: Gen[Seq[FlowOnlyPipeDescr_13384_V4]] =
        for
            innerShape <- genSetInnerShape_FlowOnly_13384_V4
            material   <- genSetMaterial_FlowOnly_13384_V4
            roughness  <- genSetRoughness_FlowOnly_13384_V4
        yield List[FlowOnlyPipeDescr_13384_V4](innerShape, material, roughness)

    // ── Composite: split-merge sequence ─────────────────────────────────

    /**
     * Generator for a split-merge sequence modelled after
     * `test_separation_1_1.fcalc` reference project.
     *
     * Produces: [setup props, section, Split, dual-flow sections×N, Merge, post-merge section]
     * The Split bundles 90° turn + flow change (1→2) + shape change into one element.
     * The Merge bundles flow change (2→1) + shape change into one element.
     */
    def genSplitMergeFlowOnlyPipeDescr_13384_V4_Seq: Gen[Seq[FlowOnlyPipeDescr_13384_V4]] =
        for
            initShape  <- genPipeShape
            splitShape <- genPipeShape
            mergeShape <- genPipeShape
            nDual      <- Gen.choose(2, 4)
        yield
            val setup     = List[FlowOnlyPipeDescr_13384_V4](
                SetRoughness (0.003.meters),
                SetInnerShape(initShape   ),
                SetMaterial(Material_13384_V2.WeldedSteel())
            )
            val preSplit  = List(
                AddFlowOnlyPipeElement_13384_V4.AddSectionSlopped("PreSplit", 0.3.meters)
            )
            val split     = List(
                AddFlowOnlyPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn         (
                    name          = "Split",
                    absDir        = None,
                    newInnerShape = splitShape
                )
            )
            val dualFlow  = (1 to nDual).flatMap: i =>
                List(
                    AddFlowOnlyPipeElement_13384_V4.AddSectionSlopped     (s"Car$i", 1.0.meters        ),
                    AddFlowOnlyPipeElement_13384_V4.AddSharpeAngle_0_to_90(s"Angle$i", 90.degrees, None)
                )
            val merge     = List(
                AddFlowOnlyPipeElement_13384_V4.MergeTwoFlowsIntoSingleWith90DegTurn         (
                    name          = "Merge",
                    absDir        = None,
                    newInnerShape = mergeShape
                )
            )
            val postMerge = List(
                AddFlowOnlyPipeElement_13384_V4.AddSectionSlopped("PostMerge", 2.0.meters)
            )
            setup ++ preSplit ++ split ++ dualFlow ++ merge ++ postMerge

end SetFlowOnlyPipeProp_13384_V4_Generators
