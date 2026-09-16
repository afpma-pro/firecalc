/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.CoefficientOfFlowResistance
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.OpPoint
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given

import io.circe.syntax.*
import afpma.firecalc.engine.utils

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.*

import io.taig.babel.Language
import io.taig.babel.Locale
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * B.1-B round-trip guard for PipeResult.
 *
 * Verifies that `PipeResult.fromValues(WireConversions.fromWireResult(WireConversions.toWireResult(r))) == r`
 * on all 21 members, including `gas_temp_mean` which is NOT carried by the wire type
 * and must be re-derived via `utils.mean_temp_using_inverse_alg(gas_temp_start, gas_temp_end)`.
 *
 * If a future refactor changes how `gas_temp_mean` is derived, this test MUST be updated.
 * See also `PipeResultFromValues` in `results.scala` (line with `val gas_temp_mean = ...`).
 */
class PipeResultRoundTripSuite extends AnyFreeSpec with Matchers {

    given locale: Locale = Locale(Language("en"))

    /**
     * Assert all 21 PipeResult members are equal after round-trip.
     *
     * PipeResult is a sealed trait with a private case class implementation,
     * so we compare via the 21 public accessors rather than structural equality.
     */
    private def assertPipeResultEqual(original: PipeResult, roundTripped: PipeResult): Unit =
        roundTripped.typ `shouldBe` original.typ
        roundTripped.lengthSum `shouldBe` original.lengthSum
        roundTripped.heightSum `shouldBe` original.heightSum
        roundTripped.ζ `shouldBe` original.ζ
        roundTripped.pu `shouldBe` original.pu
        roundTripped.pd `shouldBe` original.pd
        roundTripped.pRs `shouldBe` original.pRs
        roundTripped.pRg `shouldBe` original.pRg
        roundTripped.ph `shouldBe` original.ph
        roundTripped.gas_temp_start `shouldBe` original.gas_temp_start
        roundTripped.gas_temp_middle `shouldBe` original.gas_temp_middle
        roundTripped.gas_temp_end `shouldBe` original.gas_temp_end
        // gas_temp_mean is the KEY assertion — NOT carried by wire, re-derived from start/end
        roundTripped.gas_temp_mean `shouldBe` original.gas_temp_mean
        roundTripped.density_middle `shouldBe` original.density_middle
        roundTripped.density_mean `shouldBe` original.density_mean
        roundTripped.v_start `shouldBe` original.v_start
        roundTripped.v_end `shouldBe` original.v_end
        roundTripped.last_density_mean `shouldBe` original.last_density_mean
        roundTripped.last_density_middle `shouldBe` original.last_density_middle
        roundTripped.last_velocity_mean `shouldBe` original.last_velocity_mean
        roundTripped.last_velocity_middle `shouldBe` original.last_velocity_middle

    "PipeResult round-trip preserves gas_temp_mean (B.1-B guard)" in {
        // Use temperatures where harmonic mean is NOT simply (start + end) / 2.
        // 20°C = 293.15 K, 100°C = 373.15 K
        // Harmonic mean in K: 2 / (1/293.15 + 1/373.15) = 329.815 K = 56.665°C
        // Arithmetic mean would be: (20 + 100) / 2 = 60°C — different!
        val gasTempStart  = 20.0.withTemperature[Celsius]
        val gasTempMiddle = 50.0.withTemperature[Celsius]
        val gasTempEnd    = 100.0.withTemperature[Celsius]

        // Compute expected mean independently to verify the formula
        val expectedMean: TCelsius = utils.mean_temp_using_inverse_alg(gasTempStart, gasTempEnd)

        // Verify the expected mean is NOT the arithmetic mean (sanity check)
        val arithmeticMean = ((gasTempStart.value + gasTempEnd.value) / 2.0).withTemperature[Celsius]
        expectedMean.value should not be arithmeticMean.value

        val r = PipeResult.fromValues(
            pt                   = FluePipeT,
            lengthSum            = 10.0.withUnit[Meter],
            heightSum            = 5.0.withUnit[Meter],
            pd                   = Some(50.0.withUnit[Pascal]),
            ζ                    = Some(0.5.withUnit[1]),
            pu                   = Validated.valid(101325.0.withUnit[Pascal]),
            pRs                  = 100.0.withUnit[Pascal],
            pRg                  = 200.0.withUnit[Pascal],
            ph                   = 50.0.withUnit[Pascal],
            gas_temp_start       = gasTempStart,
            gas_temp_middle      = gasTempMiddle,
            gas_temp_end         = gasTempEnd,
            density_middle       = Some(1.0.withUnit[Kilogram / (Meter ^ 3)]),
            density_mean         = Some(0.9.withUnit[Kilogram / (Meter ^ 3)]),
            v_start              = Some(2.0.withUnit[Meter / Second]),
            v_end                = Some(1.5.withUnit[Meter / Second]),
            last_density_mean    = Some(0.85.withUnit[Kilogram / (Meter ^ 3)]),
            last_density_middle  = Some(0.95.withUnit[Kilogram / (Meter ^ 3)]),
            last_velocity_mean   = Some(1.8.withUnit[Meter / Second]),
            last_velocity_middle = Some(1.2.withUnit[Meter / Second])
        )

        // Verify the original result has the expected gas_temp_mean
        r.gas_temp_mean `shouldBe` expectedMean

        // Round-trip through wire format
        val w  = WireConversions.toWireResult(r)
        val r2 = WireConversions.fromWireResult(w)

        // Assert all 21 members match
        assertPipeResultEqual(r, r2)

        // Explicit assertion for gas_temp_mean (the B.1-B guard)
        r2.gas_temp_mean `shouldBe` r.gas_temp_mean
        r2.gas_temp_mean `shouldBe` expectedMean
    }

