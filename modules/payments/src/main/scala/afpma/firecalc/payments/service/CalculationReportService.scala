/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.payments.service

import java.io.File

import cats.effect.IO

/** Generates PDF reports using the standalone calculation backend. */
trait CalculationReportService:

    /**
     * Generates a PDF report for an Austrian firebox project using the
     * remote calculation backend.
     *
     * @param yamlContent the FireCalcYAML content
     * @param asDraft whether the report should be marked as draft
     * @return IO[Either[CalculationReportError, File]] the generated PDF file
     */
    def generateRemotePdf(
        yamlContent: String,
        asDraft    : Boolean
    ): IO[Either[CalculationReportError, File]]

/** Errors that can occur during remote calculation report generation. */
sealed trait CalculationReportError

object CalculationReportError:

    final case class YamlParsingError(reason: String, cause: Option[Throwable] = None) extends CalculationReportError

    final case class CalculationError(reason: String, cause: Option[Throwable] = None) extends CalculationReportError

    final case class PdfGenerationError(reason: String, cause: Option[Throwable] = None) extends CalculationReportError
