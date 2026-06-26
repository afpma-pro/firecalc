/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (2026) Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled_V1
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled_V2
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.*
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.DirectionChange.AngleVifDe0A180
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.DirectionChange.CircularArc60
import afpma.firecalc.engine.testutil.SnapshotAssert

import cats.syntax.show.*

import coulomb.*
import coulomb.policy.standard.given

import afpma.firecalc.engine.utils.showAsCliTable
import afpma.firecalc.engine.ops.en15544.ShowAsTableInstances_15544
import io.taig.babel.Locale
import io.taig.babel.Locales
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3_V2
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.reports.typst.TypstReportFactory_15544_Strict
import io.github.fatihcatalkaya.javatypst.JavaTypst

/**
 * Snapshot test: transcribe the combustion-air pipe FullDescr for both
 * ecolabeled firebox versions (V1 with chambre de détente, V2 with direct
 * air-intake shape) into a human-readable table.
 *
 * Fixture values are taken from CasType_15544_C3 (golden fixture).
 *
 * Variable suffixes (from source code):
 *   A  = firebox_width_A                                  (firebox width)
 *   B  = firebox_depth_B                                  (firebox depth)
 *   W  = air_manifold_height_W                            (air manifold height)
 *   S  = air_column_thickness_S                           (air column thickness)
 *   Y  = distance_between_air_injectors_Y                 (injector spacing)
 *   Z  = injector_height_Z                                (injector height)
 *   D1 = inner_wall_thickness_D1                          (inner wall thickness)
 *   Ls = injector_width_side_wall_Ls                      (lateral injector width)
 *   Lr = injector_width_rear_wall_Lr                      (rear injector width)
 *   Lt = injector_width_door_wall_Lt                      (under-door injector width)
 *   E  = width_between_two_air_columns_sides_E / rear_E  (reinforcement bar width)
 */
