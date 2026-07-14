/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.AddSharpeAngle_0_to_180
import afpma.firecalc.dto.v7.AirIntakePosition
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.dto.v7.PostFireboxStartPosition

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class EnigneState_Suite extends AnyFreeSpec with Matchers:

    private def exampleFlueBend(name: String): AddSharpeAngle_0_to_180 =
        EngineState.example_projet_15544.post_firebox_pipes.slots
            .collectFirst { case PostFireboxPipeDescrSlot_V7.FlueSlot(descr) =>
                descr.collectFirst { case bend: AddSharpeAngle_0_to_180 if bend.name == name => bend }
            }
            .flatten
            .getOrElse(fail(s"Bend '$name' not found"))

    "EngineState" - {

        "V7 post-firebox invariant" - {

            val allStates: List[(String, EngineState)] = List(
                "example_projet_15544"                 -> EngineState.example_projet_15544,
                "init_as_CasType_15544_C3"             -> EngineState.init_as_CasType_15544_C3,
                "init_as_CasPratique_15544_FDIM_EX_03" -> EngineState.init_as_CasPratique_15544_FDIM_EX_03,
                "empty"                                -> EngineState.empty,
                "minimal"                              -> EngineState.minimal
            )

            allStates.foreach { (name, state) =>
                s"$name has clean post_firebox_pipes.slots" in {
                    val slots = state.post_firebox_pipes.slots
                    slots.foreach { slot =>
                        FramedPostFireboxPipes.hasDeprecatedPostFireboxElement(slot) shouldBe false
                    }
                }
            }
        }

        "encoding to JSON" - {

            "should produce valid JSON" in {
                val a    = EngineState.init
                val json = a.asJson
                json.isObject shouldBe true
            }

            "should round-trip correctly" in {
                val a       = EngineState.init
                val json    = a.asJson.noSpaces
                val decoded = decode[EngineState](json)
                decoded shouldBe Right(a)
            }
        }

        "example project V7 post-firebox state" - {

            "stores the original initial frame at FramedPostFireboxPipes level" in {
                val pipes = EngineState.example_projet_15544.post_firebox_pipes

                pipes.initialDirection shouldBe (
                    PipeInitialDirection(
                        AzimuthDirection.Left,
                        InclinationDirection.Horizontal
                    )
                )
                pipes.initialPosition shouldBe PostFireboxStartPosition.Auto
            }

            "keeps vertical direction-change absDir pins as explicit azimuth None" in {
                exampleFlueBend("virage avant descente").absDir.shouldBe(
                    Some(
                        AbsoluteDirection(None, InclinationDirection.Down)
                    )
                )
                exampleFlueBend("virage avant remontée").absDir.shouldBe(
                    Some(
                        AbsoluteDirection(None, InclinationDirection.Up)
                    )
                )
            }
        }

        "empty state defaults" - {
            "empty post-firebox position is Auto" in {
                EngineState.empty.post_firebox_pipes.initialPosition shouldBe PostFireboxStartPosition.Auto
            }

            "minimal post-firebox position is Auto" in {
                EngineState.minimal.post_firebox_pipes.initialPosition shouldBe PostFireboxStartPosition.Auto
            }

            "empty air intake position is FinalAuto" in {
                EngineState.empty.air_intake_pipes.position shouldBe AirIntakePosition.FinalAuto
            }

            "minimal air intake position is FinalAuto" in {
                EngineState.minimal.air_intake_pipes.position shouldBe AirIntakePosition.FinalAuto
            }
        }

    }
