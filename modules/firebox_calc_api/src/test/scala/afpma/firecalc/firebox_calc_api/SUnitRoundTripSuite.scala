/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.firebox_calc_api

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.others.sunits.SUnits.given

import afpma.firecalc.dto.instances.CommonInstances.given

import coulomb.*
import coulomb.syntax.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * AC-5: Round-trip tests for SUnit-based encoder_QtyD / decoder_QtyD codecs.
 *
 * Verifies that Coulomb typed quantities (Density, FlowVelocity, Volume)
 * encode to JSON and decode back to the same value via the CommonInstances
 * SUnit codecs.
 */
class SUnitRoundTripSuite extends AnyFreeSpec with Matchers {

    "encoder_QtyD / decoder_QtyD round-trips Density" in {
        val d: Density = 1.2.withUnit[Kilogram / (Meter ^ 3)]
        val json    = encoder_QtyD[Kilogram / (Meter ^ 3)].apply(d)
        val decoded = decoder_QtyD[Kilogram / (Meter ^ 3)].decodeJson(json)
        decoded shouldBe Right(d)
    }

    "encoder_QtyD / decoder_QtyD round-trips FlowVelocity" in {
        val v: FlowVelocity = 2.5.withUnit[Meter / Second]
        val json    = encoder_QtyD[Meter / Second].apply(v)
        val decoded = decoder_QtyD[Meter / Second].decodeJson(json)
        decoded shouldBe Right(v)
    }

    "encoder_QtyD / decoder_QtyD round-trips Volume (CubicMeter)" in {
        val vol: QtyD[(Meter ^ 3)] = 10.0.withUnit[(Meter ^ 3)]
        val json    = encoder_QtyD[(Meter ^ 3)].apply(vol)
        val decoded = decoder_QtyD[(Meter ^ 3)].decodeJson(json)
        decoded shouldBe Right(vol)
    }
}