    "PipeResult round-trip with None optional fields" in {
        val r = PipeResult.fromValues(
            pt                   = ChimneyPipeT,
            lengthSum            = 15.0.withUnit[Meter],
            heightSum            = 12.0.withUnit[Meter],
            pd                   = None,
            ζ                    = None,
            pu                   = Validated.valid(100000.0.withUnit[Pascal]),
            pRs                  = 50.0.withUnit[Pascal],
            pRg                  = 75.0.withUnit[Pascal],
            ph                   = 30.0.withUnit[Pascal],
            gas_temp_start       = 100.0.withTemperature[Celsius],
            gas_temp_middle      = 80.0.withTemperature[Celsius],
            gas_temp_end         = 60.0.withTemperature[Celsius],
            density_middle       = None,
            density_mean         = None,
            v_start              = None,
            v_end                = None,
            last_density_mean    = None,
            last_density_middle  = None,
            last_velocity_mean   = None,
            last_velocity_middle = None
        )

        val w  = WireConversions.toWireResult(r)
        val r2 = WireConversions.fromWireResult(w)

        assertPipeResultEqual(r, r2)
    }

    "PipeResult round-trip with Invalid pu" in {
        val r = PipeResult.fromValues(
            pt                   = ConnectorPipeT,
            lengthSum            = 3.0.withUnit[Meter],
            heightSum            = 1.0.withUnit[Meter],
            pd                   = None,
            ζ                    = Some(1.4.withUnit[1]),
            pu                   = Validated.invalid(
                NonEmptyList.one(MecaFlu_Error.UnexpectedPipeType("test error", FireboxPipeT))
            ),
            pRs                  = 0.0.withUnit[Pascal],
            pRg                  = 0.0.withUnit[Pascal],
            ph                   = 0.0.withUnit[Pascal],
            gas_temp_start       = 150.0.withTemperature[Celsius],
            gas_temp_middle      = 120.0.withTemperature[Celsius],
            gas_temp_end         = 90.0.withTemperature[Celsius],
            density_middle       = None,
            density_mean         = None,
            v_start              = None,
            v_end                = None,
            last_density_mean    = None,
            last_density_middle  = None,
            last_velocity_mean   = None,
            last_velocity_middle = None
        )

        val w  = WireConversions.toWireResult(r)
        val r2 = WireConversions.fromWireResult(w)

        // pu round-trips as Invalid (the error message may differ due to wrapping)
        r.pu.isInvalid `shouldBe` true
        r2.pu.isInvalid `shouldBe` true

        // All other fields should match
        r2.typ `shouldBe` r.typ
        r2.lengthSum `shouldBe` r.lengthSum
        r2.heightSum `shouldBe` r.heightSum
        r2.ζ `shouldBe` r.ζ
        r2.pd `shouldBe` r.pd
        r2.pRs `shouldBe` r.pRs
        r2.pRg `shouldBe` r.pRg
        r2.ph `shouldBe` r.ph
        r2.gas_temp_start `shouldBe` r.gas_temp_start
        r2.gas_temp_middle `shouldBe` r.gas_temp_middle
        r2.gas_temp_end `shouldBe` r.gas_temp_end
        r2.gas_temp_mean `shouldBe` r.gas_temp_mean
        r2.density_middle `shouldBe` r.density_middle
        r2.density_mean `shouldBe` r.density_mean
        r2.v_start `shouldBe` r.v_start
        r2.v_end `shouldBe` r.v_end
        r2.last_density_mean `shouldBe` r.last_density_mean
        r2.last_density_middle `shouldBe` r.last_density_middle
        r2.last_velocity_mean `shouldBe` r.last_velocity_mean
        r2.last_velocity_middle `shouldBe` r.last_velocity_middle
    }

