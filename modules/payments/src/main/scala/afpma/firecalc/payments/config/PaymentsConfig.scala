/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.config

import java.time.ZoneId

case class PaymentsConfig(
  environment: String,
  productCatalog: String,
  invoiceNumberPrefix: String,
  invoiceNumberDigits: Int,
  invoiceCounterStartingNumber: Int,
  invoiceTimezone: ZoneId,
  retryConfig: InvoiceRetryConfig,
  databaseConfig: DatabaseConfig,
  invoiceConfig: InvoiceConfig,
  adminConfig: AdminConfig,
  reportAsDraft: Boolean
) {
  require(invoiceCounterStartingNumber >= 1,
    "Starting number must be at least 1")
  require(
    productCatalog == "development" || productCatalog == "staging" || productCatalog == "production",
    s"Product catalog must be 'development', 'staging', or 'production', got: $productCatalog"
  )
}

case class DatabaseConfig(
  filename: String,
  path: String
) {
  require(filename.nonEmpty, "Database filename cannot be empty")
  require(path.nonEmpty, "Database path cannot be empty")
  require(filename.endsWith(".db"), "Database filename must end with .db")
}

case class InvoiceRetryConfig(
  maxRetries: Int = 5,
  baseDelayMs: Long = 1000,
  maxDelayMs: Long = 30000
) {
  require(maxRetries >= 0, "Max retries must be non-negative")
  require(baseDelayMs > 0, "Base delay must be positive")
  require(maxDelayMs >= baseDelayMs, "Max delay must be >= base delay")
}

case class InvoiceConfig(
  configFilePath: String,
  enabled: Boolean = true
) {
  require(configFilePath.nonEmpty, "Invoice config file path cannot be empty")
  require(configFilePath.endsWith(".yaml") || configFilePath.endsWith(".yml"), 
    "Invoice config file must be a YAML file")
}

case class AdminConfig(
  email: String
) {
  require(email.nonEmpty, "Admin email cannot be empty")
  require(email.contains("@"), "Admin email must be a valid email address")
}
