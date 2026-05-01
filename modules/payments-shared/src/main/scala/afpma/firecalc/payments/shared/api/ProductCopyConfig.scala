/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.shared.api

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class ProductCopyConfig(entries: Map[String, Map[String, ProductCopy]])

object ProductCopyConfig:
    val empty: ProductCopyConfig = ProductCopyConfig(Map.empty)
    given Encoder[ProductCopyConfig] = deriveEncoder
    given Decoder[ProductCopyConfig] = deriveDecoder
