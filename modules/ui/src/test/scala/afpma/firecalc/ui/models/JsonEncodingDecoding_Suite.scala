/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.units.all.given
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.instances
import afpma.firecalc.ui.instances.circe.given
import afpma.firecalc.ui.utils.InputQtyD
import afpma.firecalc.units.all.SUnit

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.*
import coulomb.units.us.*

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*


class JsonEncodingDecoding_Suite extends AnyFreeSpec with Matchers:

    def roundTripJsonShouldWork[X: {Encoder, Decoder}](
        x_title: String
    )(
        x: X,
        makeAssertion: (X, X) => Assertion = (x: X, y: X) => x `shouldEqual` y
    ) =
        s"${x_title}" - {
            "encoding to JSON" - {
                "should work" in {
                    noException should be thrownBy x.asJson.noSpaces
                }
            }

            "decoding from JSON" - {
                "should work" in {
                    val json = x.asJson.noSpaces
                    decode[X](json) match
                        case Left(e)  => fail(e)
                        case Right(y) => makeAssertion(x, y)
                }
            }
        }

    def inputQtyDAssertion[DU: SUnit, FU]: (InputQtyD[DU, FU], InputQtyD[DU, FU]) => Assertion =
        (x, y) => x.displayValue shouldEqual y.displayValue

    "JSON Encoding/Decoding" - {

        roundTripJsonShouldWork("InputQtyD [123.meters]")(
            x = InputQtyD.fromDisplayQty[Meter, Inch](123.meters),
            makeAssertion = inputQtyDAssertion
        )

        roundTripJsonShouldWork("InputQtyD [10.inch]")(
            x = InputQtyD.fromFinalQty[Meter, Inch](10.withUnit[Inch]),
            makeAssertion = inputQtyDAssertion
        )

        roundTripJsonShouldWork("QtyD [123.meters]")(
            x = 123.meters
        )(using
            instances.circe.encoder_QtyD_meter,
            instances.circe.decoder_QtyD_meter,
        )

        roundTripJsonShouldWork("LocalConditions.default")(
            x = LocalConditions.default
        )

        roundTripJsonShouldWork("EngineState.init")(
            x = EngineState.init
        )
    }
