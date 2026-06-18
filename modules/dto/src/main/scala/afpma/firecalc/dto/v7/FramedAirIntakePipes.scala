/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */
package afpma.firecalc.dto.v7

import afpma.firecalc.dto.common.CirceEnumCodecs.{decodeManualPos, isUnitJson}
import afpma.firecalc.dto.common.FramedPipeSequence
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.Position3D

import afpma.firecalc.dto.instances.V7Instances

import io.circe.{Decoder, DecodingFailure, Encoder, Json}

/**
 * Auto/Manual mode selector for the air intake pipe position.
 *
 * Flat 4-case enum: Initial/Final x Auto/Manual.
 * Auto modes carry no position — the value is always recomputed.
 * Manual modes store a user override.
 */
enum AirIntakePosition:
    case InitialAuto
    case InitialManual(position: Position3D)
    case FinalAuto
    case FinalManual(position: Position3D)

object AirIntakePosition:
    given Encoder[AirIntakePosition] = Encoder.instance {
        // Use Json.fromBoolean(true) for unit cases so YAML preserves them (empty {} becomes null).
        case AirIntakePosition.InitialAuto      => Json.obj("InitialAuto" -> Json.fromBoolean(true))
        case AirIntakePosition.InitialManual(p) =>
            Json.obj("InitialManual" -> Json.obj("position" -> summon[Encoder[Position3D]].apply(p)))
        case AirIntakePosition.FinalAuto        => Json.obj("FinalAuto" -> Json.fromBoolean(true))
        case AirIntakePosition.FinalManual(p)   =>
            Json.obj("FinalManual" -> Json.obj("position" -> summon[Encoder[Position3D]].apply(p)))
    }

    given Decoder[AirIntakePosition] = Decoder.instance { c =>
        c.focus.flatMap(_.asObject) match
            case Some(obj) =>
                val m = obj.toMap
                if m.get("InitialAuto").exists(isUnitJson) then Right(AirIntakePosition.InitialAuto)
                else
                    m.get("InitialManual") match
                        case Some(v) => decodeManualPos(v).map(AirIntakePosition.InitialManual)
                        case None    =>
                            if m.get("FinalAuto").exists(isUnitJson) then Right(AirIntakePosition.FinalAuto)
                            else
                                m.get("FinalManual") match
                                    case Some(v) => decodeManualPos(v).map(AirIntakePosition.FinalManual)
                                    case None    => Left(DecodingFailure("AirIntakePosition", c.history))
            case None      => Left(DecodingFailure("AirIntakePosition", c.history))
    }

final case class FramedAirIntakePipes(
    initialDir: PipeInitialDirection,
    position  : AirIntakePosition,
    descr     : Seq[FlowOnlyPipeDescr_13384_V4]
) extends FramedPipeSequence[FlowOnlyPipeDescr_13384_V4, AirIntakePosition] {

    override def initialDirection: PipeInitialDirection = initialDir

    override def descriptors: Seq[FlowOnlyPipeDescr_13384_V4] = descr
}

object FramedAirIntakePipes:
    import V7Instances.given

    given Encoder[FramedAirIntakePipes] = io.circe.Encoder.instance[FramedAirIntakePipes] { pipes =>
        io.circe.Json.obj(
            "initialDir" -> summon[Encoder[PipeInitialDirection]].apply(pipes.initialDir),
            "position"   -> summon[Encoder[AirIntakePosition]].apply(pipes.position),
            "descr"      -> summon[Encoder[Seq[FlowOnlyPipeDescr_13384_V4]]].apply(pipes.descr)
        )
    }

    given Decoder[FramedAirIntakePipes] = io.circe.Decoder.instance[FramedAirIntakePipes] { c =>
        for
            dir      <- c.downField("initialDir").as[PipeInitialDirection]
            position <- c.downField("position").as[AirIntakePosition]
            descr    <- c.downField("descr").as[Option[Seq[FlowOnlyPipeDescr_13384_V4]]].map(_.getOrElse(Seq.empty))
        yield FramedAirIntakePipes(dir, position, descr)
    }

    def fromLegacy(
        descr     : Seq[FlowOnlyPipeDescr_13384_V4],
        initialDir: PipeInitialDirection = PipeInitialDirection.default,
        position  : AirIntakePosition    = AirIntakePosition.FinalAuto
    ): FramedAirIntakePipes =
        FramedAirIntakePipes(initialDir, position, descr)
