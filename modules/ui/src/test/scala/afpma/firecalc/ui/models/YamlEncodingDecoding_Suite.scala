/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*

import io.circe.yaml.scalayaml.{printer as yamlPrinter, parser as yamlParser}


import org.scalatest.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.ui.utils.InputQtyD
import afpma.firecalc.units.all.given
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import afpma.firecalc.ui.instances
import afpma.firecalc.ui.instances.circe.given
import afpma.firecalc.dto.instances.given
import coulomb.units.us.*

class YamlEncodingDecoding_Suite extends AnyFreeSpec with Matchers:


    def encodingAndDecodingToYamlShouldWork[X: {Encoder, Decoder}](
        x_title: String
    )(
        x: X, 
        yamlStringExp: String,
        makeAssertion: (X, X) => Assertion = (x: X, y: X) => x `shouldEqual` y
    ) =
        s"${x_title}" - {
            "encoding to YAML" - {
                "should work" in {
                    val j = x.asJson
                    val yamlOut = yamlPrinter.print(j)
                    // println("---")
                    // println(yamlOut)
                    // println("---")
                    // println(yamlString)
                    yamlOut `shouldEqual` yamlStringExp
                }
            }

            "decoding from YAML" - {
                "should work" in {
                    yamlParser.parse(yamlStringExp) match
                        case Left(pf) => fail(pf)
                        case Right(yamlParsed) =>
                            val jString = yamlParsed.noSpaces
                            decode[X](jString) match
                                case Left(err) => fail(err)
                                case Right(y)  => makeAssertion(x, y)
                }
            }

        }
    
    "YAML Encoding/Decoding" - {

        encodingAndDecodingToYamlShouldWork("InputQtyD [123.meters]")(
            x = InputQtyD.fromFinalQty[Meter, Inch](123.meters), 
            yamlStringExp = """|value: "123"
                               |unit: "meter"
                               |""".stripMargin
        )
    
        encodingAndDecodingToYamlShouldWork("InputQtyD [10.inch]")(
            x = InputQtyD.fromDisplayQty[Meter, Inch](10.withUnit[Inch]), 
            yamlStringExp = """|value: "10"
                               |unit: "inch"
                               |""".stripMargin
        )
        
        encodingAndDecodingToYamlShouldWork("QtyD [123.meters]")(
            x = 123.meters, 
            yamlStringExp = """|value: "123"
                               |unit: "meter"
                               |""".stripMargin
        )(using 
            instances.circe.encoder_QtyD_meter, 
            instances.circe.decoder_QtyD_meter,
        )
    
        encodingAndDecodingToYamlShouldWork("LocalConditions.empty")(
            x = LocalConditions.default, 
            yamlStringExp = """|z_geodetical_height: 
                            |  value: "100"
                            |  unit: "meter"
                            |coastal_region: false
                            |chimney_termination: 
                            |  chimney_location_on_roof: 
                            |    h: "MoreThan40cm"
                            |    d: !!null
                            |    rs: !!null
                            |    o: !!null
                            |    s: !!null
                            |  adjacent_buildings: 
                            |    l: "MoreThan15m"
                            |    alpha: !!null
                            |    beta: !!null
                            |""".stripMargin,
        )
    
        encodingAndDecodingToYamlShouldWork("AppState.init")(
            x = AppState.init, 
            yamlStringExp = """|customer: 
                            |  first_name: ""
                            |  last_name: ""
                            |  phone_no: ""
                            |  email: ""
                            |  address: ""
                            |  city: ""
                            |  postal_code: ""
                            |localConditions: 
                            |  z_geodetical_height: 
                            |    value: "100"
                            |    unit: "meter"
                            |  coastal_region: false
                            |  chimney_termination: 
                            |    chimney_location_on_roof: 
                            |      h: "MoreThan40cm"
                            |      d: !!null
                            |      rs: !!null
                            |      o: !!null
                            |      s: !!null
                            |    adjacent_buildings: 
                            |      l: "MoreThan15m"
                            |      alpha: !!null
                            |      beta: !!null
                            |""".stripMargin,
        )
    }
