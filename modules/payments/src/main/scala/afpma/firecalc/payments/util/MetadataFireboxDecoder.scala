/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.util

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.all.Firebox
import afpma.firecalc.payments.shared.api.FileDescriptionWithContent

import scala.util.Success

object MetadataFireboxDecoder:

    /**
     * Extract the firebox from uploaded product metadata.
     *
     * Returns:
     *   - `Right(firebox)` on successful decode and migration
     *   - `Left(error)` with a descriptive message on any failure
     *     (bad base64, unparseable YAML, unknown schema version, migration failure)
     */
    def extractFirebox(metadata: FileDescriptionWithContent): Either[String, Firebox] =
        decodeFirebox(metadata.content)

    private def decodeFirebox(base64Yaml: String): Either[String, Firebox] =
        for
            yaml <- Base64StringDecoder.decodeToString(base64Yaml)
            fc   <- FireCalcYAMLMigrations.decodeAndMigrateTry(yaml) match
                case Success(fc)             => Right(fc)
                case scala.util.Failure(err) =>
                    Left(s"decode_and_migrate failed: ${err.getMessage}")
        yield fc.firebox
