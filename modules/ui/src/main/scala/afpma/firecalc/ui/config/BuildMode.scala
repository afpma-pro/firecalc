/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.config

/**
 * Scala 3 enum representing the application's build/runtime mode.
 * 
 * This enum provides type-safe access to the current environment mode,
 * eliminating the need for string-based comparisons and enabling
 * exhaustive pattern matching at compile time.
 */
enum BuildMode:
  /** Development mode - local development with hot reload */
  case Development
  
  /** Staging mode - pre-production testing environment */
  case Staging
  
  /** Production mode - live production environment */
  case Production

object BuildMode:
  /**
   * Parse a mode string from Vite into a BuildMode enum value.
   * 
   * @param modeString The mode string from import.meta.env.MODE
   * @return The corresponding BuildMode enum value
   * @throws IllegalArgumentException if the mode string is not recognized
   */
  def fromString(modeString: String): BuildMode = modeString.toLowerCase match
    case "development" | "dev" => Development
    case "staging" => Staging
    case "production" | "prod" => Production
    case unknown => 
      throw IllegalArgumentException(
        s"Unknown build mode: '$unknown'. Expected one of: development, staging, production"
      )
  
  /**
   * Get the string representation suitable for Vite mode names.
   */
  def toModeString(mode: BuildMode): String = mode match
    case Development => "development"
    case Staging => "staging"
    case Production => "production"