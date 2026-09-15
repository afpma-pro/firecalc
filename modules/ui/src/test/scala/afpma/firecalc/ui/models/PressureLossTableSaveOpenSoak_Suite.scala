/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.PipeShape
import afpma.firecalc.dto.v4.EmissionValues_DTO
import afpma.firecalc.dto.v4.EmissionsAndEfficiencyValues_DTO
import afpma.firecalc.dto.v4.PolluantName
import afpma.firecalc.dto.v4.TestEmissionValue_DTO
import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.ui.models.schema.AppStateSchema

import coulomb.*
import coulomb.syntax.*

import scala.collection.mutable.ListBuffer

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Save/open soak for `pressure_loss_table_raw`.
 *
 * Contract: push a [[Firebox_V4.Door15aFirebox_Catalog]] whose table uses REAL tab
 * (U+0009) / newline (U+000A) characters through repeated
 * `AppStateSchemaHelper.encodeToYaml` → `decodeFromYamlStrict` cycles and assert the
 * encoded document size stays constant and the table is written with printable separators
 * (`;` fields, `|` rows) and no escapes. Before the storage-form change the stock
 * `io.circe.yaml.scalayaml` pair doubled every backslash run per cycle — geometric growth,
 * the consumer-visible symptom of the 8.4 MB document.
 */
class PressureLossTableSaveOpenSoak_Suite extends AnyFreeSpec with Matchers:

    /** Faithful excerpt of the LANSELLE Grande porte table (real tabs/newlines). */
    private val pressureLossTable: String = Vector(
        Vector("mb_in_kg/sb_in_cm", "1.6", "2.3", "3.0"                        ),
        Vector("10", "1.90276576358025", "2.01549772345679", "2.07804162160494"),
        Vector("11", "2.3184165388889", "2.46304751111112", "2.53025359444445" ),
        Vector("12", "2.77695419320987", "2.94923911604938", "3.02091145864197"),
        Vector("13", "3.27837872654322", "3.47407253827161", "3.55001521419754"),
        Vector("14", "", "", ""                                                )
    ).map(_.mkString("\t")).mkString("\n")

    private def schemaWithTable(table: String = pressureLossTable): AppStateSchema =
        val o2ref   = 13.0.percent
        val door15a = Firebox_V4.Door15aFirebox_Catalog(
            reference                   = "SOAK-LANSELLE-EXCERPT",
            firebox_depth               = 0.428.withUnit[Meter],
            firebox_width               = 0.428.withUnit[Meter],
            firebox_height              = 0.0.withUnit[Meter],
            sb                          = 1.6.withUnit[Centimeter],
            pressure_loss_table_raw     = table,
            expectedAirIntakePipeShapes = List(PipeShape.Circle(0.173.withUnit[Meter])),
            actualAirIntakePipeShape    = PipeShape.Circle(0.173.withUnit[Meter]),
            co2_dry_nominal             = 12.0.percent,
            emissions_values            = EmissionsAndEfficiencyValues_DTO(
                firebox_name                = "SOAK-LANSELLE-EXCERPT",
                accredited_or_notified_body = "TestBody",
                test_reports                = Nil,
                emissions_values            = EmissionValues_DTO(
                    co   = TestEmissionValue_DTO(PolluantName.CO, Some(1154.0.mg_per_Nm3), "", o2ref),
                    dust = TestEmissionValue_DTO(PolluantName.Dust, Some(25.0.mg_per_Nm3), "", o2ref),
                    ogc  = TestEmissionValue_DTO(PolluantName.OGC, Some(40.0.mg_per_Nm3), "", o2ref),
                    nox  = TestEmissionValue_DTO(PolluantName.NOx, Some(119.0.mg_per_Nm3), "", o2ref)
                )
            ),
            glass_area                  = 0.1764.withUnit[(Meter ^ 2)],
            height_of_lowest_opening    = 0.05.withUnit[Meter]
        )
        AppStateSchemaHelper
            .createInitialSchema()
            .copy(engine_state = EngineState.init.copy(firebox = door15a))

    private def encode(schema: AppStateSchema): String =
        AppStateSchemaHelper.encodeToYaml(schema).fold(e => fail(s"encode failed: $e"), identity)

    private def decodeStrict(yaml: String): AppStateSchema =
        AppStateSchemaHelper.decodeFromYamlStrict(yaml).fold(e => fail(s"strict decode failed: $e"), identity)

    private def tableOf(schema: AppStateSchema): String =
        schema.engine_state.firebox match
            case d: Firebox_V4.Door15aFirebox_Catalog => d.pressure_loss_table_raw
            case other => fail(s"expected Door15a, got $other")

    /** The single `pressure_loss_table_raw` line of an encoded document. */
    private def tableLine(yaml: String): String =
        yaml.linesIterator.find(_.contains("pressure_loss_table_raw")).getOrElse(fail("field missing from document"))

    /**
     * Runs `cycles` save/open cycles, failing as soon as the encoded size changes —
     * geometric growth makes 100 unbounded cycles dangerous otherwise.
     */
    private def soak(cycles: Int): List[Int] =
        var schema = schemaWithTable()
        val sizes  = ListBuffer.empty[Int]
        for cycle <- 1 to cycles do
            val yaml = encode(schema)
            sizes += yaml.length
            if sizes.length > 1 && sizes.last != sizes.init.last then
                fail(
                    "escape doubling in YAML codec: encoded size grew at cycle " +
                        s"$cycle: ${sizes.init.last} -> ${sizes.last} chars " +
                        s"(expected constant across save/open; sizes so far: ${sizes.mkString(", ")})"
                )
            schema = decodeStrict(yaml)
        sizes.toList

    "pressure_loss_table_raw save/open soak" - {

        "encoded document size stays constant across 100 save/open cycles" in {
            val sizes = soak(100)

            sizes.distinct.length shouldBe 1
        }

        "the table is written with printable separators and no escapes" in {
            val line = tableLine(encode(schemaWithTable()))

            line should include(";")
            line should include("|")
            line should not include "\\"
        }

        "decoded table holds the canonical real tabs/newlines after one save/open cycle" in {
            tableOf(decodeStrict(encode(schemaWithTable()))) shouldBe pressureLossTable
        }

        "a poisoned document heals to the canonical table on load" in {
            // What the old codec left on disk in a re-saved document: doubled backslashes.
            val poisoned = pressureLossTable.replace("\t", "\\\\t").replace("\n", "\\\\n")

            tableOf(decodeStrict(encode(schemaWithTable(poisoned)))) shouldBe pressureLossTable
        }

        "loading a legacy document migrates the field to the storage form" in {
            // A document as an older build left it on disk: the scalar carries one escape
            // layer instead of printable separators.
            val legacyScalar = pressureLossTable.replace("\t", "\\t").replace("\n", "\\n")
            val stored       = encode(schemaWithTable())
            val legacyDoc    = stored.linesIterator
                .map: line =>
                    if line.contains("pressure_loss_table_raw") then
                        val indent = line.takeWhile(_ == ' ')
                        s"""${indent}pressure_loss_table_raw: "$legacyScalar""""
                    else line
                .mkString("\n") + "\n"

            val reopened = decodeStrict(legacyDoc)
            val resaved  = encode(reopened)

            tableOf(reopened) shouldBe pressureLossTable
            tableLine(resaved) should include(";")
            tableLine(resaved) should not include "\\"
        }
    }

end PressureLossTableSaveOpenSoak_Suite
