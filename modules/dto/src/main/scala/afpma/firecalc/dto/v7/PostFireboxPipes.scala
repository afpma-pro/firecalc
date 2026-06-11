/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto

import afpma.firecalc.dto.instances.V7Instances

/**
 * Initial direction of the post-firebox pipe chain — the azimuth/inclination
 * at the firebox exit, before any concrete pipe sections.
 *
 * Lives at the `PostFireboxPipes` level (not inside slot descriptors) so the
 * invariant "exactly one initial direction" is enforced by the type.
 */
final case class PostFireboxInitialDirection(
    azimuth    : AzimuthDirection,
    inclination: InclinationDirection
)

object PostFireboxInitialDirection:
    import afpma.firecalc.dto.instances.V4Instances.given

    given Encoder[PostFireboxInitialDirection] = semiauto.deriveEncoder
    given Decoder[PostFireboxInitialDirection] = semiauto.deriveDecoder

    /** Default: right (azimuth) + horizontal (inclination). */
    val default: PostFireboxInitialDirection =
        PostFireboxInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)

/**
 * Initial 3D position of the post-firebox pipe chain — the (x, y, z) coordinate
 * at the firebox exit, before any concrete pipe sections.
 *
 * Lives at the `PostFireboxPipes` level (not inside slot descriptors) so the
 * invariant "exactly one initial position" is enforced by the type.
 */
final case class PostFireboxInitialPosition(
    x: Length,
    y: Length,
    z: Length
)

object PostFireboxInitialPosition:
    import afpma.firecalc.dto.instances.CommonInstances.given

    given Encoder[PostFireboxInitialPosition] = semiauto.deriveEncoder
    given Decoder[PostFireboxInitialPosition] = semiauto.deriveDecoder

/**
 * Wrapper for the post-firebox pipe chain that enforces the invariant:
 * exactly one initial direction and one initial position, followed by a
 * sequence of tagged pipe slots.
 *
 * V7 uses `PostFireboxPipeDescrSlot_V7` with V4 descriptor types
 * (FlowOnlyPipeDescr_15544_V4, ThermalPipeDescr_13384_V4).
 * Tracking operations (SetInitialDirection, SetInitialPosition, SetFinalPosition)
 * are valid in V7 descriptors and are NOT stripped during sanitization.
 */
final case class PostFireboxPipes(
    initialDirection: PostFireboxInitialDirection,
    initialPosition : PostFireboxInitialPosition,
    slots           : Seq[PostFireboxPipeDescrSlot_V7]
)

