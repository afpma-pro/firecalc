/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.ui.models.schema.AppStateSchema
import afpma.firecalc.ui.models.schema.AppStateSchemaLoader
import afpma.firecalc.ui.models.schema.v6.AppStateSchema_V6

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.circe.syntax.*
import io.circe.yaml.scalayaml.printer as yamlPrinter

/** Helper for managing AppStateSchema initialization and persistence. */
object AppStateSchemaHelper:

    /** Create initial schema with default values */
    def createInitialSchema(): AppStateSchema =
        AppStateSchema_V6  (
            engine_state   = EngineState.init,
            sensitive_data = ClientProjectData.empty,
            billing_data   = afpma.firecalc.ui.instances.defaultable.default_BillingInfo.default
        )

    /** Encode schema to YAML */
    def encodeToYaml(schema: AppStateSchema): Try[String] =
        try
            val json = schema.asJson
            Success(yamlPrinter.print(json))
        catch
            case e: Exception =>
                scala.scalajs.js.Dynamic.global.console.log(s"Failed to encode schema: ${e.getMessage()}")
                Failure                                    (e                                            )

    /** Decode schema from YAML */
    def decodeFromYaml(yaml: String): Try[AppStateSchema] =
        AppStateSchemaLoader.loadFromYaml(yaml) match
            case Success(sch) => Success(sch)
            case Failure(e)   =>
                scala.scalajs.js.Dynamic.global.console.log(s"Failed to decode schema: ${e.getMessage()}")
                scala.scalajs.js.Dynamic.global.console.log("Returning default schema"                   )
                Success                                    (createInitialSchema()                        )
