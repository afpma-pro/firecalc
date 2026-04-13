/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.instances

import afpma.firecalc.units.all.given
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.Firebox_V3
import afpma.firecalc.dto.v4.TypeOfAppliance
import afpma.firecalc.dto.v5.Firebox_V4

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

object V5Instances:

    import CommonInstances.given
    export V4Instances.given

    given Decoder[Firebox_V4] = semiauto.deriveDecoder[Firebox_V4]
    given Encoder[Firebox_V4] = semiauto.deriveEncoder[Firebox_V4]
