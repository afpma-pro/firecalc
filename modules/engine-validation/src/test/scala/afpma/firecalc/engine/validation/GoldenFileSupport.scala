/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.validation

import org.scalatest.freespec.AnyFreeSpec

trait GoldenFileSupport:
    self: AnyFreeSpec =>

    private val validationBaseDir = java.nio.file.Paths.get("modules/engine/validation")

    /** Load golden reference from classpath. Normalizes line endings to \n. */
    protected def loadGoldenFile(resourcePath: String): String =
        val stream = getClass.getResourceAsStream(resourcePath)
        if stream == null then
            fail(
                s"Golden reference file not found: $resourcePath\n" +
                    "Run `make run-validation` then `make update-validation` to create the baseline."
            )
        try
            val content = scala.io.Source.fromInputStream(stream)(using scala.io.Codec.UTF8).mkString
            content.replace("\r\n", "\n")
        finally stream.close()

    /** Write current output to validation/current/ for git-diff workflow. */
    protected def writeCurrentOutput(relativePath: String, content: String): Unit =
        val path = validationBaseDir.resolve(relativePath)
        java.nio.file.Files.createDirectories(path.getParent                                        )
        java.nio.file.Files.writeString      (path, content, java.nio.charset.StandardCharsets.UTF_8)

    /** Assert exact text match. On failure, show numeric diff diagnostic. */
    protected def assertGoldenMatch(actual: String, expected: String, label: String): Unit =
        if actual != expected then
            val diagnostic = numericDiffDiagnostic(expected, actual)
            fail(
                s"""Golden file mismatch for $label.
                   |
                   |Run `make update-validation` to accept new output after reviewing.
                   |
                   |=== Numeric Difference Summary ===
                   |$diagnostic""".stripMargin
            )

    /** Walk lines pairwise, extract numeric tokens, show deltas. */
    private def numericDiffDiagnostic(expected: String, actual: String): String =
        val expectedLines = expected.linesIterator.toVector
        val actualLines   = actual.linesIterator.toVector
        val sb            = new StringBuilder

        // Regex for French-locale numerics with units
        // Longer units listed before shorter to avoid partial matches
        val numPattern =
            """(-?\d+(?:[,\.]\d+)?)\s*(Pa|°C|m²K/W|mg/Nm³|kg/m³|m/s|kW|mm|cm|m|%|g/s|W/mK)?""".r

        def parseNum(s: String): Double =
            s.replace(",", ".").toDouble

        val maxLines  = math.max(expectedLines.length, actualLines.length)
        var diffCount = 0

        for i <- 0 until maxLines do
            val eLine = expectedLines.lift(i).getOrElse("")
            val aLine = actualLines.lift(i).getOrElse("")
            if eLine != aLine then
                val eNums =
                    numPattern
                        .findAllMatchIn(eLine)
                        .map(m => (m.group(1), Option(m.group(2)).getOrElse("")))
                        .toVector
                val aNums =
                    numPattern
                        .findAllMatchIn(aLine)
                        .map(m => (m.group(1), Option(m.group(2)).getOrElse("")))
                        .toVector

                val pairs = eNums.zipAll(aNums, ("?", ""), ("?", ""))
                for ((eNum, eUnit), (aNum, aUnit)) <- pairs do
                    if eNum != aNum then
                        try
                            val eVal  = parseNum(eNum)
                            val aVal  = parseNum(aNum)
                            val delta = aVal - eVal
                            val sign  = if delta >= 0 then "+" else ""
                            sb.append(
                                f"  line ${i + 1}%4d: expected ${eNum}%8s ${eUnit}%-8s  " +
                                    f"actual ${aNum}%8s ${aUnit}%-8s  delta = $sign$delta%.4f\n"
                            )
                            diffCount += 1
                        catch
                            case _: NumberFormatException =>
                                sb.append(
                                    f"  line ${i + 1}%4d: expected '${eLine.trim.take(60)}' " +
                                        f"≠ actual '${aLine.trim.take(60)}'\n"
                                )
                                diffCount += 1

        if diffCount == 0 then
            sb.append("  (non-numeric difference — likely formatting or label change)\n")
            val diffLines = (0 until maxLines).filter: i =>
                expectedLines.lift(i).getOrElse("") != actualLines.lift(i).getOrElse("")
            for i <- diffLines.take(5) do
                sb.append(f"  line ${i + 1}%4d:\n"                                   )
                sb.append(s"    - ${expectedLines.lift(i).getOrElse("").take(100)}\n")
                sb.append(s"    + ${actualLines.lift(i).getOrElse("").take(100)}\n"  )

        sb.append(s"\n  Total numeric differences: $diffCount")
        if expectedLines.length != actualLines.length then
            sb.append(s"\n  Line count: expected ${expectedLines.length}, actual ${actualLines.length}")

        sb.toString
