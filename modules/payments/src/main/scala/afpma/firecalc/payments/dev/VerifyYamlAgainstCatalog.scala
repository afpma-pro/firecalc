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
 * CI gate. Verifies every present target config decodes cleanly and contains
 * a productCatalog entry with (at minimum) a `"default"` locale for every
 * active SKU in the current Scala catalog.
 *
 * Targets: the root-tree `configs/{development,staging,production}/invoices/company-invoice.yaml`
 * files, which legitimately may not exist on a given machine (each absent one
 * is reported on stderr and skipped), and the repo-tracked
 * `firecalc-docker/configs/staging/invoices/invoice-config.yaml`,
 * `invoice-config.yaml.example`, and `invoice-config.yaml.template` files plus
 * `configs/templates/invoices/company-invoice.yaml.template` — the tracked
 * template guarantees at least one present target on any checkout, and its
 * productCatalog block is machine-maintained by GenYamlTemplate.
 *
 * Exits non-zero on any decode/SKU error, and also when zero targets are
 * present: a silently empty check is a broken checkout, not a pass.
 */
object VerifyYamlAgainstCatalog:

    private val targets: List[Path] = List(
        Paths.get("configs/development/invoices/company-invoice.yaml"                    ),
        Paths.get("configs/staging/invoices/company-invoice.yaml"                        ),
        Paths.get("configs/production/invoices/company-invoice.yaml"                     ),
        Paths.get("configs/templates/invoices/company-invoice.yaml.template"             ),
        Paths.get("firecalc-docker/configs/staging/invoices/invoice-config.yaml"         ),
        Paths.get("firecalc-docker/configs/staging/invoices/invoice-config.yaml.example" ),
        Paths.get("firecalc-docker/configs/staging/invoices/invoice-config.yaml.template")
    )

    def main(args: Array[String]): Unit =
        val activeSkus = StagingProductCatalog.allProducts.filter(_.active).map(_.sku.value)
        val errors     = mutable.ListBuffer.empty[String]
        val checked    = mutable.ListBuffer.empty[Path]

        val (present, missing) = targets.partition(f => Files.exists(f))
        missing.foreach(f => Console.err.println(s"[VerifyYamlAgainstCatalog] skipped, not present: $f"))
        present.foreach { f =>
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

        if checked.isEmpty then
            Console.err.println("[VerifyYamlAgainstCatalog] FAILED: no target YAML files present to check")
            System.exit        (1                                                                         )
        else if errors.nonEmpty then
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
