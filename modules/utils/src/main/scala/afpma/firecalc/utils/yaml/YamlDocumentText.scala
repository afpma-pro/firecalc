/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.utils.yaml

/** Read-side input normalization shared by every YAML parse seam. */
object YamlDocumentText:

    /**
     * scala-yaml 0.3 reads one char past EOF when a tagged scalar ends the document
     * without a final newline (StringIndexOutOfBoundsException, JDK frames only).
     * Appending the missing newline converts that crash into a normal parse; docs
     * that already end with one are returned verbatim, so this cannot change the
     * behavior of any document we ourselves wrote.
     */
    def withTrailingNewline(text: String): String =
        if text.isEmpty || text.endsWith("\n") then text else text + "\n"

end YamlDocumentText
