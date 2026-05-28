/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.dev

import afpma.firecalc.invoices.config.EnvironmentConfigLoader
import afpma.firecalc.payments.shared.api.v1.StagingProductCatalog

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable
import scala.util.Failure
import scala.util.Success

/**
 * CI gate. Verifies every staging `invoice-config.yaml*` file decodes cleanly
 * and contains a productCatalog entry with (at minimum) a `"default"` locale
 * for every active SKU in the current Scala catalog.
 *
 * Exits non-zero on any failure.
 */
object VerifyYamlAgainstCatalog:

    private val targets: List[Path] = List(
        Paths.get("configs/dev/invoices/company-invoice.yaml"                   ),
        Paths.get("configs/staging/invoices/company-invoice.yaml"               ),
        Paths.get("docker/configs/staging/invoices/invoice-config.yaml"         ),
        Paths.get("docker/configs/staging/invoices/invoice-config.yaml.example" ),
        Paths.get("docker/configs/staging/invoices/invoice-config.yaml.template")
    )

    def main(args: Array[String]): Unit =
        val activeSkus = StagingProductCatalog.allProducts.filter(_.active).map(_.sku.value)
        val errors     = mutable.ListBuffer.empty[String]
        val checked    = mutable.ListBuffer.empty[Path]

        targets.filter(Files.exists(_)).foreach { f =>
            checked += f
            EnvironmentConfigLoader.loadFromFile(f.toFile) match
                case Failure(err) =>
                    errors += s"[${f}] decode failure: ${err.getMessage}"
                case Success(cfg) =>
                    val entries = cfg.invoice.productCatalog
                        .map(_.entries)
                        .getOrElse(Map.empty[String, Map[String, ?]])
                    activeSkus.foreach { sku =>
                        entries.get(sku) match
                            case None                                            =>
                                errors += s"[${f}] missing productCatalog entry for sku=$sku"
                            case Some(byLocale) if !byLocale.contains("default") =>
                                errors += s"[${f}] sku=$sku missing 'default' locale entry"
                            case _                                               => ()
                    }
        }

        if errors.nonEmpty then
            errors.foreach     (e => Console.err.println(s"[VerifyYamlAgainstCatalog] $e"))
            Console.err.println(
                s"[VerifyYamlAgainstCatalog] FAILED with ${errors.size} error(s) across ${checked.size} file(s)"
            )
            System.exit        (1                                                         )
        else
            println(
                s"[VerifyYamlAgainstCatalog] OK: ${checked.size} YAML file(s) match the Scala catalog " +
                    s"(${activeSkus.size} active SKU(s))"
            )
