/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports

import afpma.firecalc.engine.models // scalafix:ok
import java.io.File

import FireCalcReportFactory_15544_Strict.Op
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.en15544.std.Inputs_EN15544_Strict
import afpma.firecalc.dto.FireCalcYAML
import scala.util.Failure
import scala.util.Success
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr
import cats.data.Validated.Valid
import cats.data.Validated.Invalid
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.reports.typst.TypstReportFactory_15544
import afpma.firecalc.engine.models.Pipes_EN15544_Strict
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.LoadQty
import io.taig.babel.Locale
import io.github.fatihcatalkaya.javatypst.JavaTypst
import java.nio.file.Files
import java.io.FileOutputStream

trait FireCalcReportFactory_15544_Strict:

    def loadYAMLFile(yamlFile: File): Op[FireCalcReportFactory_15544_Strict]
    def loadYAMLString(yamlString: String): Op[FireCalcReportFactory_15544_Strict]
    def loadFireCalcProject(fcProj: StoveProjectDescr_EN15544_Strict_Alg): Op[FireCalcReportFactory_15544_Strict]

    def makeTypstString(isDraft: Boolean): Op[String]
    def makePDFBuffer(isDraft: Boolean): Op[Array[Byte]]
    def makePDF(isDraft: Boolean): Op[File]

object FireCalcReportFactory_15544_Strict:

    type Op[X] = Either[String, X]

    def init()(using Locale): FireCalcReportFactory_15544_Strict =
        new FireCalcReportFactory_15544_Strict_Impl {}

    private trait FireCalcReportFactory_15544_Strict_Impl
        extends FireCalcReportFactory_15544_Strict:
        self =>
        
        given Locale = compiletime.deferred
        protected var appl: Option[EN15544_Strict_Application] = None
        protected var fcProj: Option[StoveProjectDescr_EN15544_Strict_Alg] = None
        protected var typString: Option[String] = None
        protected var lastIsDraft: Option[Boolean] = None

        override def loadYAMLFile(
            yamlFile: File
        ): Op[FireCalcReportFactory_15544_Strict] =
            val path = os.Path(yamlFile)
            val y    = os.read(path)
            loadYAMLString(y)

        override def loadYAMLString(
            yamlString: String
        ): Op[FireCalcReportFactory_15544_Strict] =
            FireCalcYAML.decodeFromYaml(yamlString) match
                case Success(fc) =>
                    val stoveProj = StoveProjectDescr.makeFor_EN15544_Strict(fc)
                    loadFireCalcProject(stoveProj)
                case Failure(e) =>
                    Left(e.getMessage())
        
        override def loadFireCalcProject(fireCalcProj: StoveProjectDescr_EN15544_Strict_Alg) = 
            fireCalcProj.en15544_Alg match
                case Valid(en15544_appl) =>
                    Right(
                        new FireCalcReportFactory_15544_Strict_Impl {
                            appl        = Some(en15544_appl)
                            fcProj      = Some(fireCalcProj)
                            typString   = None
                            lastIsDraft = None
                        }
                    )
                case Invalid(e) => 
                    Left(e.toList.mkString("\n"))

        private def compileAndRenderTypString(isDraft: Boolean): Op[Unit] =
            (appl, fcProj) match
                case (Some(strict_appl), Some(fcProj)) =>
                    val typstReportFactory = new TypstReportFactory_15544[Pipes_EN15544_Strict](strict_appl, isDraft) {
                        override val stove_proj_15544_strict = fcProj
                        given params: en15544_app.Params_15544 = 
                            given pReq: DraftCondition = DraftCondition.DraftMinOrPositivePressureMax
                            import LoadQty.givens.nominal
                            (pReq, nominal)
                    }
                    // update local state
                    typString = Some(typstReportFactory.build())
                    Right(())
                case (None, None) => Left(s"expected 'appl' and 'fcProj' variables to be defined")
                case (None, _) => Left(s"expected 'appl' variable to be defined")
                case (_, None) => Left(s"expected 'fcProj' variable to be defined")
            


        override def makeTypstString(isDraft: Boolean): Op[String] =
            for 
                _      <- compileAndRenderTypString(isDraft)
                typStr <- Either.cond(typString.isDefined, typString.get, s"expected 'typString' to be defined")
            yield
                typStr

        override def makePDFBuffer(isDraft: Boolean): Op[Array[Byte]] =
            for 
                _      <- compileAndRenderTypString(isDraft)
                typStr <- Either.cond(typString.isDefined, typString.get, s"expected 'typString' to be defined")
            yield
                JavaTypst.render(typStr)

        override def makePDF(isDraft: Boolean): Op[File] =
            makePDFBuffer(isDraft).map: pdfBytes =>
                val tempFile = Files.createTempFile("firecalc-report", ".pdf").toFile
                tempFile.deleteOnExit() // Clean up when JVM exits

                val fos = new FileOutputStream(tempFile)
                try {
                    fos.write(pdfBytes)
                    tempFile
                } finally {
                    fos.close()
                }

    end FireCalcReportFactory_15544_Strict_Impl
