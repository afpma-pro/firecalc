/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import io.circe.Json
import io.circe.syntax.*
import io.circe.yaml.scalayaml.printer as yamlPrinter

/** Serializes a [[CatalogFile]] to YAML (.fcalc-db format).
  * Reuses the existing Circe encoders from [[CatalogCategoryInstances]].
  */
object CatalogWriter:

    def toYaml(file: CatalogFile): String =
        val sectionFields = CatalogCategoryRegistry.all.flatMap(_.encodeSectionIfPresent(file.sections))
        val allFields: List[(String, Json)] =
            ("catalog_version" -> Json.fromInt(file.catalog_version.unwrap)) ::
            ("catalog_name"    -> file.catalog_name.asJson) ::
            sectionFields
        yamlPrinter.print(Json.obj(allFields*))
