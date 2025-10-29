/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

import scala.io.Source
import org.scalajs.linker.interface.ModuleSplitStyle
import sbtassembly.MergeStrategy

ThisBuild / semanticdbEnabled := true
ThisBuild / scalafixOnCompile := false

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / excludeLintKeys ++= Set(mainClass)
// Global / watchForceTriggerOnAnyChange := true

val scala_version = "3.7.3"

// =========
// Shared i18n Configuration
// =========

/** Supported language IDs for i18n modules.
  * Add new languages here to automatically include them in all i18n modules.
  */
val SUPPORTED_LANGUAGES_IDS: Seq[String] = Seq("en", "fr")

/** Helper function to create watch sources for i18n conf files.
  *
  * @param i18nModules List of i18n module names (e.g., "i18n", "ui-i18n", "payments-i18n")
  * @return Seq of watch source settings
  */
def watchI18nSources(i18nModules: String*): Seq[Setting[_]] = Seq(
  Compile / watchSources ++= i18nModules.flatMap { moduleName =>
    SUPPORTED_LANGUAGES_IDS.map { lang =>
      file(s"modules/$moduleName/src/main/resources/i18n/$lang.conf")
    }
  }
)

/** Helper function to generate i18n source files from HOCON conf files.
  *
  * @param moduleName The i18n module name (e.g., "i18n", "ui-i18n")
  * @param packagePath The package path for the generated file (e.g., Seq("afpma", "firecalc", "i18n"))
  * @return Source generator task
  */
def i18nSourceGenerator(moduleName: String, packagePath: Seq[String]): Def.Initialize[Task[Seq[File]]] = Def.task {
  val cachedFun = FileFunction.cached(
    streams.value.cacheDirectory / "i18n"
  ) { (in: Set[File]) =>
    
    // Read all language files
    val langContents = SUPPORTED_LANGUAGES_IDS.map { lang =>
      val langFile = in.find(_.getName == s"$lang.conf").get
      lang -> Source.fromFile(langFile).getLines().mkString("\n")
    }.toMap
    
    System.err.println(s"[info] => Importing HOCON files:")
    in.toList.map(f => s"\t${f.getName()}").foreach(name => System.err.println(s"[info] $name"))
    
    val packageName = packagePath.mkString(".")
    val i18nFile = (Compile / sourceManaged).value / packagePath.mkString("/") / "files.scala"
    
    // Generate val declarations for each language
    val langVals = SUPPORTED_LANGUAGES_IDS.map { lang =>
      s"""  val $lang: String =
         |    \"\"\"
         |${langContents(lang)}
         |\"\"\"
         |""".stripMargin
    }.mkString("\n")
    
    // Generate configs map entries
    val configsMap = SUPPORTED_LANGUAGES_IDS.map { lang =>
      s""""$lang" -> files.$lang"""
    }.mkString(",\n  ")
    
    IO.write(i18nFile, s"""
      |package $packageName
      |
      |object files {
      |
      |$langVals
      |}
      |
      |val configs = Map(
      |  $configsMap
      |)
      |""".stripMargin
    )
    Set(i18nFile)
  }
  
  val inputFiles = SUPPORTED_LANGUAGES_IDS.map { lang =>
    file(s"modules/$moduleName/src/main/resources/i18n/$lang.conf")
  }.toSet
  
  cachedFun(inputFiles).toSeq
}

ThisBuild / scalaVersion        := scala_version
ThisBuild / organization        := "pro.afpma"
ThisBuild / organizationName    := "Association Française du Poêle Maçonné Artisanal"
ThisBuild / startYear           := Some(2025)
ThisBuild / licenses            := Seq("AGPL-3.0-or-later" -> url("https://www.gnu.org/licenses/agpl-3.0.html"))
ThisBuild / homepage            := Some(url("https://www.afpma.pro"))

// Windows CI workaround: allow switching to Ivy (to avoid Coursier file-lock issues)
// Set FIRECALC_CI_FORCE_IVY=1 in CI (Windows) to disable Coursier resolution.
ThisBuild / useCoursier := {
  val isWin = System.getProperty("os.name", "").toLowerCase.contains("win")
  if (isWin && sys.env.get("FIRECALC_CI_FORCE_IVY").nonEmpty) false else true
}

lazy val engine_version         = "0.3.0-b6"
lazy val reports_base_version   = "0.9.0-b6"
lazy val payments_base_version  = "0.9.0-b6"
lazy val ui_base_version        = "0.9.0-b6"

// Repository information (single source of truth)
lazy val githubOwner            = "afpma-pro"
lazy val githubRepo             = "firecalc"

lazy val reports_version       = s"${reports_base_version}+engine-${engine_version}"
lazy val payments_version      = s"${payments_base_version}+reports-${reports_base_version}+engine-${engine_version}"
lazy val ui_version            = s"${ui_base_version}+engine-${engine_version}"

