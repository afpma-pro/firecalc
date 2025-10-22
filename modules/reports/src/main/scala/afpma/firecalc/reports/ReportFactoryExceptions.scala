/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports

/**
 * Base sealed trait for all FireCalc report generation errors.
 * Following cats-effect best practices for typed error handling.
 */
sealed abstract class FireCalcReportError(
  message: String,
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull) with Product with Serializable {
  
  /** Error code for API responses */
  def errorCode: String = this.getClass.getSimpleName.replace("Exception", "").toLowerCase
  
  /** Additional context for logging and debugging */
  def context: Map[String, String] = Map.empty
}

// YAML Loading Errors
final case class YAMLFileReadException(
  filePath: String,
  reason: String,
  cause: Option[Throwable] = None
) extends FireCalcReportError(
  s"Failed to read YAML file: $filePath",
  cause
) {
  override def context: Map[String, String] = Map(
    "filePath" -> filePath,
    "reason" -> reason
  )
}

final case class YAMLDecodingException(
  yamlLength: Int,
  reason: String,
  cause: Option[Throwable] = None
) extends FireCalcReportError(
  s"Failed to decode YAML content",
  cause
) {
  override def context: Map[String, String] = Map(
    "yamlLength" -> yamlLength.toString,
    "reason" -> reason
  )
}

// Validation Errors
final case class EN15544ValidationException(
  validationErrors: List[String]
) extends FireCalcReportError(
  s"EN15544 validation failed: ${validationErrors.mkString(", ")}"
) {
  override def context: Map[String, String] = Map(
    "validationErrors" -> validationErrors.mkString("; "),
    "errorCount" -> validationErrors.length.toString
  )
}

// State Errors
final case class InternalStateException(
  missingVariable: String
) extends FireCalcReportError(
  s"Internal state error: expected '$missingVariable' to be defined"
) {
  override def context: Map[String, String] = Map(
    "missingVariable" -> missingVariable
  )
}

// PDF Generation Errors
final case class TypstRenderingException(
  typstLength: Int,
  reason: String,
  cause: Option[Throwable] = None
) extends FireCalcReportError(
  s"Failed to render Typst content to PDF",
  cause
) {
  override def context: Map[String, String] = Map(
    "typstLength" -> typstLength.toString,
    "reason" -> reason
  )
}

final case class PDFFileCreationException(
  operation: String,
  reason: String,
  cause: Option[Throwable] = None
) extends FireCalcReportError(
  s"Failed to create PDF file during $operation",
  cause
) {
  override def context: Map[String, String] = Map(
    "operation" -> operation,
    "reason" -> reason
  )
}