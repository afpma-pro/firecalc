/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.config

import java.nio.file.{Files, Path, Paths}

/** Utility for resolving configuration file paths in a centralized, environment-aware manner.
  * 
  * Replaces getClassLoader-based resource loading with file system paths that work
  * consistently in both development (sbt) and production (JAR) environments.
  */
object ConfigPathResolver {

  /** Resolves the current environment from various sources.
    * 
    * Priority order:
    * 1. FIRECALC_ENV environment variable
    * 2. firecalc.env system property  
    * 3. Default to "dev"
    */
  def resolveEnvironment(): String = 
    sys.env.get("FIRECALC_ENV")
      .orElse(sys.props.get("firecalc.env"))
      .getOrElse("dev")
  
  /** Resolves a configuration file path for a specific module.
    * 
    * @param module The module name (e.g., "payments", "reports", "invoices")
    * @param filename The configuration filename
    * @return Path relative to project root: configs/{env}/{module}/{filename}
    */
  def resolveConfigPath(module: String, filename: String): Path = {
    val env = resolveEnvironment()
    Paths.get("configs", env, module, filename)
  }
    
  /** Attempts to find a logo file for a module.
    * 
    * Searches for logo files with common extensions (png, jpg, jpeg) in the
    * module's configuration directory.
    * 
    * @param module The module name (e.g., "reports", "invoices")
    * @return Some(Path) if a logo file exists, None otherwise
    */
  def resolveLogoPath(module: String): Option[Path] = {
    val env = resolveEnvironment()
    val extensions = List("png", "jpg", "jpeg")
    
    extensions.map { ext =>
      Paths.get("configs", env, module, s"logo.$ext")
    }.find(Files.exists(_))
  }

  /** Checks if a configuration file exists at the resolved path.
    * 
    * @param module The module name
    * @param filename The configuration filename
    * @return true if the file exists
    */
  def configExists(module: String, filename: String): Boolean = {
    val path = resolveConfigPath(module, filename)
    Files.exists(path)
  }

  /** Returns current environment and base config path for debugging. */
  def debugInfo(): String = {
    val env = resolveEnvironment()
    val basePath = Paths.get("configs", env)
    s"Environment: $env, Base config path: $basePath"
  }
}