object PostFireboxPipes:
    // ── Frame extraction (for legacy migration from V6 slots) ────────────

    private val defaultInitialPosition: PostFireboxInitialPosition =
        PostFireboxInitialPosition(0.cm, 0.cm, 0.cm)

    private def extractInitialDirectionFlowOnly(
        descr: Seq[FlowOnlyPipeDescr_15544_V4]
    ): PostFireboxInitialDirection =
        descr
            .collectFirst:
                case FlowOnlyPipeTrackingOp_15544_V4.SetInitialDirection(az, incl) =>
                    PostFireboxInitialDirection(az, incl)
            .getOrElse(PostFireboxInitialDirection.default)

    private def extractInitialPositionFlowOnly(
        descr: Seq[FlowOnlyPipeDescr_15544_V4]
    ): Option[PostFireboxInitialPosition] =
        descr.collectFirst:
            case FlowOnlyPipeTrackingOp_15544_V4.SetInitialPosition(x, y, z) =>
                PostFireboxInitialPosition(x, y, z)

    private def extractInitialDirectionThermal(
        descr: Seq[ThermalPipeDescr_13384_V4]
    ): PostFireboxInitialDirection =
        descr
            .collectFirst:
                case ThermalPipeTrackingOp_13384_V4.SetInitialDirection(az, incl) =>
                    PostFireboxInitialDirection(az, incl)
            .getOrElse(PostFireboxInitialDirection.default)

    private def extractInitialPositionThermal(
        descr: Seq[ThermalPipeDescr_13384_V4]
    ): Option[PostFireboxInitialPosition] =
        descr.collectFirst:
            case ThermalPipeTrackingOp_13384_V4.SetInitialPosition(x, y, z) =>
                PostFireboxInitialPosition(x, y, z)

    private def extractInitialFrame(
        slot: PostFireboxPipeDescrSlot_V7
    ): (PostFireboxInitialDirection, Option[PostFireboxInitialPosition]) =
        slot match
            case PostFireboxPipeDescrSlot_V7.FlueSlot(d)        =>
                (extractInitialDirectionFlowOnly(d), extractInitialPositionFlowOnly(d))
            case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
                (extractInitialDirectionThermal(d), extractInitialPositionThermal(d))
            case PostFireboxPipeDescrSlot_V7.ConnectorSlot(d)   =>
                (extractInitialDirectionThermal(d), extractInitialPositionThermal(d))
            case PostFireboxPipeDescrSlot_V7.ChimneySlot(d)     =>
                (extractInitialDirectionThermal(d), extractInitialPositionThermal(d))
            case PostFireboxPipeDescrSlot_V7.NoFlueSlot         =>
                (PostFireboxInitialDirection.default, None)

    // ── Sanitization: no-op in V7 (tracking ops are valid) ────────────────

    private[dto] def stripDeprecatedFlowOnly15544(
        descr: Seq[FlowOnlyPipeDescr_15544_V4]
    ): Seq[FlowOnlyPipeDescr_15544_V4] =
        descr

    private[dto] def stripDeprecatedThermal13384(
        descr: Seq[ThermalPipeDescr_13384_V4]
    ): Seq[ThermalPipeDescr_13384_V4] =
        descr

    def sanitizeSlot(slot: PostFireboxPipeDescrSlot_V7): PostFireboxPipeDescrSlot_V7 =
        slot

    def sanitize(pipes: PostFireboxPipes): PostFireboxPipes =
        pipes

    def hasDeprecatedPostFireboxElement(slot: PostFireboxPipeDescrSlot_V7): Boolean =
        false

    // ── Construction helpers ──────────────────────────────────────────

    def clean(
        initialDirection: PostFireboxInitialDirection,
        initialPosition : PostFireboxInitialPosition,
        slots           : Seq[PostFireboxPipeDescrSlot_V7]
    ): PostFireboxPipes =
        PostFireboxPipes(initialDirection, initialPosition, slots)

    def fromLegacySlots(slots: Seq[PostFireboxPipeDescrSlot_V7]): PostFireboxPipes =
        slots.headOption match
            case None            =>
                PostFireboxPipes(PostFireboxInitialDirection.default, defaultInitialPosition, Seq.empty)
            case Some(firstSlot) =>
                val (initialDirection, initialPosition) = extractInitialFrame(firstSlot)
                PostFireboxPipes(initialDirection, initialPosition.getOrElse(defaultInitialPosition), slots)

    // ── Codecs ────────────────────────────────────────────────────────────

    import V7Instances.given

    given Encoder[PostFireboxPipes] = io.circe.Encoder.instance[PostFireboxPipes] { pipes =>
        Json.obj(
            "initialDirection" -> summon[Encoder[PostFireboxInitialDirection]].apply(pipes.initialDirection),
            "initialPosition"  -> summon[Encoder[PostFireboxInitialPosition]].apply(pipes.initialPosition),
            "slots"            -> summon[Encoder[Seq[PostFireboxPipeDescrSlot_V7]]].apply(pipes.slots)
        )
    }

    given Decoder[PostFireboxPipes] = io.circe.Decoder.instance[PostFireboxPipes] { c =>
        for
            dir   <- c.downField("initialDirection").as[PostFireboxInitialDirection]
            pos   <- c.downField("initialPosition").as[PostFireboxInitialPosition]
            slots <- c.downField("slots").as[Seq[PostFireboxPipeDescrSlot_V7]]
        yield PostFireboxPipes(dir, pos, slots)
    }
