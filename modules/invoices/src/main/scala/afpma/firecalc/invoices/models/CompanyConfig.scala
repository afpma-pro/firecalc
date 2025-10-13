/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.models

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*

/**
 * Static company configuration that defines the invoice issuer and template styling.
 * This configuration is loaded once and reused for generating multiple invoices.
 * 
 * @param sender The company issuing invoices
 * @param templateConfig Template styling and layout configuration
 * @param defaultLogoPath Default logo path used as fallback when Company.logo is not specified
 */
case class CompanyConfig(
  sender: Company,
  templateConfig: TemplateConfig,
  defaultLogoPath: Option[String] = Some("modules/invoices/configs/logo.png")
):
  
  /**
   * Resolves the effective logo path with fallback logic.
   * Priority: Company.logo -> CompanyConfig.defaultLogoPath -> None
   */
  def resolveLogoPath: Option[String] =
    sender.logo.orElse(defaultLogoPath)
  
  /**
   * Creates a copy of this config with the resolved logo path applied to the sender.
   */
  def withResolvedLogo: CompanyConfig =
    this.copy(sender = sender.copy(logo = resolveLogoPath))
  
  /**
   * Validates the company configuration.
   */
  def validate: List[String] =
    val errors = scala.collection.mutable.ListBuffer[String]()
    
    if (sender.name.trim.isEmpty) errors += "Sender company name cannot be empty"
    if (sender.email.trim.isEmpty) errors += "Sender email cannot be empty"
    
    errors.toList

object CompanyConfig:
  given Codec[CompanyConfig] = deriveCodec[CompanyConfig]
