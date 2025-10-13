/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports.typst

import io.taig.babel.Locales
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Application_Alg
import afpma.firecalc.engine.models.en15544.std.Inputs
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models.Pipes_EN15544_Strict
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.LoadQty
import io.taig.babel.Locale
import scala.io.Source
import io.github.fatihcatalkaya.javatypst.JavaTypst
import afpma.firecalc.reports.FireCalcReportFactory_15544_Strict

object GenerateSampleTyp:

    def main(args: Array[String]): Unit = 

        val wd = os.pwd
        
        given Locale = Locales.fr

        FireCalcReportFactory_15544_Strict
            .init()
            .loadFireCalcProject(CasType_15544_C3)
            .fold(
                err => 
                    System.err.println(err)
                    System.exit(-1),

                reportFactory =>

                    // First generate the Typst string and write it to file for debugging
                    reportFactory.makeTypstString(isDraft = true) match
                        case Left(err) => 
                            System.err.println(s"Error generating Typst string: $err")
                            System.exit(-1)
                        case Right(typstString) =>
                            println("Writing intermediate .typ file...")
                            val typOutPath = wd / "modules" / "reports" / "report-example.typ"
                            os.write.over(
                                typOutPath,
                                typstString
                            )
                            println(s"Typst file written: ${typOutPath.toString}")

                    // Then generate the PDF
                    reportFactory.makePDFBuffer(isDraft = true) match
                    
                        case Left(err) => 
                            System.err.println(s"Error generating PDF: $err")
                            System.exit(-1)
                    
                        case Right(pdfBuffer) =>
                            println("Generating PDF...")
                            val outPath = wd / "modules" / "reports" / "report-example.pdf"
                            os.write.over(
                                outPath,
                                pdfBuffer
                            )
                            println(s"PDF Generation OK : ${outPath.toString}")
            )
