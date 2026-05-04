/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.testutil

import java.nio.file.{Files, Path}

object SnapshotAssert:

    private val UpdateEnv = "FIRECALC_UPDATE_SNAPSHOTS"

    def assertMatches(actual: String, snapshotPath: Path): Unit =
        if sys.env.get(UpdateEnv).contains("1") then
            Files.createDirectories(snapshotPath.getParent)
            Files.writeString      (snapshotPath, actual  )
        else
            val expected = Files.readString(snapshotPath)
            if expected != actual then
                throw new AssertionError(
                    s"""Snapshot mismatch for $snapshotPath
                       |Set $UpdateEnv=1 to regenerate.
                       |--- expected (first diff) ---
                       |${diffFirstLine(expected, actual)}""".stripMargin
                )

    private def diffFirstLine(expected: String, actual: String): String =
        val expLines = expected.linesWithSeparators.toVector
        val actLines = actual.linesWithSeparators.toVector
        val idx      = expLines.zip(actLines).indexWhere { case (e, a) => e != a }
        if idx >= 0 then
            s"Line ${idx + 1}:\n  expected: ${expLines(idx).stripLineEnd}\n  actual:   ${actLines(idx).stripLineEnd}"
        else if expLines.size != actLines.size then
            s"Line count differs: expected ${expLines.size}, actual ${actLines.size}"
        else "(no diff found)"
