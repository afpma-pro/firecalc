/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.generators.AllGenerators
import afpma.firecalc.dto.v4.*
import afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_13384_V3.*
import afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_13384_V3.*
import afpma.firecalc.units.coulombutils.*

import coulomb.syntax.*

import io.taig.babel.Language
import io.taig.babel.Locale

import org.scalactic.anyvals.PosInt
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/**
 * Regression test for AbsoluteDirection YAML roundtrip through the full V4 pipeline.
 *
 * Motivated by commit 5e9d375 where codec ordering in V4Instances.scala caused
 * a ClassCastException. This suite ensures AbsoluteDirection (including all
 * AzimuthDirection and InclinationDirection variants) survives encode/decode
 * through FireCalcYAML_V4.
 */
class AbsoluteDirectionRoundTripSuite extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks:

    override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
        PropertyCheckConfiguration(
            minSuccessful = PosInt(100)
        )

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Build a minimal FireCalcYAML_V4 with the given air intake pipe descriptors. */
    private def minimalV4WithAirIntake(
        airIntake                       : Seq[FlowOnlyPipeDescr_13384_V3]
    ): FireCalcYAML_V4 =
        FireCalcYAML_V4(
            version                        = FireCalcYAML_V4.VERSION,
            locale                         = Locale(Language("en")),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = ProjectDescr(
                reference = "FINAL-DIR-TEST",
                date      = "2025-01-01",
                country   = Country.France
            ),
            local_conditions               = LocalConditions.default,
            stove_params                   = StoveParams.fromMaxLoadAndStoragePeriod(
                maximum_load   = 20.kg,
                heating_cycle  = 12.hours,
                min_efficiency = 80.percent,
                facing_type    = FacingType.WithoutAirGap
            ),
            air_intake_descr               = airIntake,
            firebox                        = Firebox_V3.Traditional(
                heat_output_reduced                   = HeatOutputReduced.NotDefined,
                firebox_depth                         = 40.cm,
                firebox_width                         = 50.cm,
                firebox_height                        = 60.cm,
                height_of_lowest_opening              = 5.cm,
                pressure_loss_coefficient_from_door   = 0.5.withUnit[1],
                total_air_intake_surface_area_on_door = 0.01.m2,
                glass_width                           = 30.cm,
                glass_height                          = 40.cm
            ),
            flue_pipe_descr                = Seq(),
            connector_pipe_descr           = Seq(),
            chimney_pipe_descr             = Seq()
        )

    /** Encode a V4 instance to YAML, decode it back, and return both the YAML string and the decoded value. */
    private def roundTripV4(v4: FireCalcYAML_V4): FireCalcYAML_V4 =
        val encoded = FireCalcYAML_V4.encodeToYaml(v4)
        withClue(s"Encoding failed: ${encoded.failed.toOption}\n") {
            encoded.isSuccess.shouldBe(true)
        }
        val yaml    = encoded.get
        val decoded = FireCalcYAML_V4.decodeFromYaml(yaml)
        withClue(s"Decoding failed: ${decoded.failed.toOption}\nYAML was:\n$yaml\n") {
            decoded.isSuccess.shouldBe(true)
        }
        decoded.get

    // ── Full V4 pipeline roundtrip with AbsoluteDirection on pipe elements ─

    "Full V4 pipeline roundtrip with AbsoluteDirection values" - {

        "roundtrips a pipe with SetInitialDirection and direction changes with named AbsoluteDirection" in {
            val airIntake: Seq[FlowOnlyPipeDescr_13384_V3] = Seq(
                SetInitialDirection   (AzimuthDirection.Rear, InclinationDirection.Up),
                SetInnerShape(PipeShape.Circle(15.cm)                         ),
                SetRoughness          (3.mm                                          ),
                SetMaterial  (afpma.firecalc.dto.v3.Material_13384_V2.Bricks()),
                AddSectionVertical    ("vertical-1", 100.cm                          ),
                AddSharpeAngle_0_to_90(
                    "bend-1",
                    90.degrees,
                    absDir = Some(AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal))
                ),
                AddSectionHorizontal  ("horizontal-1", 50.cm                         ),
                AddSmoothCurve_90     (
                    "curve-1",
                    15.cm,
                    absDir = Some(AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up))
                )
            )

            val original = minimalV4WithAirIntake(airIntake)
            val decoded  = roundTripV4(original)
            decoded.shouldBe(original)
        }

        "roundtrips a pipe with custom angle AbsoluteDirection" in {
            val airIntake: Seq[FlowOnlyPipeDescr_13384_V3] = Seq(
                SetInitialDirection(
                    AzimuthDirection.Custom    (42.5.degrees),
                    InclinationDirection.Custom(15.0.degrees)
                ),
                SetInnerShape(PipeShape.Circle(12.cm)                              ),
                SetRoughness       (2.mm              ),
                SetMaterial  (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
                AddSectionSlopped  ("slopped-1", 80.cm),
                AddAngleAdjustable (
                    "adj-bend-1",
                    45.degrees,
                    0.5.withUnit[1],
                    absDir = Some(
                        AbsoluteDirection(
                            AzimuthDirection.Custom    (123.0.degrees),
                            InclinationDirection.Custom(-15.0.degrees)
                        )
                    )
                )
            )

            val original = minimalV4WithAirIntake(airIntake)
            val decoded  = roundTripV4(original)
            decoded.shouldBe(original)
        }

        "roundtrips a pipe with mixed named and custom AbsoluteDirection variants" in {
            val airIntake: Seq[FlowOnlyPipeDescr_13384_V3] = Seq(
                SetInitialDirection   (AzimuthDirection.Front, InclinationDirection.Horizontal),
                SetInnerShape(PipeShape.Rectangle(20.cm, 15.cm)                       ),
                SetRoughness          (3.mm                                                   ),
                SetMaterial  (afpma.firecalc.dto.v3.Material_13384_V2.ClayFlueLiners()),
                AddSectionHorizontal  ("horiz-1", 100.cm                                      ),
                AddSharpeAngle_0_to_90(
                    "bend-named",
                    90.degrees,
                    absDir =
                        Some(AbsoluteDirection(AzimuthDirection.FrontLeft, InclinationDirection.Custom(45.0.degrees)))
                ),
                AddSectionSlopped     ("slopped-1", 60.cm                                     ),
                AddSmoothCurve_60     (
                    "curve-custom",
                    20.cm,
                    absDir = Some(AbsoluteDirection(AzimuthDirection.Custom(200.0.degrees), InclinationDirection.Down))
                )
            )

            val original = minimalV4WithAirIntake(airIntake)
            val decoded  = roundTripV4(original)
            decoded.shouldBe(original)
        }

        "roundtrips direction changes with absDir = None" in {
            val airIntake: Seq[FlowOnlyPipeDescr_13384_V3] = Seq(
                SetInnerShape(PipeShape.Circle(15.cm)                         ),
                SetRoughness          (3.mm                     ),
                SetMaterial  (afpma.firecalc.dto.v3.Material_13384_V2.Bricks()),
                AddSectionVertical    ("vertical-1", 100.cm     ),
                AddSharpeAngle_0_to_90("bend-no-dir", 90.degrees),
                AddSectionHorizontal  ("horizontal-1", 50.cm    )
            )

            val original = minimalV4WithAirIntake(airIntake)
            val decoded  = roundTripV4(original)
            decoded.shouldBe(original)
        }
    }

    // ── Targeted tests for each direction variant ───────────────────────

    "Each AzimuthDirection variant roundtrips through full V4 pipeline" - {

        val allAzimuthVariants: List[AzimuthDirection] =
            AzimuthDirection.namedCases :+ AzimuthDirection.Custom(73.5.degrees)

        allAzimuthVariants.foreach { azDir =>
            val label = azDir match
                case AzimuthDirection.Custom(a) => s"Custom(${a.value}deg)"
                case named                      => named.toString

            s"AzimuthDirection.$label with InclinationDirection.Horizontal" in {
                val airIntake: Seq[FlowOnlyPipeDescr_13384_V3] = Seq(
                    SetInitialDirection   (AzimuthDirection.Rear, InclinationDirection.Up),
                    SetInnerShape(PipeShape.Circle(15.cm)                         ),
                    SetRoughness          (3.mm                                          ),
                    SetMaterial  (afpma.firecalc.dto.v3.Material_13384_V2.Bricks()),
                    AddSectionVertical    ("vert", 100.cm                                ),
                    AddSharpeAngle_0_to_90(
                        s"bend-$label",
                        90.degrees,
                        absDir = Some(AbsoluteDirection(azDir, InclinationDirection.Horizontal))
                    )
                )

                val original = minimalV4WithAirIntake(airIntake)
                val decoded  = roundTripV4(original)
                decoded.shouldBe(original)
            }
        }
    }

    "Each InclinationDirection variant roundtrips through full V4 pipeline" - {

        val allInclinationVariants: List[InclinationDirection] =
            InclinationDirection.namedCases :+ InclinationDirection.Custom(33.0.degrees)

        allInclinationVariants.foreach { inclDir =>
            val label = inclDir match
                case InclinationDirection.Custom(a) => s"Custom(${a.value}deg)"
                case named                          => named.toString

            s"InclinationDirection.$label with AzimuthDirection.Rear" in {
                val airIntake: Seq[FlowOnlyPipeDescr_13384_V3] = Seq(
                    SetInitialDirection (AzimuthDirection.Front, InclinationDirection.Horizontal),
                    SetInnerShape(PipeShape.Circle(15.cm)                         ),
                    SetRoughness        (3.mm                                                   ),
                    SetMaterial  (afpma.firecalc.dto.v3.Material_13384_V2.Bricks()),
                    AddSectionHorizontal("horiz", 100.cm                                        ),
                    AddSmoothCurve_90   (
                        s"curve-$label",
                        15.cm,
                        absDir = Some(AbsoluteDirection(AzimuthDirection.Rear, inclDir))
                    )
                )

                val original = minimalV4WithAirIntake(airIntake)
                val decoded  = roundTripV4(original)
                decoded.shouldBe(original)
            }
        }
    }

    // ── Property-based test using existing generators ───────────────────

    "Property-based V4 roundtrip (with AbsoluteDirection from generators)" - {

        "random V4 instances roundtrip through YAML" in forAll(
            AllGenerators.genFireCalcYAML_V4
        ) { original =>
            val encoded = FireCalcYAML_V4.encodeToYaml(original)
            withClue(s"Encoding failed: ${encoded.failed.toOption}\n") {
                encoded.isSuccess.shouldBe(true)
            }

            val yaml    = encoded.get
            val decoded = FireCalcYAML_V4.decodeFromYaml(yaml)
            withClue(s"Decoding failed: ${decoded.failed.toOption}\nYAML was:\n$yaml\n") {
                decoded.isSuccess.shouldBe(true)
            }

            decoded.get.shouldBe(original)
        }
    }

end AbsoluteDirectionRoundTripSuite
