/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

// Stabilize plugin resolution on Windows runners and CI by making plugin/maven repos explicit.
// This helps avoid Ivy POM parsing quirks like:
//   "bad module name: expected='sbt-crossproject_2.12_1.0' found='sbt-crossproject'"
resolvers ++= Seq(
    Resolver.sbtPluginRepo("releases"),
    "sbt-plugin-snapshots" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots",
    Resolver.mavenCentral,
//   Resolver.sonatypeRepo("releases") // deprecated
    Resolver.sonatypeCentralSnapshots
)

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0" )
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.22.0")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"             % "0.14.7")
addSbtPlugin("org.scalamolecule"  % "sbt-molecule"             % "1.25.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")
addSbtPlugin("ch.epfl.scala" % "sbt-bloop"    % "2.1.2")

// addSbtPlugin("org.scalameta" % "sbt-metals" % "1.6.2")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// sbt 2 hard-errors on mixed cross-version suffixes in the meta-build classpath
// (sbt 1 only warned). scala-debug-adapter_3:4.2.13 declares
// org.scalameta:parsers_2.13 but never uses it (no class in the jar references
// scalameta); sbt-molecule and scalafmt need the _3 scalameta line. Prune the
// dead 2.13 subtree (parsers_2.13 -> trees_2.13 -> common_2.13 -> sourcecode_2.13).
excludeDependencies += ExclusionRule(organization = "org.scalameta", name = "parsers_2.13")

// addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "2.0.19")

// sbt 2: custom tasks defined in this meta-build (i18n generators, watchSources appends)
// are side-effecting and lack sjsonnew codecs (e.g. sbt.internal.io.Source) — opt them out
// of the default task cache; sbt-provided tasks stay cached.
Compile / scalacOptions += "-Xmacro-settings:sbt:no-default-task-cache"
