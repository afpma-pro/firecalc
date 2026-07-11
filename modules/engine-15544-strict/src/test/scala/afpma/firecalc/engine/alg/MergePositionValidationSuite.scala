/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import java.nio.charset.StandardCharsets

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.{
    SplitSingleFlowIntoTwoFlowsWith90DegTurn,
    MergeTwoFlowsIntoSingleWith90DegTurn
}
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.engine.models.FluePipe_Module_15544
import afpma.firecalc.engine.models.PipeBuildSeed
import afpma.firecalc.engine.models.geometry.{PipeFrame, PositionTracker, SplitMergeTwoHelper, SymmetryPlaneConfig}
import afpma.firecalc.engine.standard.MergeBranchTipNotAtMergePosition
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.units.Vec3

import io.circe.yaml.scalayaml.parser as yamlParser

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Verifies merge position validation against the symmetry plane Π.
 *
 * With VerticalIncoming(0°): Π = span(Up, Right) = XZ plane (Y=0).
 * The branch tip is orthogonally projected onto Π to get the merge position.
 *
 * Geometry (FireboxSplitFrame forces incoming = Up):
 *   Split at origin, branch goes Rear (+Y).
 *   Symmetry plane Π = span(Up, Right) = XZ plane (Y=0).
 *   Branch trace: (0,0,0) → 0.3m Rear → (0,0.3,0) → bend Down → 1m → (0,0.3,-1)
 *   → bend Front → 0.7m → (0,-0.4,-1) → Merge
 *   Branch tip (0, -0.20, -0.48) projected onto XZ → merge = (0, 0, -0.48).
 *
 * Failing case uses a longer Front section, taking the tip off Π in Y.
 */
class MergePositionValidationSuite extends AnyFreeSpec with Matchers {

    import PipeShape.*

    private def assertApproxVec3(actual: Vec3, expected: Vec3, label: String)(using
        org.scalactic.source.Position
    ): Unit =
        assertApprox(actual.x, expected.x, s"$label x")
        assertApprox(actual.y, expected.y, s"$label y")
        assertApprox(actual.z, expected.z, s"$label z")

    private def assertApprox(a: Double, b: Double, label: String)(using org.scalactic.source.Position): Unit =
        assert(math.abs(a - b) < 1e-9, s"$label: expected $b, got $a")

    val builder = FluePipe_Module_15544.incremental

    private def loadFcalcFile(resourcePath: String): FireCalcYAML =
        val stream  = getClass.getClassLoader.getResourceAsStream(resourcePath)
        if stream == null then fail(s"Resource not found: $resourcePath")
        val content = scala.io.Source.fromInputStream(stream)(using StandardCharsets.UTF_8).mkString
        val parsed  = yamlParser.parse(content)
        parsed match
            case scala.util.Left(err)      =>
                fail(s"Failed to parse YAML '$resourcePath': ${err.getMessage}")
            case scala.util.Right(decoded) =>
                val engineJson = decoded.hcursor.downField("engine_state").focus.getOrElse(decoded)
                FireCalcYAMLMigrations.decodeAndMigrateJson(engineJson) match
                    case Right(fc) => fc
                    case Left(err) =>
                        fail(s"Failed to decode/migrate '$resourcePath': $err")

