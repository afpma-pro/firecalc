/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (2026) Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled
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

import java.nio.file.Path
import java.nio.file.Paths
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Snapshot test: transcribe the combustion-air pipe FullDescr for both
 * ecolabeled firebox versions (V1 with chambre de détente, V2 with direct
 * air-intake shape) into a human-readable table.
 *
 * Fixture values are taken from CasType_15544_C3 (golden fixture).
 */
class EcolabeledCombustionAirPipeFullDescrSuite extends AnyFreeSpec with Matchers:

    given FireboxToCombustionAirPipe_15544_Strict[Ecolabeled] =
        EcolabeledToFireboxInternalPipes_15544_Strict

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
        Ecolabeled_V1                                     (
            pn_reduced                                      = p.heatOutputReduced,
            h11_profondeurDuFoyer                           = p.fireboxDepth,
            h12_largeurDuFoyer                              = p.fireboxWidth,
            h13_hauteurDuFoyer                              = p.fireboxHeight,
            h70_largeurPorteDansMaconnerie                  = p.doorOpeningWidth,
            h71_largeurVitre                                = p.glassWidth,
            h72_hauteurVitre                                = p.glassHeight,
            h74_hauteur_de_cendrier_AF                      = p.ashPitHeight,
            h75_hauteurArriveeConduitAir_DessousSoleFoyer_W = p.airManifoldHeight,
            h76_epaisseurSole                               = p.fireboxFloorThickness,
            h77_epaisseurParoiInterneFoyer_D1               = p.innerWallThickness,
            epaisseurParoiExterneFoyer_D2                   = p.outerWallThickness,
            h78_largeurEspaceInterparoisDuFoyer_S           = p.airColumnThickness,
            h79_largeurRenfortMedianLateraux                = p.widthBetweenAirColumnsSides,
            h80_largeurRenfortMedianArriere                 = p.widthBetweenAirColumnsRear,
            r1                                              = p.reinforcementOffsetR1,
            r2                                              = p.reinforcementOffsetR2,
            r3                                              = p.reinforcementOffsetR3,
            h82_hauteurDesInjecteurs_Z                      = p.injectorHeight,
            h83_hauteurEntreLaSoleEtLe1erInjecteur_X        = p.heightFirstRowInjectors
        )

    private def makeV2(p: EcolabeledFixtureParams): Ecolabeled_V2 =
        Ecolabeled_V2                                     (
            pn_reduced                                      = p.heatOutputReduced,
            arriveeAirGeometry                              = PipeShape.Circle(20.cm),
            h11_profondeurDuFoyer                           = p.fireboxDepth,
            h12_largeurDuFoyer                              = p.fireboxWidth,
            h13_hauteurDuFoyer                              = p.fireboxHeight,
            h70_largeurPorteDansMaconnerie                  = p.doorOpeningWidth,
            h71_largeurVitre                                = p.glassWidth,
            h72_hauteurVitre                                = p.glassHeight,
            h74_hauteur_de_cendrier_AF                      = p.ashPitHeight,
            h75_hauteurArriveeConduitAir_DessousSoleFoyer_W = p.airManifoldHeight,
            h76_epaisseurSole                               = p.fireboxFloorThickness,
            h77_epaisseurParoiInterneFoyer_D1               = p.innerWallThickness,
            epaisseurParoiExterneFoyer_D2                   = p.outerWallThickness,
            h78_largeurEspaceInterparoisDuFoyer_S           = p.airColumnThickness,
            h79_largeurRenfortMedianLateraux                = p.widthBetweenAirColumnsSides,
            h80_largeurRenfortMedianArriere                 = p.widthBetweenAirColumnsRear,
            r1                                              = p.reinforcementOffsetR1,
            r2                                              = p.reinforcementOffsetR2,
            r3                                              = p.reinforcementOffsetR3,
            h82_hauteurDesInjecteurs_Z                      = p.injectorHeight,
            h83_hauteurEntreLaSoleEtLe1erInjecteur_X        = p.heightFirstRowInjectors
        )

    // ── Formatting helpers ───────────────────────────────────────────────

    private def fmtShape(shape: PipeShape): String = shape.show

    private def fmtLength(q: Length): String =
        f"${q.toUnit[Centimeter].value}%.2f cm"

    private def fmtAngle(q: Angle): String =
        f"${q.toUnit[Degree].value}%.1f °"

    private def fmtRoughness(r: Roughness): String =
        f"${r.unwrap.toUnit[Milli * Meter].value}%.1f mm"

    private def fmtZeta(z: ζ): String =
        f"${z.toUnit[Unitless].value}%.2f"

    private def fmtPressure(p: QtyD[Pascal]): String =
        f"${p.toUnit[Pascal].value}%.1f Pa"

    private def fmtArea(a: Area): String =
        f"${a.toUnit[Centi * Meter ^ 2].value}%.2f cm²"

    private def elementRow(idx: Int, named: NamedPipeElDescr): String =
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
                val angles = dc.angleN2 match
                    case Some(n2) => s"${fmtAngle(dc.angleN1)} / ${fmtAngle(n2)}"
                    case None     => fmtAngle(dc.angleN1)
                s"angle=$angles"
            case sr: SingularFlowResistance => s"ζ=${fmtZeta(sr.zeta)}"
            case pd: PressureDiff           => s"Δp=${fmtPressure(pd.pa)}"
            case _ => ""

        s"| $idx | $name | $typeLabel | $lengthStr | $shapeStr | $roughnessStr | $elevStr | $extraStr |"

    private def formatFullDescrAsTable(fullDescr: CombustionAirPipe_15544): String =
        // CombustionAirPipe_15544 = FullDescrResult.PipeCanBe = FullDescr
        // Use the type test to unwrap
        val pipeFullDescr: FlowOnlyPipeDescr_15544.PipeFullDescr =
            fullDescr.asInstanceOf[FlowOnlyPipeDescr_15544.PipeFullDescr]

        val header =
            """|#  | Name | Type | Length | Shape | Roughness | Elev. Gain | Extra
               |---|------|------|--------|-------|-----------|------------|------""".stripMargin

        val rows = pipeFullDescr.elems.zipWithIndex.map:
            case (named, idx) => elementRow(idx, named)

        (header :: rows.toList).mkString("\n")

    // ── Snapshot path helper ─────────────────────────────────────────────

    private def snapshotPath(name: String): Path =
        Paths.get(
            "modules/engine-15544-strict/src/test/resources/snapshots/EcolabeledCombustionAirPipeFullDescrSuite",
            name
        )

    // ── Tests ────────────────────────────────────────────────────────────

    "Ecolabeled V1 combustion-air pipe FullDescr" in {
        val firebox = makeV1(sharedEcolabeledParams)
        val result  = firebox.toCombustionAirPipe_FullDescr
        result.isValid shouldBe true

        val pipe  = result.toOption.get
        val table = formatFullDescrAsTable(pipe)
        SnapshotAssert.assertMatches(table, snapshotPath("version1_combustion_air.md"))
    }

    "Ecolabeled V2 combustion-air pipe FullDescr" in {
        val firebox = makeV2(sharedEcolabeledParams)
        val result  = firebox.toCombustionAirPipe_FullDescr
        result.isValid shouldBe true

        val pipe  = result.toOption.get
        val table = formatFullDescrAsTable(pipe)
        SnapshotAssert.assertMatches(table, snapshotPath("version2_combustion_air.md"))
    }

end EcolabeledCombustionAirPipeFullDescrSuite