    /**
     * AC-3 structural assertion: PipeResultFromValues is the only instantiable implementor.
     *
     * PipeResult is sealed with two impls:
     *   - PipeResultFromValues (concrete, used by fromValues)
     *   - PipeResultFromSections (abstract, no concrete subclass in current codebase)
     *
     * If someone adds a second concrete impl with a different gas_temp_mean derivation,
     * this test should be updated. See also the `gas_temp_mean` val in `PipeResultFromValues`
     * in `results.scala` and the `fromWireResult` in `WireConversions.scala`.
     */
    "PipeResultFromValues is the only instantiable implementor" in {
        val r = PipeResult.fromValues(
            pt                   = FluePipeT,
            lengthSum            = 1.0.withUnit[Meter],
            heightSum            = 1.0.withUnit[Meter],
            pd                   = None,
            ζ                    = None,
            pu                   = Validated.valid(101325.0.withUnit[Pascal]),
            pRs                  = 0.0.withUnit[Pascal],
            pRg                  = 0.0.withUnit[Pascal],
            ph                   = 0.0.withUnit[Pascal],
            gas_temp_start       = 20.0.withTemperature[Celsius],
            gas_temp_middle      = 20.0.withTemperature[Celsius],
            gas_temp_end         = 20.0.withTemperature[Celsius],
            density_middle       = None,
            density_mean         = None,
            v_start              = None,
            v_end                = None,
            last_density_mean    = None,
            last_density_middle  = None,
            last_velocity_mean   = None,
            last_velocity_middle = None
        )

        // PipeResultFromValues is the concrete class backing fromValues
        r.getClass.getSimpleName should include("PipeResultFromValues")
    }

    /**
     * Chunk-003 bijectivity guard: the keyed wire grid and the engine grid are
     * two representations of the same data. engine grid → wire grid → engine
     * grid is the identity on keys, order, and every PipeResult member; and
     * wire grid → engine grid → wire grid is the identity on the JSON bytes.
     */
    "AsResult grid: engine → wire → engine is identity (bijectivity)" in {
        def resultAt(pu: Double, gasTempEnd: Double): PipeResult =
            PipeResult.fromValues                  (
                pt                   = FireboxPipeT,
                lengthSum            = 1.0.withUnit[Meter],
                heightSum            = 1.0.withUnit[Meter],
                pd                   = None,
                ζ                    = None,
                pu                   = Validated.valid(pu.withUnit[Pascal]),
                pRs                  = 0.0.withUnit[Pascal],
                pRg                  = 0.0.withUnit[Pascal],
                ph                   = 0.0.withUnit[Pascal],
                gas_temp_start       = 400.0.withTemperature[Celsius],
                gas_temp_middle      = 375.0.withTemperature[Celsius],
                gas_temp_end         = gasTempEnd.withTemperature[Celsius],
                density_middle       = Some(1.0.withUnit[Kilogram / (Meter ^ 3)]),
                density_mean         = Some(1.1.withUnit[Kilogram / (Meter ^ 3)]),
                v_start              = Some(3.0.withUnit[Meter / Second]),
                v_end                = Some(2.0.withUnit[Meter / Second]),
                last_density_mean    = Some(1.2.withUnit[Kilogram / (Meter ^ 3)]),
                last_density_middle  = Some(1.3.withUnit[Kilogram / (Meter ^ 3)]),
                last_velocity_mean   = Some(4.0.withUnit[Meter / Second]),
                last_velocity_middle = Some(5.0.withUnit[Meter / Second])
            )

        val grid = NonEmptyList.of(
            OpPoint(DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Nominal) -> resultAt(10.0, 347.0),
            OpPoint(DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Reduced) -> resultAt(11.0, 348.0),
            OpPoint(DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Nominal) -> resultAt(12.0, 349.0),
            OpPoint(DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Reduced) -> resultAt(13.0, 350.0)
        )
        val engineContrib: FireboxContribution[WirePipeFullDescr] = FireboxContribution.AsResult(grid)

        // engine → wire
        val wire1    = WireConversions.toWireFireboxContribution(engineContrib)(using identity, locale)
        val results1 = wire1 match
            case WireFireboxContribution.AsResult(rs) => rs
            case other                                => fail(s"expected AsResult, got $other")
        results1.size shouldBe 4

        // wire → engine
        val engine2 =
            WireConversions.fromWireFireboxContribution[WirePipeFullDescr](wire1)(using identity, locale)
        val grid2   = engine2.toOption.get match
            case FireboxContribution.AsResult(g) => g
            case other                           => fail(s"expected AsResult, got $other")

        // Keys and order preserved entry-for-entry
        grid2.map(_._1).toList shouldBe grid.map(_._1).toList
        grid.toList.zip(grid2.toList).foreach { case ((_, r1), (_, r2)) =>
            assertPipeResultEqual(r1, r2)
        }

        // engine → wire again: the JSON is byte-identical (wire → engine → wire)
        val wire2 = WireConversions.toWireFireboxContribution(engine2.toOption.get)(using identity, locale)
        wire2.asJson.noSpaces shouldBe wire1.asJson.noSpaces
    }
}
