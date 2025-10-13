/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.utils

import io.circe.*
import io.circe.Decoder
import io.circe.Decoder.Result
import io.circe.DecodingFailure.Reason
import io.circe.DecodingFailure.Reason.WrongTypeExpectation
import io.circe.Encoder
import io.circe.generic.semiauto

import scala.util.Try

object circe:

    // =======
    // HELPERS

    // Conversion[A, B] or Conversion[B, A]

    def decodeUsingConversion[A, B](using da: Decoder[A], conv: Conversion[A, B]): Decoder[B] =
        new Decoder[B]:
            def apply(c: HCursor): Result[B] = 
                da(c).map(conv)

    def encodeUsingConversion[A, B](using ea: Encoder[A], conv: Conversion[B, A]): Encoder[B] =
        new Encoder[B]:
            def apply(b: B): Json = 
                ea(conv(b))

    // Either

    given deriveEncoderForEither: [A, B] => (ea: Encoder[A]) => (eb: Encoder[B]) => Encoder[Either[A, B]] = new Encoder[Either[A, B]]:
        def apply(a: Either[A, B]): Json = Json.obj(
            ("type", Json.fromString("either")),
            ("left_or_right", a.fold(_ => Json.fromString("left"), _ => Json.fromString("right"))),
            ("value", a.fold(ea.apply, eb.apply))
        )

    given deriveDecoderForEither: [A, B] => (da: Decoder[A]) => (db: Decoder[B]) => Decoder[Either[A, B]] = new Decoder[Either[A, B]]:
        def apply(c: HCursor): Result[Either[A, B]] = 
            val tpeCursor = c.downField("type")
            val lrCursor = c.downField("left_or_right")
            for 
                tpe <- tpeCursor.as[String]
                lr  <- lrCursor.as[String]
                ret <- {
                    if (tpe == "either")
                        lr match
                            case "left" => c.get[A]("value").map(a => Left(a))
                            case "right" => c.get[B]("value").map(b => Right(b))
                            case _ => Left(DecodingFailure(
                                Reason.CustomReason(s"expected 'type' to equal 'either' and 'left_or_right' to equal 'left' or 'right' but got 'type'=$tpe and 'left_or_right'=${lr}"),
                                lrCursor)
                            )
                    else
                        Left(DecodingFailure(
                            Reason.CustomReason(s"expected 'type' to equal 'either' but got 'type'= '$tpe'"),
                            lrCursor)
                        )
                }
            yield ret

    // Enum

    inline def deriveEncoderForEnum[A <: reflect.Enum]: Encoder[A] = 
        Encoder.encodeString.contramap[A](_.toString)

    inline def deriveDecoderForEnum[A <: reflect.Enum](dec: String => A): Decoder[A] = 
        Decoder.decodeString.emapTry(str => Try(dec(str)))

    // Option[_]

    given decodeOption: [A] => Decoder[A] => Decoder[Option[A]] =
        Decoder.decodeOption[A]
    given encodeOption: [A] => Encoder[A] => Encoder[Option[A]] =
        Encoder.encodeOption[A]

    // OptionOfEither[L, R]

    given deriveEncoderForOptionOfEither: [A, B] => (ea: Encoder[A]) => (eb: Encoder[B]) => Encoder[OptionOfEither[A, B]] = new Encoder[OptionOfEither[A, B]]:
        def apply(a: OptionOfEither[A, B]): Json = Json.obj(
            ("type", Json.fromString("option_of_either")),
            ("none_or_left_or_right", a match
                case NoneOfEither => Json.fromString("none")
                case SomeLeft(l)  => Json.fromString("left")
                case SomeRight(r) => Json.fromString("right")
            ),
            ("value", a match
                case NoneOfEither => Json.Null
                case SomeLeft(l)  => ea.apply(l)
                case SomeRight(r) => eb.apply(r)
            )
        )

    given deriveDecoderForOptionOfEither: [A, B] => (da: Decoder[A]) => (db: Decoder[B]) => Decoder[OptionOfEither[A, B]] = new Decoder[OptionOfEither[A, B]]:
        def apply(c: HCursor): Result[OptionOfEither[A, B]] = 
            val tpeCursor = c.downField("type")
            val lrCursor = c.downField("none_or_left_or_right")
            for 
                tpe <- tpeCursor.as[String]
                nlr  <- lrCursor.as[String]
                ret <- {
                    if (tpe == "option_of_either")
                        nlr match
                            case "none"  => Right(NoneOfEither)
                            case "left"  => 
                                c.get[A]("value") match
                                    case Left(err) => 
                                        // scala.scalajs.js.Dynamic.global.console.log(s"ERR $err")
                                        Left(err)
                                    case Right(value) =>
                                        Right(SomeLeft(value))
                                // .map(a => SomeLeft(a))
                            case "right" => c.get[B]("value").map(b => SomeRight(b))
                            case _ => Left(DecodingFailure(
                                Reason.CustomReason(s"expected 'type' to equal 'option_of_either' and 'none_or_left_or_right' to equal 'none', 'left' or 'right' but got 'type'=$tpe and 'none_or_left_or_right'=${nlr}"),
                                lrCursor)
                            )
                    else
                        Left(DecodingFailure(
                            Reason.CustomReason(s"expected 'type' to equal 'option_of_either' but got 'type'= '$tpe'"),
                            lrCursor)
                        )
                }
            yield ret

    // Seq

    // ensure null value in yaml are decoded as empty sequences

    given seqDecoder: [A] => (decoder: Decoder[A]) => Decoder[Seq[A]] = 
        val dseq = Decoder.decodeSeq[A]
        dseq.handleErrorWith: r =>
            r.reason match
                case WrongTypeExpectation("array", Json.Null) =>
                    Decoder.const(Seq.empty[A])
                // case WrongTypeExpectation("object", Json.Null) =>
                //     Decoder.const(Seq.empty[A])
                case otherReason => 
                    // scala.scalajs.js.Dynamic.global.console.log("failed to decode seq")
                    // scala.scalajs.js.Dynamic.global.console.log(otherReason.toString)
                    // scala.scalajs.js.Dynamic.global.console.log(r.getMessage)
                    dseq

    // END HELPERS
    // ===========

end circe
