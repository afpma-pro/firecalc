/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

import afpma.firecalc.dto.common.FramedPipeSequence
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.PipeInitialFrame
import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.dto.instances.V7Instances

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

/**
 * Wrapper for the post-firebox pipe chain that enforces the invariant:
 * exactly one initial direction and one initial position, followed by a
 * sequence of tagged pipe slots.
 *
 * V7 uses `PostFireboxPipeDescrSlot_V7` with V4 descriptor types
 * (FlowOnlyPipeDescr_15544_V4, ThermalPipeDescr_13384_V4).
 * Initial direction/position live in `FramedPostFireboxPipes.initialFrame`.
 * Descriptor-level tracking operations are stripped by sanitization.
 */
final case class FramedPostFireboxPipes(
    initialFrame: PipeInitialFrame,
    slots       : Seq[PostFireboxPipeDescrSlot_V7]
) extends FramedPipeSequence[PostFireboxPipeDescrSlot_V7] {
    override def descriptors: Seq[PostFireboxPipeDescrSlot_V7] = slots
}

object FramedPostFireboxPipes:
    // ── Frame extraction (for legacy migration from V6 slots) ────────────

    val defaultInitialPosition: Position3D = Position3D.Origin

    private[dto] def extractInitialFrame(
        slot: PostFireboxPipeDescrSlot_V7
    ): (PipeInitialDirection, Option[Position3D]) =
        (PipeInitialDirection.default, None)

    import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7.*

    def sanitizeSlot(slot: PostFireboxPipeDescrSlot_V7): PostFireboxPipeDescrSlot_V7 =
        slot match
            case FlueSlot(d)        => FlueSlot(d)
            case ThermalFlueSlot(d) => ThermalFlueSlot(d.map(stripDeprecatedThermalDescr))
            case ConnectorSlot(d)   => ConnectorSlot(d)
            case ChimneySlot(d)     => ChimneySlot    (d.map(stripDeprecatedThermalDescr))
            case NoFlueSlot         => NoFlueSlot

    private def stripDeprecatedThermalDescr(
        d: ThermalPipeDescr_13384_V4
    ): ThermalPipeDescr_13384_V4 =
        d match
            case SetThermalPipeProp_13384_V4.SetPropertiesInBatch(batchName, props, image) =>
                val strippedProps = stripDeprecatedThermalProps(props)
                if strippedProps.isEmpty then d
                else SetThermalPipeProp_13384_V4.SetPropertiesInBatch(batchName, strippedProps, image)
            case SetThermalPipeProp_13384_V4.LinedFlue(batchName, liner, airSpace, casing) =>
                val strippedLiner  = sanitizeThermalBatch(liner)
                val strippedCasing = sanitizeThermalBatch(casing)
                if strippedLiner.props.isEmpty && strippedCasing.props.isEmpty then d
                else
                    SetThermalPipeProp_13384_V4.LinedFlue(
                        batch_name = batchName,
                        liner      = strippedLiner,
                        air_space  = airSpace,
                        casing     = strippedCasing
                    )
            case _                                                                         => d

    private def sanitizeThermalBatch(
        batch: SetThermalPipeProp_13384_V4.SetPropertiesInBatch
    ): SetThermalPipeProp_13384_V4.SetPropertiesInBatch =
        batch.copy(props = stripDeprecatedThermalProps(batch.props))

    private def stripDeprecatedThermalProp(
        prop: SetThermalPipeProp_13384_V4.SetSingleProp
    ): Option[SetThermalPipeProp_13384_V4.SetSingleProp] =
        prop match
            case SetThermalPipeProp_13384_V4.SetPipeLocation(_) => None
            case other                                          => Some(other)

    private def stripDeprecatedThermalProps(
        props: Seq[SetThermalPipeProp_13384_V4.SetSingleProp]
    ): Seq[SetThermalPipeProp_13384_V4.SetSingleProp] =
        props.flatMap(stripDeprecatedThermalProp)

    def sanitize(pipes: FramedPostFireboxPipes): FramedPostFireboxPipes =
        pipes.copy(slots = pipes.slots.map(sanitizeSlot))

    def hasDeprecatedPostFireboxElement(slot: PostFireboxPipeDescrSlot_V7): Boolean =
        slot match
            case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
                d.exists { desc =>
                    desc match
                        case SetThermalPipeProp_13384_V4.SetPropertiesInBatch(_, props, _) =>
                            props.exists(_.isInstanceOf[SetThermalPipeProp_13384_V4.SetPipeLocation])
                        case SetThermalPipeProp_13384_V4.LinedFlue(_, liner, _, casing)    =>
                            liner.props.exists(_.isInstanceOf[SetThermalPipeProp_13384_V4.SetPipeLocation]) ||
                            casing.props.exists(_.isInstanceOf[SetThermalPipeProp_13384_V4.SetPipeLocation])
                        case _                                                             => false
                }
            case _                                              => false

    // ── Construction helpers ──────────────────────────────────────────

    def clean(
        initialDirection: PipeInitialDirection,
        initialPosition : Position3D,
        slots           : Seq[PostFireboxPipeDescrSlot_V7]
    ): FramedPostFireboxPipes =
        FramedPostFireboxPipes(PipeInitialFrame(initialDirection, initialPosition), slots)

    def fromLegacySlots(slots: Seq[PostFireboxPipeDescrSlot_V7]): FramedPostFireboxPipes =
        slots.headOption match
            case None            =>
                FramedPostFireboxPipes(
                    PipeInitialFrame(PipeInitialDirection.default, defaultInitialPosition),
                    Seq.empty
                )
            case Some(firstSlot) =>
                val (initialDirection, initialPosition) = extractInitialFrame(firstSlot)
                FramedPostFireboxPipes(
                    PipeInitialFrame(initialDirection, initialPosition.getOrElse(defaultInitialPosition)),
                    slots
                )

    // ── Codecs ────────────────────────────────────────────────────────────

    import V7Instances.given

    given Encoder[FramedPostFireboxPipes] = io.circe.Encoder.instance[FramedPostFireboxPipes] { pipes =>
        Json.obj(
            "initialDirection" -> summon[Encoder[PipeInitialDirection]].apply(pipes.initialFrame.direction),
            "initialPosition"  -> summon[Encoder[Position3D]].apply(pipes.initialFrame.position),
            "slots"            -> summon[Encoder[Seq[PostFireboxPipeDescrSlot_V7]]].apply(pipes.slots)
        )
    }

    given Decoder[FramedPostFireboxPipes] = io.circe.Decoder.instance[FramedPostFireboxPipes] { c =>
        for
            dir   <- c.downField("initialDirection").as[PipeInitialDirection]
            pos   <- c.downField("initialPosition").as[Position3D]
            slots <- c.downField("slots").as[Seq[PostFireboxPipeDescrSlot_V7]]
        yield FramedPostFireboxPipes(PipeInitialFrame(dir, pos), slots)
    }