val commonSettings = Seq(
  scalaVersion := scala_version,
  scalacOptions ++= Seq(
    // "-explain",
    // "-explain-cyclic",
    // "-deprecation", // TODO: uncomment when building (deactivation should improve compile time)
    // "-encoding",
    // "utf8",
    "-feature",
    // "-language:existentials",
    // "-language:higherKinds",
    "-language:implicitConversions",
    // "-unchecked",
    // "-Wunused:all",
    // "-Wunused:all",
    // "-Xfatal-warnings",
    // "-source:future",
  )
)
val commonAssemblyMergeStrategy: String => MergeStrategy = {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) if xs.exists(_.endsWith(".SF")) => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) if xs.exists(_.endsWith(".DSA")) => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) if xs.exists(_.endsWith(".RSA")) => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.first
  case PathList("reference.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}


lazy val root = (project in file("."))
  .aggregate(i18n.js, i18n.jvm, dto.js, dto.jvm, engine.js, engine.jvm, ui, ui_i18n.js/*, ui_i18n.jvm*/, payments_i18n, invoices_i18n, invoices, reports, payments_shared.js, payments_shared.jvm, payments)
  .settings(
    name := "firecalc-root",
  )

// =========
// i18n-utils

lazy val i18n_utils = crossProject(JVMPlatform, JSPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/i18n-utils"))
  .settings(
    commonSettings,
    name := "firecalc-i18n-utils",
    version := engine_version,
    scalacOptions ++= Seq(
        // "-Xmax-inlines:48",
    ),
    libraryDependencies ++= Seq(
        // hackish fork of magnolia to prevent Transl to be listed in annotation (compiler bug because of macros colliding)
        "pro.afpma" %%% "magnolia" % "1.3.16",

        "org.typelevel" %%% "cats-core" % "2.13.0",
        "io.taig" %%% "babel-loader"  % "0.5.3",
    ),
  ).jsConfigure(_.settings(jsSourceMapSettings: _*))

// =========
// utils

lazy val utils = crossProject(JVMPlatform, JSPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/utils"))
  .settings(
    commonSettings,
    name := "firecalc-utils",
    version := engine_version,
    libraryDependencies ++= Seq(
        // encoding / decoding

        // generic JSON
        "io.circe"            %%% "circe-core"    % "0.14.13",
        "io.circe"            %%% "circe-generic" % "0.14.13",
        "io.circe"            %%% "circe-parser"  % "0.14.13",

        // JSON <-> YAML
        "io.circe"            %%% "circe-yaml-scalayaml" % "0.16.0",

        // Testing
        "org.scalameta" %% "munit" % "1.0.0" % "test",
    ),

    // Generate BuildInfo.scala with version and repository information from build.sbt
    Compile / sourceGenerators += Def.task {
        val buildInfoFile = (Compile / sourceManaged).value / "afpma" / "firecalc" / "utils" / "BuildInfo.scala"

        System.err.println("[info] => Generating BuildInfo.scala with versions and repository info:")
        System.err.println(s"[info] \tengine_version: $engine_version")
        System.err.println(s"[info] \treports_base_version: $reports_base_version")
        System.err.println(s"[info] \treports_version: $reports_version")
        System.err.println(s"[info] \tpayments_base_version: $payments_base_version")
        System.err.println(s"[info] \tpayments_version: $payments_version")
        System.err.println(s"[info] \tui_latest_version: $ui_version")
        System.err.println(s"[info] \trepository: https://github.com/$githubOwner/$githubRepo")

        IO.write(buildInfoFile, s"""
        |package afpma.firecalc.utils
        |
        |// Auto-generated from build.sbt - DO NOT EDIT MANUALLY
        |object BuildInfo {
        |  val engineVersion: String       = "$engine_version"
        |  val reportsBaseVersion: String  = "$reports_base_version"
        |  val reportsVersion: String      = "$reports_version"
        |  val paymentsBaseVersion: String = "$payments_base_version"
        |  val paymentsVersion: String     = "$payments_version"
        |  val uiLatestVersion: String     = "$ui_version"
        |
        |  object Repository {
        |    val owner: String = "$githubOwner"
        |    val name: String  = "$githubRepo"
        |    val url: String   = s"https://github.com/$$owner/$$name"
        |  }
        |}
        |""".stripMargin
        )

        Seq(buildInfoFile)
    }.taskValue,
  ).jsConfigure(_.settings(jsSourceMapSettings: _*))

// =========
// units

lazy val units = crossProject(JVMPlatform, JSPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/units"))
  .settings(
    commonSettings,
    name := "firecalc-units",
    version := engine_version,
    scalacOptions ++= Seq(
        // "-Xmax-inlines:48",
    ),
    libraryDependencies ++= Seq(
        "com.manyangled"  %%% "coulomb-core"  % "0.8.0",
        "com.manyangled"  %%% "coulomb-units" % "0.8.0",
    ),
  ).jsConfigure(_.settings(jsSourceMapSettings: _*))
  .settings(watchI18nSources("i18n"))
  .dependsOn(i18n)

// =========
// dto

lazy val dto = crossProject(JVMPlatform, JSPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/dto"))
  .settings(
    commonSettings,
    name := "firecalc-dto",
    version := engine_version,
    scalacOptions ++= Seq(
        "-Xmax-inlines:48",
        // "-explain-cyclic",
    ),
    libraryDependencies ++= Seq(
        "io.scalaland"      %%% "chimney"           % "1.8.2",
        "io.taig"           %%% "babel-generic"     % "0.5.3",
        "org.typelevel"     %%% "kittens"           % "3.5.0",
    ),
  ).jsConfigure(_.settings(jsSourceMapSettings: _*))
  .settings(watchI18nSources("i18n"))
  .dependsOn(utils, i18n, units)

// =========
// engine

lazy val engine = crossProject(JVMPlatform, JSPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/engine"))
  .settings(
    commonSettings,
    name := "firecalc-engine",
    version := engine_version,
    mainClass := Some("afpma.firecalc.MCalc"),
    scalacOptions ++= Seq(
        "-Xmax-inlines:32",
        // "-Yprofile-enabled",
        // "-Yprofile-trace:compiler.trace"
    ),
    libraryDependencies += "com.manyangled"             %%% "coulomb-core"                       % "0.8.0",
    libraryDependencies += "com.manyangled"             %%% "coulomb-units"                      % "0.8.0",
    libraryDependencies += "org.typelevel"              %%% "cats-core"                          % "2.13.0",
    libraryDependencies += "org.typelevel"              %%% "cats-effect"                        % "3.6.1",
    libraryDependencies += "org.typelevel"              %%% "kittens"                            % "3.5.0",
    libraryDependencies += "com.softwaremill.quicklens" %%% "quicklens"                          % "1.9.12",

    // Test

    // scalatest
    libraryDependencies += "org.scalatest"      %%% "scalatest"         % "3.2.19"      % "test",
    libraryDependencies += "org.scalatestplus"  %%% "scalacheck-1-18"   % "3.2.19.0"    % "test",

    // i18n
    libraryDependencies += "io.taig" %%% "babel-circe"   % "0.5.3",
    libraryDependencies += "io.taig" %%% "babel-generic" % "0.5.3",
    libraryDependencies += "io.taig" %%% "babel-loader"  % "0.5.3",
  ).jsConfigure(_.settings(jsSourceMapSettings: _*))
  .settings(watchI18nSources("i18n"))
  .dependsOn(i18n, units, dto)



// =========
// fdim

lazy val fdim = (project in file("modules/fdim"))
  .settings(
    name := "firecalc-fdim",
    version := engine_version,
    commonSettings,
    // scalatest
    libraryDependencies += "org.scalatest"      %%% "scalatest"         % "3.2.19"      % "test",
    scalacOptions ++= Seq(
      "-Xmax-inlines:32",
    ),
  )
  .settings(watchI18nSources("i18n"))
  .dependsOn(engine.jvm, engine.jvm % "test->test")

// =========
// i18n

lazy val i18n = crossProject(JVMPlatform, JSPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/i18n"))
  .settings(
    commonSettings,
    name := "firecalc-i18n",
    version := engine_version,
    scalacOptions ++= Seq(
        "-Xmax-inlines:48",
    ),

    // i18n
    libraryDependencies += "io.taig" %%% "babel-circe"   % "0.5.3",
    libraryDependencies += "io.taig" %%% "babel-generic" % "0.5.3",
    libraryDependencies += "io.taig" %%% "babel-loader"  % "0.5.3",

    // Make Bloop/Metals watch the i18n conf files for changes
    Compile / watchSources ++= SUPPORTED_LANGUAGES_IDS.map { lang =>
      file(s"modules/i18n/src/main/resources/i18n/$lang.conf")
    },

    // Generate Scala source files from HOCON conf files
    Compile / sourceGenerators += i18nSourceGenerator("i18n", Seq("afpma", "firecalc", "i18n")),
  ).jsConfigure(_.settings(jsSourceMapSettings: _*))
  .dependsOn(i18n_utils)

lazy val i18nJVM = i18n.jvm
lazy val i18nJS = i18n.js

// =========
// labo

lazy val labo = (project in file("modules/labo"))
  .settings(
    name := "firecalc-labo",
    version := engine_version,
  )
  .settings(
    commonSettings,
    scalacOptions ++= Seq(
      "-Xmax-inlines:32",
    ),
  )
  .settings(watchI18nSources("i18n"))
  .dependsOn(engine.jvm, engine.jvm % "test->test")



// =========
// ui

//
// Waiting for a better fix: https://github.com/ScalablyTyped/Converter/issues/706
//

val hackScalablyTypedRemoveSourceFuture: Seq[Setting[_]] = {
  import org.scalablytyped.converter.internal.scalajs.Versions
  import org.scalablytyped.converter.internal.ZincCompiler

  /* First, we need to override stConversionOptions.versions with a hacked
   * subclass that gets rid of "-source:future".
   */

  // Yes, I'm extending a case class; I will burn in hell.
  class HackedVersions(orig: Versions)
      extends Versions(orig.scala, orig.scalaJs) {
    override val scalacOptions: List[String] =
      orig.scalacOptions.filterNot(_ == "-source:future")

    override def toString(): String =
      s"${super.toString()} hacked with $scalacOptions"
  }

  val conversionSetting: Setting[_] = stConversionOptions := {
    val prev = stConversionOptions.value
    prev.copy(versions = new HackedVersions(prev.versions))
  }

  /* Unfortunately, the internal stInternalZincCompiler task recreates its
   * own Versions object, so we will also have to patch that one.
   * This is much trickier, because there is no real public access to that
   * thing. We're on the JVM, though, so nothing is ever *really* private,
   * if we try hard enough.
   */

  // Get access to the private `inputs` field of ZincCompiler:
  // https://github.com/ScalablyTyped/Converter/blob/02257bf3588da08deec5a3b07f306fcc6236642d/sbt-converter/src/main/scala/org/scalablytyped/converter/internal/ZincCompiler.scala#L25
  val inputsField = classOf[ZincCompiler].getDeclaredField("inputs")
  inputsField.setAccessible(true)

  // Access to a private setting
  // https://github.com/ScalablyTyped/Converter/blob/02257bf3588da08deec5a3b07f306fcc6236642d/sbt-converter/src/main/scala/org/scalablytyped/converter/plugin/ScalablyTypedConverterExternalNpmPlugin.scala#L15C19-L15C97
  val stInternalZincCompiler = taskKey[ZincCompiler]("Hijack compiler settings")

  val zincCompilerSetting: Setting[_] = stInternalZincCompiler := {
    val prev = stInternalZincCompiler.value
    val prevInputs = inputsField.get(prev).asInstanceOf[xsbti.compile.Inputs]
    val prevScalacOptions = prevInputs.options().scalacOptions()
    val newScalacOptions = prevScalacOptions.filterNot(_ == "-source:future")
    val newOptions = prevInputs.options().withScalacOptions(newScalacOptions)
    val newInputs = prevInputs.withOptions(newOptions)

    // And brutally set the immutable field, because we can.
    // (What do you mean, that's less bad than extending a case class?)
    inputsField.set(prev, newInputs)

    prev
  }

  Def.settings(
    conversionSetting,
    zincCompilerSetting
  )
}

// In CI on Windows, disable the reflective hack that touches ScalablyTyped's internal Zinc compiler,
// as it might contribute to timing/locking issues on some runners.
val maybeHackScalablyTypedRemoveSourceFuture: Seq[Setting[_]] =
  if (sys.env.get("FIRECALC_CI_NO_ST_HACK").nonEmpty) Def.settings()
  else hackScalablyTypedRemoveSourceFuture

// lazy val jsSourceMapSettings = Def.settings(
//     scalacOptions += {
//         val baseDirectory = (ThisBuild / baseDirectory).value.getAbsolutePath
//         "-P:scalajs:mapSourceURI:http://localhost:3000/{localdir}/"
//     }
// )

lazy val jsSourceMapSettings = Def.settings(
    scalacOptions ++= {
        // Ensure ALL file:// URIs in sourcemaps resolve via Vite:
        // - Browser cannot fetch file:///... via HTTP; map them to http://localhost:5173/@fs/...
        // - Include both file:/ and file:/// prefixes (both occur in practice).
        // - Keep trailing slash on the target so absolute paths preserve their leading slash.
        Seq(
          "-scalajs-mapSourceURI:file:/->http://localhost:5173/@fs/",
          "-scalajs-mapSourceURI:file:///->http://localhost:5173/@fs/"
        )
    }
)

lazy val ui = (project in file("modules/ui"))
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(ScalablyTypedConverterExternalNpmPlugin)
  .settings(
    name := "firecalc-ui",
    version := ui_version,
    stIgnore += "@tailwindcss/vite",
    maybeHackScalablyTypedRemoveSourceFuture,
    
    // Generate .env.electron file with repository and version information
    TaskKey[Unit]("generateEnvVars") := {
      val envFilePath = file("web/.env.electron")
      val envContent = s"""# Auto-generated from build.sbt - DO NOT EDIT MANUALLY
                          |GITHUB_REPO_OWNER=$githubOwner
                          |GITHUB_REPO_NAME=$githubRepo
                          |UI_VERSION=$ui_version
                          |""".stripMargin
      
      IO.write(envFilePath, envContent)
      System.err.println("[info] => Generated web/.env.electron with repository info")
      System.err.println(s"[info] \tGITHUB_REPO_OWNER=$githubOwner")
      System.err.println(s"[info] \tGITHUB_REPO_NAME=$githubRepo")
      System.err.println(s"[info] \tUI_VERSION=$ui_version")
    },
    
    // Generate JavaScript constants for renderer process
    TaskKey[Unit]("generateJsConstants") := {
      val jsFilePath = file("web/generated-constants.js")
      
      // Read backend URLs from .env files
      def readEnvFile(envFile: File): Map[String, String] = {
        if (envFile.exists()) {
          Source.fromFile(envFile).getLines()
            .filter(_.contains("="))
            .filterNot(_.trim.startsWith("#"))
            .map { line =>
              val Array(key, value) = line.split("=", 2).map(_.trim)
              key -> value
            }
            .toMap
        } else Map.empty
      }
      
      // Build backend URLs from environment files
      def buildBackendUrl(envMap: Map[String, String]): String = {
        val protocol = envMap.getOrElse("VITE_BACKEND_PROTOCOL", "http")
        val host = envMap.getOrElse("VITE_BACKEND_HOST", "localhost")
        val port = envMap.getOrElse("VITE_BACKEND_PORT", "8181")
        val portSuffix = if (port == "443" || port == "80") "" else s":$port"
        s"$protocol://$host$portSuffix"
      }
      
      val devEnv = readEnvFile(file("modules/ui/.env.development"))
      val stagingEnvLocal = readEnvFile(file("modules/ui/.env.staging.local"))
      val stagingEnv = readEnvFile(file("modules/ui/.env.staging"))
      val prodEnvLocal = readEnvFile(file("modules/ui/.env.production.local"))
      val prodEnv = readEnvFile(file("modules/ui/.env.production"))
      
      // Prefer .local files over base files (local overrides base)
      val devBackendUrl = buildBackendUrl(devEnv)
      val stagingBackendUrl = buildBackendUrl(stagingEnv ++ stagingEnvLocal)
      val prodBackendUrl = buildBackendUrl(prodEnv ++ prodEnvLocal)
      
      val jsContent = s"""// Auto-generated from build.sbt - DO NOT EDIT MANUALLY
                        |export const GITHUB_REPO_OWNER = '$githubOwner';
                        |export const GITHUB_REPO_NAME = '$githubRepo';
                        |export const UI_VERSION = '$ui_version';
                        |
                        |// Backend API URLs per environment
                        |export const BACKEND_API_DEV = '$devBackendUrl';
                        |export const BACKEND_API_STAGING = '$stagingBackendUrl';
                        |export const BACKEND_API_PRODUCTION = '$prodBackendUrl';
                        |""".stripMargin
      
      IO.write(jsFilePath, jsContent)
      System.err.println("[info] => Generated web/generated-constants.js for renderer process")
      System.err.println(s"[info] \tBACKEND_API_DEV: $devBackendUrl")
      System.err.println(s"[info] \tBACKEND_API_STAGING: $stagingBackendUrl")
      System.err.println(s"[info] \tBACKEND_API_PRODUCTION: $prodBackendUrl")
    },
    
    // Combined task to sync all build configuration
    TaskKey[Unit]("syncBuildConfig") := {
      // Update package.json version
      val packageJsonPath = file("web/package.json")
      if (packageJsonPath.exists()) {
        val content = IO.read(packageJsonPath)
        val updatedContent = content.replaceFirst(
          "\"version\"\\s*:\\s*\"[^\"]*\"",
          s""""version": "$ui_version""""
        )
        IO.write(packageJsonPath, updatedContent)
        System.err.println(s"[info] => Synced Electron version to: $ui_version")
      } else {
        System.err.println(s"[warn] => Warning: web/package.json not found")
      }
      
      // Generate environment variables
      val generateEnvTask = (TaskKey[Unit]("generateEnvVars")).value
      val generateJsTask = (TaskKey[Unit]("generateJsConstants")).value
    },
    
    libraryDependencies ++= Seq(
      "com.raquo"                     %%% "laminar"             % "17.2.1",
      "com.raquo"                     %%% "waypoint"            % "9.0.0",
      "com.raquo"                     %%% "airstream"           % "17.2.1",
      "com.lihaoyi"                   %%% "upickle"             % "4.1.0",

      // provides implementation of java.time for scala js
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0",

      // automatic data transformations (helps going from 'ui' models to 'engine' models)
      "io.scalaland"      %%% "chimney" % "1.8.2",

      // hackish fork of magnolia to prevent Transl to be listed in annotation (compiler bug because of macros colliding)
      "pro.afpma" %%% "magnolia" % "1.3.16", 

      "io.taig"             %%% "babel-circe"   % "0.5.3",
      "io.taig"             %%% "babel-generic" % "0.5.3",
      "io.taig"             %%% "babel-loader"  % "0.5.3",

      // encoding / decoding
      
      // generic JSON
      "io.circe"            %%% "circe-core"    % "0.14.13",
      "io.circe"            %%% "circe-generic" % "0.14.13",
      "io.circe"            %%% "circe-parser"  % "0.14.13",
      
      // JSON <-> YAML
      "io.circe"            %%% "circe-yaml-scalayaml" % "0.16.0",

      // Runtime unit
      "com.manyangled"      %%% "coulomb-parser" % "0.8.0",

      // Testing
      "org.scalatest"       %%% "scalatest"     % "3.2.19" % "test",
    ),

    // common Scala.js config for dev / prod
    scalaJSLinkerConfig ~= { _
        .withModuleKind(ModuleKind.ESModule) 
    },

    // Development settings (applied when using fastLinkJS)
    // Dev: keep bundles minimal to reduce the number of emitted .js/.map files
    Compile / fastLinkJS / scalaJSLinkerConfig ~= { _
      .withModuleSplitStyle(ModuleSplitStyle.FewestModules)
    //   .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("afpma.firecalc.ui")))
      .withSourceMap(true)
      .withCheckIR(false)  // Skip IR validation in dev for faster incremental compiles
    //   .withOptimizer(false) // only deactivate optimizer for debugging (temporarily). See https://github.com/scala-js/scala-js/issues/5159
    },

    // Production settings (applied when using fullLinkJS)
    Compile / fullLinkJS / scalaJSLinkerConfig ~= { _
      .withModuleSplitStyle(ModuleSplitStyle.FewestModules)
      .withSourceMap(false)
      .withOptimizer(true)
      .withClosureCompiler(false)
    },

    scalaJSUseMainModuleInitializer := true,

    Compile / sourceGenerators += Def.task { List(
      (ui_i18n.js / Compile / sourceManaged).value / "afpma" / "firecalc" / "ui" / "i18n" / "files.scala"
    )},

    // Tell ScalablyTyped that we manage `npm install` ourselves
    externalNpm := (baseDirectory).value,
  )
  .settings(
    commonSettings,
    scalacOptions ++= Seq(
      "-Xmax-inlines:32", // increase for deep circe encoding / decoding (extract this logic into its own subproject to reduce compile time if needed ?)
    //   "--explain-cyclic",
    ),
  )
  .settings(jsSourceMapSettings)
  .settings(watchI18nSources("i18n", "ui-i18n", "payments-shared-i18n"))
  .dependsOn(dto.js, i18n.js, i18n_utils.js, engine.js, ui_i18n.js, payments_shared.js)

// =========
// ui-i18n

lazy val ui_i18n = crossProject(JSPlatform/*, JVMPlatform*/)
  .crossType(CrossType.Pure)
  .in(file("modules/ui-i18n"))
  .settings(
    commonSettings,
    name    := "firecalc-ui-i18n",
    version := ui_version,

    libraryDependencies += "org.typelevel"  %%% "cats-core"     % "2.13.0",
    
    libraryDependencies += "io.taig"        %%% "babel-generic" % "0.5.3",

    // Make Bloop/Metals watch the i18n conf files for changes
    Compile / watchSources ++= SUPPORTED_LANGUAGES_IDS.map { lang =>
      file(s"modules/ui-i18n/src/main/resources/i18n/$lang.conf")
    },

    // Generate Scala source files from HOCON conf files
    Compile / sourceGenerators += i18nSourceGenerator("ui-i18n", Seq("afpma", "firecalc", "ui", "i18n")),

    // scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    // scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  )
  .dependsOn(i18n_utils)
  .jsConfigure(
    _.enablePlugins(ScalaJSPlugin)
     .settings(jsSourceMapSettings: _*)
  )

// =========
// payments-shared-i18n

lazy val payments_shared_i18n = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/payments-shared-i18n"))
  .settings(
    commonSettings,
    name    := "firecalc-payments-shared-i18n",
    version := payments_base_version,

    libraryDependencies += "org.typelevel"  %%% "cats-core"     % "2.13.0",

    libraryDependencies += "io.taig"        %%% "babel-generic" % "0.5.3",

    // Make Bloop/Metals watch the i18n conf files for changes
    Compile / watchSources ++= SUPPORTED_LANGUAGES_IDS.map { lang =>
      file(s"modules/payments-shared-i18n/src/main/resources/i18n/$lang.conf")
    },

    // Generate Scala source files from HOCON conf files
    Compile / sourceGenerators += i18nSourceGenerator("payments-shared-i18n", Seq("afpma", "firecalc", "payments", "shared", "i18n")),

    // scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    // scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  )
  .dependsOn(i18n_utils)
  .jsConfigure(
    _.enablePlugins(ScalaJSPlugin)
     .settings(jsSourceMapSettings: _*)
  )

// =========
// payments-shared

lazy val payments_shared = crossProject(JVMPlatform, JSPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/payments-shared"))
  .settings(
    commonSettings,
    name := "firecalc-payments-shared",
    version := payments_version,
    scalacOptions ++= Seq(
    ),

    libraryDependencies ++= Seq(
        // hackish fork of magnolia to prevent Transl to be listed in annotation (compiler bug because of macros colliding)
        "pro.afpma" %%% "magnolia" % "1.3.16",

        // transformers
        "io.scalaland"   %% "chimney"             % "1.8.2",

        // i18n
        "io.taig" %% "babel-circe"   % "0.5.3",
        "io.taig" %% "babel-generic" % "0.5.3",
        "io.taig" %% "babel-loader"  % "0.5.3",

        // encoding / decoding
        "io.circe" %% "circe-core"    % "0.14.13",
        "io.circe" %% "circe-generic" % "0.14.13",
        "io.circe" %% "circe-parser"  % "0.14.13",

        // testing
        "org.scalatest" %% "scalatest" % "3.2.19" % "test",
    ),
  ).jsConfigure(_.settings(jsSourceMapSettings: _*))
  .settings(watchI18nSources("payments-shared-i18n"))
  .dependsOn(utils, payments_shared_i18n)

// =========
// payments-i18n

lazy val payments_i18n = (project in file("modules/payments-i18n"))
  .settings(
    commonSettings,
    name    := "firecalc-payments-i18n",
    version := payments_version,

    libraryDependencies += "org.typelevel"  %% "cats-core"     % "2.13.0",
    
    libraryDependencies += "io.taig"        %% "babel-generic" % "0.5.3",

    // Make Bloop/Metals watch the i18n conf files for changes
    Compile / watchSources ++= SUPPORTED_LANGUAGES_IDS.map { lang =>
      file(s"modules/payments-i18n/src/main/resources/i18n/$lang.conf")
    },

    // Generate Scala source files from HOCON conf files
    Compile / sourceGenerators += i18nSourceGenerator("payments-i18n", Seq("afpma", "firecalc", "payments", "i18n")),
  )
  .dependsOn(i18n_utils.jvm)
//   .jsConfigure(
//     _.enablePlugins(ScalaJSPlugin)
//   )

// =========
// invoices-i18n

lazy val invoices_i18n = (project in file("modules/invoices-i18n"))
  .settings(
    commonSettings,
    name    := "firecalc-invoices-i18n",
    version := payments_version,

    libraryDependencies += "org.typelevel"  %% "cats-core"     % "2.13.0",
    
    libraryDependencies += "io.taig"        %% "babel-generic" % "0.5.3",

    // Make Bloop/Metals watch the i18n conf files for changes
    Compile / watchSources ++= SUPPORTED_LANGUAGES_IDS.map { lang =>
      file(s"modules/invoices-i18n/src/main/resources/i18n/$lang.conf")
    },

    // Generate Scala source files from HOCON conf files
    Compile / sourceGenerators += i18nSourceGenerator("invoices-i18n", Seq("afpma", "firecalc", "invoices", "i18n")),
  )
  .dependsOn(i18n_utils.jvm)


lazy val reports = (project in file("modules/reports"))
  .settings(
    name := "firecalc-reports",
    version := reports_version,
    commonSettings,
    resolvers += Resolver.mavenLocal, // lookup "java-typst" in local maven repo (~/.m2/local/...)
    scalacOptions ++= Seq(
    ),

    // Assembly configuration for fat jar
    assembly / assemblyJarName := "firecalc-reports-assembly.jar",

    // Merge strategy for conflicting files
    assembly / assemblyMergeStrategy := commonAssemblyMergeStrategy,

    libraryDependencies ++= Seq(
        // i18n
        "io.taig" %% "babel-circe"   % "0.5.3",
        "io.taig" %% "babel-generic" % "0.5.3",
        "io.taig" %% "babel-loader"  % "0.5.3",

        // os
        "com.lihaoyi" %% "os-lib" % "0.11.4",

        // java-typst
        "io.github.fatihcatalkaya" % "java-typst" % "1.4.0",
    ),
  )
  .settings(watchI18nSources("i18n"))
  .dependsOn(engine.jvm, utils.jvm)

lazy val payments = (project in file("modules/payments"))
  .enablePlugins(MoleculePlugin)
  .settings(
    name := "firecalc-payments",
    version := payments_version,
    commonSettings,
    scalacOptions ++= Seq(
    ),

    // Ensure moleculeGen runs before compile and copy SQL files to classpath
    Compile / compile := {
      val compilationResult = (Compile / compile).dependsOn(moleculeGen).value
      
      // Copy moleculeGen SQL files to target classes directory after compilation
      val moleculeGenSourceDir = baseDirectory.value / "src" / "main" / "resources" / "moleculeGen"
      val targetClassesDir = (Compile / classDirectory).value / "moleculeGen"
      
      if (moleculeGenSourceDir.exists()) {
        System.err.println(s"[info] => Copying moleculeGen resources from $moleculeGenSourceDir to $targetClassesDir")
        
        // Remove target directory first to avoid file/directory conflicts
        if (targetClassesDir.exists()) {
          IO.delete(targetClassesDir)
        }
        
        // Use IO.copy with proper file mapping instead of copyDirectory
        val mappings = (moleculeGenSourceDir ** "*.sql").get.map { file =>
          val relativePath = file.relativeTo(moleculeGenSourceDir).get
          (file, targetClassesDir / relativePath.getPath)
        }
        IO.copy(mappings)
        System.err.println(s"[info] => Copied ${mappings.length} SQL files to classpath")
      } else {
        System.err.println(s"[info] => moleculeGen source directory not found: $moleculeGenSourceDir")
      }
      
      compilationResult
    },

    Compile / run / fork := true,
    Compile / run / baseDirectory := (ThisBuild / baseDirectory).value,

    assembly / assemblyJarName := "firecalc-payments-assembly.jar",

    // Merge strategy for conflicting files
    assembly / assemblyMergeStrategy := commonAssemblyMergeStrategy,

    libraryDependencies ++= Seq(
        "org.http4s"     %% "http4s-ember-server" % "0.23.30",
        "org.http4s"     %% "http4s-ember-client" % "0.23.30", 
        "org.http4s"     %% "http4s-circe"        % "0.23.30",
        "org.http4s"     %% "http4s-dsl"          % "0.23.30",
        "org.http4s"     %% "http4s-scalatags"    % "0.25.2",

        "io.circe"       %% "circe-generic"       % "0.14.14",
        "io.circe"       %% "circe-literal"       % "0.14.14",
        
        "org.typelevel"  %% "cats-effect"         % "3.6.1",

        "ch.qos.logback" % "logback-classic"      % "1.5.18",

        "org.typelevel"  %% "log4cats-slf4j"      % "2.7.1",

        "com.lihaoyi"    %% "scalatags"           % "0.13.1",

        // transformers
        "io.scalaland"   %% "chimney"             % "1.8.2",

        // configuration
        "com.typesafe"   % "config"               % "1.4.3",

        // email
        "com.github.eikek" %% "emil-common"     % "0.15.0",
        "com.github.eikek" %% "emil-javamail"   % "0.15.0",

        // molecule
        "org.scalamolecule" %% "molecule-db-sqlite" % "0.25.1",

        // flyway
        "org.flywaydb" % "flyway-core" % "11.10.4",
        "org.xerial" % "sqlite-jdbc" % "3.50.3.0",

        // testing
        "com.lihaoyi" %% "utest" % "0.9.0" % "test",
        
        // TODO: remove, only use utest
        "org.scalatest" %% "scalatest" % "3.2.19" % "test",
        "org.typelevel" %% "cats-effect-testing-scalatest" % "1.6.0" % "test"
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
  )
  .settings(watchI18nSources("payments-i18n", "invoices-i18n"))
  .dependsOn(engine.jvm, payments_i18n, invoices, reports, payments_shared.jvm)

lazy val invoices = (project in file("modules/invoices"))
  .settings(
    name := "firecalc-invoices",
    version := payments_version,
    commonSettings,
    resolvers += Resolver.mavenLocal, // lookup "java-typst" in local maven repo (~/.m2/local/...)
    scalacOptions ++= Seq(
    ),

    // Assembly configuration for fat jar
    assembly / assemblyJarName := "firecalc-invoices-assembly.jar",

    // Merge strategy for conflicting files
    assembly / assemblyMergeStrategy := commonAssemblyMergeStrategy,

    libraryDependencies ++= Seq(
        // i18n
        "io.taig" %% "babel-circe"   % "0.5.3",
        "io.taig" %% "babel-generic" % "0.5.3",
        "io.taig" %% "babel-loader"  % "0.5.3",

        // os
        "com.lihaoyi" %% "os-lib" % "0.11.4",

        // java-typst
        "io.github.fatihcatalkaya" % "java-typst" % "1.4.0",

        // json/yaml support
        "io.circe" %% "circe-core"    % "0.14.13",
        "io.circe" %% "circe-generic" % "0.14.13",
        "io.circe" %% "circe-parser"  % "0.14.13",
        "io.circe" %% "circe-yaml-scalayaml" % "0.16.0",

        // testing
        "org.scalatest" %% "scalatest" % "3.2.19" % "test",
    ),
  )
  .settings(watchI18nSources("invoices-i18n"))
  .dependsOn(invoices_i18n)
