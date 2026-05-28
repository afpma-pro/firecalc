/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.util

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.all.Firebox
import afpma.firecalc.payments.shared.api.FileDescriptionWithContent
import afpma.firecalc.payments.shared.api.ProductMetadata

import io.circe.yaml.scalayaml.parser as yamlParser

object MetadataFireboxDecoder:

    def extractFirebox(metadata: ProductMetadata): Option[Firebox] =
        metadata match
            case fdc: FileDescriptionWithContent =>
                decodeFirebox(fdc.content)

    private def decodeFirebox(base64Yaml: String): Option[Firebox] =
        for
            yaml  <- Base64StringDecoder.decodeToString(base64Yaml).toOption
            ac    <- yamlParser.parse(yaml).toOption
            fcYml <- ac.as[FireCalcYAML].toOption
        yield fcYml.firebox
