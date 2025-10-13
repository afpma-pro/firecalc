/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.models

import io.circe.Codec
import io.circe.generic.semiauto.*
import afpma.firecalc.invoices.i18n.I18nData_Invoices

/**
 * Represents a company or business entity for invoice purposes.
 * 
 * @param name Legal company name
 * @param displayName Optional display name if different from legal name
 * @param address Company's address
 * @param vatNumber Optional VAT/Tax identification number
 * @param registrationNumber Optional business registration number
 * @param email Contact email address
 * @param phone Optional phone number
 * @param website Optional website URL
 * @param logo Optional path to company logo
 */
case class Company(
  name: String,
  displayName: Option[String] = None,
  address: Address,
  vatNumber: Option[String] = None,
  registrationNumber: Option[String] = None,
  email: String,
  phone: Option[String] = None,
  website: Option[String] = None,
  logo: Option[String] = None
):
  /**
   * Returns the display name if available, otherwise the legal name.
   */
  def effectiveName: String = displayName.getOrElse(name)

  /**
   * Checks if the company has tax identification information.
   */
  def hasTaxInfo: Boolean = vatNumber.isDefined || registrationNumber.isDefined

object Company:
  given Codec[Company] = deriveCodec[Company]
