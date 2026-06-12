/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.v4.*
import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.dto.v6.*
import afpma.firecalc.dto.v7.*
import afpma.firecalc.dto.common.*

import afpma.firecalc.units.coulombutils.*

import io.taig.babel.Language
import io.taig.babel.Locale

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class V6ToV7TransformerSuite extends AnyFreeSpec with Matchers:

    // ─── Helper factories ──────────────────────────────────────────

    private def flowOnlySlot(
        descr: Seq[FlowOnlyPipeDescr_15544_V3]
    ): PostFireboxPipeDescrSlot =
        PostFireboxPipeDescrSlot.FlueSlot(descr)

    private def thermalSlot(
        descr: Seq[ThermalPipeDescr_13384_V3]
    ): PostFireboxPipeDescrSlot =
        PostFireboxPipeDescrSlot.ThermalFlueSlot(descr)

    private def connectorSlot(
        descr: Seq[ThermalPipeDescr_13384_V3]
    ): PostFireboxPipeDescrSlot =
        PostFireboxPipeDescrSlot.ConnectorSlot(descr)

    private def chimneySlot(
        descr: Seq[ThermalPipeDescr_13384_V3]
    ): PostFireboxPipeDescrSlot =
        PostFireboxPipeDescrSlot.ChimneySlot(descr)

    private def flowOnlyInitialDir(
        az  : AzimuthDirection,
        incl: InclinationDirection
    ): FlowOnlyPipeDescr_15544_V3 =
        SetFlowOnlyPipeProp_15544_V3.SetInitialDirection(az, incl)

    private def flowOnlyInitialPos(
        x: Length,
        y: Length,
        z: Length
    ): FlowOnlyPipeDescr_15544_V3 =
        SetFlowOnlyPipeProp_15544_V3.SetInitialPosition(x, y, z)

    private def flowOnlyFinalPos(
        x: Length,
        y: Length,
        z: Length
    ): FlowOnlyPipeDescr_15544_V3 =
        SetFlowOnlyPipeProp_15544_V3.SetFinalPosition(x, y, z)

    private def thermalInitialDir(
        az  : AzimuthDirection,
        incl: InclinationDirection
    ): ThermalPipeDescr_13384_V3 =
        SetThermalPipeProp_13384_V3.SetInitialDirection(az, incl)

    private def thermalInitialPos(
        x: Length,
        y: Length,
        z: Length
    ): ThermalPipeDescr_13384_V3 =
        SetThermalPipeProp_13384_V3.SetInitialPosition(x, y, z)

    private def thermalFinalPos(
        x: Length,
        y: Length,
        z: Length
    ): ThermalPipeDescr_13384_V3 =
        SetThermalPipeProp_13384_V3.SetFinalPosition(x, y, z)

    // ─── Helpers ───────────────────────────────────────────────────

