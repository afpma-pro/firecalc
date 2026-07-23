/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.dto.common.PipeInitialDirection

import afpma.firecalc.engine.models.FluePipe_Module_15544
import FluePipe_Module_15544.*
import afpma.firecalc.engine.Slot0ContextFixture
import FluePipe_Module_15544.FullDescrResult.given
import FluePipe_Module_15544.toFullDescr

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Verifies that IdsMapping.reverseToIntMap correctly maps
 * PipeIdx (sequential "add" counter) back to descriptor index
 * (position in the full incremental descriptor, including property setters).
 */
class IdsMappingSuite extends AnyFlatSpec with Matchers with Slot0ContextFixture:

    // ============================================================================
    // Case 1: Simple descriptor — no auto-inserted elements
    // ============================================================================

    //   idx 0: SetRoughness         (property setter)
    //   idx 1: SetInnerShape        (property setter)
    //   idx 2: AddSectionHorizontal (add element → PipeIdx 0)
    //   idx 3: AddSharpeAngle       (add element → PipeIdx 1)
    //   idx 4: AddSectionVertical   (add element → PipeIdx 2)
    //   (initial direction set via withInitialDirection, not in descriptor)
    private val simpleDescr: Seq[FlowOnlyPipeDescr_15544] = Seq(
        SetFlowOnlyPipeProp_15544.SetRoughness              (3.0.mm            ),
        SetFlowOnlyPipeProp_15544.SetInnerShape(PipeShape.Rectangle(11.1.cm, 12.2.cm)),
        AddFlowOnlyPipeElement_15544.AddSectionHorizontal   ("section1", 50.cm ),
        AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
            "angle1",
            90.0.degrees,
            absDir = Some(AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up))
        ),
        AddFlowOnlyPipeElement_15544.AddSectionVertical     ("section2", 100.cm)
    )

    private lazy val simpleResult: FluePipe_Module_15544.FullDescrResult = {
        FluePipe_Module_15544.incremental.withInitialDirection(
            PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        )
        FluePipe_Module_15544.incremental
            .define(simpleDescr*)
            .toFullDescr
    }

    "simple descriptor reverseToIntMap" should "map PipeIdx back to descriptor index" in {
        val idsMapping = simpleResult.extractIdsMapping.toOption.get
        val reverseMap = idsMapping.reverseToIntMap

        reverseMap.size shouldBe 3
        reverseMap(0) shouldBe 2 // AddSectionHorizontal
        reverseMap(1) shouldBe 3 // AddSharpeAngle
        reverseMap(2) shouldBe 4 // AddSectionVertical
    }

    "simple descriptor fullDescr" should "have same element count as IdsMapping" in {
        val idsMapping = simpleResult.extractIdsMapping.toOption.get
        val fullDescr  = simpleResult.extractPipe.toOption.get

        fullDescr.elems.size shouldBe idsMapping.getAll.size
    }

    // ============================================================================
    // Case 2: Descriptor with geometry change — triggers auto-inserted SectionGeometryChange
    // ============================================================================

    //   idx 0: SetRoughness               (property setter)
    //   idx 1: SetInnerShape Rectangle    (property setter)
    //   idx 2: AddSectionHorizontal       (add element)
    //   idx 3: AddSharpeAngle             (add element)
    //   idx 4: SetInnerShape Circle       (property setter — changes geometry!)
    //   idx 5: AddSectionVertical         (add element — builder auto-inserts SectionGeometryChange before this)
    //   (initial direction set via withInitialDirection, not in descriptor)
    //
    // Expected PipeFullDescr.elements:
    //   PipeIdx 0: StraightSection (from AddSectionHorizontal at idx 2)
    //   PipeIdx 1: DirectionChange (from AddSharpeAngle at idx 3)
    //   PipeIdx 2: SectionGeometryChange (AUTO-INSERTED — no IdsMapping entry!)
    //   PipeIdx 3: StraightSection (from AddSectionVertical at idx 5)
    private val geomChangeDescr: Seq[FlowOnlyPipeDescr_15544] = Seq(
        SetFlowOnlyPipeProp_15544.SetRoughness              (3.0.mm            ),
        SetFlowOnlyPipeProp_15544.SetInnerShape(PipeShape.Rectangle(11.1.cm, 12.2.cm)),
        AddFlowOnlyPipeElement_15544.AddSectionHorizontal   ("section1", 50.cm ),
        AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
            "angle1",
            90.0.degrees,
            absDir = Some(AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up))
        ),
        SetFlowOnlyPipeProp_15544.SetInnerShape(PipeShape.Circle(15.0.cm)            ), // geometry change!
        AddFlowOnlyPipeElement_15544.AddSectionVertical     ("section2", 100.cm)
    )

    private lazy val geomChangeResult: FluePipe_Module_15544.FullDescrResult = {
        FluePipe_Module_15544.incremental.withInitialDirection(
            PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        )
        FluePipe_Module_15544.incremental
            .define(geomChangeDescr*)
            .toFullDescr
    }

    "geom-change descriptor" should "produce 4 full elements (3 user + 1 auto-inserted)" in {
        val fullDescr = geomChangeResult.extractPipe.toOption.get
        fullDescr.elems.size shouldBe 4
    }

    "geom-change IdsMapping" should "only have 3 entries (user-defined add elements)" in {
        val idsMapping = geomChangeResult.extractIdsMapping.toOption.get
        idsMapping.getAll.size shouldBe 3
    }

    "geom-change reverseToIntMap" should "map user-defined elements correctly" in {
        val idsMapping = geomChangeResult.extractIdsMapping.toOption.get
        val reverseMap = idsMapping.reverseToIntMap
        val fullDescr  = geomChangeResult.extractPipe.toOption.get

        // reverseMap should have 3 entries (user-defined)
        reverseMap.size shouldBe 3

        // The auto-inserted SectionGeometryChange at PipeIdx 2 should NOT be in the reverseMap
        reverseMap.contains(2) shouldBe false

        // User-defined elements:
        reverseMap(0) shouldBe 2 // AddSectionHorizontal at descriptor idx 2
        reverseMap(1) shouldBe 3 // AddSharpeAngle at descriptor idx 3
        reverseMap(3) shouldBe 5 // AddSectionVertical at descriptor idx 5 (PipeIdx 3, NOT 2!)

        // Verify PipeIdx values in fullDescr
        fullDescr.elems.map(_.idx.unwrap) shouldBe Vector(0, 1, 2, 3)

        // Verify element names
        fullDescr.elems.map(_.name) shouldBe Vector("section1", "angle1", "section geometry change", "section2")
    }

    "geom-change: auto-inserted element" should "be detectable by absence from reverseMap" in {
        val idsMapping = geomChangeResult.extractIdsMapping.toOption.get
        val reverseMap = idsMapping.reverseToIntMap
        val fullDescr  = geomChangeResult.extractPipe.toOption.get

        // For each element in fullDescr, check if it's in the reverseMap
        val autoInserted = fullDescr.elems.filter(el => !reverseMap.contains(el.idx.unwrap))
        autoInserted.size shouldBe 1
        autoInserted.head.name shouldBe "section geometry change"
    }
