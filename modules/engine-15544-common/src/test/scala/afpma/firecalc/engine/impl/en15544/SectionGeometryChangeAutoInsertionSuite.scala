/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.SectionGeometryChange
import afpma.firecalc.units.coulombutils.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SectionGeometryChangeAutoInsertionSuite extends AnyFlatSpec with Matchers:

    import PipeShape.*

    "15544 auto-insertion" should "insert SectionGeometryChange with correct from/to shapes" in {
        // Test 14: Auto-insertion in 15544 — SectionGeometryChange with correct from/to
        val builder = FluePipe_Module_15544.incremental
        builder.withInitialDirection(
            PipeInitialDirection    (
                azimuth     = AzimuthDirection.Rear,
                inclination = InclinationDirection.Horizontal
            )
        )
        val pipeA = Circle(100.mm)
        val pipeB      = Circle(150.mm)
        val p          = builder.define(
            builder.innerShape       (pipeA         ),
            builder.roughness        (2.mm          ),
            builder.addSectionSlopped("s1", 1.meters),
            builder.innerShape       (pipeB         ),
            builder.addSectionSlopped("s2", 1.meters)
        )
        val result     = p.toFullDescr()
        result.isValid shouldBe true
        val elems      = result.toOption.get._2.elems
        // Should have 3 elements: s1, SectionGeometryChange, s2
        elems.size shouldBe 3
        elems(0).name shouldBe "s1"
        elems(1).name shouldBe "section geometry change"
        val geomChange = elems(1).el.asInstanceOf[SectionGeometryChange]
        geomChange.from shouldBe pipeA
        geomChange.to shouldBe pipeB
        elems(2).name shouldBe "s2"
    }

    // ── prevent-auto (dev-only DSL escape hatch) ────────────────────────────
    // See `SetInnerShapePreventSectionGeometryChangeAuto` / `IsBackendForbidden`.
    // preventAuto = true suppresses the automatic SectionGeometryChange element only;
    // area-conservation validation still runs.

    "15544 prevent-auto with different shapes" should "NOT insert SectionGeometryChange (dev-only escape hatch)" in {
        val builder = FluePipe_Module_15544.incremental
        builder.withInitialDirection(
            PipeInitialDirection    (
                azimuth     = AzimuthDirection.Rear,
                inclination = InclinationDirection.Horizontal
            )
        )
        val pipeA = Circle(100.mm)
        val pipeB  = Circle(100.mm) // same shape → trivially area-conserving
        val p      = builder.define(
            builder.innerShape       (pipeA                                         ),
            builder.roughness        (2.mm                                          ),
            builder.addSectionSlopped("s1", 1.meters                                ),
            builder.innerShape       (pipeB, preventAutoSectionGeometryChange = true),
            builder.addSectionSlopped("s2", 1.meters                                )
        )
        val result = p.toFullDescr()
        result.isValid shouldBe true
        val elems  = result.toOption.get._2.elems
        // No auto SectionGeometryChange inserted: just s1 and s2.
        elems.size shouldBe 2
        elems.exists(_.name == "section geometry change") shouldBe false
        elems(0).name shouldBe "s1"
        elems(1).name shouldBe "s2"
    }

    "15544 prevent-auto=false collapses to plain SetInnerShape" should "still auto-insert on a real shape change (DSL collapse guard)" in {
        // Regression guard: innerShape(shape, preventAutoSectionGeometryChange = false)
        // must behave exactly like plain innerShape(shape) — i.e. the auto element
        // IS inserted on a real shape change. Proves the DSL collapse (Q7a).
        val builder = FluePipe_Module_15544.incremental
        builder.withInitialDirection(
            PipeInitialDirection    (
                azimuth     = AzimuthDirection.Rear,
                inclination = InclinationDirection.Horizontal
            )
        )
        val pipeA = Circle(100.mm)
        val pipeB  = Circle(150.mm)
        val p      = builder.define(
            builder.innerShape       (pipeA                                          ),
            builder.roughness        (2.mm                                           ),
            builder.addSectionSlopped("s1", 1.meters                                 ),
            builder.innerShape       (pipeB, preventAutoSectionGeometryChange = false),
            builder.addSectionSlopped("s2", 1.meters                                 )
        )
        val result = p.toFullDescr()
        result.isValid shouldBe true
        val elems  = result.toOption.get._2.elems
        elems.size shouldBe 3
        elems(1).name shouldBe "section geometry change"
        elems(1).el.asInstanceOf[SectionGeometryChange].from shouldBe pipeA
        elems(1).el.asInstanceOf[SectionGeometryChange].to shouldBe pipeB
    }
