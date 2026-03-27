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
        if version.unwrap > CatalogMigrations.CURRENT_VERSION.unwrap then
            Left(CatalogParseError.VersionTooNew(version, CatalogMigrations.CURRENT_VERSION))
        else if version.unwrap >= CatalogMigrations.OLDEST_SUPPORTED_VERSION.unwrap then
            // V4 and V5 catalog types are structurally identical — decode with current decoders.
            // When a future version changes catalog types, add version-specific migration here.
            decodeCurrent(json, CatalogMigrations.CURRENT_VERSION)
        else
            Left(CatalogParseError.MigrationFailed(
                version,
                s"Version V${version.unwrap} is too old; oldest supported is V${CatalogMigrations.OLDEST_SUPPORTED_VERSION.unwrap}"
            ))

    private def decodeCurrent(json: Json, version: FireCalc_Version): Either[CatalogParseError, CatalogFile] =
        val cursor = json.hcursor

        // Decode catalog_name as Map[String, String].
        // Absent is OK (optional), but present-and-malformed is an error.
        val catalogNameEither: Either[CatalogParseError, Map[String, String]] =
            cursor.downField("catalog_name").focus match
                case None       => Right(Map.empty)
                case Some(json) => json.as[Map[String, String]]
                    .left.map(e => CatalogParseError.InvalidFile(s"catalog_name: ${e.getMessage}"))

        // Decode each category section using the registry.
        // Absent sections are skipped (forward-compatible).
        // Present sections that are not arrays are surfaced as DecodeError.
        // Present sections that fail to decode are surfaced as DecodeError.
        def decodeSections(): Either[CatalogParseError, Map[String, Seq[Any]]] =
            CatalogCategoryRegistry.all.foldLeft[Either[CatalogParseError, Map[String, Seq[Any]]]](Right(Map.empty)):
                (accOrErr, cat) =>
                    accOrErr.flatMap: acc =>
                        cursor.downField(cat.yamlKey).focus match
                            case None => Right(acc)  // section absent — skip (forward-compatible)
                            case Some(sectionJson) if sectionJson.isArray =>
                                cat.decodeSectionJson(sectionJson) match
                                    case Right(entries) => Right(acc + (cat.yamlKey -> entries))
                                    case Left(failure)  => Left(CatalogParseError.DecodeError(cat.yamlKey, failure.getMessage))
                            case Some(_) =>
                                Left(CatalogParseError.DecodeError(cat.yamlKey, "expected an array"))

        for
            catalogName <- catalogNameEither
            sections    <- decodeSections()
        yield CatalogFile(catalog_version = version, catalog_name = catalogName, sections = CatalogSections(sections))
