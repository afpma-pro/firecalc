/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.config

import java.nio.file.{Files, Path, Paths}

/**
 * Utility for resolving configuration file paths in a centralized, environment-aware manner.
 *
 * Replaces getClassLoader-based resource loading with file system paths that work
 * consistently in both development (sbt) and production (JAR) environments.
 */
object ConfigPathResolver {

    /**
     * Resolves the current environment from various sources.
     *
     * Priority order:
     * 1. FIRECALC_ENV environment variable
     * 2. firecalc.env system property
     * 3. Default to "dev"
     */
    def resolveEnvironment(): String =
        sys.env
            .get("FIRECALC_ENV")
            .orElse(sys.props.get("firecalc.env"))
            .getOrElse("dev")

    /**
     * Resolves a configuration file path for a specific module.
     *
     * @param module The module name (e.g., "payments", "reports", "invoices")
     * @param filename The configuration filename
     * @param basePath Root directory for resolving the config path (default: current working directory)
     * @return Path: basePath/configs/{env}/{module}/{filename}
     */
    def resolveConfigPath(module: String, filename: String, basePath: Path = Paths.get("")): Path = {
        val env = resolveEnvironment()
        List("configs", env, module, filename).foldLeft(basePath)((p, name) => p.resolve(name))
    }

    /**
     * Attempts to find a logo file for a module.
     *
     * Searches for logo files with common extensions (png, jpg, jpeg) in the
     * module's configuration directory.
     *
     * @param module The module name (e.g., "reports", "invoices")
     * @param basePath Root directory for resolving the config path (default: current working directory)
     * @return Some(Path) if a logo file exists, None otherwise
     */
    def resolveLogoPath(module: String, basePath: Path = Paths.get("")): Option[Path] = {
        val extensions = List("png", "jpg", "jpeg")

        extensions
            .map(ext => resolveConfigPath(module, s"logo.$ext", basePath))
            .find(Files.exists(_))
    }

    /**
     * Checks if a configuration file exists at the resolved path.
     *
     * @param module The module name
     * @param filename The configuration filename
     * @param basePath Root directory for resolving the config path (default: current working directory)
     * @return true if the file exists
     */
    def configExists(module: String, filename: String, basePath: Path = Paths.get("")): Boolean = {
        val path = resolveConfigPath(module, filename, basePath)
        Files.exists(path)
    }

    /** Returns current environment and base config path for debugging. */
    def debugInfo(): String = {
        val env      = resolveEnvironment()
        val basePath = Paths.get("configs", env)
        s"Environment: $env, Base config path: $basePath"
    }
}
