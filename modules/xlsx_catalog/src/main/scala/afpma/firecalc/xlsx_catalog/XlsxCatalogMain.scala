/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import afpma.firecalc.catalog.*
import afpma.firecalc.xlsx_catalog.importers.*
import afpma.firecalc.xlsx_catalog.templates.*

/** CLI for catalog Excel operations.
  *
  * Usage:
  *   generate-templates <output-dir>
  *   import firebox        <input.xlsx> <output.fcalc-db>
  *   import single-tested  <input.xlsx> <output.fcalc-db>
  *   import pipes          <input.xlsx> <output.fcalc-db>
  *   import casings        <input.xlsx> <output.fcalc-db>
  *   import flow-res       <input.xlsx> <output.fcalc-db>
  *   export single-tested  <input.fcalc-db> <output-dir>
  */
object XlsxCatalogMain:

    def main(args: Array[String]): Unit =
        args.toList match
            case "generate-templates" :: outputDir :: Nil =>
                generateTemplates(Paths.get(outputDir))
            case "import" :: category :: inputFile :: outputFile :: Nil =>
                importXlsx(category, Paths.get(inputFile), Paths.get(outputFile))
            case "export" :: category :: inputFile :: outputDir :: Nil =>
                exportXlsx(category, Paths.get(inputFile), Paths.get(outputDir))
            case _ =>
                System.err.println(
                    """Usage:
                      |  generate-templates <output-dir>
                      |  import firebox        <input.xlsx> <output.fcalc-db>
                      |  import single-tested  <input.xlsx> <output.fcalc-db>
                      |  import pipes          <input.xlsx> <output.fcalc-db>
                      |  import casings        <input.xlsx> <output.fcalc-db>
                      |  import flow-res       <input.xlsx> <output.fcalc-db>
                      |  export single-tested  <input.fcalc-db> <output-dir>""".stripMargin)
                sys.exit(1)

    private def generateTemplates(outputDir: Path): Unit =
        Files.createDirectories(outputDir)
        println("Generating catalog Excel templates...")
        FireboxTemplateWriter.generate(outputDir.resolve("firebox-template.xlsx"))
        println("  -> firebox-template.xlsx")
        SingleTestedTemplateWriter.generate(outputDir.resolve("single-tested-template.xlsx"))
        println("  -> single-tested-template.xlsx")
        PipesTemplateWriter.generate(outputDir.resolve("pipes-template.xlsx"))
        println("  -> pipes-template.xlsx")
        CasingsTemplateWriter.generate(outputDir.resolve("casings-template.xlsx"))
        println("  -> casings-template.xlsx")
        FlowResTemplateWriter.generate(outputDir.resolve("flow-resistances-template.xlsx"))
        println("  -> flow-resistances-template.xlsx")
        println("Done.")

    private def exportXlsx(category: String, inputPath: Path, outputDir: Path): Unit =
        import CatalogCategoryInstances.given

        val yaml = Files.readString(inputPath)
        val catalogFile = CatalogParser.parse(yaml) match
            case Right(cf) => cf
            case Left(err) =>
                System.err.println(s"Failed to parse catalog file: $err")
                sys.exit(1)

        Files.createDirectories(outputDir)

        category match
            case "single-tested" =>
                val entries = catalogFile.entriesFor[afpma.firecalc.dto.all.Firebox.SingleTested]
                if entries.isEmpty then
                    System.err.println("No single-tested firebox entries found in catalog file")
                    sys.exit(1)
                println(s"Exporting ${entries.size} single-tested firebox(es)...")
                for entry <- entries do
                    val safeName = entry.reference.replaceAll("[^a-zA-Z0-9_.-]", "_")
                    val outPath = outputDir.resolve(s"$safeName.xlsx")
                    SingleTestedTemplateWriter.write(entry, outPath)
                    println(s"  -> $outPath")
            case other =>
                System.err.println(s"Unknown export category: $other (expected: single-tested)")
                sys.exit(1)
        println("Done.")

    private def importXlsx(category: String, inputPath: Path, outputPath: Path): Unit =
        import CatalogCategoryInstances.given

        println(s"Importing $category from $inputPath...")

        val builder = CatalogSections.Builder()
        category match
            case "firebox" =>
                val firebox = FireboxXlsxImporter.read(inputPath)
                println(s"  Read firebox: ${firebox.reference}")
                builder.add(Seq(firebox))
            case "single-tested" =>
                val st = SingleTestedXlsxImporter.read(inputPath)
                println(s"  Read single-tested firebox: ${st.reference}")
                builder.add(Seq(st))
            case "pipes" =>
                val pipes = PipesXlsxImporter.read(inputPath)
                println(s"  Read ${pipes.size} pipe preset(s)")
                builder.add(pipes)
            case "casings" =>
                val casings = CasingsXlsxImporter.read(inputPath)
                println(s"  Read ${casings.size} casing preset(s)")
                builder.add(casings)
            case "flow-res" =>
                val flowRes = FlowResXlsxImporter.read(inputPath)
                println(s"  Read ${flowRes.size} flow resistance(s)")
                builder.add(flowRes)
            case other =>
                System.err.println(s"Unknown category: $other (expected: firebox, single-tested, pipes, casings, flow-res)")
                sys.exit(1)
        val sections = builder.build

        val catalogFile = CatalogFile(
            catalog_version = CatalogMigrations.CURRENT_VERSION,
            catalog_name = Map("fr" -> "Catalogue importé", "en" -> "Imported catalog"),
            sections = sections,
        )

        val yaml = CatalogWriter.toYaml(catalogFile)

        // Validate round-trip
        CatalogParser.parse(yaml) match
            case Right(_) =>
                Files.writeString(outputPath, yaml)
                println(s"  -> $outputPath (validated)")
            case Left(error) =>
                System.err.println(s"Validation failed: $error")
                // Still write for debugging
                Files.writeString(outputPath, yaml)
                System.err.println(s"  -> $outputPath (UNVALIDATED - check for errors)")
                sys.exit(1)
