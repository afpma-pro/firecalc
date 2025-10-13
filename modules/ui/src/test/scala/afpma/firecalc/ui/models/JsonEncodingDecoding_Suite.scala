/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*

import org.scalatest.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.ui.instances
import afpma.firecalc.ui.utils.InputQtyD
import afpma.firecalc.units.all.given
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import afpma.firecalc.engine.models.*
import afpma.firecalc.dto.all.*
import coulomb.units.us.*

import afpma.firecalc.ui.instances.circe.given
import afpma.firecalc.dto.instances.given


class JsonEncodingDecoding_Suite extends AnyFreeSpec with Matchers:


    def encodingAndDecodingToJsonShouldWork[X: {Encoder, Decoder}](
        x_title: String
    )(
        x: X, 
        jsonStringExp: String,
        makeAssertion: (X, X) => Assertion = (x: X, y: X) => x `shouldEqual` y
    ) =
        s"${x_title}" - {
            "encoding to JSON" - {
                "should work" in {
                    x.asJson.noSpaces `shouldEqual` jsonStringExp
                }
            }

            "decoding from JSON" - {
                "should work" in {
                    decode[X](jsonStringExp) match
                        case Left(e)  => fail(e)
                        case Right(y) => makeAssertion(x, y)
                }
            }

        }

    "JSON Encoding/Decoding" - {

        encodingAndDecodingToJsonShouldWork("InputQtyD [123.meters]")(
            x = InputQtyD.fromDisplayQty[Meter, Inch](123.meters), 
            jsonStringExp = """{"value":"123","unit":"meter"}"""
        )

        encodingAndDecodingToJsonShouldWork("InputQtyD [10.inch]")(
            x = InputQtyD.fromFinalQty[Meter, Inch](10.withUnit[Inch]), 
            jsonStringExp = """{"value":"10","unit":"inch"}"""
        )
        
        encodingAndDecodingToJsonShouldWork("QtyD [123.meters]")(
            x = 123.meters, 
            jsonStringExp = """{"value":"123","unit":"meter"}""",
        )(using 
            instances.circe.encoder_QtyD_meter, 
            instances.circe.decoder_QtyD_meter,
        )

        encodingAndDecodingToJsonShouldWork("LocalConditions.empty")(
            x = LocalConditions.default, 
            jsonStringExp = """{"z_geodetical_height":{"value":"100","unit":"meter"},"coastal_region":false,"chimney_termination":{"chimney_location_on_roof":{"h":"MoreThan40cm","d":null,"rs":null,"o":null,"s":null},"adjacent_buildings":{"l":"MoreThan15m","alpha":null,"beta":null}}}""",
        )

        encodingAndDecodingToJsonShouldWork("AppState.init")(
            x = AppState.init, 
            jsonStringExp = """{"customer":{"first_name":"","last_name":"","phone_no":"","email":"","address":"","city":"","postal_code":""},"localConditions":{"z_geodetical_height":{"value":"100","unit":"meter"},"coastal_region":false,"chimney_termination":{"chimney_location_on_roof":{"h":"MoreThan40cm","d":null,"rs":null,"o":null,"s":null},"adjacent_buildings":{"l":"MoreThan15m","alpha":null,"beta":null}}}}""",
        )
    }