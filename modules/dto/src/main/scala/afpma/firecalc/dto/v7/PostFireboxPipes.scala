/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.FlowOnlyPipeDescr_15544_V3
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_15544_V3
import afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3
import afpma.firecalc.dto.v4.ThermalPipeDescr_13384_V3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

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
 * Replaces the flat `Seq[PostFireboxPipeDescrSlot]` representation (V6)
 * where `SetInitialDirection`/`SetInitialPosition` were embedded as regular
 * elements inside slot descriptors.
 */
final case class PostFireboxPipes(
    initialDirection: PostFireboxInitialDirection,
    initialPosition : PostFireboxInitialPosition,
    slots           : Seq[PostFireboxPipeDescrSlot]
)

object PostFireboxPipes:
    // ── Sanitizer: strip V7-forbidden legacy descriptor elements ────────

    private val defaultInitialPosition: PostFireboxInitialPosition =
        PostFireboxInitialPosition(0.cm, 0.cm, 0.cm)

    private def extractInitialDirectionFlowOnly(
        descr: Seq[FlowOnlyPipeDescr_15544_V3]
    ): PostFireboxInitialDirection =
        descr
            .collectFirst:
                case SetFlowOnlyPipeProp_15544_V3.SetInitialDirection(az, incl) =>
                    PostFireboxInitialDirection(az, incl)
            .getOrElse(PostFireboxInitialDirection.default)

    private def extractInitialPositionFlowOnly(
        descr: Seq[FlowOnlyPipeDescr_15544_V3]
    ): Option[PostFireboxInitialPosition] =
        descr.collectFirst:
            case SetFlowOnlyPipeProp_15544_V3.SetInitialPosition(x, y, z) =>
                PostFireboxInitialPosition(x, y, z)

    private def extractInitialDirectionThermal(
        descr: Seq[ThermalPipeDescr_13384_V3]
    ): PostFireboxInitialDirection =
        descr
            .collectFirst:
                case SetThermalPipeProp_13384_V3.SetInitialDirection(az, incl) =>
                    PostFireboxInitialDirection(az, incl)
            .getOrElse(PostFireboxInitialDirection.default)

    private def extractInitialPositionThermal(
        descr: Seq[ThermalPipeDescr_13384_V3]
    ): Option[PostFireboxInitialPosition] =
        descr.collectFirst:
            case SetThermalPipeProp_13384_V3.SetInitialPosition(x, y, z) =>
                PostFireboxInitialPosition(x, y, z)

    private def extractInitialFrame(
        slot: PostFireboxPipeDescrSlot
    ): (PostFireboxInitialDirection, Option[PostFireboxInitialPosition]) =
        slot match
            case PostFireboxPipeDescrSlot.FlueSlot(d)        =>
                (extractInitialDirectionFlowOnly(d), extractInitialPositionFlowOnly(d))
            case PostFireboxPipeDescrSlot.ThermalFlueSlot(d) =>
                (extractInitialDirectionThermal(d), extractInitialPositionThermal(d))
            case PostFireboxPipeDescrSlot.ConnectorSlot(d)   =>
                (extractInitialDirectionThermal(d), extractInitialPositionThermal(d))
            case PostFireboxPipeDescrSlot.ChimneySlot(d)     =>
                (extractInitialDirectionThermal(d), extractInitialPositionThermal(d))
            case PostFireboxPipeDescrSlot.NoFlueSlot         =>
                (PostFireboxInitialDirection.default, None)

    private[dto] def stripDeprecatedFlowOnly15544(
        descr: Seq[FlowOnlyPipeDescr_15544_V3]
    ): Seq[FlowOnlyPipeDescr_15544_V3] =
        descr.filterNot:
            case _: SetFlowOnlyPipeProp_15544_V3.SetInitialDirection => true
            case _: SetFlowOnlyPipeProp_15544_V3.SetInitialPosition  => true
            case _: SetFlowOnlyPipeProp_15544_V3.SetFinalPosition    => true
            case _ => false

    private[dto] def stripDeprecatedThermal13384(
        descr: Seq[ThermalPipeDescr_13384_V3]
    ): Seq[ThermalPipeDescr_13384_V3] =
        descr.filterNot:
            case _: SetThermalPipeProp_13384_V3.SetInitialDirection => true
            case _: SetThermalPipeProp_13384_V3.SetInitialPosition  => true
            case _: SetThermalPipeProp_13384_V3.SetFinalPosition    => true
            case _ => false

    def sanitizeSlot(slot: PostFireboxPipeDescrSlot): PostFireboxPipeDescrSlot =
        slot match
            case PostFireboxPipeDescrSlot.FlueSlot(d)        =>
                PostFireboxPipeDescrSlot.FlueSlot(stripDeprecatedFlowOnly15544(d))
            case PostFireboxPipeDescrSlot.ThermalFlueSlot(d) =>
                PostFireboxPipeDescrSlot.ThermalFlueSlot(stripDeprecatedThermal13384(d))
            case PostFireboxPipeDescrSlot.ConnectorSlot(d)   =>
                PostFireboxPipeDescrSlot.ConnectorSlot(stripDeprecatedThermal13384(d))
            case PostFireboxPipeDescrSlot.ChimneySlot(d)     =>
                PostFireboxPipeDescrSlot.ChimneySlot(stripDeprecatedThermal13384(d))
            case PostFireboxPipeDescrSlot.NoFlueSlot         =>
                PostFireboxPipeDescrSlot.NoFlueSlot

    def sanitize(pipes: PostFireboxPipes): PostFireboxPipes =
        pipes.copy(slots = pipes.slots.map(sanitizeSlot))

    def hasDeprecatedPostFireboxElement(slot: PostFireboxPipeDescrSlot): Boolean =
        sanitizeSlot(slot) != slot

    // ── Construction helpers ──────────────────────────────────────────

    def clean(
        initialDirection: PostFireboxInitialDirection,
        initialPosition : PostFireboxInitialPosition,
        slots           : Seq[PostFireboxPipeDescrSlot]
    ): PostFireboxPipes =
        sanitize(PostFireboxPipes(initialDirection, initialPosition, slots))

    def fromLegacySlots(slots: Seq[PostFireboxPipeDescrSlot]): PostFireboxPipes =
        slots.headOption match
            case None            =>
                PostFireboxPipes(PostFireboxInitialDirection.default, defaultInitialPosition, Seq.empty)
            case Some(firstSlot) =>
                val (initialDirection, initialPosition) = extractInitialFrame(firstSlot)
                sanitize(PostFireboxPipes(initialDirection, initialPosition.getOrElse(defaultInitialPosition), slots))

    // ── Codecs (sanitizing decoder, plain encoder) ─────────────────────

    private val rawDecoder: Decoder[PostFireboxPipes] = semiauto.deriveDecoder

    given Encoder[PostFireboxPipes] = semiauto.deriveEncoder
    given Decoder[PostFireboxPipes] = rawDecoder.map(PostFireboxPipes.sanitize)
