/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.instances.V5Instances

import io.circe.*
import io.circe.syntax.*

case class FireboxCacheState(cache: Map[String, Firebox] = Map.empty)

object FireboxCacheState:

    import V5Instances.given

    given Decoder[FireboxCacheState] = Decoder.instance { c =>
        c.downField("cache").as[Map[String, Firebox]].map(FireboxCacheState(_))
    }

    given Encoder[FireboxCacheState] = Encoder.instance { s =>
        Json.obj("cache" -> s.cache.asJson)
    }

    val empty: FireboxCacheState = FireboxCacheState()

    def cacheKey(fb: Firebox): String = fb match
        case _: Firebox.Traditional            => "Traditional"
        case _: Firebox.Ecolabeled             => "Ecolabeled"
        case _: Firebox.AFPMA_PRSE             => "AFPMA_PRSE"
        case _: Firebox.SingleTested           => "SingleTested"
        case _: Firebox.Door15aFirebox_Catalog => "Door15aFirebox_Catalog"
