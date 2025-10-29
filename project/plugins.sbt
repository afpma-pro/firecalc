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
  Resolver.sonatypeRepo("releases")
)

addSbtPlugin("io.spray"                     % "sbt-revolver"             % "0.10.0")
addSbtPlugin("org.portable-scala"           % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("org.scala-js"                 % "sbt-scalajs"              % "1.20.1")
addSbtPlugin("ch.epfl.scala"                % "sbt-scalafix"             % "0.14.3")
addSbtPlugin("org.scalablytyped.converter"  % "sbt-converter"            % "1.0.0-beta44")
addSbtPlugin("org.scalamolecule"            % "sbt-molecule"             % "1.20.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")

// addSbtPlugin("org.scalameta" % "sbt-metals" % "1.6.2")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always