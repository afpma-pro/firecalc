/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */
package afpma.firecalc.dto.v7

import afpma.firecalc.dto.common.FramedPipeSequence
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.PipeInitialFrame
import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.units.Vec3
import afpma.firecalc.dto.common.toVec3
import afpma.firecalc.dto.instances.V7Instances

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

enum AirIntakePosition:
    case Initial(pos: Position3D)
    case Final(pos: Position3D)

object AirIntakePosition:
    given Encoder[AirIntakePosition] = semiauto.deriveEncoder
    given Decoder[AirIntakePosition] = semiauto.deriveDecoder

final case class FramedAirIntakePipes(
    initialDir: PipeInitialDirection,
    position  : AirIntakePosition,
    descr     : Seq[FlowOnlyPipeDescr_13384_V4]
) extends FramedPipeSequence[FlowOnlyPipeDescr_13384_V4] {

    /**
     * The initial frame defines the starting orientation and position.
     *
     * If the pipe is anchored to the FINAL position (`AirIntakePosition.Final`),
     * the initial position is treated as `Position3D.Origin` (0,0,0).
     * The actual start point of the pipe chain is then computed by the
     * `PositionTracker` via reverse calculation from the final anchor point.
     */
    override def initialFrame: PipeInitialFrame =
        position match {
            case AirIntakePosition.Initial(pos) => PipeInitialFrame(initialDir, pos)
            case AirIntakePosition.Final(_)     => PipeInitialFrame(initialDir, Position3D.Origin)
        }

    override def descriptors: Seq[FlowOnlyPipeDescr_13384_V4] = descr
}

object FramedAirIntakePipes:
    extension (p: FramedAirIntakePipes)
        def rawPosition: Position3D = p.position match
            case AirIntakePosition.Initial(pos) => pos
            case AirIntakePosition.Final(pos)   => pos

        def positionPoints: (Vec3, Option[Vec3]) = p.position match
            case AirIntakePosition.Initial(pos) => (pos.toVec3, None               )
            case AirIntakePosition.Final(pos)   => (Vec3(0, 0, 0), Some(pos.toVec3))

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
        position  : AirIntakePosition    = AirIntakePosition.Initial(Position3D.Origin)
    ): FramedAirIntakePipes =
        FramedAirIntakePipes(initialDir, position, descr)
