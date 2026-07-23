/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.labo

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.NbOfFlows

import afpma.firecalc.engine.api.v0_2024_10_labo.StoveProjectDescr_15544_Labo_Alg
import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Application
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error
import afpma.firecalc.engine.testutil.SnapshotAssert
import afpma.firecalc.engine.utils.*

import cats.data.*
import cats.syntax.all.*

import coulomb.*

import io.taig.babel.Languages
import io.taig.babel.Locale
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

trait ConfigurationRunners_Labo extends AnyFreeSpec with Matchers {

    given Locale = Locale(Languages.Fr)

    case class SimplePreview(
        ref              : String,
        sectionType      : String,
        sectionId        : String,
        sectionName      : String,
        qtyName          : String,
        qtyValue         : String,
        qtyUnit          : String,
        dhi              : Option[Length],
        dhe              : Option[Length],
        asd              : Option[AirSpaceDetailed],
        tamb             : Option[TCelsius],
        lambda           : Option[WattsPerMeterKelvin],
        thermalResistance: Option[SquareMeterKelvinPerWatt],
        n_flows          : Option[NbOfFlows],
        dynamicPressure  : Option[Pressure],
        zeta             : Option[ζ],
        roughness        : Option[Roughness],
        length           : Option[Length],
        elev_gain        : Option[Length],
        angle            : Option[Angle],
        innerShape       : Option[PipeShape],
        pipeLoc          : Option[PipeLocation]
    ) {
        val showHeaders = List(
            "ref",
            "section_type",
            "section_id",
            "section_name",
            "qty_name",
            "qty_value",
            "qty_unit",
            "length",
            "geom",
            "h",
            "α",
            "//",
            "R th.",
            "air space",
            "area",
            "t amb.",
            "ζ",
            "kf"
        )

        val showValues: List[String] =
            List(
                ref,
                sectionType,
                sectionId,
                sectionName,
                qtyName,
                qtyValue,
                qtyUnit,
                length.fold("-")                                              (l => if (l == 0.cm) "-" else l.showP),
                innerShape.map(_.show).getOrElse                              ("-"                                 ),
                elev_gain.map(h => if (h == 0.cm) "-" else h.showP).getOrElse ("-"                                 ),
                angle.map(a => if (a == 0.degrees) "-" else a.showP).getOrElse("-"                                 ),
                n_flows.map(_.show).getOrElse                                 ("-"                                 ),
                thermalResistance.map(_.showP).getOrElse                      ("-"                                 ),
                asd.map(_.show).getOrElse                                     ("-"                                 ),
                pipeLoc.map(_.show).getOrElse                                 ("-"                                 ),
                tamb.map(_.show).getOrElse                                    ("-"                                 ),
                zeta.map(_.showP).getOrElse                                   ("-"                                 ),
                roughness.map(_.show).getOrElse                               ("-"                                 )
            )

        def showHeaderAsQuotedCSVRow(separator: String): String = showHeaders.mkString("\"", s"\"$separator\"", "\"")
        def showValuesAsQuotedCSVRow(separator: String): String = showValues.mkString("\"", s"\"$separator\"", "\"")

    }

    object SimplePreview:

        def forQtyWhenEmpty(ref: String, qtyName: String, qtyUnit: String): SimplePreview =
            forQty(ref, qtyName, "", qtyUnit)

        def forQty(ref: String, qtyName: String, qtyValue: String, qtyUnit: String): SimplePreview = SimplePreview(
            ref         = ref,
            sectionType = "",
            sectionId   = "",
            sectionName = "",
            qtyName     = qtyName,
            qtyValue    = qtyValue,
            qtyUnit     = qtyUnit,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None
        )

        def fromPreview(ref: String, p: Preview)(selectQty: Preview => (String, String, String)): SimplePreview =
            val qtyTuple = selectQty(p)
            val (qtyName, qtyValue, qtyUnit) = qtyTuple
            SimplePreview(
                ref,
                p.sectionType,
                p.sectionId,
                p.sectionName,
                qtyName,
                qtyValue,
                qtyUnit,
                p.dhi,
                p.dhe,
                p.asd,
                p.tamb,
                p.lambda,
                p.thermalResistance,
                p.n_flows,
                p.dynamicPressure,
                p.zeta,
                p.roughness,
                p.length,
                p.elev_gain,
                p.angle,
                p.innerShape,
                p.pipeLoc
            )

