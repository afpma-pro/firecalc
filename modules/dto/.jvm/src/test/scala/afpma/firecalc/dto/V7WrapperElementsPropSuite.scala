/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.PipeInitialFrame
import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7

import org.scalacheck.Gen
import org.scalactic.anyvals.PosInt
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/**
 * Property-based tests for V7 wrapper element types.
 *
 * Tests invariants on `PipeInitialDirection` and `Position3D`
 * that ensure geometric consistency: direction enums map to valid degree values,
 * position coordinates are valid lengths, and structural invariants hold.
 */
class V7WrapperElementsPropSuite extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks:

    override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
        PropertyCheckConfiguration(minSuccessful = PosInt(200))

    // ── Generators ──────────────────────────────────────────────────

    private val genAzimuth: Gen[AzimuthDirection] =
        Gen.oneOf(
            AzimuthDirection.namedCases ++
                Gen.choose(-180.0, 180.0).map(d => AzimuthDirection.Custom(d.degrees)).sample.toList
        )

    private val genInclination: Gen[InclinationDirection] =
        Gen.oneOf(
            InclinationDirection.namedCases ++
                Gen.choose(-90.0, 90.0).map(d => InclinationDirection.Custom(d.degrees)).sample.toList
        )

    private val genInitialDirection: Gen[PipeInitialDirection] =
        for
            az <- genAzimuth
            el <- genInclination
        yield PipeInitialDirection(az, el)

    private val genCoord: Gen[Length] =
        Gen.choose(-500.0, 500.0).map(_.cm)

    private val genInitialPosition: Gen[Position3D] =
        for
            x <- genCoord
            y <- genCoord
            z <- genCoord
        yield Position3D(x, y, z)

    private val genAnySlot: Gen[PostFireboxPipeDescrSlot_V7] =
        Gen.oneOf(
            PostFireboxPipeDescrSlot_V7.FlueSlot       (Seq.empty),
            PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(Seq.empty),
            PostFireboxPipeDescrSlot_V7.ConnectorSlot  (Seq.empty),
            PostFireboxPipeDescrSlot_V7.ChimneySlot    (Seq.empty),
            PostFireboxPipeDescrSlot_V7.NoFlueSlot
        )

    private val genPipes: Gen[FramedPostFireboxPipes] =
        for
            dir   <- genInitialDirection
            pos   <- genInitialPosition
            slots <- Gen.nonEmptyListOf(genAnySlot)
        yield FramedPostFireboxPipes(PipeInitialFrame(dir, pos), slots)

    // ── Direction degree-value properties ────────────────────────────

    "AzimuthDirection.toDegrees" - {

        "named cases return fixed values" in {
            AzimuthDirection.toDegrees(AzimuthDirection.Rear) shouldBe 0.0
            AzimuthDirection.toDegrees(AzimuthDirection.RearRight) shouldBe 45.0
            AzimuthDirection.toDegrees(AzimuthDirection.Right) shouldBe 90.0
            AzimuthDirection.toDegrees(AzimuthDirection.FrontRight) shouldBe 135.0
            AzimuthDirection.toDegrees(AzimuthDirection.Front) shouldBe 180.0
            AzimuthDirection.toDegrees(AzimuthDirection.FrontLeft) shouldBe -135.0
            AzimuthDirection.toDegrees(AzimuthDirection.Left) shouldBe -90.0
            AzimuthDirection.toDegrees(AzimuthDirection.RearLeft) shouldBe -45.0
        }

        "Custom case returns its angle value" in
            forAll(Gen.choose(-180.0, 180.0)) { deg =>
                AzimuthDirection.toDegrees(AzimuthDirection.Custom(deg.degrees)) shouldBe deg
            }

        "fromDegrees snaps named cases within tolerance" in
            forAll(Gen.oneOf(AzimuthDirection.namedCases)) { named =>
                val deg     = AzimuthDirection.toDegrees(named)
                val snapped = AzimuthDirection.fromDegrees(deg)
                snapped shouldBe named
            }

        "fromDegrees returns Custom for non-named angles" in
            forAll(
                Gen.choose(-179.0, 179.0)
                    .suchThat(d =>
                        !AzimuthDirection.namedCases.exists(n => math.abs(AzimuthDirection.toDegrees(n) - d) < 1.0)
                    )
            ) { deg =>
                val result = AzimuthDirection.fromDegrees(deg)
                result.isInstanceOf[AzimuthDirection.Custom] shouldBe true
            }

        "toDegrees always returns value in [-180, 180]" in
            forAll(genAzimuth) { az =>
                val deg = AzimuthDirection.toDegrees(az)
                deg should be >= -180.0
                deg should be <= 180.0
            }
    }

    "InclinationDirection.toDegrees" - {

        "named cases return fixed values" in {
            InclinationDirection.toDegrees(InclinationDirection.Up) shouldBe 90.0
            InclinationDirection.toDegrees(InclinationDirection.Down) shouldBe -90.0
            InclinationDirection.toDegrees(InclinationDirection.Horizontal) shouldBe 0.0
        }

        "Custom case returns its angle value" in
            forAll(Gen.choose(-90.0, 90.0)) { deg =>
                InclinationDirection.toDegrees(InclinationDirection.Custom(deg.degrees)) shouldBe deg
            }

        "fromDegrees snaps named cases within tolerance" in
            forAll(Gen.oneOf(InclinationDirection.namedCases)) { named =>
                val deg     = InclinationDirection.toDegrees(named)
                val snapped = InclinationDirection.fromDegrees(deg)
                snapped shouldBe named
            }

        "toDegrees always returns value in [-90, 90]" in
            forAll(genInclination) { el =>
                val deg = InclinationDirection.toDegrees(el)
                deg should be >= -90.0
                deg should be <= 90.0
            }
    }

    // ── Direction geometric properties ──────────────────────────────

    "PipeInitialDirection" - {

        "azimuth and inclination are never null" in
            forAll(genInitialDirection) { dir =>
                dir.azimuth should not be null
                dir.inclination should not be null
            }

        "named azimuth + horizontal inclination has zero elevation" in
            forAll(Gen.oneOf(AzimuthDirection.namedCases)) { az =>
                val dir = PipeInitialDirection(az, InclinationDirection.Horizontal)
                InclinationDirection.toDegrees(dir.inclination) shouldBe 0.0
            }

        "Up inclination always has 90° elevation" in
            forAll(Gen.oneOf(AzimuthDirection.namedCases)) { az =>
                val dir = PipeInitialDirection(az, InclinationDirection.Up)
                InclinationDirection.toDegrees(dir.inclination) shouldBe 90.0
            }

        "Down inclination always has -90° elevation" in
            forAll(Gen.oneOf(AzimuthDirection.namedCases)) { az =>
                val dir = PipeInitialDirection(az, InclinationDirection.Down)
                InclinationDirection.toDegrees(dir.inclination) shouldBe -90.0
            }

        "default direction is Right + Horizontal" in {
            PipeInitialDirection.default.azimuth shouldBe AzimuthDirection.Right
            PipeInitialDirection.default.inclination shouldBe InclinationDirection.Horizontal
        }

        "direction survives case equality check" in
            forAll(genInitialDirection) { dir =>
                val copy = PipeInitialDirection(dir.azimuth, dir.inclination)
                copy shouldBe dir
            }

        "direction survives copy with same values" in
            forAll(genInitialDirection) { dir =>
                val copy = dir.copy()
                copy shouldBe dir
            }
    }

    // ── Position properties ────────────────────────────────────────

    "Position3D" - {

        "coordinates are valid lengths with finite values" in
            forAll(genInitialPosition) { pos =>
                pos.x.value.isFinite shouldBe true
                pos.y.value.isFinite shouldBe true
                pos.z.value.isFinite shouldBe true
            }

        "zero position has all zero coordinates" in {
            val pos = Position3D(0.cm, 0.cm, 0.cm)
            pos.x.value shouldBe 0.0
            pos.y.value shouldBe 0.0
            pos.z.value shouldBe 0.0
        }

        "position survives case equality check" in
            forAll(genInitialPosition) { pos =>
                val copy = Position3D(pos.x, pos.y, pos.z)
                copy shouldBe pos
            }

        "position survives copy with same values" in
            forAll(genInitialPosition) { pos =>
                val copy = pos.copy()
                copy shouldBe pos
            }

        "negative coordinates are valid" in {
            val pos = Position3D(-10.cm, -20.cm, -30.cm)
            pos.x.value shouldBe -0.1
            pos.y.value shouldBe -0.2
            pos.z.value shouldBe -0.3
        }

        "position coordinates preserve magnitude (cm → m conversion)" in
            forAll(Gen.choose(-500.0, 500.0), Gen.choose(-500.0, 500.0), Gen.choose(-500.0, 500.0)) { (x, y, z) =>
                val pos = Position3D(x.cm, y.cm, z.cm)
                math.abs(pos.x.value - x / 100.0) should be < 1e-9
                math.abs(pos.y.value - y / 100.0) should be < 1e-9
                math.abs(pos.z.value - z / 100.0) should be < 1e-9
            }
    }

    // ── FramedPostFireboxPipes structural invariants ──────────────────────

    "FramedPostFireboxPipes" - {

        "always has non-null initial direction" in
            forAll(genPipes) { pipes =>
                pipes.initialFrame.direction should not be null
            }

        "always has non-null initial position" in
            forAll(genPipes) { pipes =>
                pipes.initialFrame.position should not be null
            }

        "always has at least one slot" in
            forAll(genPipes) { pipes =>
                pipes.slots.nonEmpty shouldBe true
            }

        "survives case equality check" in
            forAll(genPipes) { pipes =>
                val copy = FramedPostFireboxPipes(
                    PipeInitialFrame(pipes.initialFrame.direction, pipes.initialFrame.position),
                    pipes.slots
                )
                copy shouldBe pipes
            }

        "slot count is preserved through copy" in
            forAll(genPipes) { pipes =>
                val copy = FramedPostFireboxPipes(
                    PipeInitialFrame(pipes.initialFrame.direction, pipes.initialFrame.position),
                    pipes.slots
                )
                copy.slots.size shouldBe pipes.slots.size
            }
    }

    // ── Edge cases ──────────────────────────────────────────────────

    "Edge cases" - {

        "azimuth at ±180° both map to valid degrees" in {
            val az1 = AzimuthDirection.Custom(180.0.degrees)
            val az2 = AzimuthDirection.Custom(-180.0.degrees)
            AzimuthDirection.toDegrees(az1) shouldBe 180.0
            AzimuthDirection.toDegrees(az2) shouldBe -180.0
        }

        "azimuth at boundary values produces valid degrees" in
            forAll(Gen.oneOf(-180.0, -90.0, 0.0, 90.0, 180.0)) { azDeg =>
                val az = AzimuthDirection.Custom(azDeg.degrees)
                AzimuthDirection.toDegrees(az) shouldBe azDeg
            }

        "inclination at boundary values produces valid degrees" in
            forAll(Gen.oneOf(-90.0, 0.0, 90.0)) { elDeg =>
                val el = InclinationDirection.Custom(elDeg.degrees)
                InclinationDirection.toDegrees(el) shouldBe elDeg
            }

        "fromDegrees with tolerance snaps nearby values" in {
            val snapped = AzimuthDirection.fromDegrees(44.5)
            snapped shouldBe AzimuthDirection.RearRight
        }

        "fromDegrees with far value returns Custom" in {
            val result = AzimuthDirection.fromDegrees(30.0)
            result.isInstanceOf[AzimuthDirection.Custom] shouldBe true
        }

        "all named azimuth cases have unique degree values" in {
            val degrees = AzimuthDirection.namedCases.map(AzimuthDirection.toDegrees)
            degrees.toSet.size shouldBe AzimuthDirection.namedCases.size
        }

        "all named inclination cases have unique degree values" in {
            val degrees = InclinationDirection.namedCases.map(InclinationDirection.toDegrees)
            degrees.toSet.size shouldBe InclinationDirection.namedCases.size
        }
    }

end V7WrapperElementsPropSuite
