/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.importers

import java.nio.file.Path

import afpma.firecalc.catalog.CasingPreset

object CasingsXlsxImporter:

    def read(path: Path): Seq[CasingPreset] =
        PipesXlsxImporter.read(path).map(CasingPreset(_))