    extension (pipeResults: Vector[PipeResult])

        def asSectionResultsMerged: Vector[PipeSectionResult[?]] =
            pipeResults
                .map:
                    case pres: PipeResult.WithSections    => pres.elements
                    case pres: PipeResult.WithoutSections => Vector.empty
                .flatten

    extension (psResult: PipeSectionResult[?])
        def toPreview = Preview.fromPipeSectionResult(psResult)
        def toSimplePreview(ref: String, selectQty: Preview => (String, String, String)): SimplePreview =
            SimplePreview.fromPreview(ref, psResult.toPreview)(selectQty)

    extension (vecsec: Vector[PipeSectionResult[?]])

        private def getSectionNameIndex(searchFor: String): Option[(PipeSectionResult[?], Int)] =
            val sectionsFound = vecsec
                .mapWithIndex((x, i) => (x, i))
                .filter(_._1.section_name.contains(searchFor))
            val indices       = sectionsFound.map(_._2)
            indices.length match
                case 0 => None // throw new Exception(s"could not find any section name containing '$searchFor'")
                case 1 => Some(sectionsFound.head)
                case n =>
                    None // throw new Exception(s"found more than one section name containing '$searchFor' = [ ${sectionsFound.map(_._1).mkString("'", "', '", "'")} ]")

        private def findBySectionName(searchFor: String): Option[PipeSectionResult[?]] =
            vecsec.find(_.section_name.contains(searchFor))

        def pressureDiffAtStartOf(searchFor: String): SimplePreview =
            getSectionNameIndex(searchFor) match
                case None             => SimplePreview.forQtyWhenEmpty(ref = searchFor, qtyName = "pressure_diff", qtyUnit = "Pa")
                case Some((sec, idx)) =>
                    val press = vecsec
                        .slice(0, idx)
                        .flatMap(_.`ph-(pR+pu)`.toOption)
                        .sum
                    sec.toSimplePreview(ref = searchFor, p => ("pressure_diff", "%.2f".format(press), "Pa"))

        def gasTemperatureAtStartOf(searchFor: String): SimplePreview =
            findBySectionName(searchFor)
                .map: x =>
                    x.toSimplePreview(ref = searchFor, p => ("gas_temp", "%.2f".format(p.gas_temp_start.value), "°C"))
                .getOrElse:
                    SimplePreview.forQtyWhenEmpty(ref = searchFor, qtyName = "gas_temp", qtyUnit = "°C")

    private def buildCSVForMCEComparisonWithLabData(
        ex      : StoveProjectDescr_15544_Labo_Alg,
        _en15544: EN15544_V_2023_Common_Application,
        ap      : _en15544.AtParams
    ): String =

        import ex.given_Locale
        given _en15544.Params_15544 = ap.params
        given Option[LoadQty]       = Some(ap.params._2)

        // given LocalRegulations = ex.localRegulations

        // val showAsTableInstances = new afpma.firecalc.engine.ops.ShowAsTableInstances
        // val showAsTableInstances_EN15544 = new afpma.firecalc.engine.ops.en15544.ShowAsTableInstances_15544
        // val showAsTableInstances_EN13384 = new afpma.firecalc.engine.ops.en13384.ShowAsTableInstances_13384

        // println(_en15544.inputs.showAsCliTable)
        // println(_en15544.citedConstraints.showAsCliTable)
        // println(inputs.pipes.showAsCliTable)

        // println(_en15544.inputs.design.firebox.showAsCliTable)

        _en15544.airIntake_PipeResult.toValidatedNel.getOrThrow
        ap.combustionAir_PipeResult.getOrThrow
        ap.firebox_PipeResult.getOrThrow
        ap.postFireboxPipeResults.getOrThrow

        val pipesResult_15544 = ap.outputs.pipesResult_15544.accumulateErrors.getOrThrow
        // println(pipesResult_15544.showAsCliTable)

