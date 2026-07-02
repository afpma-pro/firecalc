/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.domain.{IsBackendForbidden, PipeShape}
import afpma.firecalc.dto.generators.AllGenerators
import afpma.firecalc.dto.instances.V7Instances.given
import afpma.firecalc.dto.v4.AirSpaceDetailed_V2
import afpma.firecalc.dto.v7.{
    AirIntakePosition,
    BackendForbiddenDtoChecker,
    FireCalcYAML_V7,
    FlowOnlyPipeDescr_13384_V4,
    FlowOnlyPipeDescr_15544_V4,
    FramedAirIntakePipes,
    FramedPostFireboxPipes,
    PostFireboxPipeDescrSlot_V7,
    PostFireboxStartPosition,
    SetFlowOnlyPipeProp_13384_V4,
    SetFlowOnlyPipeProp_15544_V4,
    SetThermalPipeProp_13384_V4,
    ThermalPipeDescr_13384_V4,
    ThermalChannelTopologyOp_13384_V4
}

import afpma.firecalc.units.coulombutils.meters

import io.circe.syntax._

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import afpma.firecalc.dto.v7.FlowOnlyChannelTopologyOp_15544_V4
import afpma.firecalc.dto.v7.FlowOnlyChannelTopologyOp_13384_V4
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_13384_V4
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4

/**
 * Tests for the dev-only `SetInnerShapePreventSectionGeometryChangeAuto` DTO variant
 * and its backend-forbidden enforcement marker `IsBackendForbidden`.
 *
 * Covers (per the feature spec):
 *   1. Marker membership — the three `*PreventSectionGeometryChangeAuto` variants
 *      extend `IsBackendForbidden`; plain `SetInnerShape` does not.
 *   2. circe round-trip + discriminator — the prevent variant is wire-distinguishable
 *      from plain `SetInnerShape` (single-key wrapper = constructor name, default
 *      circe semiauto encoding with no `Configuration`).
 *   3. `BackendForbiddenDtoChecker` walker — finds the variant in `air_intake_pipes`,
 *      in each walkable `post_firebox_pipes` slot kind, nested inside
 *      `SetPropertiesInBatch.props` and `LinedFlue.liner`/`casing`, and returns `Nil`
 *      for a project with only plain `SetInnerShape`.
 */
