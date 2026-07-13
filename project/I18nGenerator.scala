/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import scala.io.Source
import sbt._
import sbt.util.CacheImplicits._

object I18nGenerator {

    /**
     * Supported language IDs for i18n modules.
     * Add new languages here to automatically include them in all i18n modules.
     */
    val SUPPORTED_LANGUAGES_IDS: Seq[String] = Seq("en", "fr")

    /**
     * Helper function to create watch sources for i18n conf files.
     *
     * @param i18nModules List of i18n module names (e.g., "i18n", "ui-i18n", "payments-i18n")
     * @return Seq of watch source settings
     */
    def watchI18nSources(i18nModules: String*): Seq[Setting[_]] = Seq(
        Compile / Keys.watchSources ++= i18nModules.flatMap { moduleName =>
            SUPPORTED_LANGUAGES_IDS.map { lang =>
                file(s"modules/$moduleName/src/main/resources/i18n/$lang.conf")
            }
        }
    )

    /**
     * Helper function to generate i18n source files from HOCON conf files.
     *
     * Reads each supported language's .conf file and emits a Scala source file
     * (`files.scala`) containing the raw HOCON text as string constants. The
     * i18n runtime (babel-loader) then parses those strings at class load time.
     *
     * === JVM 64 KB String Literal Limit ===
     *
     * The JVM rejects string literals > 65 535 UTF-16 code units. Our French
     * conf file exceeds this limit (~66 KB). To work around it:
     *
     *  1. The content is split into chunks of ≤ 60 000 chars each.
     *  2. Chunks are split on LINE boundaries (not byte boundaries) so that
     *     HOCON quoted strings are never cut in half — a split mid-string would
     *     produce an unbalanced `"` in the generated Scala source, causing both
     *     a Scala compile error AND a HOCON parse error at runtime.
     *  3. Each chunk is emitted as a private `val <lang>_partN: String = """..."""`.
     *  4. The public `val <lang>: String` is the concatenation of all parts.
     *
     * === String Concatenation (not s-interpolation) ===
     *
     * The generated Scala source contains triple-quoted strings (`"""..."""`)
     * that themselves embed literal `"""` sequences (the HOCON content).
     * Using `s"""..."""` interpolation would require escaping those inner
     * triple-quotes as `\"\"\"`, which the Scala parser consumes as the
     * closing delimiter — breaking the string boundary.
     *
     * To avoid this trap, the generator builds output via plain string
     * concatenation (`"..." + content + "..."`) rather than `s"""..."""`.
     *
     * @param moduleName  The i18n module name (e.g., "i18n", "ui-i18n")
     * @param packagePath The package path for the generated file (e.g., Seq("afpma", "firecalc", "i18n"))
     * @return Source generator task
     */
    def i18nSourceGenerator(moduleName: String, packagePath: Seq[String]): Def.Initialize[Task[Seq[File]]] = Def.task {
        val cachedFun = FileFunction.cached(
            Keys.streams.value.cacheDirectory / "i18n"
        ) { (in: Set[File]) =>
            val langContents = SUPPORTED_LANGUAGES_IDS.map { lang =>
                val langFile = in.find(_.getName == s"$lang.conf").get
                lang -> Source.fromFile(langFile, "UTF-8").getLines().mkString("\n")
            }.toMap

            System.err.println                             (s"[info] => Importing HOCON files:"        )
            in.toList.map(f => s"\t${f.getName()}").foreach(name => System.err.println(s"[info] $name"))

            val packageName = packagePath.mkString(".")
            val i18nFile    = (Compile / Keys.sourceManaged).value / packagePath.mkString("/") / "files.scala"

            val langVals = SUPPORTED_LANGUAGES_IDS
                .map { lang =>
                    val content      = langContents(lang)
                    val lines        = content.split("\n", -1)
                    val chunks       = new scala.collection.mutable.ArrayBuffer[String]
                    var currentChunk = new StringBuilder
                    var currentSize  = 0
                    val maxChunkSize = 60000
                    for (line <- lines) {
                        if (currentSize > 0) {
                            if (currentSize + line.length + 1 > maxChunkSize) {
                                chunks += currentChunk.toString()
                                currentChunk = new StringBuilder
                                currentSize  = 0
                            } else {
                                currentChunk.append("\n")
                                currentSize += 1
                            }
                        }
                        currentChunk.append(line)
                        currentSize += line.length
                    }
                    if (currentSize > 0) chunks += currentChunk.toString()
                    val chunkList = chunks.toList
                    if (chunkList.size <= 1) {
                        "  val " + lang + ": String =\n" +
                            "    \"\"\"\n" + content + "\n\"\"\"\n"
                    } else {
                        val chunkDecls = chunkList.zipWithIndex
                            .map { case (chunk, idx) =>
                                "  private val " + lang + "_part" + idx + ": String =\n" +
                                    "    \"\"\"\n" + chunk + "\n\"\"\"\n"
                            }
                            .mkString("\n")
                        val joinExpr   = chunkList.zipWithIndex
                            .map { case (_, idx) =>
                                "files." + lang + "_part" + idx
                            }
                            .mkString(" +\n    ")
                        chunkDecls + "\n  val " + lang + ": String = " + joinExpr + "\n"
                    }
                }
                .mkString("\n")

            val configsMap = SUPPORTED_LANGUAGES_IDS
                .map { lang =>
                    "\"" + lang + "\" -> files." + lang
                }
                .mkString(",\n  ")

            IO.write(
                i18nFile,
                "package " + packageName + "\n\nobject files {\n\n" +
                    langVals + "}\n\nval configs = Map(\n  " + configsMap + "\n)\n"
            )
            Set     (i18nFile)
        }

        val inputFiles = SUPPORTED_LANGUAGES_IDS.map { lang =>
            file(s"modules/$moduleName/src/main/resources/i18n/$lang.conf")
        }.toSet

        cachedFun(inputFiles).toSeq
    }
}