    "merge position validation" - {

        "PositionTracker records splitPosition when provided (not branchOneStart)" in {
            // Firebox geometry (auto mode: firebox_depth = 40cm, firebox_height = 52cm)
            val fireboxDepthM  = 0.40
            val fireboxHeightM = 0.52
            val splitPosition  = Vec3(0, 0, fireboxHeightM)
            val offset         = fireboxDepthM / 2.0
            val branchOneStart = splitPosition + Vec3.Rear * offset

            // branchOne start = splitPosition + offset * Rear = (0, 0.20, 0.52)
            assertApproxVec3(branchOneStart, Vec3(0, 0.20, 0.52), "branchOne start position")

            // Elements (same as the valid merge test)
            val elems = Seq[FlowOnlyPipeDescr_15544](
                SplitSingleFlowIntoTwoFlowsWith90DegTurn            (
                    "Diviser en 2 flux avec virage à 90°",
                    newInnerShape = Circle(0.18.meters),
                    absDir        = Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Rear),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                SetFlowOnlyPipeProp_15544.SetRoughness              (0.003.meters                 ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("sortie de foyer", 0.3.meters),
                AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                    "vers descente",
                    90.0.degrees,
                    Some(AbsoluteDirection(azimuth = None, inclination = InclinationDirection.Down))
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("descente", 1.0.meters           ),
                AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                    "vers section horizontale",
                    90.0.degrees,
                    Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Front),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("section horizontale", 0.7.meters),
                MergeTwoFlowsIntoSingleWith90DegTurn                (
                    "Fusionner en 1 flux après virage à 90°",
                    newInnerShape = Circle(0.18.meters),
                    absDir        = Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Left),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped("section horizontale", 0.5.meters)
            )

            // Compute positions with branchOne start as startPoint (mimics UI → 3D viz)
            val upFrame   = PipeFrame.initial(Vec3.Up)
            val posResult = PositionTracker.computeFlowOnly15544(
                elems,
                PipeInitialDirection(InclinationDirection.Up),
                Some                (upFrame                ),
                branchOneStart,
                splitPosition = Some(splitPosition)
            )

            // Element index 2 = "sortie de foyer" (L=0.3m Rear)
            // Tip = branchOneStart + 0.30 * Rear = (0, 0.20, 0.52) + (0, 0.30, 0) = (0, 0.50, 0.52)
            val sortieSeg = posResult.segments.find(_.elementIndex == 2)
            sortieSeg.isDefined shouldBe true
            assertApproxVec3(sortieSeg.get.startPoint, branchOneStart, "sortie de foyer start" )
            assertApproxVec3(sortieSeg.get.endPoint, Vec3(0, 0.50, 0.52), "sortie de foyer tip")

            // Element index 6 = "section horizontale" (L=0.7m Front)
            // Path: (0,0.50,0.52) → 1.0m Down → (0,0.50,-0.48) → 0.7m Front → (0,-0.20,-0.48)
            val horizSeg = posResult.segments.find(_.elementIndex == 6)
            horizSeg.isDefined shouldBe true
            assertApproxVec3(horizSeg.get.startPoint, Vec3(0, 0.50, -0.48), "section horizontale start")
            assertApproxVec3(horizSeg.get.endPoint, Vec3(0, -0.20, -0.48), "section horizontale tip"   )

            // Merge position: project branch tip onto symmetry plane Π
            // With VerticalIncoming(AzimuthDirection.Right): symmetryPlaneNormal = Up × Right = Rear = (0, 1, 0)
            // So Π is the XZ plane passing through splitPosition (Y=0 at splitPosition.y).
            // Tip (0, -0.20, -0.48) projected onto Y=0 → (0, 0, -0.48)
            val helper   = SplitMergeTwoHelper
                .safe(
                    Vec3.Up,
                    Vec3.Rear,
                    splitPosition,
                    branchOneStart,
                    SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right)
                )
                .toOption
                .get
            val mergePos = helper.computeMergePosition(horizSeg.get.endPoint)
            assertApproxVec3(mergePos, Vec3(0, 0, -0.48), "merge position")

            // Verify SplitMergePosition.position equals the provided splitPosition,
            // not branchOneStart. The symmetry plane must pass through the actual
            // split point (firebox top center), not the displaced branch start.
            val splitMergePos = posResult.splitMergePositions.find(x => x.isSplit && x.elementIndex == 0)
            splitMergePos.isDefined shouldBe true
            assertApproxVec3(
                splitMergePos.get.position,
                splitPosition,
                "SplitMergePosition.position should be splitPosition (not branchOneStart)"
            )
        }

