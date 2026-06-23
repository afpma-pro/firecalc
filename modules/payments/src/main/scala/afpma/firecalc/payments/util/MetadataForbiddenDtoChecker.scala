/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.util

import afpma.firecalc.domain.IsBackendForbidden
import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.v7.BackendForbiddenDtoChecker
import afpma.firecalc.payments.shared.api.FileDescriptionWithContent
import afpma.firecalc.payments.shared.api.ProductMetadata

import scala.util.Success

/**
 * Detects backend-forbidden DTO instances (`IsBackendForbidden`) in a project
 * uploaded as `ProductMetadata`.
 *
 * Sibling to `MetadataFireboxDecoder`: it base64-decodes the uploaded YAML,
 * runs it through `FireCalcYAMLMigrations.decodeAndMigrateTry` (the *same*
 * decode+migrate path used by `FireCalcReportFactory_15544_Strict.loadYAMLString`,
 * so the check sees exactly what the report factory would see), narrows to the
 * latest `FireCalcYAML` version, and runs the pure `BackendForbiddenDtoChecker`
 * tree-walk from the `dto` module.
 *
 * Returns:
 *   - `Some(forbidden)` if a forbidden DTO instance is found — the caller
 *     (`PurchaseServiceImpl.createPurchaseIntent`) raises `ForbiddenDtoException`
 *     with `forbidden.getClass.getSimpleName` before any database side effect.
 *   - `None` if no forbidden DTO is found, OR if the metadata is absent / not a
 *     `FileDescriptionWithContent`, OR if decode/migrate fails, OR if the
 *     decoded value is not the latest `FireCalcYAML` version. These pass-through
 *     cases mirror `MetadataFireboxDecoder.extractFirebox`'s silent-failure
 *     behaviour — decode errors are surfaced later by the report factory's own
 *     `loadYAMLString` step, not here (avoids double error paths).
 */
object MetadataForbiddenDtoChecker:

    def findForbidden(metadata: ProductMetadata): Option[IsBackendForbidden] =
        metadata match
            case fdc: FileDescriptionWithContent =>
                findForbiddenInBase64Yaml(fdc.content)

    private def findForbiddenInBase64Yaml(base64Yaml: String): Option[IsBackendForbidden] =
        for
            yaml      <- Base64StringDecoder.decodeToString(base64Yaml).toOption
            fc        <- FireCalcYAMLMigrations.decodeAndMigrateTry(yaml) match
                case Success(fc: FireCalcYAML) => Some(fc)
                case _                         => None
            forbidden <- BackendForbiddenDtoChecker.findForbidden(fc).headOption
        yield forbidden
