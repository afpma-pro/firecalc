/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeShape.Circle
import afpma.firecalc.dto.v4.endsWithSingularFlowResistance
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.ChimneySlot
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.ConnectorSlot

import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544
import afpma.firecalc.engine.dev_fixtures.en15544.v20241001.EmptyHeadRegionFixture_15544
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.Vec3

import coulomb.policy.standard.given

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Regression guard for the chimney end-cap render path.
 *
 *   - `endsWithSingularFlowResistance` (DTO marker) decides *whether* to render.
 *   - `ChimneyPipe_Module.lastInnerShape` (typeclass-driven, exhaustive via
 *     `HasInnerShapeAtPos[PipeElDescr]` declared `compiletime.deferred`) decides
 *     *what diameter* to render at. Crucially honours mid-pipe
 *     `IsSectionGeometryChange` elements — a hand-rolled DTO fold did not.
 *
 * Tests use `PipeFrame.initial(Vec3.Up)` as a sentinel external frame *only here*
 * because shape extraction is geometrically frame-independent — the test
 * isolates the shape concern. At runtime the real upstream connector frame is
 * plumbed through.
 */
class ChimneyEndCapDetectionSuite extends AnyFlatSpec with Matchers:

    private val sentinelFrame = Some(PipeFrame.initial(Vec3.Up))

    // ── DTO marker tests ─────────────────────────────────────────────

    private val slots = EmptyHeadRegionFixture_15544.postFireboxPipeSlots

    private val chimneyDescr: Seq[ThermalPipeDescr_13384_V3] =
        slots
            .collectFirst { case ChimneySlot(d) => d }
            .getOrElse(fail("EmptyHeadRegionFixture_15544 has no ChimneySlot"))

    "endsWithSingularFlowResistance" should
        "return true for the EmptyHeadRegionFixture_15544 chimney (terminates in addFlowResistance)" in {
            chimneyDescr.endsWithSingularFlowResistance.shouldBe(true)
        }

    it should "return false for the EmptyHeadRegionFixture_15544 connector slot (terminates in addSectionVertical)" in {
        val connectorDescr = slots
            .collectFirst { case ConnectorSlot(d) => d }
            .getOrElse(fail("EmptyHeadRegionFixture_15544 has no ConnectorSlot"))
        connectorDescr.endsWithSingularFlowResistance.shouldBe(false)
    }

    "ExampleProject_15544.conduit_fumees_descr" should
        "carry the IsSingularFlowResistance marker at its tail" in {
            ExampleProject_15544.conduit_fumees_descr.endsWithSingularFlowResistance.shouldBe(true)
        }

    // ── Engine `lastInnerShape` tests (typeclass-driven path) ────────

    it should "resolve to a terminal inner PipeShape via ChimneyPipe_Module.lastInnerShape" in {
        ChimneyPipe_Module
            .lastInnerShape(ExampleProject_15544.conduit_fumees_descr, sentinelFrame)
            .shouldBe(defined)
    }

    "ChimneyPipe_Module.lastInnerShape" should
        "honour mid-pipe AddSectionDecrease — DTO fold would have returned the upstream shape" in {
            import ChimneyPipe_Module as CHPM
            val syntheticChimney: Seq[ThermalPipeDescr_13384_V3] = Seq(
                CHPM.roughness         (1.mm                                           ),
                CHPM.innerShape(circle(200.mm)),
                CHPM.layer             (e = 26.mm, tr = SquareMeterKelvinPerWatt(0.260)),
                CHPM.pipeLocation      (PipeLocation.HeatedArea                        ),
                CHPM.addSectionVertical("wide_section", 1.m                            ),
                CHPM.addSectionDecrease("narrowing", 150.mm                            ),
                CHPM.addSectionVertical("narrow_section", 1.m                          ),
                CHPM.addFlowResistance ("end", 0.5.unitless                            )
            )

            val resultShape = CHPM
                .lastInnerShape(syntheticChimney, sentinelFrame)
                .getOrElse(fail("synthetic chimney with section change failed to resolve a terminal shape"))

            resultShape match
                case Circle(d) =>
                    d.toUnit[Millimeter].value.shouldBe(150.0 +- 0.5)
                case other     =>
                    fail(s"expected Circle(~150mm) after AddSectionDecrease, got $other")
        }

end ChimneyEndCapDetectionSuite
