/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import sbt._
import sbt.Keys._

object I18nBuild {

    /**
     * Registered i18n modules: (moduleName, packagePath).
     * Each module has its HOCON conf files at:
     *   modules/<moduleName>/src/main/resources/i18n/{en,fr}.conf
     */
    val modules: Seq[(String, Seq[String])] = Seq(
        ("i18n", Seq("afpma", "firecalc", "i18n")                                      ),
        ("ui-i18n", Seq("afpma", "firecalc", "ui", "i18n")                             ),
        ("payments-shared-i18n", Seq("afpma", "firecalc", "payments", "shared", "i18n")),
        ("payments-i18n", Seq("afpma", "firecalc", "payments", "i18n")                 ),
        ("invoices-i18n", Seq("afpma", "firecalc", "invoices", "i18n")                 )
    )

    /** Returns the module name for a given package path prefix, or None. */
    def moduleNameForPackage(pkg: Seq[String]): Option[String] =
        modules.find(_._2 == pkg).map(_._1)

    /**
     * Settings for an i18n module definition (watch + generate).
     * Replaces the inline `watchSources ++= ...` and `sourceGenerators += ...` blocks.
     */
    def moduleSettings(moduleName: String, packagePath: Seq[String]): Seq[Setting[_]] = Seq(
        Compile / watchSources ++= I18nGenerator.SUPPORTED_LANGUAGES_IDS.map { lang =>
            file(s"modules/$moduleName/src/main/resources/i18n/$lang.conf")
        },
        Compile / sourceGenerators += I18nGenerator.i18nSourceGenerator(moduleName, packagePath)
    )
}
