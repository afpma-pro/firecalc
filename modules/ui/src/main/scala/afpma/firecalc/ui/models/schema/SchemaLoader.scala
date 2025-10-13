/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import scala.util.{Try, Success, Failure}
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.parser.decode
import afpma.firecalc.ui.models.schema.v1.AppStateSchema_V1

/**
 * Loads AppStateSchema from YAML/JSON strings.
 * No legacy support - expects Schema V1 format only.
 */
object SchemaLoader:

    /**
     * Load AppStateSchema_V1 from YAML string.
     * Returns Success with schema or Failure with error.
     */
    def loadFromYaml(yaml: String): Try[AppStateSchema_V1] =
        yamlParser.parse(yaml) match
            case Left(parseFailure) =>
                Failure(new Exception(s"Failed to parse YAML: ${parseFailure.getMessage()}"))
            case Right(json) =>
                decode[AppStateSchema_V1](json.noSpaces) match
                    case Left(decodeError) =>
                        Failure(new Exception(s"Failed to decode schema: ${decodeError.getMessage()}"))
                    case Right(schema) =>
                        Success(schema)

    /**
     * Load AppStateSchema_V1 from JSON string.
     */
    def loadFromJson(json: String): Try[AppStateSchema_V1] =
        decode[AppStateSchema_V1](json) match
            case Left(decodeError) =>
                Failure(new Exception(s"Failed to decode schema: ${decodeError.getMessage()}"))
            case Right(schema) =>
                Success(schema)