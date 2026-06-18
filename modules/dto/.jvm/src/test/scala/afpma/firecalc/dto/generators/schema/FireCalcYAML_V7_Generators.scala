/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.schema

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.generators.common.CommonTypes_Generators
import afpma.firecalc.dto.generators.common.StoveParams_Generators
import afpma.firecalc.dto.generators.firebox.Firebox_V4_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_13384_V4_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_15544_V4_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetThermalPipeProp_13384_V4_Generators
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.{FramedAirIntakePipes, AirIntakePosition}
import afpma.firecalc.dto.common.{PipeInitialDirection, Position3D}

import org.scalacheck.Gen

/**
 * FireCalcYAML_V7_Generators
 *
 * Generates complete FireCalcYAML_V7 instances with a `post_firebox_pipes` field that
 * uses the V7 `FramedPostFireboxPipes` wrapper type with `PostFireboxPipeDescrSlot_V7` slots.
 *
 * The generator only produces topologies that are valid according to the grammar
 * enforced by `PostFireboxPipeChain.validated`:
 *   - zero or more flue-region slots (FlueSlot / ThermalFlueSlot)
 *   - zero or one ConnectorSlot immediately after the last flue slot
 *   - exactly one ChimneySlot as the last element
 */
trait FireCalcYAML_V7_Generators
    extends CommonTypes_Generators
    with StoveParams_Generators
    with Firebox_V4_Generators
    with SetFlowOnlyPipeProp_13384_V4_Generators
    with SetFlowOnlyPipeProp_15544_V4_Generators
    with SetThermalPipeProp_13384_V4_Generators:

    // ── PipeInitialDirection / Position ──────────────────────────

    def genPipeInitialDirection: Gen[PipeInitialDirection] =
        for
            azimuth     <- Gen.oneOf(
                afpma.firecalc.dto.v4.AzimuthDirection.Front,
                afpma.firecalc.dto.v4.AzimuthDirection.Right,
                afpma.firecalc.dto.v4.AzimuthDirection.Rear,
                afpma.firecalc.dto.v4.AzimuthDirection.Left
            )
            inclination <- Gen.oneOf(
                afpma.firecalc.dto.v4.InclinationDirection.Up,
                afpma.firecalc.dto.v4.InclinationDirection.Down,
                afpma.firecalc.dto.v4.InclinationDirection.Horizontal
            )
        yield PipeInitialDirection(azimuth, inclination)

    def genPosition3D: Gen[Position3D] =
        for
            x <- Gen.choose(-10.0, 10.0).map(_.meters)
            y <- Gen.choose(-10.0, 10.0).map(_.meters)
            z <- Gen.choose(-10.0, 10.0).map(_.meters)
        yield Position3D(x, y, z)

    def genAirIntakePosition: Gen[AirIntakePosition] =
        Gen.oneOf(
            Gen.const        (AirIntakePosition.InitialAuto  ),
            genPosition3D.map(AirIntakePosition.InitialManual),
            Gen.const        (AirIntakePosition.FinalAuto    ),
            genPosition3D.map(AirIntakePosition.FinalManual  )
        )

    def genFramedAirIntakePipes: Gen[FramedAirIntakePipes] =
        for
            initialDir <- genPipeInitialDirection
            position   <- genAirIntakePosition
            descr      <- genFlowOnlyPipeDescr_13384_V4_Seq
        yield FramedAirIntakePipes(initialDir, position, descr)

    // ── Slot generators ─────────────────────────────────────────────────

    def genFlueSlot: Gen[PostFireboxPipeDescrSlot_V7.FlueSlot] =
        genFlowOnlyPipeDescr_15544_V4_Seq.map(PostFireboxPipeDescrSlot_V7.FlueSlot(_))

    def genThermalFlueSlot: Gen[PostFireboxPipeDescrSlot_V7.ThermalFlueSlot] =
        genThermalPipeDescr_13384_V4_Seq.map(PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(_))

    def genConnectorSlot: Gen[PostFireboxPipeDescrSlot_V7.ConnectorSlot] =
        genThermalPipeDescr_13384_V4_Seq.map(PostFireboxPipeDescrSlot_V7.ConnectorSlot(_))

    def genChimneySlot: Gen[PostFireboxPipeDescrSlot_V7.ChimneySlot] =
        genThermalPipeDescr_13384_V4_Seq.map(PostFireboxPipeDescrSlot_V7.ChimneySlot(_))

    def genAnyFlueSlot: Gen[PostFireboxPipeDescrSlot_V7] =
        Gen.oneOf(genFlueSlot, genThermalFlueSlot)

    // ── FramedPostFireboxPipes generator ──────────────────────────────────────

    def genFramedPostFireboxPipesN(n: Int): Gen[FramedPostFireboxPipes] =
        require(n >= 1 && n <= 8, s"n must be in [1,8], got $n")
        for
            initialDirection <- genPipeInitialDirection
            initialPosition  <- Gen.oneOf(
                Gen.const        (afpma.firecalc.dto.v7.PostFireboxStartPosition.Auto  ),
                genPosition3D.map(afpma.firecalc.dto.v7.PostFireboxStartPosition.Manual)
            )
            slots            <- genSlotsN(n)
        yield FramedPostFireboxPipes(initialDirection, initialPosition, slots)

    def genSlotsN(n: Int): Gen[Seq[PostFireboxPipeDescrSlot_V7]] =
        require(n >= 1 && n <= 8, s"n must be in [1,8], got $n")
        n match
            case 1 =>
                genChimneySlot.map(ch => Seq(ch))

            case 2 =>
                for
                    first   <- Gen.oneOf(genAnyFlueSlot, genConnectorSlot)
                    chimney <- genChimneySlot
                yield Seq(first, chimney)

            case _ =>
                Gen.oneOf(
                    // With connector: [flue × (n-2), connector, chimney]
                    for
                        flues     <- Gen.listOfN(n - 2, genAnyFlueSlot)
                        connector <- genConnectorSlot
                        chimney   <- genChimneySlot
                    yield flues ++ Seq(connector, chimney),
                    // Without connector: [flue × (n-1), chimney]
                    for
                        flues   <- Gen.listOfN(n - 1, genAnyFlueSlot)
                        chimney <- genChimneySlot
                    yield flues ++ Seq(chimney)
                )

    def genFramedPostFireboxPipes: Gen[FramedPostFireboxPipes] =
        Gen.choose(1, 8).flatMap(genFramedPostFireboxPipesN)

    // ── Complete FireCalcYAML_V7 ────────────────────────────────────────

    def genFireCalcYAML_V7: Gen[FireCalcYAML_V7] =
        for
            locale           <- genLocale
            displayUnits     <- genDisplayUnits
            method           <- genStandardOrComputationMethod
            projectDescr     <- genProjectDescr
            localConditions  <- genLocalConditions
            stoveParams      <- genStoveParams
            airIntakePipes   <- genFramedAirIntakePipes
            firebox          <- genFirebox_V4
            postFireboxPipes <- genFramedPostFireboxPipes
        yield FireCalcYAML_V7                       (
            version                        = FireCalcYAML_V7.VERSION,
            locale                         = locale,
            display_units                  = displayUnits,
            standard_or_computation_method = method,
            project_description            = projectDescr,
            local_conditions               = localConditions,
            stove_params                   = stoveParams,
            air_intake_pipes               = airIntakePipes,
            firebox                        = firebox,
            post_firebox_pipes             = postFireboxPipes
        )

end FireCalcYAML_V7_Generators
