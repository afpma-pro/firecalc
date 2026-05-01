/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.shared.api

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class ProductCopy(name: String, description: String)

object ProductCopy:
    given Encoder[ProductCopy] = deriveEncoder
    given Decoder[ProductCopy] = deriveDecoder

/**
 * Build-time seed for the YAML template. NEVER read at runtime.
 * Consumed only by the `genYamlTemplate` sbt task.
 */
opaque type YamlTemplateSeed = Map[String, ProductCopy]

object YamlTemplateSeed:
    def apply (entries: Map[String, ProductCopy])            : YamlTemplateSeed         = entries
    extension (s      : YamlTemplateSeed        ) def entries: Map[String, ProductCopy] = s
    // No Circe codec on purpose: this type is build-time-only. Any attempt to
    // serialize a containing structure must refuse to compile.