class EcolabeledFireboxPipeSuite extends AnyFreeSpec with Matchers:

    given Locale = Locales.en
    given sat15544: ShowAsTableInstances_15544 = new ShowAsTableInstances_15544
    import sat15544.given

    import afpma.firecalc.engine.impl.en15544.strict.given_FireboxToCombustionAirPipe_15544_Strict_Ecolabeled

    // ── Shared fixture values (CasType_15544_C3) ──────────────────────────

    private def sharedEcolabeledParams = EcolabeledFixtureParams(
        heatOutputReduced           = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        fireboxDepth                = 54.cm,
        fireboxWidth                = 54.cm,
        fireboxHeight               = 81.3.cm,
        doorOpeningWidth            = 52.cm,
        glassWidth                  = 50.cm,
        glassHeight                 = 40.cm,
        ashPitHeight                = 8.cm,
        airManifoldHeight           = 11.cm,
        fireboxFloorThickness       = 8.cm,
        innerWallThickness          = 6.cm,
        outerWallThickness          = 6.cm,
        airColumnThickness          = 3.5.cm,
        widthBetweenAirColumnsSides = 4.5.cm,
        widthBetweenAirColumnsRear  = 4.5.cm,
        reinforcementOffsetR1       = 4.5.cm,
        reinforcementOffsetR2       = 4.5.cm,
        reinforcementOffsetR3       = 4.5.cm,
        injectorHeight              = 0.8.cm,
        heightFirstRowInjectors     = 10.cm
    )

    private case class EcolabeledFixtureParams(
        heatOutputReduced          : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        fireboxDepth               : QtyD[Meter],
        fireboxWidth               : QtyD[Meter],
        fireboxHeight              : QtyD[Meter],
        doorOpeningWidth           : Length,
        glassWidth                 : Length,
        glassHeight                : Length,
        ashPitHeight               : Length,
        airManifoldHeight          : Length,
        fireboxFloorThickness      : Length,
        innerWallThickness         : Length,
        outerWallThickness         : Length,
        airColumnThickness         : Length,
        widthBetweenAirColumnsSides: Length,
        widthBetweenAirColumnsRear : Length,
        reinforcementOffsetR1      : Length,
        reinforcementOffsetR2      : Length,
        reinforcementOffsetR3      : Length,
        injectorHeight             : Length,
        heightFirstRowInjectors    : Length
    )

    private def makeV1(p: EcolabeledFixtureParams): Ecolabeled_V1 =
        Ecolabeled_V1                             (
            pn_reduced                              = p.heatOutputReduced,
            firebox_depth_B                         = p.fireboxDepth,
            firebox_width_A                         = p.fireboxWidth,
            firebox_height_H                        = p.fireboxHeight,
            door_opening_width                      = p.doorOpeningWidth,
            glass_width                             = p.glassWidth,
            glass_height                            = p.glassHeight,
            ash_pit_height_AF                       = p.ashPitHeight,
            air_manifold_height_W                   = p.airManifoldHeight,
            firebox_floor_thickness                 = p.fireboxFloorThickness,
            inner_wall_thickness_D1                 = p.innerWallThickness,
            outer_wall_thickness_D2                 = p.outerWallThickness,
            air_column_thickness_S                  = p.airColumnThickness,
            width_between_two_air_columns_sides_E   = p.widthBetweenAirColumnsSides,
            width_between_two_air_columns_rear_E    = p.widthBetweenAirColumnsRear,
            reinforcement_bars_offset_in_corners_R1 = p.reinforcementOffsetR1,
            reinforcement_bars_offset_in_corners_R2 = p.reinforcementOffsetR2,
            reinforcement_bars_offset_in_corners_R3 = p.reinforcementOffsetR3,
            injector_height_Z                       = p.injectorHeight,
            height_of_first_row_of_air_injectors_X  = p.heightFirstRowInjectors
        )

    private def makeV2(p: EcolabeledFixtureParams): Ecolabeled_V2 =
        Ecolabeled_V2                             (
            pn_reduced                              = p.heatOutputReduced,
            actual_air_intake_pipe_shape            = PipeShape.Circle(20.cm),
            firebox_depth_B                         = p.fireboxDepth,
            firebox_width_A                         = p.fireboxWidth,
            firebox_height_H                        = p.fireboxHeight,
            door_opening_width                      = p.doorOpeningWidth,
            glass_width                             = p.glassWidth,
            glass_height                            = p.glassHeight,
            ash_pit_height_AF                       = p.ashPitHeight,
            air_manifold_height_W                   = p.airManifoldHeight,
            firebox_floor_thickness                 = p.fireboxFloorThickness,
            inner_wall_thickness_D1                 = p.innerWallThickness,
            outer_wall_thickness_D2                 = p.outerWallThickness,
            air_column_thickness_S                  = p.airColumnThickness,
            width_between_two_air_columns_sides_E   = p.widthBetweenAirColumnsSides,
            width_between_two_air_columns_rear_E    = p.widthBetweenAirColumnsRear,
            reinforcement_bars_offset_in_corners_R1 = p.reinforcementOffsetR1,
            reinforcement_bars_offset_in_corners_R2 = p.reinforcementOffsetR2,
            reinforcement_bars_offset_in_corners_R3 = p.reinforcementOffsetR3,
            injector_height_Z                       = p.injectorHeight,
            height_of_first_row_of_air_injectors_X  = p.heightFirstRowInjectors
        )

    // ── Formula mappings ─────────────────────────────────────────────────
    // Maps element name → (length formula, shape formula) using single-letter
    // suffixes from the source variables (see class doc-comment).

    private case class ElementFormulas(length: String, shape: String)

    // Shared "end_common" formulas (same for V1 and V2)
    // Does NOT include "angle vif 90°" — that angle inherits different shapes per version.
    private val sharedFormulas: Map[String, ElementFormulas] = Map(
        "vers centre chambre de détente"    -> ElementFormulas(
            length = "W / 2.0",
            shape  = "— (inherited)"
        ),
        "vers colonnes d'air"               -> ElementFormulas(
            length = "(2.0 * A / 2.0 + 2.0 * B / 2.0) / 4.0 + D1 + S / 2.0",
            shape  = "2·air_columns_total_width_side_wall + air_columns_total_width_rear_wall  ×  W"
        ),
        "virage au pied des colonnes d'air" -> ElementFormulas(
            length = "—",
            shape  = "— (inherited)"
        ),
        "remontée dans les colonnes d'air"  -> ElementFormulas(
            length = "W / 2.0 + FLOOR_THICKNESS + Y * 2.0",
            shape  = "2·air_columns_total_width_side_wall + air_columns_total_width_rear_wall  ×  S"
        ),
        "virage 90° avant injecteur"        -> ElementFormulas(
            length = "—",
            shape  = "— (inherited)"
        ),
        "injecteurs"                        -> ElementFormulas(
            length = "D1 + S / 2.0",
            shape  = "(8·Ls + 4·Lr + Lt - 12·E)  ×  Z"
        )
    )

    // V1 overrides: start section + angle inherits détente chamber shape
    private def v1Formulas: Map[String, ElementFormulas] =
        sharedFormulas ++ Map                           (
            "-"                            -> ElementFormulas(
                length = "0",
                shape  = "(A - 6) × (B - 6)"
            ),
            "angle vif 90°"                -> ElementFormulas(
                length = "—",
                shape  = "(A - 6) × (B - 6)"
            ),
            "chambre de détente (-> Haut)" -> ElementFormulas(
                length = "15 cm (TOFIX)",
                shape  = "(A - 6) × (B - 6)"
            )
        )

    // V2 overrides: air-intake circle shape + angle inherits circle
    private def v2Formulas: Map[String, ElementFormulas] =
        sharedFormulas ++ Map(
            "vers centre chambre de détente" -> ElementFormulas(
                length = "W / 2.0",
                shape  = "◯  perimeterWetted"
            ),
            "angle vif 90°"                  -> ElementFormulas(
                length = "—",
                shape  = "◯  perimeterWetted"
            )
        )

    // ── Formatting helpers ───────────────────────────────────────────────

    // Strip trailing ".0" from numeric values (48.0 -> 48, 90.0 -> 90, 3.0 -> 3)
    private def stripDotZero(s: String): String =
        s.replaceAll("\\.0(\\s|/|\\*|\\+|\\-|°|mm|cm|x|\\)|\\(|,|$)", "$1")

    private def fmtShape(shape: PipeShape): String =
        stripDotZero(shape.show)

    private def fmtLength(q: Length): String =
        stripDotZero(f"${q.toUnit[Centimeter].value}%.2f cm")

    private def fmtAngle(q: Angle): String =
        stripDotZero(f"${q.toUnit[Degree].value}%.1f °")

    private def fmtRoughness(r: Roughness): String =
        stripDotZero(f"${r.unwrap.toUnit[Milli * Meter].value}%.1f mm")

    private def fmtZeta(z: ζ): String =
        stripDotZero(f"${z.toUnit[Unitless].value}%.2f")

    private def fmtPressure(p: QtyD[Pascal]): String =
        stripDotZero(f"${p.toUnit[Pascal].value}%.1f Pa")

    private def fmtArea(a: Area): String =
        stripDotZero(f"${a.toUnit[Centi * Meter ^ 2].value}%.2f cm²")

    private def elementRow(
        idx     : Int,
        named   : NamedPipeElDescr,
        formulas: Map[String, ElementFormulas]
    ): String =
        val name = named.name
        val el   = named.el

        val typeLabel = el match
            case _: StraightSection        => "StraightSection"
            case _: AngleVifDe0A180        => "AngleVif (sharp angle)"
            case _: CircularArc60          => "CircularArc60"
            case _: SectionGeometryChange  => "SectionGeometryChange"
            case _: SingularFlowResistance => "SingularFlowResistance"
            case _: PressureDiff           => "PressureDiff"
            case _: SplitMerge90           => "SplitMerge90"

        val lengthStr = el match
            case s: StraightSection => fmtLength(s.length)
            case _ => "—"

        val shapeStr = el match
            case s : StraightSection        => fmtShape(s.geometry)
            case dc: DirectionChange        => fmtShape(dc.effectiveShape)
            case sc: SectionGeometryChange  => s"${fmtShape(sc.from)} → ${fmtShape(sc.to)}"
            case sr: SingularFlowResistance => sr.crossSectionO.map(fmtArea).getOrElse("—")
            case pd: PressureDiff           => pd.crossSectionO.map(fmtArea).getOrElse("—")

        val roughnessStr = el match
            case s: StraightSection => fmtRoughness(s.roughness)
            case _ => "—"

        val elevStr = el match
            case s: StraightSection => fmtLength(s.elevation_gain)
            case _ => "—"

        val extraStr = el match
            case dc: DirectionChange        =>
                s"angle=${fmtAngle(dc.angleN1)}"
            case sr: SingularFlowResistance => s"ζ=${fmtZeta(sr.zeta)}"
            case pd: PressureDiff           => s"Δp=${fmtPressure(pd.pa)}"
            case _ => ""

        val lengthFormulaStr = formulas
            .get(name)
            .fold("—"): f =>
                if f.length != "—" then stripDotZero(f.length)
                else "—"
        val shapeFormulaStr  = formulas
            .get(name)
            .fold("—"): f =>
                if f.shape != "—" && f.shape != "— (inherited)" then stripDotZero(f.shape)
                else "—"

        s"| $idx | $name | $typeLabel | $lengthStr | $lengthFormulaStr | $shapeStr | $shapeFormulaStr | $roughnessStr | $elevStr | $extraStr |"

    private def formatFullDescrAsTable(
        fullDescr: CombustionAirPipe_15544,
        formulas : Map[String, ElementFormulas]
    ): String =
        val pipeFullDescr: FlowOnlyPipeDescr_15544.PipeFullDescr =
            fullDescr.asInstanceOf[FlowOnlyPipeDescr_15544.PipeFullDescr]

        val header =
            """|#  | Name | Type | Length | Length Formula | Shape | Shape Formula | Roughness | Elev. Gain | Extra
               |---|------|------|--------|----------------|-------|---------------|-----------|------------|------""".stripMargin

        val rows = pipeFullDescr.elems.zipWithIndex.map:
            case (named, idx) => elementRow(idx, named, formulas)

        (header :: rows.toList).mkString("\n")

    // ── Snapshot path helper ─────────────────────────────────────────────

    private def snapshotPath(name: String): Path =
        Paths.get(
            "modules/reports/src/test/resources/snapshots/EcolabeledFireboxPipeSuite",
            name
        )

    // ── Tests ────────────────────────────────────────────────────────────

    "Ecolabeled V1 combustion-air pipe FullDescr" in {
        val firebox = makeV1(sharedEcolabeledParams)
        val result  = firebox.toCombustionAirPipe_FullDescr
        result.isValid shouldBe true

        val pipe  = result.toOption.get
        val table = formatFullDescrAsTable(pipe, v1Formulas)
        SnapshotAssert.assertMatches(table, snapshotPath("version1_combustion_air.md"))
    }

    "Ecolabeled V2 combustion-air pipe FullDescr" in {
        val firebox = makeV2(sharedEcolabeledParams)
        val result  = firebox.toCombustionAirPipe_FullDescr
        result.isValid shouldBe true

        val pipe  = result.toOption.get
        val table = formatFullDescrAsTable(pipe, v2Formulas)
        SnapshotAssert.assertMatches(table, snapshotPath("version2_combustion_air.md"))
    }

    "Ecolabeled V1 firebox dimensions" in {
        val firebox: Firebox_15544 = makeV1(sharedEcolabeledParams)
        val table = firebox.showAsCliTable
        SnapshotAssert.assertMatches(table, snapshotPath("version1_firebox_dimensions.md"))
    }

    "Ecolabeled V2 firebox dimensions" in {
        val firebox: Firebox_15544 = makeV2(sharedEcolabeledParams)
        val table = firebox.showAsCliTable
        SnapshotAssert.assertMatches(table, snapshotPath("version2_firebox_dimensions.md"))
    }

    // ── PDF report generation ────────────────────────────────────────────

    private def generatePdf(
        casType: StoveProjectDescr_15544_Strict_Alg,
        appName: String
    ): Unit =
        given io.taig.babel.Locale = Locales.fr

        val appOpt = casType.en15544_Alg.toOption
        appOpt shouldBe defined
        val app: EN15544_Strict_Application = appOpt.get

        val typstFactory =
            new TypstReportFactory_15544_Strict                 (
                isDraft                  = true,
                checkPressureReq13384    = false,
                checkTemperatureReq13384 = false
            ):
                override val en15544_app            : EN15544_Strict_Application         = app
                override val stove_proj_15544_strict: StoveProjectDescr_15544_Strict_Alg = casType
                override val atParams = app.primary.asInstanceOf[en15544_app.AtParams]

        val typstString = typstFactory.build()
        typstString.length should be > 0

        val pdfBytes = JavaTypst.render(typstString)
        pdfBytes.length should be > 0

        val outPath = Paths.get(
            "modules/reports/src/test/resources/generated-reports",
            appName
        )
        Files.createDirectories(outPath.getParent)
        Files.write            (outPath, pdfBytes)

        val outFile = outPath.toFile
        outFile.exists() shouldBe true
        outFile.length() should be > 0L

    "Ecolabeled V1 PDF report" in {
        generatePdf(CasType_15544_C3, "ecolabeled-v1-report.pdf")
    }

    "Ecolabeled V2 PDF report" in {
        generatePdf(CasType_15544_C3_V2, "ecolabeled-v2-report.pdf")
    }

end EcolabeledFireboxPipeSuite
