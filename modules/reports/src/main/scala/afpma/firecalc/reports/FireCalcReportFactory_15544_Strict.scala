/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports

// import afpma.firecalc.engine.models // scalafix:ok
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr
import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application

import afpma.firecalc.reports.FireCalcReportFactory_15544_Strict.Op
import afpma.firecalc.reports.typst.TypstReportFactory_15544_Strict

import cats.data.Validated.Invalid
import cats.data.Validated.Valid

import scala.util.Failure
import scala.util.Success

import io.github.fatihcatalkaya.javatypst.JavaTypst
import io.taig.babel.Locale

trait FireCalcReportFactory_15544_Strict:

    def loadYAMLFile                  (yamlFile  : File  ): Op[FireCalcReportFactory_15544_Strict]
    def loadYAMLString                (yamlString: String): Op[FireCalcReportFactory_15544_Strict]
    def loadAndValidateFireCalcProject(
        fcProj: StoveProjectDescr_15544_Strict_Alg
    ): Op[FireCalcReportFactory_15544_Strict]

    def makeTypstString(isDraft: Boolean, checkPressureReq13384: Boolean): Op[String]
    def makePDFBuffer  (isDraft: Boolean, checkPressureReq13384: Boolean): Op[Array[Byte]]
    def makePDF        (isDraft: Boolean, checkPressureReq13384: Boolean): Op[File]

object FireCalcReportFactory_15544_Strict:

    type Op[X] = Either[FireCalcReportError, X]

    def init()(using Locale): FireCalcReportFactory_15544_Strict =
        new FireCalcReportFactory_15544_Strict_Impl {}

    private trait FireCalcReportFactory_15544_Strict_Impl extends FireCalcReportFactory_15544_Strict:
        self =>

        given Locale = compiletime.deferred
        protected var appl     : Option[EN15544_Strict_Application]         = None
        protected var fcProj   : Option[StoveProjectDescr_15544_Strict_Alg] = None
        protected var typString: Option[String]                             = None

        override def loadYAMLFile(
            yamlFile: File
        ): Op[FireCalcReportFactory_15544_Strict] =
            try
                val path = os.Path(yamlFile)
                val y    = os.read(path)
                loadYAMLString(y)
            catch
                case e: Exception =>
                    Left(
                        YAMLFileReadException(
                            filePath = yamlFile.getAbsolutePath,
                            reason   = Option(e.getMessage).getOrElse("Unknown error"),
                            cause    = Some(e)
                        )
                    )

        override def loadYAMLString(
            yamlString: String
        ): Op[FireCalcReportFactory_15544_Strict] =
            import afpma.firecalc.dto.FireCalcYAMLMigrations
            // Use migration-aware decoder to handle V1→V2 upgrades automatically
            FireCalcYAMLMigrations.decodeAndMigrateTry(yamlString) match
                case Success(fc) =>
                    val stoveProj = StoveProjectDescr.makeFor_EN15544_Strict(fc)
                    loadAndValidateFireCalcProject(stoveProj)
                case Failure(e)  =>
                    Left(
                        YAMLDecodingException(
                            yamlLength = yamlString.length,
                            reason     = Option(e.getMessage).getOrElse("Unknown error"),
                            cause      = Some(e)
                        )
                    )

        override def loadAndValidateFireCalcProject(fireCalcProj: StoveProjectDescr_15544_Strict_Alg) =
            val countryCode = fireCalcProj.project.country
            fireCalcProj.en15544_Alg match
                case Valid(en15544_appl) =>
                    en15544_appl.validateResultsExceptEmissionsValues(countryCode) match
                        case Invalid(e) =>
                            Left(EN15544ValidationException(e.toList.map(_.toString)))
                        case Valid(_)   =>
                            Right(
                                new FireCalcReportFactory_15544_Strict_Impl {
                                    appl      = Some(en15544_appl)
                                    fcProj    = Some(fireCalcProj)
                                    typString = None
                                }
                            )
                case Invalid(e)          =>
                    Left(EN15544ValidationException(e.toList.map(_.toString)))

        private def compileAndRenderTypString(isDraft: Boolean, checkPressureReq13384: Boolean): Op[Unit] =
            (appl, fcProj) match
                case (Some(strict_appl), Some(fcProj)) =>
                    val typstReportFactory = new TypstReportFactory_15544_Strict(isDraft, checkPressureReq13384) {
                        override val en15544_app: EN15544_Application = strict_appl
                        override val stove_proj_15544_strict = fcProj
                        override val atParams                = en15544_app.primary
                    }
                    // update local state
                    typString = Some(typstReportFactory.build())
                    Right(())
                case (None, None                     ) => Left(InternalStateException("appl and fcProj"))
                case (None, _                        ) => Left(InternalStateException("appl")           )
                case (_, None                        ) => Left(InternalStateException("fcProj")         )

        override def makeTypstString(isDraft: Boolean, checkPressureReq13384: Boolean): Op[String] =
            for
                _      <- compileAndRenderTypString(isDraft, checkPressureReq13384)
                typStr <-
                    if typString.isDefined then Right(typString.get) else Left(InternalStateException("typString"))
            yield typStr

        override def makePDFBuffer(isDraft: Boolean, checkPressureReq13384: Boolean): Op[Array[Byte]] =
            for
                _        <- compileAndRenderTypString(isDraft, checkPressureReq13384)
                typStr   <-
                    if typString.isDefined then Right(typString.get) else Left(InternalStateException("typString"))
                pdfBytes <-
                    try Right(JavaTypst.render(typStr))
                    catch
                        case e: Exception =>
                            Left(
                                TypstRenderingException(
                                    typstLength = typStr.length,
                                    reason      = Option(e.getMessage).getOrElse("Unknown error"),
                                    cause       = Some(e)
                                )
                            )
            yield pdfBytes

        override def makePDF(isDraft: Boolean, checkPressureReq13384: Boolean): Op[File] =
            makePDFBuffer(isDraft, checkPressureReq13384).flatMap: pdfBytes =>
                try
                    val tempFile = Files.createTempFile("firecalc-report", ".pdf").toFile
                    tempFile.deleteOnExit() // Clean up when JVM exits

                    val fos = new FileOutputStream(tempFile)
                    try {
                        fos.write(pdfBytes)
                        Right    (tempFile)
                    } finally {
                        fos.close()
                    }
                catch
                    case e: Exception =>
                        Left(
                            PDFFileCreationException(
                                operation = "file creation and write",
                                reason    = Option(e.getMessage).getOrElse("Unknown error"),
                                cause     = Some(e)
                            )
                        )

    end FireCalcReportFactory_15544_Strict_Impl
