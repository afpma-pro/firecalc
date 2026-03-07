/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import afpma.firecalc.dto.all.FireCalc_Version

/** Parsed catalog file. Holds a map of decoded entry lists keyed by yamlKey.
  * Use entriesFor[A] with a given CatalogCategory[A] to get type-safe access.
  */
case class CatalogFile(
    catalog_version: FireCalc_Version,
    catalog_name   : Map[String, String],  // locale key → name string (self-contained I18N)
    sections       : Map[String, Seq[Any]] // yamlKey → decoded entries (type-erased)
):
    /** Type-safe accessor: get entries for a specific category. */
    def entriesFor[A](using cat: CatalogCategory[A]): Seq[A] =
        sections.getOrElse(cat.yamlKey, Seq.empty).asInstanceOf[Seq[A]]
