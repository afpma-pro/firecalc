/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}

import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.geometry.{PipeFrame, Vec3}
import afpma.firecalc.engine.standard.ForbiddenAddElementAtStart
import afpma.firecalc.engine.standard.ForbiddenAddElementAtEnd
import afpma.firecalc.engine.standard.GeometryWithoutInitialDirection

import cats.data.Validated.{Valid, Invalid}

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Smoke/regression tests for FlowOnlyIncrementalBuilderCommon using the EN 15544 builder.
 *
 * Mirrors a subset of the 13384 regression tests to guard against hook drift
 * (isDirectionChange/addElementHasAbsDir diverging between standards).
 *
 * Uses CombustionAirPipe_Module_15544 as the concrete builder.
 */
class FlowOnlyIncrementalBuilderCommon_15544_RegressionTest extends AnyFreeSpec with Matchers {

    val builder = CombustionAirPipe_Module_15544.incremental
    import builder.*

    "FlowOnlyIncrementalBuilderCommon (EN 15544)" - {

        "direction change forbidden at start" in {
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
                    errors.toList.exists {
                        case ForbiddenAddElementAtStart(_, name) => name == "angle-at-start"
                        case _                                   => false
                    } shouldBe true
                case Valid(_) =>
                    fail("Expected ForbiddenAddElementAtStart")
        }

        "direction change forbidden at end" in {
            val result = builder.define(
                setInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSectionSlopped("s1", 1.meters),
                addSharpAngle_45deg(
                    "angle-at-end",
                    AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Custom(45.degrees))
                )
            ).toFullDescr()

            result match
                case Invalid(errors) =>
                    errors.toList.exists {
                        case ForbiddenAddElementAtEnd(_, name) => name == "angle-at-end"
                        case _                                 => false
                    } shouldBe true
                case Valid(_) =>
                    fail("Expected ForbiddenAddElementAtEnd")
        }

        "geometry without initial direction → GeometryWithoutInitialDirection" in {
            val result = builder.define(
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSectionSlopped("s-no-dir", 1.meters)
            ).toFullDescr()

            result match
                case Invalid(errors) =>
                    errors.toList.exists {
                        case _: GeometryWithoutInitialDirection => true
                        case _                                 => false
                    } shouldBe true
                case Valid(_) =>
                    fail("Expected GeometryWithoutInitialDirection")
        }

        "external frame applied when pipe has no SetInitialDirection" in {
            val externalDir   = Vec3.fromAzimuthElevation(0.0, 90.0) // Rear, Up
            val externalFrame = PipeFrame.initial(externalDir)

            val pipeDescr = builder.define(
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSectionSlopped("s-ext", 2.meters)
            )

            val result = pipeDescr.toFullDescrWithExternalInitialFrame(Some(externalFrame))
            result.isValid shouldBe true

            val (_, fd, finalFrame) = result.toOption.get
            finalFrame shouldBe defined
            finalFrame.get.direction shouldBe externalDir
            fd.elems.size shouldBe 1
        }

        "external frame ignored when pipe defines its own direction" in {
            val externalFrame = PipeFrame.initial(
                Vec3.fromAzimuthElevation(90.0, 0.0) // Right, Horizontal
            )
            val pipeOwnDir = Vec3.fromAzimuthElevation(
                AzimuthDirection.toDegrees(AzimuthDirection.Rear),
                InclinationDirection.toDegrees(InclinationDirection.Up)
            )

            val pipeDescr = builder.define(
                setInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
                innerShape(circle(100.mm)),
                roughness(2.mm),
                addSectionSlopped("s1", 2.meters)
            )

            val withExt    = pipeDescr.toFullDescrWithExternalInitialFrame(Some(externalFrame))
            val withoutExt = pipeDescr.toFullDescrWithExternalInitialFrame(None)

            withExt.isValid shouldBe true
            withoutExt.isValid shouldBe true

            val (_, fdWith, frameWith)       = withExt.toOption.get
            val (_, fdWithout, frameWithout) = withoutExt.toOption.get

            // Same result regardless of external frame
            frameWith shouldBe frameWithout
            frameWith shouldBe defined
            frameWith.get.direction shouldBe pipeOwnDir
            fdWith shouldBe fdWithout
        }
    }
}
