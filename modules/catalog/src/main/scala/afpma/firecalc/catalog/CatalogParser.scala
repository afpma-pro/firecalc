/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import afpma.firecalc.dto.all.FireCalc_Version

import io.circe.Json
import io.circe.yaml.scalayaml.parser as yamlParser

/** Errors that can occur when parsing a .fcalc-db catalog file. */
enum CatalogParseError:
    case InvalidFile(detail: String)
    case MissingVersion
    case VersionTooNew(found: FireCalc_Version, supported: FireCalc_Version)
    case MigrationFailed(version: FireCalc_Version, detail: String)
    case DecodeError(category: String, detail: String)

/** Parses .fcalc-db YAML strings into CatalogFile values. */
object CatalogParser:

    /** Parse a .fcalc-db file content string into a CatalogFile.
      * Returns Left(CatalogParseError) on any failure.
      */
    def parse(yamlString: String): Either[CatalogParseError, CatalogFile] =
        for
            json    <- yamlParser.parse(yamlString).left.map(e => CatalogParseError.InvalidFile(e.getMessage))
            version <- detectVersion(json).toRight(CatalogParseError.MissingVersion)
            file    <- decodeAndMigrate(json, version)
        yield file

    private def detectVersion(json: Json): Option[FireCalc_Version] =
        json.hcursor.downField("catalog_version").as[FireCalc_Version].toOption

    private def decodeAndMigrate(json: Json, version: FireCalc_Version): Either[CatalogParseError, CatalogFile] =
        if version > CatalogMigrations.CURRENT_VERSION then
            Left(CatalogParseError.VersionTooNew(version, CatalogMigrations.CURRENT_VERSION))
        else if version == CatalogMigrations.CURRENT_VERSION then
            decodeCurrent(json)
        else
            // Future: apply migration chain here using dto Chimney transformers
            Left(CatalogParseError.MigrationFailed(
                version,
                s"No migration path from V${version.unwrap} to V${CatalogMigrations.CURRENT_VERSION.unwrap}"
            ))

    private def decodeCurrent(json: Json): Either[CatalogParseError, CatalogFile] =
        val cursor = json.hcursor

        // Decode catalog_name as Map[String, String]
        val catalogName: Map[String, String] =
            cursor.downField("catalog_name").as[Map[String, String]].getOrElse(Map.empty)

        // Decode catalog_version (already known, just re-extract)
        val version = cursor.downField("catalog_version").as[FireCalc_Version].getOrElse(CatalogMigrations.CURRENT_VERSION)

        // Decode each category section using the registry.
        // Absent sections are skipped (forward-compatible).
        // Present sections that fail to decode are surfaced as DecodeError.
        val sectionsOrError: Either[CatalogParseError, Map[String, Seq[Any]]] =
            CatalogCategoryRegistry.all.foldLeft[Either[CatalogParseError, Map[String, Seq[Any]]]](Right(Map.empty)):
                (accOrErr, cat) =>
                    accOrErr.flatMap: acc =>
                        cursor.downField(cat.yamlKey).focus match
                            case Some(sectionJson) if sectionJson.isArray =>
                                cat.decodeSectionJson(sectionJson) match
                                    case Right(entries) => Right(acc + (cat.yamlKey -> entries))
                                    case Left(failure)  => Left(CatalogParseError.DecodeError(cat.yamlKey, failure.getMessage))
                            case _ => Right(acc)  // section absent or not an array — skip

        sectionsOrError.map: sections =>
            CatalogFile(catalog_version = version, catalog_name = catalogName, sections = sections)
