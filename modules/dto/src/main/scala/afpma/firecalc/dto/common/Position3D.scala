/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.Length
import afpma.firecalc.units.coulombutils.cm

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

final case class Position3D(
    x: Length,
    y: Length,
    z: Length
)

extension (p: Position3D) def toVec3: Vec3 = Vec3(p.x.value, p.y.value, p.z.value)

object Position3D:
    import afpma.firecalc.dto.instances.CommonInstances.given

    val Origin: Position3D = Position3D(0.cm, 0.cm, 0.cm)

    given Encoder[Position3D] = semiauto.deriveEncoder
    given Decoder[Position3D] = semiauto.deriveDecoder
