/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import scala.util.{Try, Success, Failure}
import afpma.firecalc.ui.models.schema.v1.{AppStateSchema_V1, ClientProjectData_V1, BillingInfo_V1}
import afpma.firecalc.ui.models.schema.SchemaLoader
import io.circe.syntax.*
import io.circe.yaml.scalayaml.printer as yamlPrinter
import io.scalaland.chimney.dsl.*

/**
 * Helper for managing AppStateSchema initialization and persistence.
 */
object AppStateSchemaHelper:

    import SchemaTransformers.given

    /**
     * Create initial schema with default values
     */
    def createInitialSchema(): AppStateSchema_V1 =
        AppStateSchema_V1(
            engine_state = AppState.init,
            sensitive_data = ClientProjectData.empty,
            billing_data = afpma.firecalc.ui.instances.defaultable.default_BillingInfo.default
        )

    /**
     * Encode schema to YAML
     */
    def encodeToYaml(schema: AppStateSchema_V1): Try[String] =
        try
            val json = schema.asJson
            Success(yamlPrinter.print(json))
        catch
            case e: Exception =>
                scala.scalajs.js.Dynamic.global.console.log(s"Failed to encode schema: ${e.getMessage()}")
                Failure(e)

    /**
     * Decode schema from YAML
     */
    def decodeFromYaml(yaml: String): Try[AppStateSchema_V1] =
        SchemaLoader.loadFromYaml(yaml) match
            case Success(schema) => Success(schema)
            case Failure(e) =>
                scala.scalajs.js.Dynamic.global.console.log(s"Failed to decode schema: ${e.getMessage()}")
                scala.scalajs.js.Dynamic.global.console.log(s"Returning default schema")
                Success(createInitialSchema())