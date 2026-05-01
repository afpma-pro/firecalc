/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports.typst

import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544

import afpma.firecalc.reports.FireCalcReportFactory_15544_Strict

import io.taig.babel.Locale
import io.taig.babel.Locales

/**
 * Fast iteration tool for PDF/Typst report generation
 *
 * Usage:
 * - Run: sbt "reports/runMain afpma.firecalc.reports.typst.GenerateSampleTyp"
 * - Auto-reload: sbt "~reports/runMain afpma.firecalc.reports.typst.GenerateSampleTyp"
 */
object GenerateSampleTyp:

    def main(args: Array[String]): Unit =

        val wd = os.pwd

        given Locale = Locales.fr

        FireCalcReportFactory_15544_Strict
            .init()
            .loadAndValidateFireCalcProject(ExampleProject_15544)
            .fold(
                err =>
                    System.err.println(err)
                    System.exit       (-1 )
                ,
                reportFactory =>
                    // First generate the Typst string and write it to file for debugging
                    reportFactory.makeTypstString(isDraft = true, checkPressureReq13384 = false) match
                        case Left(err)          =>
                            System.err.println(s"Error generating Typst string: $err")
                            System.exit       (-1                                    )
                        case Right(typstString) =>
                            println("Writing intermediate .typ file...")
                            val typOutPath = wd / "modules" / "reports" / "report-example-project-15544.typ"
                            os.write.over(
                                typOutPath,
                                typstString
                            )
                            println      (s"Typst file written: ${typOutPath.toString}")

                    // Then generate the PDF
                    reportFactory.makePDFBuffer(isDraft = true, checkPressureReq13384 = false) match

                        case Left(err) =>
                            System.err.println(s"Error generating PDF: $err")
                            System.exit       (-1                           )

                        case Right(pdfBuffer) =>
                            println("Generating PDF...")
                            val outPath = wd / "modules" / "reports" / "report-example-project-15544.pdf"
                            os.write.over(
                                outPath,
                                pdfBuffer
                            )
                            println      (s"PDF Generation OK : ${outPath.toString}")
            )