        "should accept merge when branch tip is on the split plane" in {
            val upFrame = PipeFrame.initial(Vec3.Up)
            val seed    = PipeBuildSeed(Some(upFrame), NbOfFlows(1), None, None)

            val p = builder.define(
                SplitSingleFlowIntoTwoFlowsWith90DegTurn            (
                    "Diviser en 2 flux avec virage à 90°",
                    newInnerShape = Circle(0.18.meters),
                    absDir        = Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Rear),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                SetFlowOnlyPipeProp_15544.SetRoughness              (0.003.meters                 ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("sortie de foyer", 0.3.meters),
                AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                    "vers descente",
                    90.0.degrees,
                    Some(AbsoluteDirection(azimuth = None, inclination = InclinationDirection.Down))
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("descente", 1.0.meters           ),
                AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                    "vers section horizontale",
                    90.0.degrees,
                    Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Front),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("section horizontale", 0.3.meters),
                MergeTwoFlowsIntoSingleWith90DegTurn                (
                    "Fusionner en 1 flux après virage à 90°",
                    newInnerShape = Circle(0.18.meters),
                    absDir        = Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Left),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped("section horizontale", 0.5.meters)
            )

            val result = p.toFullDescrWithSeed(seed)
            result.isValid shouldBe true
        }

        "should reject merge when branch tip moves off the split plane" in {
            val upFrame = PipeFrame.initial(Vec3.Up)
            val seed    = PipeBuildSeed(Some(upFrame), NbOfFlows(1), None, None)

            // Same geometry as above but with a longer Front section
            // that moves the branch tip off the symmetry plane Π (XZ plane, Y=0).
            // Front 0.6m from (0,0.3,-1.0) → tip at (0, -0.3, -1.0) → 300mm off Π.
            val p = builder.define(
                SplitSingleFlowIntoTwoFlowsWith90DegTurn            (
                    "Diviser en 2 flux avec virage à 90°",
                    newInnerShape = Circle(0.18.meters),
                    absDir        = Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Rear),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                SetFlowOnlyPipeProp_15544.SetRoughness              (0.003.meters                 ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("sortie de foyer", 0.3.meters),
                AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                    "vers descente",
                    90.0.degrees,
                    Some(AbsoluteDirection(azimuth = None, inclination = InclinationDirection.Down))
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("descente", 1.0.meters           ),
                AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                    "vers section horizontale",
                    90.0.degrees,
                    Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Front),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("section horizontale", 0.6.meters),
                MergeTwoFlowsIntoSingleWith90DegTurn                (
                    "Fusionner en 1 flux après virage à 90°",
                    newInnerShape = Circle(0.18.meters),
                    absDir        = Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Left),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped("section horizontale", 0.5.meters)
            )

            val result = p.toFullDescrWithSeed(seed)
            result.isValid shouldBe false

            val errors = result.toEither.left.toOption.get
            errors.exists(_.isInstanceOf[MergeBranchTipNotAtMergePosition]) shouldBe true
        }
    }

    "integration: merge-position-integration-test.fcalc" - {

        "loads and validates merge branch tip distance from .fcalc file" in {
            // merge-position-integration-test.fcalc: firebox 40x40x62cm, split Circle(180mm)
            // Branch: Rear 0.3m → Down 1m → Front 0.2m → Merge
            // Split at (0,0,0.52), branch tip at (0,0.3,-0.48) → 300mm off symmetry plane
            val yaml      = loadFcalcFile("test-fixtures/merge-position-integration-test.fcalc")
            val loader    = FireCalcYAML_Loader(yaml)
            val appResult = loader.make_en15544_Strict_Application

            // Application creation succeeds (input validation passes);
            // post-firebox pipe validation happens inside the application
            // as lazy vals (flueRegionPipeResults / postFireboxPipeResults).
            appResult.isValid `shouldBe` true

            val app = appResult.toOption.get

            // But validation should fail if merge validation fails
            val validationResult = app.validateResultsExceptEmissionsValues(Country.France)
            validationResult.isValid `shouldBe` false

            val errors      = validationResult.toEither.left.toOption.get
            val mergeErrors = errors.collect:
                case e: MergeBranchTipNotAtMergePosition => e

            mergeErrors `should` not `be` empty
            // With correct split position (0,0,0.52) and branch tip (0,0.3,-0.48),
            // distance to symmetry plane should be 300mm, not 100mm (which would come
            // from split at origin and default branch start)
            assert(
                mergeErrors.exists(e => math.abs(e.distanceMm - 300.0) < 1.0),
                s"Expected ~300mm distance, got distances: ${mergeErrors.map(_.distanceMm).mkString(", ")}"
            )
        }
    }
}
