/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.dev

import afpma.firecalc.payments.shared.api.v1.StagingProductCatalog

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

/**
 * Regenerates the per-SKU productCatalog YAML block inside
 * `firecalc-docker/configs/staging/invoices/invoice-config.yaml.example`,
 * `firecalc-docker/configs/staging/invoices/invoice-config.yaml.template`, and
 * `configs/templates/invoices/company-invoice.yaml.template` from the current
 * Scala catalog's `exampleCopy` build-time field. All three files are tracked
 * and checked by VerifyYamlAgainstCatalog, so their blocks are
 * machine-maintained and never hand-edited.
 *
 * Every target is validated (present, and containing the block marker) and
 * its full replacement content computed BEFORE any file is written, so a
 * failure mid-list leaves the whole tree untouched.
 *
 * The surrounding files (comments, env interpolations, other sections)
 * are preserved verbatim — only the `productCatalog:` block under
 * `invoice:` (2-space indent) is replaced.
 */
object GenYamlTemplate:

    private val targets: List[Path] = List(
        Paths.get("firecalc-docker/configs/staging/invoices/invoice-config.yaml.example" ),
        Paths.get("firecalc-docker/configs/staging/invoices/invoice-config.yaml.template"),
        Paths.get("configs/templates/invoices/company-invoice.yaml.template"             )
    )

    def main(args: Array[String]): Unit =
        val blockLines = buildBlockLines()
        // Strict List.map: every precondition (file present, block marker
        // present) is checked and all replacement content computed here;
        // nothing reaches the filesystem until all of it has succeeded.
        val rebuilt    = targets.map { target =>
            if !Files.exists(target) then
                Console.err.println(s"[GenYamlTemplate] file not found: ${target.toAbsolutePath}")
                System.exit        (1                                                            )
            target -> regenerate(target, blockLines)
        }
        rebuilt.foreach { (file, content) =>
            Files.write(file, content.getBytes(StandardCharsets.UTF_8)                           )
            println    (s"[GenYamlTemplate] regenerated productCatalog in ${file.toAbsolutePath}")
        }

    /** The generated `  productCatalog:` block, one element per line. */
    private def buildBlockLines(): List[String] =
        val sb = new StringBuilder
        sb.append("  productCatalog:\n")
        sb.append("    entries:\n"     )
        StagingProductCatalog.allProducts.foreach { p =>
            sb.append(s"      ${p.sku.value}:\n")
            p.exampleCopy.entries.toList.sortBy(_._1).foreach { case (localeTag, copy) =>
                sb.append(s"        $localeTag:\n"                                         )
                sb.append(s"          name: \"${escapeYamlDQ(copy.name)}\"\n"              )
                sb.append(s"          description: \"${escapeYamlDQ(copy.description)}\"\n")
            }
        }
        sb.toString.stripSuffix("\n").split("\n", -1).toList

    /**
     * Computes the full replacement content for `file`, splicing in
     * `newBlockLines`; never writes.
     *
     * Splice into file: replace from "  productCatalog:" to next sibling key
     * at same 2-space indent (line starts with exactly 2 spaces + non-space).
     * NOTE: any 2-space-indented line with a non-space at column 3 ends the
     * block, so a comment line inside the productCatalog block would truncate
     * the splice.
     */
    private def regenerate(file: Path, newBlockLines: List[String]): String =
        val original = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        val lines    = original.split("\n", -1).toList
        val startIdx = lines.indexWhere(_.startsWith("  productCatalog:"))
        if startIdx < 0 then
            Console.err.println(s"[GenYamlTemplate] could not find '  productCatalog:' in $file")
            System.exit        (1                                                               )

        val afterStart = lines.drop(startIdx + 1)
        val relEnd     = afterStart.indexWhere { ln =>
            ln.length >= 3 && ln.startsWith("  ") && ln.charAt(2) != ' ' && ln.trim.nonEmpty
        }
        val endIdx     = if relEnd < 0 then lines.length else startIdx + 1 + relEnd
        (lines.take(startIdx) ++ newBlockLines ++ lines.drop(endIdx)).mkString("\n")

    private def escapeYamlDQ(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
