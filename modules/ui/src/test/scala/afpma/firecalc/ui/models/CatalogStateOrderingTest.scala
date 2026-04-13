/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.dto.all.*

import coulomb.*
import coulomb.syntax.*

import afpma.firecalc.catalog.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class CatalogStateOrderingTest extends AnyFreeSpec with Matchers:

    // Deliberately reverse-alphabetical to detect accidental sorting
    private val orderedNames = Seq("Entry-E", "Entry-D", "Entry-C", "Entry-B", "Entry-A")

    private def makeFlowResEntries: Seq[FlowResistanceCatalogEntry] =
        orderedNames.zipWithIndex.map: (name, i) =>
            FlowResistanceCatalogEntry         (
                name          = name,
                zeta          = (0.5 + i * 0.5).withUnit[1],
                cross_section = NoneOfEither
            )

    private def makeCatalogFile(entries: Seq[FlowResistanceCatalogEntry]): CatalogFile =
        import CatalogCategoryInstances.given
        val builder = CatalogSections.Builder()
        builder.add(entries)
        CatalogFile(
            catalog_version = CatalogMigrations.CURRENT_VERSION,
            catalog_name    = Map("en" -> "Test"),
            sections        = builder.build
        )

    "CatalogState.merge preserves insertion order of flow resistance entries" in {
        val entries = makeFlowResEntries
        val file    = makeCatalogFile(entries)
        val state   = CatalogState.merge(CatalogState.empty, file)

        state.flow_resistance_presets.values.toSeq.map(_.name) shouldBe orderedNames
    }

    "CatalogState.merge preserves order across multiple merges" in {
        val firstBatch  = makeFlowResEntries.take(3)
        val secondBatch = makeFlowResEntries.drop(3)

        val file1 = makeCatalogFile(firstBatch)
        val file2 = makeCatalogFile(secondBatch)

        val state = CatalogState.merge(
            CatalogState.merge(CatalogState.empty, file1),
            file2
        )

        state.flow_resistance_presets.values.toSeq.map(_.name) shouldBe orderedNames
    }

    "CatalogState JSON round-trip preserves ordering" in {
        import CatalogStateCodec.given
        import io.circe.syntax.*
        import io.circe.parser.*

        val entries = makeFlowResEntries
        val file    = makeCatalogFile(entries)
        val state   = CatalogState.merge(CatalogState.empty, file)

        val json    = state.asJson.noSpaces
        val decoded = decode[CatalogState](json)

        decoded.isRight shouldBe true
        decoded.toOption.get.flow_resistance_presets.values.toSeq.map(_.name) shouldBe orderedNames
    }