        val vecsec: Vector[PipeSectionResult[?]] = (
            Vector(
                pipesResult_15544.airIntake,
                pipesResult_15544.combustionAir,
                pipesResult_15544.firebox
            ) ++ pipesResult_15544.postFirebox.map(_._2)
        ).asSectionResultsMerged

        val sb = new StringBuilder

        sb.append("\n\n\n")

        // en tête du fichier CSV
        sb.append(vecsec.pressureDiffAtStartOf("P09").showHeaderAsQuotedCSVRow(";")   ).append("\n")
        // valeurs :
        // débits massiques
        sb.append(
            SimplePreview
                .forQty(
                    "input_air_mass_rate",
                    "input_air_mass_rate",
                    _en15544.m_L.map(d => "%.5f".format(d.getOrThrow.value)).getOrElse(""),
                    "kg/s"
                )
                .showValuesAsQuotedCSVRow(";")
        ).append ("\n"                                                                )
        sb.append(
            SimplePreview
                .forQty(
                    "flue_gas_mass_rate",
                    "flue_gas_mass_rate",
                    _en15544.m_G.map(d => "%.5f".format(d.getOrThrow.value)).getOrElse(""),
                    "kg/s"
                )
                .showValuesAsQuotedCSVRow(";")
        ).append ("\n"                                                                )
        // différences de pression
        sb.append(vecsec.pressureDiffAtStartOf("P01").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P02").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P03").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P04").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P05").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P06").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P07").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P08").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P09").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P10").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P11").showValuesAsQuotedCSVRow(";")   ).append("\n")
        sb.append(vecsec.pressureDiffAtStartOf("P12").showValuesAsQuotedCSVRow(";")   ).append("\n")
        // températures
        sb.append(vecsec.gasTemperatureAtStartOf("TC01").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC02").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC03").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC04").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC05").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC06").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC07").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC08").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC09").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC10").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC11").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC12").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC13").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC14").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC15").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC16").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC17").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC18").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC19").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC20").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC21").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC22").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC23").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC24").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC25").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC26").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC27").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC28").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC29").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC30").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC31").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append(vecsec.gasTemperatureAtStartOf("TC32").showValuesAsQuotedCSVRow(";")).append("\n")
        sb.append("\n\n\n"                                                            )

        // vecsec.foreach: x =>
        //     println(s"""${x.section_name} | ${x.gas_temp_start.showP}""")

        // println(_en15544.pressureRequirement_EN15544.toOption.map(_.showAsCliTable).getOrElse("ERROR (pressure requirement)"))
        // println(_en15544.t_chimney_wall_top.showAsCliTable)

        // println(_en15544.efficiencies_values.showAsCliTable)
        // println(_en15544.flue_gas_triple_of_variates.toOption.map(_.showAsCliTable).getOrElse("ERROR (flue gas triple of variates)"))
        // println(_en15544.estimated_output_temperatures.showAsCliTable)

        // _en15544.pressureRequirements_EN13384 match
        //     case Validated.Valid(a) =>
        //         println(a.showAsCliTable)
        //     case Validated.Invalid(nel) =>
        //         println("ERROR (pressure requirements EN13384)")
        //         nel.toList.map(_.show).foreach(println)
        //         fail()

        sb.toString()

    def run_15544_mce_for_lab_comparison(ex_15544_labo: StoveProjectDescr_15544_Labo_Alg, snapshotName: String) =
        import scala.language.adhocExtensions

        val config = ex_15544_labo
        val out    = config.en15544_Alg.map: mce_labo =>
            buildCSVForMCEComparisonWithLabData(ex_15544_labo, mce_labo, mce_labo.atDraftMax_LoadNominal)
        out.fold(
            nel => nel.toList.foreach(e => fail(e.show)),
            csv =>
                SnapshotAssert.assertMatches(
                    csv,
                    java.nio.file.Paths.get(
                        getClass.getClassLoader
                            .getResource(s"snapshots/ConfigurationRunners_Labo/$snapshotName")
                            .toURI
                    )
                )
        )
    end run_15544_mce_for_lab_comparison

}