class BackendForbiddenDtoSuite extends AnyFreeSpec with Matchers:

    private val shapeA = PipeShape.Circle(0.2.meters)
    private val shapeB = PipeShape.Rectangle(0.2.meters, 0.3.meters)

    // Build a base FireCalcYAML_V7 from the generator, then patch the pipe
    // containers to inject controlled descriptors. Other fields stay valid.
    // `.sample` can return None on complex generators, so retry a few times.
    private def baseFc: FireCalcYAML_V7 =
        Iterator
            .continually(AllGenerators.genFireCalcYAML_V7.sample)
            .find(_.isDefined)
            .flatten
            .getOrElse(fail("genFireCalcYAML_V7 sample failed after retries"))

    // Empty containers reuse the base project's framing fields so they stay valid.
    private def emptyAirIntakeOf(fc: FireCalcYAML_V7): FramedAirIntakePipes =
        FramedAirIntakePipes(fc.air_intake_pipes.initialDir, AirIntakePosition.InitialAuto, descr = Nil)

    private def emptyPostFireboxOf(fc: FireCalcYAML_V7): FramedPostFireboxPipes =
        FramedPostFireboxPipes(
            fc.post_firebox_pipes.initialDirection,
            PostFireboxStartPosition.Auto,
            slots = Nil
        )

    // ── 1. Marker membership ────────────────────────────────────────────────

    "IsBackendForbidden marker membership" - {

        "Thermal 13384 prevent variant is forbidden" in {
            SetThermalPipeProp_13384_V4
                .SetInnerShapePreventSectionGeometryChangeAuto(shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(true)
        }

        "FlowOnly 13384 prevent variant is forbidden" in {
            SetFlowOnlyPipeProp_13384_V4
                .SetInnerShapePreventSectionGeometryChangeAuto(shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(true)
        }

        "FlowOnly 15544 prevent variant is forbidden" in {
            SetFlowOnlyPipeProp_15544_V4
                .SetInnerShapePreventSectionGeometryChangeAuto(shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(true)
        }

        "plain SetInnerShape (Thermal 13384) is NOT forbidden" in {
            SetThermalPipeProp_13384_V4
                .SetInnerShape(shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(false)
        }

        "plain SetInnerShape (FlowOnly 13384) is NOT forbidden" in {
            SetFlowOnlyPipeProp_13384_V4
                .SetInnerShape(shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(false)
        }

        "plain SetInnerShape (FlowOnly 15544) is NOT forbidden" in {
            SetFlowOnlyPipeProp_15544_V4
                .SetInnerShape(shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(false)
        }
    }

    // ── 2. circe round-trip + discriminator ─────────────────────────────────

    "circe round-trip and wire discriminator" - {

        // The V7 sealed-trait encoders/decoders are semiauto-derived with no
        // circe `Configuration`, so each case class encodes as a single-key object
        // keyed by its constructor name. The prevent variant and plain SetInnerShape
        // must therefore be wire-distinguishable. Encoders are derived on the
        // SetSingleProp sealed trait, so we upcast before encoding.
        "Thermal 13384 SetSingleProp round-trips both variants with distinct wrapper keys" in {
            val plain  : SetThermalPipeProp_13384_V4.SetSingleProp =
                SetThermalPipeProp_13384_V4.SetInnerShape(shapeA)
            val prevent: SetThermalPipeProp_13384_V4.SetSingleProp =
                SetThermalPipeProp_13384_V4.SetInnerShapePreventSectionGeometryChangeAuto(shapeB)
            val plainJson = plain.asJson
            val preventJson = prevent.asJson
            plainJson.as[SetThermalPipeProp_13384_V4.SetSingleProp] shouldBe Right(plain)
            preventJson.as[SetThermalPipeProp_13384_V4.SetSingleProp] shouldBe Right(prevent)
            val plainKey    = plainJson.asObject.flatMap(_.keys.toList.headOption).value
            val preventKey  = preventJson.asObject.flatMap(_.keys.toList.headOption).value
            plainKey.shouldBe  ("SetInnerShape"                                )
            preventKey.shouldBe("SetInnerShapePreventSectionGeometryChangeAuto")
            (plainKey == preventKey).shouldBe(false)
        }
    }

    // ── 3. BackendForbiddenDtoChecker walker ─────────────────────────────────

    "BackendForbiddenDtoChecker walker" - {

        "returns Nil for a project with only plain SetInnerShape" in {
            val fc = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc)
                    .copy(descr = Seq(SetFlowOnlyPipeProp_13384_V4.SetInnerShape(shapeA): FlowOnlyPipeDescr_13384_V4)),
                post_firebox_pipes = emptyPostFireboxOf(baseFc)
            )
            BackendForbiddenDtoChecker.findForbidden(fc).shouldBe(Nil)
        }

        "finds the variant in air_intake_pipes.descr (FlowOnly 13384)" in {
            val fc   = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc).copy(descr =
                    Seq(
                        SetFlowOnlyPipeProp_13384_V4.SetInnerShapePreventSectionGeometryChangeAuto(
                            shapeA
                        ): FlowOnlyPipeDescr_13384_V4
                    )
                ),
                post_firebox_pipes = emptyPostFireboxOf(baseFc)
            )
            val hits = BackendForbiddenDtoChecker.findForbidden(fc)
            hits.size.shouldBe             (1                                              )
            hits.head.dto.getClass.getSimpleName
                .shouldBe                  ("SetInnerShapePreventSectionGeometryChangeAuto")
            hits.head.elementIndex.shouldBe(0                                              )
        }

        "finds the variant in a FlueSlot (FlowOnly 15544)" in {
            val fc = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc),
                post_firebox_pipes = emptyPostFireboxOf(baseFc).copy(slots =
                    Seq(
                        PostFireboxPipeDescrSlot_V7.FlueSlot(
                            Seq(
                                SetFlowOnlyPipeProp_15544_V4.SetInnerShapePreventSectionGeometryChangeAuto(
                                    shapeA
                                ): FlowOnlyPipeDescr_15544_V4
                            )
                        )
                    )
                )
            )
            BackendForbiddenDtoChecker.findForbidden(fc).size.shouldBe(1)
        }

        "finds the variant in a ThermalFlueSlot (Thermal 13384)" in {
            val fc = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc),
                post_firebox_pipes = emptyPostFireboxOf(baseFc).copy(slots =
                    Seq(
                        PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(
                            Seq(
                                SetThermalPipeProp_13384_V4.SetInnerShapePreventSectionGeometryChangeAuto(
                                    shapeA
                                ): ThermalPipeDescr_13384_V4
                            )
                        )
                    )
                )
            )
            BackendForbiddenDtoChecker.findForbidden(fc).size.shouldBe(1)
        }

        "finds the variant in a ConnectorSlot (Thermal 13384)" in {
            val fc = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc),
                post_firebox_pipes = emptyPostFireboxOf(baseFc).copy(slots =
                    Seq(
                        PostFireboxPipeDescrSlot_V7.ConnectorSlot(
                            Seq(
                                SetThermalPipeProp_13384_V4.SetInnerShapePreventSectionGeometryChangeAuto(
                                    shapeA
                                ): ThermalPipeDescr_13384_V4
                            )
                        )
                    )
                )
            )
            BackendForbiddenDtoChecker.findForbidden(fc).size.shouldBe(1)
        }

        "finds the variant in a ChimneySlot (Thermal 13384)" in {
            val fc = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc),
                post_firebox_pipes = emptyPostFireboxOf(baseFc).copy(slots =
                    Seq(
                        PostFireboxPipeDescrSlot_V7.ChimneySlot(
                            Seq(
                                SetThermalPipeProp_13384_V4.SetInnerShapePreventSectionGeometryChangeAuto(
                                    shapeA
                                ): ThermalPipeDescr_13384_V4
                            )
                        )
                    )
                )
            )
            BackendForbiddenDtoChecker.findForbidden(fc).size.shouldBe(1)
        }

        "skips NoFlueSlot" in {
            val fc = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc),
                post_firebox_pipes = emptyPostFireboxOf(baseFc)
                    .copy(slots = Seq(PostFireboxPipeDescrSlot_V7.NoFlueSlot))
            )
            BackendForbiddenDtoChecker.findForbidden(fc).shouldBe(Nil)
        }

        "finds the variant nested inside a Thermal SetPropertiesInBatch.props" in {
            val batch = SetThermalPipeProp_13384_V4.SetPropertiesInBatch(
                batch_name = "linerb",
                props      = Seq(
                    SetThermalPipeProp_13384_V4.SetInnerShape                                (shapeA),
                    SetThermalPipeProp_13384_V4.SetInnerShapePreventSectionGeometryChangeAuto(shapeB)
                )
            )
            val fc    = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc),
                post_firebox_pipes = emptyPostFireboxOf(baseFc).copy(slots =
                    Seq(
                        PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(Seq(batch: ThermalPipeDescr_13384_V4))
                    )
                )
            )
            BackendForbiddenDtoChecker.findForbidden(fc).size.shouldBe(1)
        }

        "finds the variant nested inside a LinedFlue liner and casing" in {
            val linerWithPrevent  = SetThermalPipeProp_13384_V4.SetPropertiesInBatch(
                batch_name = "liner",
                props      = Seq(SetThermalPipeProp_13384_V4.SetInnerShapePreventSectionGeometryChangeAuto(shapeA))
            )
            val casingWithPrevent = SetThermalPipeProp_13384_V4.SetPropertiesInBatch(
                batch_name = "casing",
                props      = Seq(SetThermalPipeProp_13384_V4.SetInnerShapePreventSectionGeometryChangeAuto(shapeB))
            )
            val linedFlue         = SetThermalPipeProp_13384_V4.LinedFlue(
                batch_name = "lf",
                liner      = linerWithPrevent,
                air_space  = AirSpaceDetailed_V2.WithoutAirSpace_V2,
                casing     = casingWithPrevent
            )
            val fc                = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc),
                post_firebox_pipes = emptyPostFireboxOf(baseFc).copy(slots =
                    Seq(
                        PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(Seq(linedFlue: ThermalPipeDescr_13384_V4))
                    )
                )
            )
            // one from liner + one from casing
            BackendForbiddenDtoChecker.findForbidden(fc).size.shouldBe(2)
        }
    }

    // ── 4. Split/Merge types are NOT backend-forbidden ────────────────────

    "Split/Merge types are NOT backend-forbidden" - {

        "FlowOnly 15544 SplitSingleFlowIntoTwoFlowsWith90DegTurn is NOT forbidden" in {
            AddFlowOnlyPipeElement_15544_V4
                .SplitSingleFlowIntoTwoFlowsWith90DegTurn("split", None, shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(false)
        }

        "FlowOnly 15544 MergeTwoFlowsIntoSingleWith90DegTurn is NOT forbidden" in {
            AddFlowOnlyPipeElement_15544_V4
                .MergeTwoFlowsIntoSingleWith90DegTurn("merge", None, shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(false)
        }

        "FlowOnly 13384 SplitSingleFlowIntoTwoFlowsWith90DegTurn is NOT forbidden" in {
            AddFlowOnlyPipeElement_13384_V4
                .SplitSingleFlowIntoTwoFlowsWith90DegTurn("split", None, shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(false)
        }

        "FlowOnly 13384 MergeTwoFlowsIntoSingleWith90DegTurn is NOT forbidden" in {
            AddFlowOnlyPipeElement_13384_V4
                .MergeTwoFlowsIntoSingleWith90DegTurn("merge", None, shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(false)
        }

        "Thermal 13384 SplitSingleFlowIntoTwoFlowsWith90DegTurn is NOT forbidden" in {
            AddThermalPipeElement_13384_V4
                .SplitSingleFlowIntoTwoFlowsWith90DegTurn("split", None, shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(false)
        }

        "Thermal 13384 MergeTwoFlowsIntoSingleWith90DegTurn is NOT forbidden" in {
            AddThermalPipeElement_13384_V4
                .MergeTwoFlowsIntoSingleWith90DegTurn("merge", None, shapeA)
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(false)
        }
    }

    // ── 5. SetNumberOfFlows is backend-forbidden ─────────────────────────

    "SetNumberOfFlows is backend-forbidden" - {

        "FlowOnly 15544 SetNumberOfFlows is forbidden" in {
            FlowOnlyChannelTopologyOp_15544_V4
                .SetNumberOfFlows(afpma.firecalc.dto.common.NbOfFlows(2))
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(true)
        }

        "FlowOnly 13384 SetNumberOfFlows is forbidden" in {
            FlowOnlyChannelTopologyOp_13384_V4
                .SetNumberOfFlows(afpma.firecalc.dto.common.NbOfFlows(2))
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(true)
        }

        "Thermal 13384 SetNumberOfFlows is forbidden" in {
            ThermalChannelTopologyOp_13384_V4
                .SetNumberOfFlows(afpma.firecalc.dto.common.NbOfFlows(2))
                .isInstanceOf[IsBackendForbidden]
                .shouldBe(true)
        }

        "BackendForbiddenDtoChecker detects SetNumberOfFlows in FlueSlot" in {
            val fc   = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc),
                post_firebox_pipes = emptyPostFireboxOf(baseFc).copy(slots =
                    Seq(
                        PostFireboxPipeDescrSlot_V7.FlueSlot(
                            Seq(
                                SetFlowOnlyPipeProp_15544_V4.SetInnerShape         (shapeA),
                                FlowOnlyChannelTopologyOp_15544_V4.SetNumberOfFlows(
                                    afpma.firecalc.dto.common.NbOfFlows(2)
                                ): FlowOnlyPipeDescr_15544_V4
                            )
                        )
                    )
                )
            )
            val hits = BackendForbiddenDtoChecker.findForbidden(fc)
            hits.size.shouldBe                           (1                 )
            hits.head.dto.getClass.getSimpleName.shouldBe("SetNumberOfFlows")
            hits.head.elementIndex.shouldBe              (1                 ) // after SetInnerShape at index 0
        }

        "BackendForbiddenDtoChecker detects SetNumberOfFlows in ThermalFlueSlot" in {
            val fc   = baseFc.copy(
                air_intake_pipes   = emptyAirIntakeOf(baseFc),
                post_firebox_pipes = emptyPostFireboxOf(baseFc).copy(slots =
                    Seq(
                        PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(
                            Seq(
                                SetThermalPipeProp_13384_V4.SetInnerShape         (shapeA),
                                ThermalChannelTopologyOp_13384_V4.SetNumberOfFlows(
                                    afpma.firecalc.dto.common.NbOfFlows(2)
                                ): ThermalPipeDescr_13384_V4
                            )
                        )
                    )
                )
            )
            val hits = BackendForbiddenDtoChecker.findForbidden(fc)
            hits.size.shouldBe                           (1                 )
            hits.head.dto.getClass.getSimpleName.shouldBe("SetNumberOfFlows")
            hits.head.elementIndex.shouldBe              (1                 ) // after SetInnerShape at index 0
        }
    }
