/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.dev

import afpma.firecalc.payments.shared.api.v1.StagingProductCatalog

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

/**
 * Regenerates the per-SKU productCatalog YAML block inside
 * `docker/configs/staging/invoices/invoice-config.yaml.example`
 * from the current Scala catalog's `exampleCopy` build-time field.
 *
 * The surrounding file (comments, env interpolations, other sections)
 * is preserved verbatim — only the `productCatalog:` block under
 * `sender:` (4-space indent) is replaced.
 */
object GenYamlTemplate:

    def main(args: Array[String]): Unit =
        val exampleFile = Paths.get("docker/configs/staging/invoices/invoice-config.yaml.example")
        if !Files.exists(exampleFile) then
            Console.err.println(s"[GenYamlTemplate] file not found: ${exampleFile.toAbsolutePath}")
            System.exit        (1                                                                 )

        val original = new String(Files.readAllBytes(exampleFile), StandardCharsets.UTF_8)

        // Build replacement block from staging catalog.
        val sb = new StringBuilder
        sb.append("    productCatalog:\n")
        sb.append("      entries:\n"     )
        StagingProductCatalog.allProducts.foreach { p =>
            sb.append(s"        ${p.sku.value}:\n")
            p.exampleCopy.entries.toList.sortBy(_._1).foreach { case (localeTag, copy) =>
                sb.append(s"          $localeTag:\n"                                         )
                sb.append(s"            name: \"${escapeYamlDQ(copy.name)}\"\n"              )
                sb.append(s"            description: \"${escapeYamlDQ(copy.description)}\"\n")
            }
        }

        // Splice into file: replace from "    productCatalog:" to next sibling key
        // at same 4-space indent (line starts with exactly 4 spaces + non-space).
        val lines    = original.split("\n", -1).toList
        val startIdx = lines.indexWhere(_.startsWith("    productCatalog:"))
        if startIdx < 0 then
            Console.err.println(s"[GenYamlTemplate] could not find '    productCatalog:' in $exampleFile")
            System.exit        (1                                                                        )

        val afterStart    = lines.drop(startIdx + 1)
        val relEnd        = afterStart.indexWhere { ln =>
            ln.length >= 5 && ln.startsWith("    ") && ln.charAt(4) != ' ' && ln.trim.nonEmpty
        }
        val endIdx        = if relEnd < 0 then lines.length else startIdx + 1 + relEnd
        val newBlockLines = sb.toString.stripSuffix("\n").split("\n", -1).toList
        val rebuilt       = (lines.take(startIdx) ++ newBlockLines ++ lines.drop(endIdx)).mkString("\n")

        Files.write(exampleFile, rebuilt.getBytes(StandardCharsets.UTF_8)                           )
        println    (s"[GenYamlTemplate] regenerated productCatalog in ${exampleFile.toAbsolutePath}")

    private def escapeYamlDQ(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
