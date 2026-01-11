/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import scala.util.{Try, Success, Failure}
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.parser.decode
import afpma.firecalc.ui.models.schema.v2.AppStateSchema_V2

/**
 * Loads AppStateSchema from YAML/JSON strings.
 * Expects Schema V2 format (current version).
 */
object AppStateSchemaLoader:

    import AppStateSchema_V2.given

    /**
     * Load AppStateSchema from YAML string.
     * Returns Success with schema or Failure with error.
     */
    def loadFromYaml(yaml: String): Try[AppStateSchema] =
        yamlParser.parse(yaml) match
            case Left(parseFailure) =>
                Failure(new Exception(s"Failed to parse YAML: ${parseFailure.getMessage()}"))
            case Right(json) =>
                decode[AppStateSchema](json.noSpaces) match
                    case Left(decodeError) =>
                        Failure(new Exception(s"Failed to decode schema: ${decodeError.getMessage()}"))
                    case Right(schema) =>
                        Success(schema)

    /**
     * Load AppStateSchema from JSON string.
     */
    def loadFromJson(json: String): Try[AppStateSchema] =
        decode[AppStateSchema](json) match
            case Left(decodeError) =>
                Failure(new Exception(s"Failed to decode schema: ${decodeError.getMessage()}"))
            case Right(schema) =>
                Success(schema)