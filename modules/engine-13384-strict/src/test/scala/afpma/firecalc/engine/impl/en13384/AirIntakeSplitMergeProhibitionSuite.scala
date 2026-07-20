/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.engine.models.AirIntakePipeT
import afpma.firecalc.engine.validation.SplitMergeNotAllowedInAirIntake

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AirIntakeSplitMergeProhibitionSuite extends AnyFlatSpec with Matchers:

    given AirIntakePipeT = AirIntakePipeT

    private val horizontalDir =
        PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)

    private def makeFlowOnlyBuilder(initialDir: PipeInitialDirection) =
        FlowOnlyIncrementalBuilder_13384
            .makeFor[AirIntakePipeT]
            .withInitialDirection(initialDir)

    private def makeThermalBuilder(initialDir: PipeInitialDirection) =
        ThermalIncrementalBuilder_13384
            .makeFor[AirIntakePipeT]
            .withInitialDirection(initialDir)

    private val flowOnlySetup = Vector(
        SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(20.cm)),
        SetFlowOnlyPipeProp_13384.SetRoughness(1.mm)
    )

    private val thermalSetup = Vector(
        SetThermalPipeProp_13384.SetInnerShape(PipeShape.Circle(20.cm)),
        SetThermalPipeProp_13384.SetRoughness   (1.mm                          ),
        SetThermalPipeProp_13384.SetMaterial  (
            afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()
        ),
        SetThermalPipeProp_13384.SetLayer       (2.mm, WattsPerMeterKelvin(1.2)),
        SetThermalPipeProp_13384.SetPipeLocation(PipeLocation.HeatedArea       )
    )

    "FlowOnlyIncrementalBuilder_13384 with AirIntakePipeT" should "reject split element" in {
        val builder = makeFlowOnlyBuilder(horizontalDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("section", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(
                        AbsoluteDirection(
                            AzimuthDirection.Right,
                            InclinationDirection.Horizontal
                        )
                    )
                )
            ))*
        )
        val errors  = descr.toFullDescr().toEither.left.toOption.get
        errors.exists(_.isInstanceOf[SplitMergeNotAllowedInAirIntake]) shouldBe true
    }

    it should "reject merge element" in {
        val builder = makeFlowOnlyBuilder(horizontalDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                   ("section", 1.meters),
                AddFlowOnlyPipeElement_13384.MergeTwoFlowsIntoSingleWith90DegTurn(
                    "merge",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(
                        AbsoluteDirection(
                            AzimuthDirection.Right,
                            InclinationDirection.Horizontal
                        )
                    )
                )
            ))*
        )
        val errors  = descr.toFullDescr().toEither.left.toOption.get
        errors.exists(_.isInstanceOf[SplitMergeNotAllowedInAirIntake]) shouldBe true
    }

    "ThermalIncrementalBuilder_13384 with AirIntakePipeT" should "reject split element" in {
        val builder = makeThermalBuilder(horizontalDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.AddSectionSlopped                       ("section", 1.meters),
                AddThermalPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(
                        AbsoluteDirection(
                            AzimuthDirection.Right,
                            InclinationDirection.Horizontal
                        )
                    )
                )
            ))*
        )
        val errors  = descr.toFullDescr().toEither.left.toOption.get
        errors.exists(_.isInstanceOf[SplitMergeNotAllowedInAirIntake]) shouldBe true
    }

    it should "reject merge element" in {
        val builder = makeThermalBuilder(horizontalDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.AddSectionSlopped                   ("section", 1.meters),
                AddThermalPipeElement_13384.MergeTwoFlowsIntoSingleWith90DegTurn(
                    "merge",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(
                        AbsoluteDirection(
                            AzimuthDirection.Right,
                            InclinationDirection.Horizontal
                        )
                    )
                )
            ))*
        )
        val errors  = descr.toFullDescr().toEither.left.toOption.get
        errors.exists(_.isInstanceOf[SplitMergeNotAllowedInAirIntake]) shouldBe true
    }

    "FlowOnlyIncrementalBuilder_13384 with AirIntakePipeT" should "allow air intake without split/merge" in {
        val builder = makeFlowOnlyBuilder(horizontalDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped("section", 1.meters)
            ))*
        )
        descr.toFullDescr().isValid shouldBe true
    }
    it should "accumulate air intake and geometry errors" in {
        // Builder WITHOUT initial direction → geometry validation fails
        val builder = FlowOnlyIncrementalBuilder_13384.makeFor[AirIntakePipeT]
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("section", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(
                        AbsoluteDirection(
                            AzimuthDirection.Right,
                            InclinationDirection.Horizontal
                        )
                    )
                )
            ))*
        )
        val errors  = descr.toFullDescr().toEither.left.toOption.get
        errors.exists(_.isInstanceOf[SplitMergeNotAllowedInAirIntake]) shouldBe true
        errors.exists(_.isInstanceOf[afpma.firecalc.engine.standard.GeometryWithoutInitialDirection]) shouldBe true
        errors.exists(_.isInstanceOf[afpma.firecalc.engine.standard.FinalDirWithoutInitialDirection]) shouldBe true
    }
    it should "accumulate multiple error types including split-merge, geometry and final-direction" in {
        // Builder WITHOUT initial direction → all three validations fail
        val builder = FlowOnlyIncrementalBuilder_13384.makeFor[AirIntakePipeT]
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("section", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(
                        AbsoluteDirection(
                            AzimuthDirection.Right,
                            InclinationDirection.Horizontal
                        )
                    )
                )
            ))*
        )
        val errors  = descr.toFullDescr().toEither.left.toOption.get
        errors.exists(_.isInstanceOf[SplitMergeNotAllowedInAirIntake]) shouldBe true
        errors.exists(_.isInstanceOf[afpma.firecalc.engine.standard.FinalDirWithoutInitialDirection]) shouldBe true
    }

end AirIntakeSplitMergeProhibitionSuite
