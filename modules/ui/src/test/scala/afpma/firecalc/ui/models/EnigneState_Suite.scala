/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.ui.instances.circe.given

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class EnigneState_Suite extends AnyFreeSpec with Matchers:

    "EngineState" - {

        "encoding to JSON" - {

            "should produce valid JSON" in {
                val a = EngineState.init
                val json = a.asJson
                json.isObject shouldBe true
            }

            "should round-trip correctly" in {
                val a = EngineState.init
                val json = a.asJson.noSpaces
                val decoded = decode[EngineState](json)
                decoded shouldBe Right(a)
            }
        }

    }
