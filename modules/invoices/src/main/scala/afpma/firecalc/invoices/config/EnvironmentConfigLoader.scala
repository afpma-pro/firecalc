/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.config

import afpma.firecalc.invoices.models.InvoiceConfig
import scala.util.{Try, Success, Failure}
import scala.util.matching.Regex
import java.io.File
import scala.io.Source

/**
 * Configuration loader with environment variable substitution support.
 * 
 * Supports environment variable substitution in the format:
 * - ${VAR_NAME} - Required variable, fails if not set
 * - ${VAR_NAME:-default} - Optional variable with default value
 * 
 * This enables keeping sensitive information out of configuration files
 * while maintaining flexibility for deployment environments.
 */
object EnvironmentConfigLoader:

  // Regex to match environment variable patterns: ${VAR_NAME} or ${VAR_NAME:-default}
  private val envVarPattern: Regex = """\$\{([A-Z_][A-Z0-9_]*)(?::-(.*?))?\}""".r

  /**
   * Load configuration from YAML file with environment variable substitution.
   */
  def loadFromFile(file: File): Try[InvoiceConfig] = {
    Try {
      val yamlContent = Source.fromFile(file).mkString
      loadFromYamlString(yamlContent)
    }.flatten
  }

  /**
   * Load configuration from YAML string with environment variable substitution.
   */
  def loadFromYamlString(yamlContent: String): Try[InvoiceConfig] = {
    for {
      substitutedYaml <- substituteEnvironmentVariables(yamlContent)
      config <- InvoiceConfig.decodeFromYaml(substitutedYaml)
    } yield config
  }

  /**
   * Substitute environment variables in YAML content.
   * 
   * @param yamlContent The YAML content with environment variable placeholders
   * @return Either the substituted content or an error if required variables are missing
   */
  def substituteEnvironmentVariables(yamlContent: String): Try[String] = {
    Try {
      var result = yamlContent
      var missingVars = List.empty[String]

      // Find all environment variable references
      val matches = envVarPattern.findAllMatchIn(yamlContent).toList

      for (matchResult <- matches) {
        val fullMatch = matchResult.group(0) // ${VAR_NAME} or ${VAR_NAME:-default}
        val varName = matchResult.group(1)   // VAR_NAME
        val defaultValue = Option(matchResult.group(2)) // default value or null

        // Get environment variable value
        sys.env.get(varName) match {
          case Some(envValue) =>
            // Replace with environment variable value
            result = result.replace(fullMatch, envValue)
          
          case None =>
            defaultValue match {
              case Some(default) =>
                // Use default value
                result = result.replace(fullMatch, default)
              case None =>
                // Required variable is missing
                missingVars = varName :: missingVars
            }
        }
      }

      // Check if any required variables are missing
      if (missingVars.nonEmpty) {
        throw new IllegalStateException(
          s"Missing required environment variables: ${missingVars.mkString(", ")}"
        )
      }

      result
    }
  }

  /**
   * Validate that all required environment variables are set for a template.
   */
  def validateTemplate(templateContent: String): Try[List[String]] = {
    Try {
      val matches = envVarPattern.findAllMatchIn(templateContent).toList
      val missingVars = matches.flatMap { matchResult =>
        val varName = matchResult.group(1)
        val defaultValue = Option(matchResult.group(2))
        
        if (sys.env.contains(varName) || defaultValue.isDefined) {
          None
        } else {
          Some(varName)
        }
      }.distinct

      missingVars
    }
  }

  /**
   * Get all environment variables referenced in a template.
   */
  def getReferencedVariables(templateContent: String): List[EnvironmentVariable] = {
    val matches = envVarPattern.findAllMatchIn(templateContent).toList
    matches.map { matchResult =>
      val varName = matchResult.group(1)
      val defaultValue = Option(matchResult.group(2))
      val currentValue = sys.env.get(varName)
      
      EnvironmentVariable(
        name = varName,
        defaultValue = defaultValue,
        currentValue = currentValue,
        isRequired = defaultValue.isEmpty
      )
    }.distinctBy(_.name)
  }

  /**
   * Generate a sample .env file for a template.
   */
  def generateEnvTemplate(templateContent: String): String = {
    val variables = getReferencedVariables(templateContent)
    val envLines = variables.map { envVar =>
      val comment = if (envVar.isRequired) "# Required" else "# Optional"
      val value = envVar.defaultValue.getOrElse("YOUR_VALUE_HERE")
      s"$comment\n${envVar.name}=$value"
    }
    
    "# Environment variables for invoice configuration\n" +
    "# Copy this file to .env and fill in your company details\n\n" +
    envLines.mkString("\n\n")
  }

/**
 * Represents an environment variable reference found in a template.
 */
case class EnvironmentVariable(
  name: String,
  defaultValue: Option[String],
  currentValue: Option[String],
  isRequired: Boolean
):
  def isSet: Boolean = currentValue.isDefined
  def effectiveValue: Option[String] = currentValue.orElse(defaultValue)
