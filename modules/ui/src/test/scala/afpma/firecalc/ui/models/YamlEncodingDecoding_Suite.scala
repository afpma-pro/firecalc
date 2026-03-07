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
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter
import org.scalatest.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class YamlEncodingDecoding_Suite extends AnyFreeSpec with Matchers:

    def roundTripYamlShouldWork[X: {Encoder, Decoder}](
        x_title: String
    )(
        x: X,
        makeAssertion: (X, X) => Assertion = (x: X, y: X) => x `shouldEqual` y
    ) =
        s"${x_title}" - {
            "encoding to YAML" - {
                "should work" in {
                    noException should be thrownBy yamlPrinter.print(x.asJson)
                }
            }

            "decoding from YAML" - {
                "should work" in {
                    val yaml = yamlPrinter.print(x.asJson)
                    yamlParser.parse(yaml) match
                        case Left(pf) => fail(pf)
                        case Right(yamlParsed) =>
                            val jString = yamlParsed.noSpaces
                            decode[X](jString) match
                                case Left(err) => fail(err)
                                case Right(y)  => makeAssertion(x, y)
                }
            }
        }

    def inputQtyDAssertion[DU: SUnit, FU]: (InputQtyD[DU, FU], InputQtyD[DU, FU]) => Assertion =
        (x, y) => x.displayValue shouldEqual y.displayValue

    "YAML Encoding/Decoding" - {

        roundTripYamlShouldWork("InputQtyD [123.meters]")(
            x = InputQtyD.fromFinalQty[Meter, Inch](123.meters),
            makeAssertion = inputQtyDAssertion
        )

        roundTripYamlShouldWork("InputQtyD [10.inch]")(
            x = InputQtyD.fromDisplayQty[Meter, Inch](10.withUnit[Inch]),
            makeAssertion = inputQtyDAssertion
        )

        roundTripYamlShouldWork("QtyD [123.meters]")(
            x = 123.meters
        )(using
            instances.circe.encoder_QtyD_meter,
            instances.circe.decoder_QtyD_meter,
        )

        roundTripYamlShouldWork("LocalConditions.default")(
            x = LocalConditions.default
        )

        roundTripYamlShouldWork("EngineState.init")(
            x = EngineState.init
        )
    }