// V7 helper functions: tracking ops are stripped from post-firebox slots during migration
    private def hasTrackingFlowOnlyV7(
        d: Seq[FlowOnlyPipeDescr_15544_V4]
    ): Boolean =
        d.exists:
            case _: FlowOnlyPipeTrackingOp_15544_V4.SetInitialDirection => true
            case _: FlowOnlyPipeTrackingOp_15544_V4.SetInitialPosition  => true
            case _: FlowOnlyPipeTrackingOp_15544_V4.SetFinalPosition    => true
            case _ => false
    private def hasTrackingThermalV7(
        d: Seq[ThermalPipeDescr_13384_V4]
    ): Boolean =
        d.exists:
            case _: ThermalPipeTrackingOp_13384_V4.SetInitialDirection => true
            case _: ThermalPipeTrackingOp_13384_V4.SetInitialPosition  => true
            case _: ThermalPipeTrackingOp_13384_V4.SetFinalPosition    => true
            case _ => false

    private def minimalFirebox: Firebox_V4 =
        Firebox_V4.Traditional                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            firebox_depth                         = 40.cm,
            firebox_width                         = 50.cm,
            firebox_height                        = 60.cm,
            height_of_lowest_opening              = 10.cm,
            pressure_loss_coefficient_from_door   = 0.5.unitless,
            total_air_intake_surface_area_on_door = 100.cm2,
            glass_width                           = 30.cm,
            glass_height                          = 40.cm
        )

    private def minimalV6(slots: Seq[PostFireboxPipeDescrSlot]): FireCalcYAML_V6 =
        FireCalcYAML_V6                       (
            version                        = FireCalcYAML_V6.VERSION,
            locale                         = Locale(Language("en")),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = ProjectDescr("TEST-V6", "2026-06-06", Country.France),
            local_conditions               = LocalConditions.default,
            stove_params                   = StoveParams.fromMaxLoadAndStoragePeriod(
                maximum_load   = 20.kg,
                heating_cycle  = 12.hours,
                min_efficiency = 80.percent,
                facing_type    = FacingType.WithoutAirGap
            ),
            air_intake_descr               = Seq.empty,
            firebox                        = minimalFirebox,
            post_firebox_pipes             = slots
        )

    // ─── Tests ──────────────────────────────────────────────────────

    "normalizeToPostFireboxPipes" - {

        "extracts initial direction and position from first flow-only slot" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    flowOnlySlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Right, InclinationDirection.Up),
                            flowOnlyInitialPos(10.cm, 20.cm, 30.cm                            )
                        )
                    ),
                    connectorSlot(Seq.empty),
                    chimneySlot  (Seq.empty)
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection(
                AzimuthDirection.Right,
                InclinationDirection.Up
            )
            result.initialPosition shouldBe PostFireboxInitialPosition(10.cm, 20.cm, 30.cm)
            result.slots.size shouldBe 3

            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    hasTrackingFlowOnlyV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                   => fail(s"Expected FlueSlot, got $other")
        }

        "extracts initial direction and position from first thermal slot" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    thermalSlot(
                        Seq(
                            thermalInitialDir(AzimuthDirection.Front, InclinationDirection.Horizontal),
                            thermalInitialPos(5.cm, 15.cm, 25.cm                                     )
                        )
                    ),
                    connectorSlot(Seq.empty),
                    chimneySlot  (Seq.empty)
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection(
                AzimuthDirection.Front,
                InclinationDirection.Horizontal
            )
            result.initialPosition shouldBe PostFireboxInitialPosition(5.cm, 15.cm, 25.cm)

            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
                    hasTrackingThermalV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                          => fail(s"Expected ThermalFlueSlot, got $other")
        }

        "uses default direction when initial direction is missing" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    flowOnlySlot(
                        Seq(
                            flowOnlyInitialPos(10.cm, 20.cm, 30.cm)
                        )
                    ),
                    connectorSlot(Seq.empty)
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection.default
            result.initialPosition shouldBe PostFireboxInitialPosition(10.cm, 20.cm, 30.cm)
        }

        "uses zero position when initial position is missing" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    flowOnlySlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Left, InclinationDirection.Down)
                        )
                    ),
                    connectorSlot(Seq.empty)
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection(
                AzimuthDirection.Left,
                InclinationDirection.Down
            )
            result.initialPosition shouldBe PostFireboxInitialPosition(0.cm, 0.cm, 0.cm)
        }

        "uses defaults for both when neither initial element is present" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    flowOnlySlot(
                        Seq(
                            AddFlowOnlyPipeElement_15544_V3.AddSectionHorizontal("sec", 100.cm)
                        )
                    ),
                    connectorSlot(Seq.empty)
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection.default
            result.initialPosition shouldBe PostFireboxInitialPosition(0.cm, 0.cm, 0.cm)
        }

        "handles empty slot vector without crashing" in {
            val result = transformers.normalizeToPostFireboxPipes(Seq.empty)

            result.initialDirection shouldBe PostFireboxInitialDirection.default
            result.initialPosition shouldBe PostFireboxInitialPosition(0.cm, 0.cm, 0.cm)
            result.slots shouldBe empty
        }

        "handles empty first slot descriptor" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    flowOnlySlot (Seq.empty),
                    connectorSlot(Seq.empty)
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection.default
            result.initialPosition shouldBe PostFireboxInitialPosition(0.cm, 0.cm, 0.cm)
            result.slots.size shouldBe 2
        }

        "strips deprecated elements from ALL slots, not just first" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq (
                    flowOnlySlot (
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Right, InclinationDirection.Up),
                            flowOnlyInitialPos(10.cm, 20.cm, 30.cm                            )
                        )
                    ),
                    connectorSlot(
                        Seq(
                            thermalInitialDir(AzimuthDirection.Front, InclinationDirection.Horizontal),
                            thermalInitialPos(5.cm, 15.cm, 25.cm                                     )
                        )
                    ),
                    chimneySlot  (
                        Seq(
                            thermalFinalPos(100.cm, 200.cm, 300.cm)
                        )
                    )
                )
            )

            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    hasTrackingFlowOnlyV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                   => fail(s"Expected FlueSlot, got $other")

            result.slots(1) match
                case PostFireboxPipeDescrSlot_V7.ConnectorSlot(d) =>
                    hasTrackingThermalV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                        => fail(s"Expected ConnectorSlot, got $other")

            result.slots(2) match
                case PostFireboxPipeDescrSlot_V7.ChimneySlot(d) =>
                    hasTrackingThermalV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                      => fail(s"Expected ChimneySlot, got $other")
        }

        "strips SetFinalPosition from all slots" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq (
                    flowOnlySlot (
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Right, InclinationDirection.Up),
                            flowOnlyFinalPos  (50.cm, 60.cm, 70.cm                            )
                        )
                    ),
                    connectorSlot(
                        Seq(
                            thermalFinalPos(100.cm, 200.cm, 300.cm)
                        )
                    )
                )
            )

            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    hasTrackingFlowOnlyV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                   => fail(s"Expected FlueSlot, got $other")

            result.slots(1) match
                case PostFireboxPipeDescrSlot_V7.ConnectorSlot(d) =>
                    hasTrackingThermalV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                        => fail(s"Expected ConnectorSlot, got $other")
        }

        "handles connector-first chain (connector as first slot)" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    connectorSlot(
                        Seq(
                            thermalInitialDir(AzimuthDirection.Rear, InclinationDirection.Up),
                            thermalInitialPos(1.cm, 2.cm, 3.cm                              )
                        )
                    ),
                    chimneySlot(Seq.empty)
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection(
                AzimuthDirection.Rear,
                InclinationDirection.Up
            )
            result.initialPosition shouldBe PostFireboxInitialPosition(1.cm, 2.cm, 3.cm)
            result.slots.size shouldBe 2
        }

        "handles chimney-first chain" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    chimneySlot(
                        Seq(
                            thermalInitialDir(
                                AzimuthDirection.Custom    (45.degrees),
                                InclinationDirection.Custom(30.degrees)
                            ),
                            thermalInitialPos(100.cm, 200.cm, 300.cm)
                        )
                    )
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection(
                AzimuthDirection.Custom    (45.degrees),
                InclinationDirection.Custom(30.degrees)
            )
            result.initialPosition shouldBe PostFireboxInitialPosition(100.cm, 200.cm, 300.cm)
        }

        "preserves non-deprecated elements in slot descriptors" in {
            val section   = AddFlowOnlyPipeElement_15544_V3.AddSectionHorizontal("my-section", 150.cm)
            val roughness = SetFlowOnlyPipeProp_15544_V3.SetRoughness(0.5.mm)

            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    flowOnlySlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Right, InclinationDirection.Up),
                            roughness,
                            section
                        )
                    )
                )
            )

            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    // tracking ops stripped, only migrated V3→V4 elements remain
                    d.size shouldBe 2
                    (d.exists:
                        case afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4.SetRoughness(r) => r == 0.5.mm
                        case _                                                                  => false
                    ) shouldBe true
                    (d.exists:
                        case afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.AddSectionHorizontal(n, l) =>
                            n == "my-section" && l == 150.cm
                        case _                                                                                => false
                    ) shouldBe true
                case other                                   => fail(s"Expected FlueSlot, got $other")
        }

        "extracts from first slot even when later slots have initial elements" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    flowOnlySlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Right, InclinationDirection.Up),
                            flowOnlyInitialPos(10.cm, 20.cm, 30.cm                            )
                        )
                    ),
                    flowOnlySlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Left, InclinationDirection.Down),
                            flowOnlyInitialPos(99.cm, 88.cm, 77.cm                             )
                        )
                    )
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection(
                AzimuthDirection.Right,
                InclinationDirection.Up
            )
            result.initialPosition shouldBe PostFireboxInitialPosition(10.cm, 20.cm, 30.cm)

            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    hasTrackingFlowOnlyV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                   => fail(s"Expected FlueSlot, got $other")

            result.slots(1) match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    hasTrackingFlowOnlyV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                   => fail(s"Expected FlueSlot, got $other")
        }

        "handles mixed flow-only and thermal slots" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    flowOnlySlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Right, InclinationDirection.Up),
                            flowOnlyInitialPos(10.cm, 20.cm, 30.cm                            )
                        )
                    ),
                    thermalSlot (
                        Seq(
                            thermalInitialDir                       (AzimuthDirection.Front, InclinationDirection.Horizontal),
                            thermalInitialPos                       (5.cm, 15.cm, 25.cm                                     ),
                            SetThermalPipeProp_13384_V3.SetRoughness(0.3.mm                                                 )
                        )
                    ),
                    chimneySlot(Seq.empty)
                )
            )

            result.initialDirection shouldBe PostFireboxInitialDirection(
                AzimuthDirection.Right,
                InclinationDirection.Up
            )
            result.initialPosition shouldBe PostFireboxInitialPosition(10.cm, 20.cm, 30.cm)

            result.slots(1) match
                case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
                    hasTrackingThermalV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                    (d.exists:
                        case afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.SetRoughness(r) => r == 0.3.mm
                        case _                                                                 => false
                    ) shouldBe true
                case other                                          => fail(s"Expected ThermalFlueSlot, got $other")
        }
        "top-level flow-only SetNumberOfFlows survives migration as ChannelTopologyOp" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    flowOnlySlot(
                        Seq(SetFlowOnlyPipeProp_15544_V3.SetNumberOfFlows(NbOfFlows(2)))
                    )
                )
            )
            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    (d.exists:
                        case afpma.firecalc.dto.v7.FlowOnlyChannelTopologyOp_15544_V4.SetNumberOfFlows(n) =>
                            n == NbOfFlows(2)
                        case _                                                                            => false
                    ) shouldBe true
                case other                                   => fail(s"Expected FlueSlot, got $other")
        }
        "top-level thermal SetNumberOfFlows survives migration as ThermalChannelTopologyOp" in {
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(
                    thermalSlot(
                        Seq(SetThermalPipeProp_13384_V3.SetNumberOfFlows(NbOfFlows(2)))
                    )
                )
            )
            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
                    (d.exists:
                        case afpma.firecalc.dto.v7.ThermalChannelTopologyOp_13384_V4.SetNumberOfFlows(n) =>
                            n == NbOfFlows(2)
                        case _                                                                           => false
                    ) shouldBe true
                case other                                          => fail(s"Expected ThermalFlueSlot, got $other")
        }
        "SetNumberOfFlows inside SetPropertiesInBatch.props is removed, sibling properties remain" in {
            val batch  = SetThermalPipeProp_13384_V3.SetPropertiesInBatch(
                "batch1",
                Seq   (
                    SetThermalPipeProp_13384_V3.SetInnerShape   (PipeShape.Circle(20.cm)),
                    SetThermalPipeProp_13384_V3.SetNumberOfFlows(NbOfFlows(2)           )
                )
            )
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(thermalSlot(Seq(batch)))
            )
            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
                    (d.exists:
                        case afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.SetPropertiesInBatch(name, props, _) =>
                            name == "batch1" &&
                            {
                                val hasInnerShape = props.collectFirst {
                                    case afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.SetInnerShape(shape)
                                        if shape == PipeShape.Circle(20.cm) =>
                                        ()
                                }.isDefined
                                hasInnerShape
                            }
                        case _                                                                                      => false
                    ) shouldBe true
                case other                                          => fail(s"Expected ThermalFlueSlot, got $other")
        }
        "SetNumberOfFlows inside LinedFlue.liner.props is removed, sibling liner properties remain" in {
            val liner  = SetThermalPipeProp_13384_V3.SetPropertiesInBatch(
                "liner-batch",
                Seq   (
                    SetThermalPipeProp_13384_V3.SetInnerShape   (PipeShape.Circle(20.cm)),
                    SetThermalPipeProp_13384_V3.SetNumberOfFlows(NbOfFlows(2)           )
                )
            )
            val casing = SetThermalPipeProp_13384_V3.SetPropertiesInBatch(
                "casing-batch",
                Seq(SetThermalPipeProp_13384_V3.SetInnerShape(PipeShape.Circle(30.cm)))
            )
            val lined  = SetThermalPipeProp_13384_V3.LinedFlue(
                "my-lined",
                liner,
                AirSpaceDetailed_V2.WithoutAirSpace_V2,
                casing
            )
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(thermalSlot(Seq(lined)))
            )
            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
                    (d.exists:
                        case afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.LinedFlue(name, liner, _, _) =>
                            name == "my-lined" &&
                            {
                                val hasInnerShape = liner.props.collectFirst {
                                    case afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.SetInnerShape(shape)
                                        if shape == PipeShape.Circle(20.cm) =>
                                        ()
                                }.isDefined
                                hasInnerShape
                            }
                        case _                                                                              => false
                    ) shouldBe true
                case other                                          => fail(s"Expected ThermalFlueSlot, got $other")
        }
        "SetNumberOfFlows inside LinedFlue.casing.props is removed, sibling casing properties remain" in {
            val liner  = SetThermalPipeProp_13384_V3.SetPropertiesInBatch(
                "liner-batch",
                Seq(SetThermalPipeProp_13384_V3.SetInnerShape(PipeShape.Circle(20.cm)))
            )
            val casing = SetThermalPipeProp_13384_V3.SetPropertiesInBatch(
                "casing-batch",
                Seq   (
                    SetThermalPipeProp_13384_V3.SetInnerShape   (PipeShape.Circle(30.cm)),
                    SetThermalPipeProp_13384_V3.SetNumberOfFlows(NbOfFlows(2)           )
                )
            )
            val lined  = SetThermalPipeProp_13384_V3.LinedFlue(
                "my-lined",
                liner,
                AirSpaceDetailed_V2.WithoutAirSpace_V2,
                casing
            )
            val result = transformers.normalizeToPostFireboxPipes(
                Seq(thermalSlot(Seq(lined)))
            )
            result.slots(0) match
                case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
                    (d.exists:
                        case afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.LinedFlue(name, _, _, casing) =>
                            name == "my-lined" &&
                            {
                                val hasInnerShape = casing.props.collectFirst {
                                    case afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.SetInnerShape(shape)
                                        if shape == PipeShape.Circle(30.cm) =>
                                        ()
                                }.isDefined
                                hasInnerShape
                            }
                        case _                                                                               => false
                    ) shouldBe true
                case other                                          => fail(s"Expected ThermalFlueSlot, got $other")
        }
    }

    "FireCalcYAML_V6 to FireCalcYAML_V7 migration" - {

        "moves initial direction and position to PostFireboxPipes and removes deprecated post-firebox state" in {
            val v6       = minimalV6(
                Seq (
                    flowOnlySlot (
                        Seq(
                            flowOnlyInitialDir                                  (AzimuthDirection.Left, InclinationDirection.Horizontal),
                            flowOnlyInitialPos                                  (-21.cm, 9.cm, 63.cm                                   ),
                            AddFlowOnlyPipeElement_15544_V3.AddSectionHorizontal("sortie foyer", 317.mm                                )
                        )
                    ),
                    connectorSlot(
                        Seq(
                            thermalInitialDir(AzimuthDirection.Front, InclinationDirection.Up),
                            thermalInitialPos(1.cm, 2.cm, 3.cm                               )
                        )
                    )
                )
            )
            val migrated = FireCalcYAMLMigrations.migrateV6ToV7(v6)

            migrated.post_firebox_pipes.initialDirection shouldBe PostFireboxInitialDirection(
                AzimuthDirection.Left,
                InclinationDirection.Horizontal
            )
            migrated.post_firebox_pipes.initialPosition shouldBe PostFireboxInitialPosition(-21.cm, 9.cm, 63.cm)

            migrated.post_firebox_pipes.slots(0) match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    hasTrackingFlowOnlyV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                   => fail(s"Expected FlueSlot, got $other")

            migrated.post_firebox_pipes.slots(1) match
                case PostFireboxPipeDescrSlot_V7.ConnectorSlot(d) =>
                    hasTrackingThermalV7(d) shouldBe false // tracking ops stripped from post-firebox slots
                case other                                        => fail(s"Expected ConnectorSlot, got $other")
        }
        "air_intake_descr is migrated to V7 flow-only 13384 type" in {
            val v6       = minimalV6(
                Seq(
                    flowOnlySlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Right, InclinationDirection.Up),
                            flowOnlyInitialPos(10.cm, 20.cm, 30.cm                            )
                        )
                    ),
                    connectorSlot(Seq.empty)
                )
            ).copy(
                air_intake_descr = Seq(
                    SetFlowOnlyPipeProp_13384_V3.SetInnerShape(PipeShape.Circle(20.cm))
                )
            )
            val migrated = FireCalcYAMLMigrations.migrateV6ToV7(v6)
            (migrated.air_intake_descr.exists:
                case afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_13384_V4.SetInnerShape(shape) =>
                    shape == PipeShape.Circle(20.cm)
                case _                                                                       => false
            ) shouldBe true
        }
    }

end V6ToV7TransformerSuite
