/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot

import afpma.firecalc.units.coulombutils.Length

import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

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
    given Encoder[PostFireboxPipes] = semiauto.deriveEncoder
    given Decoder[PostFireboxPipes] = semiauto.deriveDecoder
