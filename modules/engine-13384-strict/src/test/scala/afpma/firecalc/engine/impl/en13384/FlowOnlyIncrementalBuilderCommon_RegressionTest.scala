/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}

import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_Module_13384
import afpma.firecalc.engine.models.geometry.{PipeFrame, Vec3}
import afpma.firecalc.engine.standard.ForbiddenAddElementAtStart
import afpma.firecalc.engine.standard.ForbiddenAddElementAtEnd
import afpma.firecalc.engine.standard.GeometryWithoutInitialDirection
import afpma.firecalc.engine.standard.FinalDirWithoutInitialDirection

import cats.data.Validated.{Valid, Invalid}

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Regression tests for FlowOnlyIncrementalBuilderCommon logic
 * that was centralized from the 13384 and 15544 builders.
 *
 * Uses FlowOnlyIncrementalBuilder_13384 (via FlowOnlyAirIntakePipe_Module_13384)
 * because 13384 has richer direction change types.
 *
 * Covers 6 scenarios:
 *   1. Direction change forbidden at start of pipe definition
 *   2. Direction change forbidden at end of pipe definition
 *   3. Geometry (AddElement) without initial direction → GeometryWithoutInitialDirection
 *   4. Geometry with absDir but no initial direction → still GeometryWithoutInitialDirection
 *      (FinalDirWithoutInitialDirection is unreachable: any absDir-bearing element IS an
 *       AddElement, so hasGeometry is true first)
 *   5. External initial frame ignored when pipe defines its own initial direction
 *   6. External initial frame applied when pipe does NOT define its own direction
 */
class FlowOnlyIncrementalBuilderCommon_RegressionTest extends AnyFreeSpec with Matchers {

    // Use the FlowOnly builder for AirIntakePipeT (same builder as FlowOnlyAirIntakePipe_Module_13384)
    val builder = FlowOnlyAirIntakePipe_Module_13384.incremental
    import builder.*

    "FlowOnlyIncrementalBuilderCommon regression" - {

        "1. direction change forbidden at start of pipe definition" in {
            // A pipe definition that starts with a direction change (no section before it)
            // should fail with ForbiddenAddElementAtStart
            val result = builder.define(
                setInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSharpAngle_45deg(
                    "angle-at-start",
                    AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Custom(45.degrees))
                )
            ).toFullDescr()

            result match
                case Invalid(errors) =>
                    val hasForbiddenAtStart = errors.toList.exists {
                        case ForbiddenAddElementAtStart(_, name) => name == "angle-at-start"
                        case _                                   => false
                    }
                    hasForbiddenAtStart shouldBe true
                case Valid(_)        =>
                    fail("Expected Invalid with ForbiddenAddElementAtStart, but got Valid")
        }

        "2. direction change forbidden at end of pipe definition" in {
            // A pipe definition that ends with a direction change (no section after it)
            // should fail with ForbiddenAddElementAtEnd
            val result = builder.define(
                setInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSectionSlopped("section-1", 1.meters),
                addSharpAngle_45deg(
                    "angle-at-end",
                    AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Custom(45.degrees))
                )
            ).toFullDescr()

