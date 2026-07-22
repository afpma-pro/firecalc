/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.validation

import java.util.Collections
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.FireCalcYAMLMigrations

import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr
import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.cas_types.CasTypesRunner_15544_Strict
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error

import afpma.firecalc.reports.FireCalcReportFactory_15544_Strict

import cats.syntax.all.*

import io.circe.yaml.scalayaml.parser as yamlParser
import org.scalatest.freespec.AnyFreeSpec

/**
 * Reference projects test suite.
 *
 * Auto-discovers `.fcalc` files under `src/test/resources/reference-projects/`,
 * runs the EN 15544 strict engine, and generates `.txt` and `.pdf` outputs
 * to a mirrored `reference-outputs/` directory.
 *
 * These are NOT golden files — no assertions on output content.
 * Tests fail only on decode or engine validation errors.
 */
class ReferenceProjects_Suite extends AnyFreeSpec with CasTypesRunner_15544_Strict:

    // Resolve repo root by walking up from user.dir to find build.sbt
    private val repoRoot: Path =
        @annotation.tailrec
        def walkUp(p: Path): Path =
            if Files.exists(p.resolve("build.sbt")) then p
            else
                p.getParent match
                    case null       => p
                    case parentPath => walkUp(parentPath)
        walkUp(Paths.get(System.getProperty("user.dir")))

    private val referenceOutputsDir: Path =
        repoRoot.resolve("modules/engine-validation/reference-outputs")

    /**
     * Discover .fcalc files on the test classpath under reference-projects/.
     * Returns (relativePath, yamlContent) pairs sorted by relative path.
     */
    private def discoverFcalcFiles(): Seq[(String, String)] =
        val base = "reference-projects"
        val urls =
            Collections.list(getClass.getClassLoader.getResources(base)).asScala.toList

        if urls.isEmpty then Seq.empty
        else
            val results = ListBuffer.empty[(String, String)]
            for url <- urls do
                val file = Paths.get(url.toURI)
                if Files.isDirectory(file) then
                    Files
                        .walk(file)
                        .iterator()
                        .asScala
                        .foreach: p =>
                            if Files.isRegularFile(p) && p.getFileName.toString.endsWith(".fcalc") then
                                val rel     = file.relativize(p).toString
                                val content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                                results += ((rel, content))
            results.result().sortBy(tuple => tuple._1)

    /** Compute the output path mirroring the input structure. */
    private def outputDir(relPath: String): Path =
        val parent = Paths.get(relPath).getParent
        if parent != null then referenceOutputsDir.resolve(parent) else referenceOutputsDir

    private def outputTxtPath(relPath: String): Path =
        outputDir(relPath).resolve(
            Paths.get(relPath).getFileName.toString.stripSuffix(".fcalc") + ".txt"
        )

    private def outputPdfPath(relPath: String): Path =
        outputDir(relPath).resolve(
            Paths.get(relPath).getFileName.toString.stripSuffix(".fcalc") + ".pdf"
        )

    // ── Discover and register test cases ────────────────────────────────

    val fcalcFiles = discoverFcalcFiles()

    if fcalcFiles.isEmpty then info("No .fcalc files found on classpath under reference-projects/"    )
    else info                      (s"Discovered ${fcalcFiles.size} reference project(s) on classpath")

    for (relName, _) <- fcalcFiles do
        s"Reference project: $relName" in {
            val yamlContent = fcalcFiles.find(_._1 == relName).get._2
            runReferenceProject(yamlContent, relName)
        }

    private def runReferenceProject(yamlContent: String, label: String): Unit =
        // 1. Decode the YAML (handle AppStateSchema engine_state wrapper)
        val fireCalcYaml: FireCalcYAML =
            yamlParser.parse(yamlContent) match
                case Left(parseErr) =>
                    fail(s"Failed to parse YAML '$label': ${parseErr.getMessage()}")
                case Right(json)    =>
                    val engineJson = json.hcursor.downField("engine_state").focus.getOrElse(json)
                    FireCalcYAMLMigrations.decodeAndMigrateJson(engineJson) match
                        case Right(fc) => fc
                        case Left(err) =>
                            fail(s"Failed to decode/reference-migrate '$label': $err")

        // 2. Build the stove project description
        val stoveProject: StoveProjectDescr_15544_Strict_Alg =
            StoveProjectDescr.makeFor_EN15544_Strict(fireCalcYaml)

        // 3. Run the engine and generate .txt output
        val txtOutput: String =
            try
                run_cas_type_15544_strict_asString(stoveProject) match
                    case cats.data.Validated.Invalid(errs) =>
                        fail(
                            s"Engine validation failed for '$label':\n" +
                                errs.toList.map(_.show).mkString("\n")
                        )
                    case cats.data.Validated.Valid(output) =>
                        output
            catch
                case e: Throwable =>
                    fail(
                        s"Engine execution threw exception for '$label': ${e.getClass.getSimpleName}: ${e.getMessage}",
                        e
                    )

        // 4. Write .txt output
        val txtPath = outputTxtPath(label)
        Files.createDirectories(txtPath.getParent                         )
        Files.writeString      (txtPath, txtOutput, StandardCharsets.UTF_8)

        // 5. Generate and write .pdf output (skip validation — report warnings in PDF)
        val pdfPath = outputPdfPath(label)
        Files.createDirectories(pdfPath.getParent)

        val reportFactory = FireCalcReportFactory_15544_Strict.init()(using loc)
        reportFactory.loadFireCalcProject_NoValidate(stoveProject) match
            case Left(err)      =>
                fail(s"Report factory load failed for '$label': $err")
            case Right(factory) =>
                factory.makePDFBuffer                 (
                    isDraft                  = false,
                    checkPressureReq13384    = false,
                    checkTemperatureReq13384 = false
                ) match
                    case Left(err)                    =>
                        fail(s"PDF generation failed for '$label': $err")
                    case Right(pdfBytes: Array[Byte]) =>
                        Files.write(pdfPath, pdfBytes)

end ReferenceProjects_Suite
