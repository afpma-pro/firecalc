/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.firebox_calc_api

import io.circe.{Encoder, Decoder}
import io.circe.parser
import io.circe.syntax.*
import Codecs.given

import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class WireCodecRoundTripSuite extends AnyFreeSpec with Matchers {

    private def roundTrip[T](value: T)(using Encoder[T], Decoder[T]): Unit =
        parser.decode[T](value.asJson.noSpaces) match
            case Right(decoded) => decoded shouldBe value
            case Left(err)      => fail(err.toString)

    // =========================================================================
    // WirePipeElDescr — 7 shapes
    // =========================================================================

    "WirePipeElDescr.StraightSection round-trips" - {
        "straight section" in {
            roundTrip[WirePipeElDescr](
                WirePipeElDescr.StraightSection(
                    WireQty("5.0", "m" ),
                    WirePipeShape.Circle(WireQty("0.2", "m")),
                    WireQty("0.01", "m"),
                    WireQty("1.0", "m" )
                )
            )
        }
    }

    "WirePipeElDescr.AngleVifDe0A180 round-trips" - {
        "angle vif with angleN2" in {
            roundTrip[WirePipeElDescr](
                WirePipeElDescr.AngleVifDe0A180                (
                    WireQty("45.0", "deg"),
                    Some                (WireQty("2.0", "deg")),
                    WirePipeShape.Square(WireQty("0.15", "m") )
                )
            )
        }

        "angle vif without angleN2" in {
            roundTrip[WirePipeElDescr](
                WirePipeElDescr.AngleVifDe0A180(
                    WireQty                ("90.0", "deg"                           ),
                    None,
                    WirePipeShape.Rectangle(WireQty("0.1", "m"), WireQty("0.2", "m"))
                )
            )
        }
    }

    "WirePipeElDescr.CircularArc60 round-trips" in {
        roundTrip[WirePipeElDescr](
            WirePipeElDescr.CircularArc60(
                WirePipeShape.Circle(WireQty("0.18", "m"))
            )
        )
    }

    "WirePipeElDescr.SplitMerge90 round-trips" in {
        roundTrip[WirePipeElDescr](
            WirePipeElDescr.SplitMerge90                (
                3,
                Some                (WireQty("1.5", "deg")),
                WirePipeShape.Circle(WireQty("0.2", "m")  )
            )
        )
    }

    "WirePipeElDescr.SectionGeometryChange round-trips" in {
        roundTrip[WirePipeElDescr](
            WirePipeElDescr.SectionGeometryChange(
                WirePipeShape.Circle(WireQty("0.2", "m")),
                WirePipeShape.Rectangle(WireQty("0.15", "m"), WireQty("0.25", "m"))
            )
        )
    }

    "WirePipeElDescr.SingularFlowResistance round-trips with crossSection" in {
        roundTrip[WirePipeElDescr](
            WirePipeElDescr.SingularFlowResistance(
                1.4,
                Some(WireQty("0.0314", "m\u00b2"))
            )
        )
    }

    "WirePipeElDescr.SingularFlowResistance round-trips without crossSection" in {
        roundTrip[WirePipeElDescr](
            WirePipeElDescr.SingularFlowResistance(0.5, None)
        )
    }

    "WirePipeElDescr.PressureDiff round-trips" in {
        roundTrip[WirePipeElDescr](
            WirePipeElDescr.PressureDiff(
                WireQty("100.0", "Pa"),
                Some(WireQty("0.0314", "m\u00b2"))
            )
        )
    }

    // =========================================================================
    // WirePipeShape — 3 shapes
    // =========================================================================

    "WirePipeShape.Circle round-trips" in {
        roundTrip(WirePipeShape.Circle(WireQty("0.2", "m")))
    }

    "WirePipeShape.Square round-trips" in {
        roundTrip(WirePipeShape.Square(WireQty("0.15", "m")))
    }

    "WirePipeShape.Rectangle round-trips" in {
        roundTrip(WirePipeShape.Rectangle(WireQty("0.1", "m"), WireQty("0.2", "m")))
    }

    // =========================================================================
    // WireFireboxContribution — 3 arms
    // =========================================================================

    "WireFireboxContribution.Without round-trips" in {
        roundTrip[WireFireboxContribution](WireFireboxContribution.Without)
    }

    "WireFireboxContribution.AsDescr round-trips" in {
        roundTrip[WireFireboxContribution](
            WireFireboxContribution.AsDescr(
                WirePipeFullDescr(
                    WirePipeType.Firebox,
                    List(
                        WireNamedPipeEl(
                            "firebox-straight",
                            WirePipeElDescr.StraightSection(
                                WireQty("3.0", "m" ),
                                WirePipeShape.Circle(WireQty("0.2", "m")),
                                WireQty("0.01", "m"),
                                WireQty("2.0", "m" )
                            )
                        )
                    )
                )
            )
        )
    }

    // A compact WirePipeResult for grid-entry tests (20 fields, optionals None).
    private def stubWirePipeResult(pu: String = "101325.0"): WirePipeResult =
        WirePipeResult           (
            WirePipeType.Flue,
            WireQty("10.0", "m"       ),
            WireQty("5.0", "m"        ),
            Some   (0.5               ),
            WireValidated.Valid(WireQty(pu, "Pa")           ),
            Some               (WireQty("50.0", "Pa")       ),
            WireQty("100.0", "Pa"     ),
            WireQty("200.0", "Pa"     ),
            WireQty("50.0", "Pa"      ),
            WireQty("20.0", "\u00b0C" ),
            WireQty("60.0", "\u00b0C" ),
            WireQty("100.0", "\u00b0C"),
            Some               (WireQty("1.0", "kg/m\u00b3")),
            Some               (WireQty("0.9", "kg/m\u00b3")),
            Some               (WireQty("2.0", "m/s")       ),
            Some               (WireQty("1.5", "m/s")       ),
            None,
            None,
            None,
            None
        )

    "WireFireboxContribution.AsResult round-trips (1-entry grid)" in {
        roundTrip[WireFireboxContribution](
            WireFireboxContribution.AsResult(
                List(
                    WireOpPointResult(
                        WireDraftCondition.DraftMinOrPositivePressureMax,
                        WireLoadQty.Nominal,
                        stubWirePipeResult()
                    )
                )
            )
        )
    }

    "WireFireboxContribution.AsResult round-trips (4-entry grid)" in {
        roundTrip[WireFireboxContribution](
            WireFireboxContribution.AsResult(
                List(
                    WireOpPointResult(
                        WireDraftCondition.DraftMinOrPositivePressureMax,
                        WireLoadQty.Nominal,
                        stubWirePipeResult("10.0")
                    ),
                    WireOpPointResult(
                        WireDraftCondition.DraftMinOrPositivePressureMax,
                        WireLoadQty.Reduced,
                        stubWirePipeResult("11.0")
                    ),
                    WireOpPointResult(
                        WireDraftCondition.DraftMaxOrPositivePressureMin,
                        WireLoadQty.Nominal,
                        stubWirePipeResult("12.0")
                    ),
                    WireOpPointResult(
                        WireDraftCondition.DraftMaxOrPositivePressureMin,
                        WireLoadQty.Reduced,
                        stubWirePipeResult("13.0")
                    )
                )
            )
        )
    }

    "WireFireboxContribution.AsResult rejects duplicate keys" in {
        val dup: WireFireboxContribution = WireFireboxContribution.AsResult(
            List(
                WireOpPointResult(
                    WireDraftCondition.DraftMinOrPositivePressureMax,
                    WireLoadQty.Nominal,
                    stubWirePipeResult("10.0")
                ),
                WireOpPointResult(
                    WireDraftCondition.DraftMinOrPositivePressureMax,
                    WireLoadQty.Nominal,
                    stubWirePipeResult("99.0")
                )
            )
        )
        parser.decode[WireFireboxContribution](dup.asJson.noSpaces) match
            case Left(err) => err.getMessage should include("duplicate")
            case Right(_)  => fail("duplicate (draftCondition, loadQty) keys must be a decoding failure")
    }

    "WireFireboxContribution.AsResult rejects an empty grid" in {
        val empty: WireFireboxContribution = WireFireboxContribution.AsResult(Nil)
        parser.decode[WireFireboxContribution](empty.asJson.noSpaces) match
            case Left(err) => err.getMessage should include("at least one result")
            case Right(_)  => fail("an empty asResult grid must be a decoding failure")
    }

    // =========================================================================
    // WireDraftCondition / WireLoadQty — every value round-trips, unknown fails
    // =========================================================================

    "WireDraftCondition — every value round-trips" in {
        WireDraftCondition.values.foreach(v => roundTrip[WireDraftCondition](v))
    }

    "WireDraftCondition — unknown value fails" in {
        parser.decode[WireDraftCondition]("\"bogus\"") match
            case Left(err) => err.getMessage should include("Unknown WireDraftCondition")
            case Right(_)  => fail("an unknown draft condition must be a decoding failure")
    }

    "WireLoadQty — every value round-trips" in {
        WireLoadQty.values.foreach(v => roundTrip[WireLoadQty](v))
    }

    "WireLoadQty — unknown value fails" in {
        parser.decode[WireLoadQty]("\"bogus\"") match
            case Left(err) => err.getMessage should include("Unknown WireLoadQty")
            case Right(_)  => fail("an unknown load quantity must be a decoding failure")
    }

    // =========================================================================
    // WireValidated[WireFireboxContribution] — both arms
    // =========================================================================

    "WireValidated[WireFireboxContribution].Valid round-trips" in {
        roundTrip[WireValidated[WireFireboxContribution]](
            WireValidated.Valid(
                WireFireboxContribution.AsResult(
                    List(
                        WireOpPointResult(
                            WireDraftCondition.DraftMaxOrPositivePressureMin,
                            WireLoadQty.Reduced,
                            stubWirePipeResult()
                        )
                    )
                )
            )
        )
    }

    "WireValidated[WireFireboxContribution].Invalid round-trips" in {
        roundTrip[WireValidated[WireFireboxContribution]](
            WireValidated.Invalid(
                List(WireErrorItem("MissingMB", "maximum load (m_B) not provided"))
            )
        )
    }

    // =========================================================================
    // WireOperatingEnvelope — present/absent, overrides present/absent
    // =========================================================================

    "WireOperatingEnvelope without overrides round-trips" in {
        roundTrip(
            WireOperatingEnvelope            (
                altitude             = WireQty("350.0", "m"),
                temperatureOverrides = None
            )
        )
    }

    "WireOperatingEnvelope with full overrides round-trips" in {
        roundTrip(
            WireOperatingEnvelope            (
                altitude             = WireQty("350.0", "m"),
                temperatureOverrides = Some(
                    WireTemperatureOverrides(
                        draftMinOrPositivePressureMax = Some(WireQty("0.0", "\u00b0C")),
                        draftMaxOrPositivePressureMin = Some(WireQty("-5.0", "\u00b0C"))
                    )
                )
            )
        )
    }

    "WireOperatingEnvelope with partial overrides round-trips" in {
        roundTrip(
            WireOperatingEnvelope            (
                altitude             = WireQty("350.0", "m"),
                temperatureOverrides = Some(
                    WireTemperatureOverrides(
                        draftMinOrPositivePressureMax = Some(WireQty("12.0", "\u00b0C")),
                        draftMaxOrPositivePressureMin = None
                    )
                )
            )
        )
    }

    "FireboxCalcRequest without operatingEnvelope round-trips" in {
        roundTrip(
            FireboxCalcRequest(
                ApiVersion.v1,
                WireFireboxKind.Traditional,
                afpma.firecalc.dto.v5.Firebox_V4.Traditional                  (
                    heat_output_reduced                   = afpma.firecalc.dto.all.HeatOutputReduced.NotDefined,
                    firebox_depth                         = 0.442.withUnit[Meter],
                    firebox_width                         = 0.332.withUnit[Meter],
                    firebox_height                        = 0.641.withUnit[Meter],
                    height_of_lowest_opening              = 0.1.withUnit[Meter],
                    pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
                    total_air_intake_surface_area_on_door = 0.01.withUnit[(Meter ^ 2)],
                    glass_width                           = 0.3.withUnit[Meter],
                    glass_height                          = 0.3.withUnit[Meter]
                ),
                Standard15544Params                                           (None, None),
                None,
                "en",
                WireStandardVariant.Strict,
                None
            )
        )
    }

    "FireboxCalcRequest with operatingEnvelope round-trips" in {
        roundTrip(
            FireboxCalcRequest(
                ApiVersion.v1,
                WireFireboxKind.Traditional,
                afpma.firecalc.dto.v5.Firebox_V4.Traditional                  (
                    heat_output_reduced                   = afpma.firecalc.dto.all.HeatOutputReduced.NotDefined,
                    firebox_depth                         = 0.442.withUnit[Meter],
                    firebox_width                         = 0.332.withUnit[Meter],
                    firebox_height                        = 0.641.withUnit[Meter],
                    height_of_lowest_opening              = 0.1.withUnit[Meter],
                    pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
                    total_air_intake_surface_area_on_door = 0.01.withUnit[(Meter ^ 2)],
                    glass_width                           = 0.3.withUnit[Meter],
                    glass_height                          = 0.3.withUnit[Meter]
                ),
                Standard15544Params                                           (None, None),
                None,
                "en",
                WireStandardVariant.Strict,
                Some(
                    WireOperatingEnvelope            (
                        altitude             = WireQty("350.0", "m"),
                        temperatureOverrides = Some(
                            WireTemperatureOverrides(
                                draftMinOrPositivePressureMax = Some(WireQty("0.0", "\u00b0C")),
                                draftMaxOrPositivePressureMin = None
                            )
                        )
                    )
                )
            )
        )
    }

    // =========================================================================
    // ConstraintOutcome — 4 arms
    // =========================================================================

    "ConstraintOutcome.Satisfied round-trips" in {
        roundTrip[ConstraintOutcome](ConstraintOutcome.Satisfied)
    }

    "ConstraintOutcome.Violated round-trips" in {
        roundTrip[ConstraintOutcome](ConstraintOutcome.Violated("TooShort", "too short", true))
    }

    "ConstraintOutcome.NotEvaluated round-trips" in {
        roundTrip[ConstraintOutcome](ConstraintOutcome.NotEvaluated(NotEvaluatedReason.MissingMB))
    }

    "ConstraintOutcome.NotApplicable round-trips" in {
        roundTrip[ConstraintOutcome](ConstraintOutcome.NotApplicable)
    }

    // =========================================================================
    // ConstraintDescriptor — 5 arms (Min/Max/Range with Either[WireQty, Double])
    // =========================================================================

    "ConstraintDescriptor.Min with WireQty round-trips" in {
        roundTrip[ConstraintDescriptor](
            ConstraintDescriptor.Min(Left(WireQty("0.5", "m")))
        )
    }

    "ConstraintDescriptor.Min with Double round-trips" in {
        roundTrip[ConstraintDescriptor](
            ConstraintDescriptor.Min(Right(0.5))
        )
    }

    "ConstraintDescriptor.Max with WireQty round-trips" in {
        roundTrip[ConstraintDescriptor](
            ConstraintDescriptor.Max(Left(WireQty("10.0", "m")))
        )
    }

    "ConstraintDescriptor.Max with Double round-trips" in {
        roundTrip[ConstraintDescriptor](
            ConstraintDescriptor.Max(Right(10.0))
        )
    }

    "ConstraintDescriptor.Range with WireQty round-trips" in {
        roundTrip[ConstraintDescriptor](
            ConstraintDescriptor.Range(
                Some(Left(WireQty("1.0", "m"))),
                Some(Left(WireQty("5.0", "m")))
            )
        )
    }

    "ConstraintDescriptor.Range with Double round-trips" in {
        roundTrip[ConstraintDescriptor](
            ConstraintDescriptor.Range(
                Some(Right(1.0)),
                Some(Right(5.0))
            )
        )
    }

    "ConstraintDescriptor.Range with nulls round-trips" in {
        roundTrip[ConstraintDescriptor](
            ConstraintDescriptor.Range(None, None)
        )
    }

    "ConstraintDescriptor.ShapeOneOf round-trips" in {
        roundTrip[ConstraintDescriptor](
            ConstraintDescriptor.ShapeOneOf(
                List (
                    WirePipeShape.Circle(WireQty("0.2", "m") ),
                    WirePipeShape.Square(WireQty("0.15", "m"))
                )
            )
        )
    }

    "ConstraintDescriptor.Opaque round-trips" in {
        roundTrip[ConstraintDescriptor](ConstraintDescriptor.Opaque)
    }

    // =========================================================================
    // WireValidated — Valid and Invalid
    // =========================================================================

    "WireValidated.Valid round-trips" in {
        roundTrip[WireValidated[WireQty]](
            WireValidated.Valid(WireQty("101325.0", "Pa"))
        )
    }

    "WireValidated.Invalid round-trips" in {
        roundTrip[WireValidated[WireQty]](
            WireValidated.Invalid(
                List(
                    WireErrorItem("E001", "pressure out of range")
                )
            )
        )
    }

    // =========================================================================
    // WireFireboxKind — 6 cases (assert wire strings)
    // =========================================================================

    "WireFireboxKind.Traditional round-trips with wire string 'traditional'" in {
        val json = WireFireboxKind.Traditional.asJson.noSpaces
        json shouldBe "\"traditional\""
        roundTrip(WireFireboxKind.Traditional)
    }

    "WireFireboxKind.Ecolabeled round-trips with wire string 'ecolabeled'" in {
        val json = WireFireboxKind.Ecolabeled.asJson.noSpaces
        json shouldBe "\"ecolabeled\""
        roundTrip(WireFireboxKind.Ecolabeled)
    }

    "WireFireboxKind.AfpmaPrse round-trips with wire string 'afpmaPrse'" in {
        val json = WireFireboxKind.AfpmaPrse.asJson.noSpaces
        json shouldBe "\"afpmaPrse\""
        roundTrip(WireFireboxKind.AfpmaPrse)
    }

    "WireFireboxKind.SingleTested round-trips with wire string 'singleTested'" in {
        val json = WireFireboxKind.SingleTested.asJson.noSpaces
        json shouldBe "\"singleTested\""
        roundTrip(WireFireboxKind.SingleTested)
    }

    "WireFireboxKind.Door15aCatalog round-trips with wire string 'door15aCatalog'" in {
        val json = WireFireboxKind.Door15aCatalog.asJson.noSpaces
        json shouldBe "\"door15aCatalog\""
        roundTrip(WireFireboxKind.Door15aCatalog)
    }

    "WireFireboxKind.CustomForLab round-trips with wire string 'customForLab'" in {
        val json = WireFireboxKind.CustomForLab.asJson.noSpaces
        json shouldBe "\"customForLab\""
        roundTrip(WireFireboxKind.CustomForLab)
    }

    // =========================================================================
    // WirePipeType — 7 cases
    // =========================================================================

    "WirePipeType.AirIntake round-trips" in { roundTrip(WirePipeType.AirIntake) }

    "WirePipeType.CombustionAir round-trips" in { roundTrip(WirePipeType.CombustionAir) }

    "WirePipeType.Firebox round-trips" in { roundTrip(WirePipeType.Firebox) }

    "WirePipeType.Flue round-trips" in { roundTrip(WirePipeType.Flue) }

    "WirePipeType.Connector round-trips" in { roundTrip(WirePipeType.Connector) }

    "WirePipeType.Chimney round-trips" in { roundTrip(WirePipeType.Chimney) }

    "WirePipeType.NoFlue round-trips" in { roundTrip(WirePipeType.NoFlue) }

    // =========================================================================
    // ConstraintTerm — 11 cases (assert wire strings)
    // =========================================================================

    "ConstraintTerm.MB round-trips with wire string 'm_B'" in {
        val json = ConstraintTerm.MB.asJson.noSpaces
        json shouldBe "\"m_B\""
        roundTrip(ConstraintTerm.MB)
    }

    "ConstraintTerm.MBMin round-trips" in { roundTrip(ConstraintTerm.MBMin) }

    "ConstraintTerm.GlassArea round-trips" in { roundTrip(ConstraintTerm.GlassArea) }

    "ConstraintTerm.FireboxBase round-trips" in { roundTrip(ConstraintTerm.FireboxBase) }

    "ConstraintTerm.H_BR round-trips with wire string 'H_BR'" in {
        val json = ConstraintTerm.H_BR.asJson.noSpaces
        json shouldBe "\"H_BR\""
        roundTrip(ConstraintTerm.H_BR)
    }

    "ConstraintTerm.Lambda round-trips" in { roundTrip(ConstraintTerm.Lambda) }

    "ConstraintTerm.HeightOfLowestOpening round-trips" in { roundTrip(ConstraintTerm.HeightOfLowestOpening) }

    "ConstraintTerm.GlassSurfaceRatio round-trips" in { roundTrip(ConstraintTerm.GlassSurfaceRatio) }

    "ConstraintTerm.AirIntakePipeShape round-trips" in { roundTrip(ConstraintTerm.AirIntakePipeShape) }

    "ConstraintTerm.NominalLoadInRange round-trips" in { roundTrip(ConstraintTerm.NominalLoadInRange) }

    "ConstraintTerm.Custom round-trips" in { roundTrip(ConstraintTerm.Custom) }

    // =========================================================================
    // NotEvaluatedReason — 3 cases
    // =========================================================================

    "NotEvaluatedReason.MissingMB round-trips" in { roundTrip(NotEvaluatedReason.MissingMB) }

    "NotEvaluatedReason.MissingFlowRate round-trips" in { roundTrip(NotEvaluatedReason.MissingFlowRate) }

    "NotEvaluatedReason.MissingTopologyShape round-trips" in { roundTrip(NotEvaluatedReason.MissingTopologyShape) }

    // =========================================================================
    // WirePipeResult — all 20 fields populated
    // =========================================================================

    "WirePipeResult with all fields round-trips" in {
        roundTrip(
            WirePipeResult    (
                WirePipeType.Flue,
                WireQty("10.0", "m"       ),
                WireQty("5.0", "m"        ),
                Some   (0.5               ),
                WireValidated.Valid(WireQty("101325.0", "Pa")    ),
                Some               (WireQty("50.0", "Pa")        ),
                WireQty("100.0", "Pa"     ),
                WireQty("200.0", "Pa"     ),
                WireQty("50.0", "Pa"      ),
                WireQty("20.0", "\u00b0C" ),
                WireQty("60.0", "\u00b0C" ),
                WireQty("100.0", "\u00b0C"),
                Some               (WireQty("1.0", "kg/m\u00b3") ),
                Some               (WireQty("0.9", "kg/m\u00b3") ),
                Some               (WireQty("2.0", "m/s")        ),
                Some               (WireQty("1.5", "m/s")        ),
                Some               (WireQty("0.85", "kg/m\u00b3")),
                Some               (WireQty("0.95", "kg/m\u00b3")),
                Some               (WireQty("1.8", "m/s")        ),
                Some               (WireQty("1.2", "m/s")        )
            )
        )
    }

    // =========================================================================
    // FireboxCalcResponse
    // =========================================================================

    "FireboxCalcResponse round-trips" in {
        roundTrip(
            FireboxCalcResponse(
                ApiVersion.v1,
                WireFireboxKind.Traditional,
                "en",
                WireValidated.Valid                                (WireFireboxContribution.Without),
                WireValidated.Valid                                (WireFireboxContribution.Without),
                List(
                    ConstraintVerdict(
                        ConstraintTerm.MB,
                        "m_B constraint",
                        ConstraintDescriptor.Min(Right(0.6)),
                        ConstraintOutcome.Satisfied
                    )
                ),
                FireboxReportData                                  (
                    WireTable(Some("Firebox"), Some(List("key", "value")), List(List("a", "b"))),
                    WireTable(None, None, List.empty                                           ),
                    List(List("col1", "col2")                                ),
                    List(List("col1", "col2")                                ),
                    Some(WireEcolabeledOutputs("10", "5", "5", "3", "2", "1"))
                )
            )
        )
    }

    // =========================================================================
    // FireboxCalcError — with and without details
    // =========================================================================

    "FireboxCalcError without details round-trips" in {
        roundTrip(FireboxCalcError("ERR001", "something went wrong", None))
    }

    "FireboxCalcError with details round-trips" in {
        roundTrip(
            FireboxCalcError(
                "ERR002",
                "validation failed",
                Some(List(WireErrorItem("E001", "field missing")))
            )
        )
    }

    // =========================================================================
    // DiscoveryResponse
    // =========================================================================

    "DiscoveryResponse round-trips" in {
        roundTrip(
            DiscoveryResponse(
                ApiVersion.v1,
                List(
                    WireFireboxKind.Traditional,
                    WireFireboxKind.Ecolabeled,
                    WireFireboxKind.SingleTested
                )
            )
        )
    }

    // =========================================================================
    // WireTable
    // =========================================================================

    "WireTable round-trips" in {
        roundTrip(
            WireTable(
                Some("My Table"                              ),
                Some(List("col1", "col2", "col3")),
                List(List("a", "b", "c"), List("d", "e", "f"))
            )
        )
    }

    // =========================================================================
    // WireEcolabeledOutputs
    // =========================================================================

    "WireEcolabeledOutputs round-trips" in {
        roundTrip(
            WireEcolabeledOutputs("10", "5", "5", "3", "2", "1")
        )
    }

    // =========================================================================
    // FireboxReportData
    // =========================================================================

    "FireboxReportData round-trips" in {
        roundTrip(
            FireboxReportData                                       (
                WireTable(Some("Header"), Some(List("k", "v")), List(List("x", "y"))),
                WireTable(None, None, List.empty                                    ),
                List(List("row1")                                       ),
                List(List("row2")                                       ),
                Some(WireEcolabeledOutputs("1", "2", "3", "4", "5", "6"))
            )
        )
    }

    // =========================================================================
    // Standard15544Params — Some/None for m_B and n_min (independently optional)
    // =========================================================================

    "Standard15544Params with Some m_B and Some n_min round-trips" in {
        roundTrip(
            Standard15544Params  (
                m_B   = Some(WireQty("1.5", "kg/h")),
                n_min = Some(WireQty("80", "percent"))
            )
        )
    }

    "Standard15544Params with both None round-trips" in {
        roundTrip(Standard15544Params(m_B = None, n_min = None))
    }

    "Standard15544Params with Some m_B and None n_min round-trips" in {
        roundTrip(Standard15544Params(m_B = Some(WireQty("1.5", "kg/h")), n_min = None))
    }

    "Standard15544Params with None m_B and Some n_min round-trips" in {
        roundTrip(Standard15544Params(m_B = None, n_min = Some(WireQty("80", "percent"))))
    }
}
