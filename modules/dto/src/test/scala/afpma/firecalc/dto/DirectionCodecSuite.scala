/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.instances.CommonInstances.given
import afpma.firecalc.dto.instances.V4Instances.given
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.units.coulombutils.*

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DirectionCodecSuite extends AnyFreeSpec with Matchers:

    private def roundTrip[A: Encoder: Decoder](value: A): A =
        val json = Encoder[A].apply(value)
        Decoder[A].decodeJson(json) match
            case Right(v)  => v
            case Left(err) => fail(s"Decode failed for $value → ${json.noSpaces}: $err")

    // ── AzimuthDirection ───────────────────────────────────────────────

    "AzimuthDirection" - {

        "named cases encode as plain strings" in {
            Encoder[AzimuthDirection].apply(AzimuthDirection.Rear).shouldBe (Json.fromString("Rear") )
            Encoder[AzimuthDirection].apply(AzimuthDirection.Right).shouldBe(Json.fromString("Right"))
            Encoder[AzimuthDirection].apply(AzimuthDirection.Front).shouldBe(Json.fromString("Front"))
            Encoder[AzimuthDirection].apply(AzimuthDirection.Left).shouldBe (Json.fromString("Left") )
        }

        "all named cases round-trip" in {
            AzimuthDirection.namedCases.foreach { c =>
                roundTrip[AzimuthDirection](c).shouldBe(c)
            }
        }

        "Custom case round-trips" in {
            val custom = AzimuthDirection.Custom(42.5.degrees)
            roundTrip[AzimuthDirection](custom).shouldBe(custom)
        }

        "Custom encodes as {\"Custom\": <angle>}" in {
            val custom = AzimuthDirection.Custom(42.5.degrees)
            val json   = Encoder[AzimuthDirection].apply(custom)
            json.isObject.shouldBe                                   (true)
            json.hcursor.downField("Custom").focus.isDefined.shouldBe(true)
        }

        "decodes named string" in {
            val json = Json.fromString("RearRight")
            Decoder[AzimuthDirection].decodeJson(json).shouldBe(Right(AzimuthDirection.RearRight))
        }

        "rejects unknown string by falling through to Custom decoder (which fails)" in {
            val json = Json.fromString("Bogus")
            Decoder[AzimuthDirection].decodeJson(json).isLeft.shouldBe(true)
        }
    }

    // ── InclinationDirection ───────────────────────────────────────────

    "InclinationDirection" - {

        "named cases encode as plain strings" in {
            Encoder[InclinationDirection].apply(InclinationDirection.Up).shouldBe        (Json.fromString("Up")        )
            Encoder[InclinationDirection].apply(InclinationDirection.Down).shouldBe      (Json.fromString("Down")      )
            Encoder[InclinationDirection].apply(InclinationDirection.Horizontal).shouldBe(Json.fromString("Horizontal"))
        }

        "all named cases round-trip" in {
            InclinationDirection.namedCases.foreach { c =>
                roundTrip[InclinationDirection](c).shouldBe(c)
            }
        }

        "Custom case round-trips" in {
            val custom = InclinationDirection.Custom(30.0.degrees)
            roundTrip[InclinationDirection](custom).shouldBe(custom)
        }

        "Custom encodes as {\"Custom\": <angle>}" in {
            val custom = InclinationDirection.Custom(30.0.degrees)
            val json   = Encoder[InclinationDirection].apply(custom)
            json.isObject.shouldBe                                   (true)
            json.hcursor.downField("Custom").focus.isDefined.shouldBe(true)
        }

        "rejects unknown string" in {
            val json = Json.fromString("Sideways")
            Decoder[InclinationDirection].decodeJson(json).isLeft.shouldBe(true)
        }
    }

    // ── AbsoluteDirection ─────────────────────────────────────────────────

    "AbsoluteDirection" - {

        "round-trips with named directions" in {
            val fd = AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up)
            roundTrip[AbsoluteDirection](fd).shouldBe(fd)
        }

        "round-trips with custom directions" in {
            val fd = AbsoluteDirection(
                AzimuthDirection.Custom    (123.0.degrees),
                InclinationDirection.Custom(-15.0.degrees)
            )
            roundTrip[AbsoluteDirection](fd).shouldBe(fd)
        }

        "round-trips with mixed named/custom" in {
            val fd = AbsoluteDirection(AzimuthDirection.FrontLeft, InclinationDirection.Custom(45.0.degrees))
            roundTrip[AbsoluteDirection](fd).shouldBe(fd)
        }

        "encodes azimuth and inclination as top-level fields" in {
            val fd   = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            val json = Encoder[AbsoluteDirection].apply(fd)
            json.hcursor.downField("azimuth").as[String].shouldBe    (Right("Right")     )
            json.hcursor.downField("inclination").as[String].shouldBe(Right("Horizontal"))
        }
    }

    // ── Option[AbsoluteDirection] ─────────────────────────────────────────

    "Option[AbsoluteDirection]" - {

        "Some round-trips" in {
            val opt: Option[AbsoluteDirection] =
                Some                                          (AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Down))
            roundTrip[Option[AbsoluteDirection]](opt).shouldBe(opt                                                                )
        }

        "None round-trips" in {
            val opt: Option[AbsoluteDirection] = None
            roundTrip[Option[AbsoluteDirection]](opt).shouldBe(opt)
        }
    }

end DirectionCodecSuite