            result match
                case Invalid(errors) =>
                    val hasForbiddenAtEnd = errors.toList.exists {
                        case ForbiddenAddElementAtEnd(_, name) => name == "angle-at-end"
                        case _                                 => false
                    }
                    hasForbiddenAtEnd shouldBe true
                case Valid(_)        =>
                    fail("Expected Invalid with ForbiddenAddElementAtEnd, but got Valid")
        }

        "3. geometry (AddElement) without initial direction → GeometryWithoutInitialDirection" in {
            // A pipe definition with a section but no SetInitialDirection should fail
            // with GeometryWithoutInitialDirection in postBuildValidation
            val result = builder.define(
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSectionSlopped("section-no-dir", 1.meters)
            ).toFullDescr()

            result match
                case Invalid(errors) =>
                    // Must be exactly GeometryWithoutInitialDirection
                    val hasGeomWithoutDir = errors.toList.exists {
                        case _: GeometryWithoutInitialDirection => true
                        case _                                 => false
                    }
                    hasGeomWithoutDir shouldBe true
                    // Must NOT contain FinalDirWithoutInitialDirection
                    val hasFinalDirErr = errors.toList.exists {
                        case _: FinalDirWithoutInitialDirection => true
                        case _                                 => false
                    }
                    hasFinalDirErr shouldBe false
                case Valid(_)        =>
                    fail("Expected Invalid with GeometryWithoutInitialDirection, but got Valid")
        }

        "4. geometry with absDir but no initial direction → GeometryWithoutInitialDirection (not FinalDirWithoutInitialDirection)" in {
            // A pipe definition with a direction change that carries an absDir
            // but no SetInitialDirection.
            //
            // IMPORTANT: In current production logic, FinalDirWithoutInitialDirection is
            // unreachable for FlowOnly builders because any AddElement carrying absDir
            // IS an AddElement, so postBuildValidation hits hasGeometry first.
            // This test locks down that the actual error is GeometryWithoutInitialDirection.
            val result = builder.define(
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSectionSlopped("section-before", 1.meters),
                addSharpAngle_45deg(
                    "angle-with-absdir",
                    AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Custom(45.degrees))
                ),
                addSectionSlopped("section-after", 1.meters)
            ).toFullDescr()

            result match
                case Invalid(errors) =>
                    // Assert exactly GeometryWithoutInitialDirection
                    val hasGeomWithoutDir = errors.toList.exists {
                        case _: GeometryWithoutInitialDirection => true
                        case _                                 => false
                    }
                    hasGeomWithoutDir shouldBe true
                    // FinalDirWithoutInitialDirection must NOT appear (dead branch)
                    val hasFinalDirErr = errors.toList.exists {
                        case _: FinalDirWithoutInitialDirection => true
                        case _                                 => false
                    }
                    hasFinalDirErr shouldBe false
                case Valid(_)        =>
                    fail("Expected Invalid with GeometryWithoutInitialDirection, but got Valid")
        }

        "5. external initial frame ignored when pipe defines its own initial direction" in {
            // When a pipe defines SetInitialDirection, the external frame should be ignored.
            // We verify by building with an external frame pointing Right-Horizontal
            // while the pipe itself sets direction to Rear-Up.
            val externalFrame = PipeFrame.initial(
                Vec3.fromAzimuthElevation(90.0, 0.0) // Right, Horizontal
            )

            val pipeOwnDir_azimuth     = AzimuthDirection.Rear
            val pipeOwnDir_inclination = InclinationDirection.Up

            // Expected direction: Rear=0° azimuth, Up=90° elevation → Vec3(0, 1, 0).snap → (0,0,1) after normalized
            // Actually: azimuth=0° elevation=90° → x=sin(0)*cos(90)=0, y=cos(0)*cos(90)=0, z=sin(90)=1 → Vec3(0,0,1)
            val expectedDir = Vec3.fromAzimuthElevation(
                AzimuthDirection.toDegrees(pipeOwnDir_azimuth),
                InclinationDirection.toDegrees(pipeOwnDir_inclination)
            )

            val pipeDescr = builder.define(
                setInitialDirection(pipeOwnDir_azimuth, pipeOwnDir_inclination),
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSectionSlopped("section-1", 2.meters)
            )

            // Build WITH external frame
            val resultWithExternal = pipeDescr.toFullDescrWithExternalInitialFrame(Some(externalFrame))

            // Build WITHOUT external frame
            val resultWithoutExternal = pipeDescr.toFullDescrWithExternalInitialFrame(None)

            // Both should succeed
            resultWithExternal.isValid shouldBe true
            resultWithoutExternal.isValid shouldBe true

            // Extract the results
            val (_, fdWithExt, frameWithExt)       = resultWithExternal.toOption.get
            val (_, fdWithoutExt, frameWithoutExt) = resultWithoutExternal.toOption.get

            // Final frame must be defined (direction tracking was active)
            frameWithExt shouldBe defined
            frameWithoutExt shouldBe defined

            // The final frame should be the same regardless of external frame,
            // because the pipe's own SetInitialDirection takes precedence.
            frameWithExt shouldBe frameWithoutExt

            // The direction in the frame should match the pipe's own direction, not the external one
            frameWithExt.get.direction shouldBe expectedDir

            // The pipe descriptions should be fully identical
            fdWithExt shouldBe fdWithoutExt
        }

        "6. external initial frame applied when pipe does NOT define its own direction" in {
            // When a pipe does NOT use SetInitialDirection, an external frame should be
            // applied as the initial/current frame. The build should succeed and the
            // final frame should reflect the external direction.
            val externalDir   = Vec3.fromAzimuthElevation(90.0, 0.0) // Right, Horizontal
            val externalFrame = PipeFrame.initial(externalDir)

            val pipeDescr = builder.define(
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSectionSlopped("section-ext", 2.meters)
            )

            // Without external frame: should fail (no initial direction)
            val resultWithout = pipeDescr.toFullDescrWithExternalInitialFrame(None)
            resultWithout.isValid shouldBe false
            resultWithout match
                case Invalid(errors) =>
                    errors.toList.exists {
                        case _: GeometryWithoutInitialDirection => true
                        case _                                 => false
                    } shouldBe true
                case _ => fail("unreachable")

            // With external frame: should succeed
            val resultWith = pipeDescr.toFullDescrWithExternalInitialFrame(Some(externalFrame))
            resultWith.isValid shouldBe true

            val (_, fd, finalFrame) = resultWith.toOption.get

            // Frame should be defined and reflect the external direction
            finalFrame shouldBe defined
            finalFrame.get.direction shouldBe externalDir

            // Should have exactly 1 element (the straight section)
            fd.elems.size shouldBe 1
        }

    }
}
