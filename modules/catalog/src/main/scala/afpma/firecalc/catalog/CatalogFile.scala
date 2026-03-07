/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import afpma.firecalc.dto.all.FireCalc_Version

/** Opaque wrapper around decoded catalog sections.
  * Type-safe access only via `get`; the underlying Map[String, Seq[Any]] is not exposed.
  */
final class CatalogSections private[catalog] (private val data: Map[String, Seq[Any]]):
    /** Type-safe accessor: get entries for a specific category. */
    def get[A](using cat: CatalogCategory[A]): Seq[A] =
        data.getOrElse(cat.yamlKey, Seq.empty).asInstanceOf[Seq[A]]
    def isEmpty: Boolean  = data.isEmpty
    def nonEmpty: Boolean = data.nonEmpty

object CatalogSections:
    private[catalog] val empty: CatalogSections                      = new CatalogSections(Map.empty)
    private[catalog] def apply(data: Map[String, Seq[Any]]): CatalogSections = new CatalogSections(data)

/** Parsed catalog file. Use entriesFor[A] with a given CatalogCategory[A] to get type-safe access. */
case class CatalogFile(
    catalog_version: FireCalc_Version,
    catalog_name   : Map[String, String],
    sections       : CatalogSections
):
    /** Type-safe accessor: get entries for a specific category. */
    def entriesFor[A](using CatalogCategory[A]): Seq[A] = sections.get[A]
