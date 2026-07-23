/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.engine.models.*
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.Slot0ContextFixture

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * FlowOnly 13384 auto-insertion + prevent-auto (dev-only escape hatch) tests.
 *
 * Mirrors `SectionGeometryChangeAutoInsertionSuite` (Thermal 13384) but uses the
 * FlowOnly 13384 builder (`FlowOnlyAirIntakePipe_Module_13384.incremental`).
 * See `SetInnerShapePreventSectionGeometryChangeAuto` / `IsBackendForbidden`.
 */
class FlowOnlySectionGeometryChangeAutoInsertionSuite_13384 extends AnyFlatSpec with Matchers with Slot0ContextFixture:

    import PipeShape.*

    private def setupBuilder =
        val builder = FlowOnlyAirIntakePipe_Module_13384.incremental
        builder.withInitialDirection(
            PipeInitialDirection    (
                azimuth     = AzimuthDirection.Rear,
                inclination = InclinationDirection.Horizontal
            )
        )
        builder

    "FlowOnly 13384 auto-insertion" should "insert SectionGeometryChange on a real shape change" in {
        val builder = setupBuilder
        val pipeA   = Circle(100.mm)
        val pipeB   = Circle(150.mm)
        val p       = builder.define(
            builder.innerShape       (pipeA         ),
            builder.roughness        (2.mm          ),
            builder.addSectionSlopped("s1", 1.meters),
            builder.innerShape       (pipeB         ),
            builder.addSectionSlopped("s2", 1.meters)
        )
        val result  = p.toFullDescr
        result.isValid shouldBe true
        val elems   = result.toOption.get._2.elems
        elems.size shouldBe 3
        elems(1).name shouldBe "section geometry change"
        elems(0).name shouldBe "s1"
        elems(2).name shouldBe "s2"
    }

    "FlowOnly 13384 auto-insertion with same shape" should "NOT insert SectionGeometryChange" in {
        val builder = setupBuilder
        val pipeA   = Circle(100.mm)
        val p       = builder.define(
            builder.innerShape       (pipeA         ),
            builder.roughness        (2.mm          ),
            builder.addSectionSlopped("s1", 1.meters),
            builder.innerShape       (pipeA         ),
            builder.addSectionSlopped("s2", 1.meters)
        )
        val result  = p.toFullDescr
        result.isValid shouldBe true
        val elems   = result.toOption.get._2.elems
        elems.size shouldBe 2
        elems.exists(_.name == "section geometry change") shouldBe false
    }

    "FlowOnly 13384 prevent-auto with different shapes" should "NOT insert SectionGeometryChange (dev-only escape hatch)" in {
        val builder = setupBuilder
        val pipeA   = Circle(100.mm)
        val pipeB   = Circle(100.mm) // same shape → trivially area-conserving
        val p       = builder.define(
            builder.innerShape       (pipeA                                         ),
            builder.roughness        (2.mm                                          ),
            builder.addSectionSlopped("s1", 1.meters                                ),
            builder.innerShape       (pipeB, preventAutoSectionGeometryChange = true),
            builder.addSectionSlopped("s2", 1.meters                                )
        )
        val result  = p.toFullDescr
        result.isValid shouldBe true
        val elems   = result.toOption.get._2.elems
        // No auto SectionGeometryChange inserted: just s1 and s2.
        elems.size shouldBe 2
        elems.exists(_.name == "section geometry change") shouldBe false
        elems(0).name shouldBe "s1"
        elems(1).name shouldBe "s2"
    }

    "FlowOnly 13384 prevent-auto=false collapses to plain SetInnerShape" should "still auto-insert on a real shape change (DSL collapse guard)" in {
        // Regression guard: innerShape(shape, preventAutoSectionGeometryChange = false)
        // must behave exactly like plain innerShape(shape) — i.e. the auto element
        // IS inserted on a real shape change. Proves the DSL collapse (Q7a).
        val builder = setupBuilder
        val pipeA   = Circle(100.mm)
        val pipeB   = Circle(150.mm)
        val p       = builder.define(
            builder.innerShape       (pipeA                                          ),
            builder.roughness        (2.mm                                           ),
            builder.addSectionSlopped("s1", 1.meters                                 ),
            builder.innerShape       (pipeB, preventAutoSectionGeometryChange = false),
            builder.addSectionSlopped("s2", 1.meters                                 )
        )
        val result  = p.toFullDescr
        result.isValid shouldBe true
        val elems   = result.toOption.get._2.elems
        elems.size shouldBe 3
        elems(1).name shouldBe "section geometry change"
    }
