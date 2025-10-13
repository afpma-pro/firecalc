/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import io.circe.*
import io.circe.syntax.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.ui.instances.circe.given

class AppState_Suite extends AnyFreeSpec with Matchers:

    "AppState" - {

        "encoding to JSON" - {

            "should work" in {
                val a = AppState.init
                val exp = """|bla""".stripMargin
                a.asJson `shouldEqual` exp
            }
        }

    }
