/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.models

import io.circe.Codec
import io.circe.generic.semiauto.*

/**
 * Represents a postal address for billing or shipping purposes.
 * 
 * @param street Street address line (e.g., "123 Main St")
 * @param streetLine2 Optional second line for apartment, suite, etc.
 * @param city City name
 * @param postalCode Postal/ZIP code
 * @param region State, province, or region
 * @param country Country name or ISO code
 */
case class Address(
  street: String,
  streetLine2: Option[String] = None,
  city: String,
  postalCode: String,
  region: String,
  country: String
):
  /**
   * Formats the address as a multi-line string suitable for display.
   */
  def formatMultiLine: String =
    val lines = Seq(
      Some(street),
      streetLine2,
      Some(s"$postalCode $city"),
      Some(s"$region, $country")
    ).flatten
    lines.mkString("\n")

  /**
   * Formats the address as a single line string.
   */
  def formatSingleLine: String =
    val parts = Seq(
      Some(street),
      streetLine2,
      Some(city),
      Some(postalCode),
      Some(region),
      Some(country)
    ).flatten
    parts.mkString(", ")

object Address:
  given Codec[Address] = deriveCodec[Address]
