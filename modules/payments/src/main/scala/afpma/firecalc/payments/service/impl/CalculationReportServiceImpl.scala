/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import java.io.File
import java.nio.file.Files

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.others.sunits.SUnits.given

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.v5.Firebox_V4

import afpma.firecalc.engine.alg.en15544.RemoteFireboxData
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.api.WireConversions
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544

import afpma.firecalc.payments.service.CalculationReportError
import afpma.firecalc.payments.service.CalculationReportService

import afpma.firecalc.reports.typst.TypstReportFactory_15544_Strict

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all.*

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import afpma.firecalc.firebox_calc_api.*
import io.github.fatihcatalkaya.javatypst.JavaTypst
import io.taig.babel.Locale

class CalculationReportServiceImpl(
    calcClient: FireboxCalcClient[IO],
    locale    : Locale
)                                 (using ExecutionContext)
    extends CalculationReportService:

    private type StrictRemoteData = RemoteFireboxData[
        FireboxPipe_Module_15544.FullDescr,
        CombustionAirPipe_Module_15544.FullDescr
    ]

    override def generateRemotePdf(
        yamlContent: String,
        asDraft    : Boolean
    ): IO[Either[CalculationReportError, File]] =
        (for
            fc         <- EitherT               (parseYaml(yamlContent)                     )
            loader     <- EitherT               (createLoader(fc)                           )
            request    <- EitherT.fromEither[IO](constructRequest(loader, fc)               )
            response   <- EitherT               (callCalculation(request)                   )
            remoteData <- EitherT               (convertResponse(response)                  )
            remoteApp  <- EitherT               (buildRemoteApplication(loader, remoteData) )
            pdfFile    <- EitherT               (generatePdfFile(loader, remoteApp, asDraft))
        yield pdfFile).value

    private def parseYaml(yamlContent: String): IO[Either[CalculationReportError, FireCalcYAML]] =
        IO.delay {
            FireCalcYAMLMigrations.decodeAndMigrateTry(yamlContent) match
                case Success(fc) => Right(fc)
                case Failure(e)  =>
                    Left(
                        CalculationReportError.YamlParsingError(
                            s"Failed to decode YAML: ${errorMessage(e)}",
                            Some(e)
                        )
                    )
        }

    private def createLoader(fc: FireCalcYAML): IO[Either[CalculationReportError, FireCalcYAML_Loader]] =
        IO.delay(FireCalcYAML_Loader(fc))
            .attempt
            .map(
                _.leftMap(error =>
                    CalculationReportError.CalculationError(
                        s"Failed to load calculation project: ${errorMessage(error)}",
                        Some(error)
                    )
                )
            )

    private def constructRequest(
        loader: FireCalcYAML_Loader,
        fc    : FireCalcYAML
    ): Either[CalculationReportError, FireboxCalcRequest] =
        val firebox = fc.firebox
        val kind    = fireboxToWireKind(firebox)

        if !isAustrianFirebox(kind) then
            Left(
                CalculationReportError.CalculationError(
                    s"Firebox kind $kind is not Austrian; expected Ecolabeled or Door15aCatalog"
                )
            )
        else
            sizingParams(loader).map(params =>
                val airIntakeShape: Option[WirePipeShape] = None

                val operatingEnvelope = Some(
                    WireOperatingEnvelope            (
                        altitude             = WireConversions.qtyDToWireQty[Meter](fc.local_conditions.altitude),
                        temperatureOverrides = None
                    )
                )

                FireboxCalcRequest        (
                    apiVersion         = ApiVersion.v1,
                    kind               = kind,
                    firebox            = firebox,
                    params             = params,
                    airIntakePipeShape = airIntakeShape,
                    locale             = fc.locale.language.value,
                    standard           = WireStandardVariant.Strict,
                    operatingEnvelope  = operatingEnvelope
                )
            )

    /**
     * The EN 15544 sizing params read off the strict application, so the server
     * can build its ConstraintContext: without `m_B` and `n_min` the
     * m_B-dependent constraints come back NotEvaluated(MissingMB).
     *
     * Construction itself can throw (e.g. the firebox-engine registry
     * rejecting an unregistered firebox) and the algebra can come back
     * `Invalid`; both mean the application is absent here, and the request
     * proceeds with absent params — the report then fails later at
     * application build (existing behavior).
     *
     * One case does NOT proceed: an application that built fine whose `m_B`
     * throws. That ISE has exactly one cause — the project has no sizing
     * input (`sizing_method: MaxLoad` with a null `maximum_load`, or its
     * NominalHeatOutput twin) — and sending it to the server only yields
     * MissingMB verdicts that crash PDF generation when the lazy `m_B` is
     * forced. Name the failure early, before the client seam.
     */
    private def sizingParams(loader: FireCalcYAML_Loader): Either[CalculationReportError, Standard15544Params] =
        val appOpt = Try(loader.make_en15544_Strict_Application).toOption.flatMap(_.toOption)

        appOpt match
            case None      =>
                // No usable application: proceed with absent params; the
                // report fails later at application build (existing behavior).
                Right(Standard15544Params(m_B = None, n_min = None))
            case Some(app) =>
                Try(app.m_B) match
                    case Failure(_)  =>
                        Left(
                            CalculationReportError.CalculationError(
                                "Project has no sizing input: set a maximum load (m_B) or a nominal heat output (P_n) to generate a report"
                            )
                        )
                    case Success(mb) =>
                        Right(
                            Standard15544Params  (
                                m_B   = Some(WireConversions.qtyDToWireQty[Kilogram](mb)),
                                n_min = Some(WireConversions.qtyDToWireQty[Percent](app.n_min))
                            )
                        )

    private def callCalculation(
        request: FireboxCalcRequest
    ): IO[Either[CalculationReportError, FireboxCalcResponse]] =
        calcClient.calculate(request).attempt.map {
            case Right(result) => result.leftMap(calculationFailure)
            case Left(error)   =>
                Left(
                    CalculationReportError.CalculationError(
                        s"Remote calculation failed: ${errorMessage(error)}",
                        Some(error)
                    )
                )
        }

    private def calculationFailure(failure: CalculationFailure): CalculationReportError.CalculationError =
        failure match
            case CalculationFailure.TransportError(cause)                               =>
                CalculationReportError.CalculationError(
                    s"Remote calculation failed: Transport error: ${errorMessage(cause)}",
                    Some(cause)
                )
            case protocol @ CalculationFailure.ProtocolError(status, error, message, _) =>
                // The canonical pre-check predicate (shared with the UI prefetch
                // consumer so the two surfaces cannot drift): a §7 calculation_failed
                // 422 with details names the server's verdict items in the reason.
                val prechecks = protocol.preCheckDetails.fold("")(items => s": ${items.map(_.message).mkString(", ")}")
                CalculationReportError.CalculationError(
                    s"Remote calculation failed: Protocol error: HTTP $status ($error) - $message$prechecks"
                )
            case CalculationFailure.DecodeError(cause, _)                               =>
                CalculationReportError.CalculationError(
                    s"Remote calculation failed: Decode error: ${errorMessage(cause)}",
                    Some(cause)
                )

    private def convertResponse(
        response: FireboxCalcResponse
    ): IO[Either[CalculationReportError, StrictRemoteData]] =
        IO.delay {
            val fireboxContribution             =
                WireConversions.fromWireValidatedFireboxContribution[FireboxPipe_Module_15544.FullDescr](
                    response.fireboxContribution
                )(using
                    WireConversions.fromWireFireboxPipeFullDescr,
                    locale
                )
            val combustionAirContribution       =
                WireConversions
                    .fromWireValidatedFireboxContribution[CombustionAirPipe_Module_15544.FullDescr](
                        response.combustionAirContribution
                    )(using
                        WireConversions.fromWireCombustionAirPipeFullDescr,
                        locale
                    )

            RemoteFireboxData[
                FireboxPipe_Module_15544.FullDescr,
                CombustionAirPipe_Module_15544.FullDescr
            ](
                fireboxContribution       = fireboxContribution,
                combustionAirContribution = combustionAirContribution,
                constraints               = response.constraints,
                reportData                = response.reportData
            )
        }.attempt
            .map(
                _.leftMap(error =>
                    CalculationReportError.CalculationError(
                        s"Failed to convert remote calculation response: ${errorMessage(error)}",
                        Some(error)
                    )
                )
            )

    private def buildRemoteApplication(
        loader    : FireCalcYAML_Loader,
        remoteData: StrictRemoteData
    ): IO[Either[CalculationReportError, EN15544_Strict_Application]] =
        IO.delay(loader.make_en15544_Strict_Application(remoteData = Some(remoteData))).attempt.map {
            case Left(error)       =>
                Left(
                    CalculationReportError.CalculationError(
                        s"Failed to build remote calculation application: ${errorMessage(error)}",
                        Some(error)
                    )
                )
            case Right(validation) =>
                validation.toEither.leftMap(errors =>
                    CalculationReportError.CalculationError(
                        s"Invalid remote calculation application: ${errors.toList.mkString(", ")}"
                    )
                )
        }

    private def generatePdfFile(
        loader   : FireCalcYAML_Loader,
        remoteApp: EN15544_Strict_Application,
        asDraft  : Boolean
    ): IO[Either[CalculationReportError, File]] =
        given Locale = locale

        IO.blocking {
            val typstFactory =
                new TypstReportFactory_15544_Strict                 (
                    isDraft                  = asDraft,
                    checkPressureReq13384    = false,
                    checkTemperatureReq13384 = false
                ):
                    override val en15544_app: EN15544_Strict_Application = remoteApp
                    override val stove_proj_15544_strict = loader.stoveProjectDescr_EN15544_Strict
                    override val atOperatingPoint        = remoteApp.primary.asInstanceOf[en15544_app.AtOperatingPoint]

            val typstContent = typstFactory.build()
            val pdfBytes     = JavaTypst.render(typstContent)
            val pdfPath      = Files.createTempFile("firecalc-report-", ".pdf")
            Files.write(pdfPath, pdfBytes)
            val pdfFile = pdfPath.toFile
            pdfFile.deleteOnExit()
            pdfFile
        }.attempt
            .map(
                _.leftMap(error =>
                    CalculationReportError.PdfGenerationError(
                        s"Failed to generate PDF: ${errorMessage(error)}",
                        Some(error)
                    )
                )
            )

    private def errorMessage(error: Throwable): String =
        Option(error.getMessage).getOrElse(error.getClass.getSimpleName)

    private def fireboxToWireKind(firebox: Firebox_V4): WireFireboxKind =
        firebox match
            case _: Firebox_V4.Traditional            => WireFireboxKind.Traditional
            case _: Firebox_V4.Ecolabeled             => WireFireboxKind.Ecolabeled
            case _: Firebox_V4.AFPMA_PRSE             => WireFireboxKind.AfpmaPrse
            case _: Firebox_V4.SingleTested           => WireFireboxKind.SingleTested
            case _: Firebox_V4.Door15aFirebox_Catalog => WireFireboxKind.Door15aCatalog

    private def isAustrianFirebox(kind: WireFireboxKind): Boolean =
        kind == WireFireboxKind.Ecolabeled || kind == WireFireboxKind.Door15aCatalog
