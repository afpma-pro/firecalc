/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.v7.PostFireboxInitialDirection
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.SectionDecrease
import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.SectionIncrease
import afpma.firecalc.units.coulombutils.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SectionGeometryChangeAutoInsertionSuite extends AnyFlatSpec with Matchers:

    import PipeShape.*

    private def setupBuilder =
        val builder = ChimneyPipe_Module.incremental
        builder.withInitialDirection(
            PostFireboxInitialDirection    (
                azimuth     = AzimuthDirection.Rear,
                inclination = InclinationDirection.Horizontal
            )
        )
        builder

    "13384 auto-insertion with Circle shapes" should "insert SectionDecrease for decreasing circle" in {
        val builder    = setupBuilder
        import builder.*
        val pipeA      = Circle(200.mm)
        val pipeB      = Circle(150.mm)
        val p          = builder.define(
            innerShape       (pipeA                         ),
            layer            (2.mm, WattsPerMeterKelvin(1.2)),
            roughness        (2.mm                          ),
            pipeLocation     (HeatedArea                    ),
            addSectionSlopped("s1", 1.meters                ),
            innerShape       (pipeB                         ),
            addSectionSlopped("s2", 1.meters                )
        )
        val result     = p.toFullDescr()
        result.isValid shouldBe true
        val elems      = result.toOption.get._2.elems
        elems.size shouldBe 3
        elems(0).name shouldBe "s1"
        elems(1).name shouldBe "section geometry change"
        val geomChange = elems(1).el
        geomChange shouldBe a[SectionDecrease]
        val decr       = geomChange.asInstanceOf[SectionDecrease]
        decr.from shouldBe Circle(200.mm)
        decr.to shouldBe Circle(150.mm)
        elems(2).name shouldBe "s2"
    }

    "13384 auto-insertion with Square shapes" should "use equivalent circle via dh for Square" in {
        val builder    = setupBuilder
        import builder.*
        val pipeA      = Square(200.mm)
        val pipeB      = Circle(150.mm)
        val p          = builder.define(
            innerShape       (pipeA                         ),
            layer            (2.mm, WattsPerMeterKelvin(1.2)),
            roughness        (2.mm                          ),
            pipeLocation     (HeatedArea                    ),
            addSectionSlopped("s1", 1.meters                ),
            innerShape       (pipeB                         ),
            addSectionSlopped("s2", 1.meters                )
        )
        val result     = p.toFullDescr()
        result.isValid shouldBe true
        val elems      = result.toOption.get._2.elems
        elems.size shouldBe 3
        elems(1).name shouldBe "section geometry change"
        val geomChange = elems(1).el
        geomChange shouldBe a[SectionDecrease]
        val decr       = geomChange.asInstanceOf[SectionDecrease]
        // Square(200mm).dh = 200mm = 20cm, but floating-point may produce slight imprecision
        val fromDiamCm = decr.from.diameter.to_cm.value
        val toDiamCm   = decr.to.diameter.to_cm.value
        fromDiamCm should be(20.0 +- 0.01)
        toDiamCm should be(15.0 +- 0.01)
    }

    "13384 auto-insertion with Rectangle shapes" should "use equivalent circle via dh for Rectangle" in {
        val builder    = setupBuilder
        import builder.*
        val pipeA      = Rectangle(180.mm, 90.mm)
        val pipeB      = Circle(150.mm)
        val p          = builder.define(
            innerShape       (pipeA                         ),
            layer            (2.mm, WattsPerMeterKelvin(1.2)),
            roughness        (2.mm                          ),
            pipeLocation     (HeatedArea                    ),
            addSectionSlopped("s1", 1.meters                ),
            innerShape       (pipeB                         ),
            addSectionSlopped("s2", 1.meters                )
        )
        val result     = p.toFullDescr()
        result.isValid shouldBe true
        val elems      = result.toOption.get._2.elems
        elems.size shouldBe 3
        elems(1).name shouldBe "section geometry change"
        val geomChange = elems(1).el
        geomChange shouldBe a[SectionIncrease]
        val incr       = geomChange.asInstanceOf[SectionIncrease]
        incr.from shouldBe Circle(120.mm)
        incr.to shouldBe Circle(150.mm)
    }

    "13384 auto-insertion with same dh but different shape" should "insert SectionGeometryChange (H1 fix: direct shape comparison)" in {
        // Square(200mm) and Circle(200mm) have the same dh (≈200mm) but different actual shapes
        // Before H1 fix: equivalent-circle comparison would NOT insert (BUG)
        // After H1 fix: direct shape comparison DOES insert (CORRECT)
        val builder    = setupBuilder
        import builder.*
        val pipeA      = Square(200.mm)
        val pipeB      = Circle(200.mm)
        val p          = builder.define(
            innerShape       (pipeA                         ),
            layer            (2.mm, WattsPerMeterKelvin(1.2)),
            roughness        (2.mm                          ),
            pipeLocation     (HeatedArea                    ),
            addSectionSlopped("s1", 1.meters                ),
            innerShape       (pipeB                         ),
            addSectionSlopped("s2", 1.meters                )
        )
        val result     = p.toFullDescr()
        result.isValid shouldBe true
        val elems      = result.toOption.get._2.elems
        elems.size shouldBe 3
        elems(1).name shouldBe "section geometry change"
        val geomChange = elems(1).el
        geomChange shouldBe a[SectionDecrease]
        val decr       = geomChange.asInstanceOf[SectionDecrease]
        // Both have dh ≈ 200mm, so equivalent circles are Circle(200mm)
        val fromDiamCm = decr.from.diameter.to_cm.value
        val toDiamCm   = decr.to.diameter.to_cm.value
        fromDiamCm should be(20.0 +- 0.01)
        toDiamCm should be(20.0 +- 0.01)
    }

    "13384 auto-insertion with same shape" should "NOT insert SectionGeometryChange" in {
        // Same shape — no geometry change needed
        val builder = setupBuilder
        import builder.*
        val pipeA   = Circle(100.mm)
        val p       = builder.define(
            innerShape       (pipeA                         ),
            layer            (2.mm, WattsPerMeterKelvin(1.2)),
            roughness        (2.mm                          ),
            pipeLocation     (HeatedArea                    ),
            addSectionSlopped("s1", 1.meters                ),
            innerShape       (pipeA                         ),
            addSectionSlopped("s2", 1.meters                )
        )
        val result  = p.toFullDescr()
        result.isValid shouldBe true
        val elems   = result.toOption.get._2.elems
        elems.size shouldBe 2
        elems(0).name shouldBe "s1"
        elems(1).name shouldBe "s2"
    }
