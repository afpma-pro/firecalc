/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_testkit

import afpma.firecalc.units.coulombutils.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import coulomb.*
import coulomb.syntax.*

import afpma.firecalc.dto.common.HeatOutputReduced
import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.dto.v4.{EmissionValues_DTO, EmissionsAndEfficiencyValues_DTO, TestEmissionValue_DTO}
import afpma.firecalc.domain.PolluantName

import afpma.firecalc.engine.alg.en15544.{
    FireboxContribution,
    FireboxKind,
    RemoteBackedFireboxEngine,
    RemoteFireboxData
}
import afpma.firecalc.engine.api.WireConversions
import afpma.firecalc.engine.models.{CombustionAirPipe_Module_15544, FireboxPipe_Module_15544}
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_client.Http4sCalcClient

import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Cross-repository acceptance (ticket 17, checklist item 5).
 *
 * Proves that public requests — built only from public wire types and public
 * DTOs — are accepted by the PRIVATE Austrian server over real HTTP, and that
 * the complete `AsResult` operating-point continuation state returns through
 * the wire and folds into the generic `RemoteBackedFireboxEngine` WITHOUT any
 * private-class sharing. The public side links no Austrian code; the private
 * server is reached solely through the normative seam.
 *
 * Gated on the system property `firecalc.private.server.uri` (e.g.
 * `http://localhost:9000/v1`). When absent, every test is CANCELED — the suite
 * never fails the public build for a missing private deployment. Run it with:
 *
 * {{{
 *   sbt -Dfirecalc.private.server.uri=http://localhost:9000/v1 \
 *       "firebox_calc_testkit/testOnly *CrossRepositoryAcceptanceSuite"
 * }}}
 */
class CrossRepositoryAcceptanceSuite extends AnyFreeSpec with Matchers:

    given io.taig.babel.Locale = Locales.en

    private type StrictFD = FireboxPipe_Module_15544.FullDescr
    private type StrictCD = CombustionAirPipe_Module_15544.FullDescr

    private val baseUri: Option[String] =
        sys.props.get("firecalc.private.server.uri").orElse(sys.env.get("FIRECALC_PRIVATE_SERVER_URI"))

    private def withLiveClient[A](f: Http4sCalcClient => A): A =
        baseUri match
            case None      =>
                cancel("private server not configured (set -Dfirecalc.private.server.uri)")
            case Some(uri) =>
                FireboxCalcTestkit
                    .client(uri)
                    .use(client => IO(f(client)))
                    .unsafeRunSync()

    private val ecolabeledFixture: Firebox_V4.Ecolabeled =
        Firebox_V4.Ecolabeled                    (
            heat_output_reduced                     = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            version                                 = Left("Version 1"),
            air_intake_shape                        = None,
            firebox_depth                           = 54.0.cm,
            firebox_width                           = 54.0.cm,
            firebox_height                          = 81.3.cm,
            height_of_first_row_of_air_injectors    = 10.0.cm,
            door_opening_width                      = 52.0.cm,
            glass_width                             = 50.0.cm,
            glass_height                            = 40.0.cm,
            ash_pit_height                          = 8.0.cm,
            air_manifold_height                     = 11.0.cm,
            firebox_floor_thickness                 = 8.0.cm,
            firebox_inner_wall_thickness            = 6.0.cm,
            firebox_outer_wall_thickness            = 6.0.cm,
            air_column_thickness                    = 3.5.cm,
            width_between_two_air_columns_sides     = 4.5.cm,
            width_between_two_air_columns_rear      = 4.5.cm,
            reinforcement_bars_offset_in_corners_R1 = 4.5.cm,
            reinforcement_bars_offset_in_corners_R2 = 4.5.cm,
            reinforcement_bars_offset_in_corners_R3 = 4.5.cm,
            injector_height                         = 0.8.cm
        )

    private val traditionalFixture: Firebox_V4.Traditional =
        Firebox_V4.Traditional                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            firebox_depth                         = 0.5.withUnit[Meter],
            firebox_width                         = 0.5.withUnit[Meter],
            firebox_height                        = 0.6.withUnit[Meter],
            height_of_lowest_opening              = 0.1.withUnit[Meter],
            pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
            total_air_intake_surface_area_on_door = 0.025.withUnit[(Meter ^ 2)],
            glass_width                           = 0.3.withUnit[Meter],
            glass_height                          = 0.2.withUnit[Meter]
        )

    private val door15aEmissions: EmissionsAndEfficiencyValues_DTO =
        EmissionsAndEfficiencyValues_DTO               (
            firebox_name                = "cross-repo-test",
            accredited_or_notified_body = "cross-repo-test",
            test_reports                = Nil,
            emissions_values            = EmissionValues_DTO(
                co   = TestEmissionValue_DTO(PolluantName.CO, None, "", 0.0.percent),
                dust = TestEmissionValue_DTO(PolluantName.Dust, None, "", 0.0.percent),
                ogc  = TestEmissionValue_DTO(PolluantName.OGC, None, "", 0.0.percent),
                nox  = TestEmissionValue_DTO(PolluantName.NOx, None, "", 0.0.percent)
            )
        )

    private val door15aFixture: Firebox_V4.Door15aFirebox_Catalog =
        Firebox_V4.Door15aFirebox_Catalog                  (
            reference                   = "cross-repo-test",
            firebox_depth               = 1.0.cm,
            firebox_width               = 1.0.cm,
            firebox_height              = 1.0.cm,
            load_size_nominal           = Some(15.0.kg),
            sb                          = 2.4.withUnit[Centimeter],
            sb_min                      = None,
            sb_max                      = None,
            mb_min                      = Some(10.0.kg),
            mb_max                      = Some(40.0.kg),
            pressure_loss_table_raw     = "mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0\n10\t4\t3\t2\t1\n25\t22\t20\t18\t16",
            expectedAirIntakePipeShapes = List(PipeShape.Circle(10.0.cm)),
            actualAirIntakePipeShape    = PipeShape.Circle(10.0.cm),
            co2_dry_nominal             = 0.1.percent,
            co2_dry_lowest              = None,
            emissions_values            = door15aEmissions,
            glass_area                  = 1.0.cm2,
            height_of_lowest_opening    = 1.0.cm,
            heat_output_reduced         = HeatOutputReduced.NotDefined,
            image                       = None
        )

    private val engineModel: TraditionalFirebox =
        TraditionalFirebox                      (
            firebox_depth_B                       = 0.5.withUnit[Meter],
            firebox_width_A                       = 0.5.withUnit[Meter],
            firebox_height_H                      = 0.6.withUnit[Meter],
            pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
            total_air_intake_surface_area_on_door = 0.025.withUnit[(Meter ^ 2)],
            glass_width                           = 0.3.withUnit[Meter],
            glass_height                          = 0.2.withUnit[Meter],
            ash_pit_height_AF                     = 0.1.withUnit[Meter]
        )

    private def remoteData(response: FireboxCalcResponse): RemoteFireboxData[StrictFD, StrictCD] =
        RemoteFireboxData[StrictFD, StrictCD]      (
            fireboxContribution       = WireConversions.fromWireValidatedFireboxContribution[StrictFD](
                response.fireboxContribution
            )(using WireConversions.fromWireFireboxPipeFullDescr, summon),
            combustionAirContribution = WireConversions.fromWireValidatedFireboxContribution[StrictCD](
                response.combustionAirContribution
            )(using WireConversions.fromWireCombustionAirPipeFullDescr, summon),
            constraints               = response.constraints,
            reportData                = response.reportData
        )

    "the private Austrian server, reached over real HTTP" - {

        "advertises the served kinds on GET /v1/kinds" in withLiveClient { client =>
            val discovery = client.getKinds.unsafeRunSync().toOption.get
            discovery.kinds.map(_.wire) should contain allOf
                ("traditional", "ecolabeled", "door15aCatalog")
        }

        "accepts a public Traditional request and returns a decodable response" in withLiveClient { client =>
            val response = client
                .calculate(
                    FireboxCalcRequest        (
                        apiVersion         = ApiVersion.v1,
                        kind               = WireFireboxKind.Traditional,
                        firebox            = traditionalFixture,
                        params             = Standard15544Params(m_B = None, n_min = None),
                        airIntakePipeShape = None,
                        locale             = "en",
                        standard           = WireStandardVariant.Strict,
                        operatingEnvelope  = None
                    )
                )
                .unsafeRunSync()
                .toOption
                .get
            response.kind shouldBe WireFireboxKind.Traditional
            response.fireboxContribution shouldBe a[WireValidated.Valid[?]]
        }

        "returns complete AsResult state for Ecolabeled, folded generically (invariant 4)" in withLiveClient { client =>
            val response = client
                .calculate(
                    FireboxCalcRequest        (
                        apiVersion         = ApiVersion.v1,
                        kind               = WireFireboxKind.Ecolabeled,
                        firebox            = ecolabeledFixture,
                        params             = Standard15544Params(
                            m_B   = Some(WireQty("15.0", "kilogram")),
                            n_min = None
                        ),
                        airIntakePipeShape = None,
                        locale             = "en",
                        standard           = WireStandardVariant.Strict,
                        operatingEnvelope  = Some(
                            WireOperatingEnvelope            (
                                altitude             = WireQty("350.0", "meter"),
                                temperatureOverrides = None
                            )
                        )
                    )
                )
                .unsafeRunSync()
                .toOption
                .get

            val wireKeys = response.fireboxContribution match
                case WireValidated.Valid(WireFireboxContribution.AsResult(results)) =>
                    results should not be empty
                    results.map(r => (r.draftCondition, r.loadQty))
                case other                                                          =>
                    fail(s"expected an AsResult grid from the private server, got $other")
            // The wire decoder guarantees unique operating-point keys.
            wireKeys.distinct should have size wireKeys.size

            // Fold through the generic remote engine — no Austrian linkage.
            val engine =
                RemoteBackedFireboxEngine[TraditionalFirebox, StrictFD, StrictCD](
                    FireboxKind.Ecolabeled,
                    remoteData(response)
                )
            engine.fireboxContribution(engineModel).toOption.get match
                case FireboxContribution.AsResult(grid) =>
                    // The COMPLETE operating-point grid crosses the wire and
                    // folds into the generic engine without loss (invariant 4).
                    grid.size shouldBe wireKeys.size
                case other                              =>
                    fail(s"expected a folded AsResult grid, got $other")
        }

        "returns numerically sane AsResult state, not just structural completeness" in withLiveClient { client =>
            val response = client
                .calculate(
                    // Ecolabeled is the kind answered with an operating-point
                    // grid (AsResult) per the normative policy; Traditional
                    // returns a full AsDescr and cannot exercise this check.
                    FireboxCalcRequest        (
                        apiVersion         = ApiVersion.v1,
                        kind               = WireFireboxKind.Ecolabeled,
                        firebox            = ecolabeledFixture,
                        params             = Standard15544Params(
                            m_B   = Some(WireQty("15.0", "kilogram")),
                            n_min = None
                        ),
                        airIntakePipeShape = None,
                        locale             = "en",
                        standard           = WireStandardVariant.Strict,
                        operatingEnvelope  = Some(
                            WireOperatingEnvelope            (
                                altitude             = WireQty("350.0", "meter"),
                                temperatureOverrides = None
                            )
                        )
                    )
                )
                .unsafeRunSync()
                .toOption
                .get

            // Structural completeness is not enough: the solved pressures must
            // be the real engine output, not a zero substitution. The normative
            // engine semantics (private FireboxCalcPolicySuite) pin this
            // fixture's (m_B = 15 kg, altitude = 350 m, no temperature
            // overrides) combustion-air draft-min/nominal pressure at
            // ≈ 1.5457395011587576 Pa, while the firebox gas path is a single
            // vertical section with no flow resistance, so its solved pu is
            // exactly 0 Pa. Every grid pressure must be finite.
            def draftMinNominalPu(contrib: WireValidated[WireFireboxContribution], what: String): Double =
                contrib match
                    case WireValidated.Valid(WireFireboxContribution.AsResult(results)) =>
                        results.find(r =>
                            r.draftCondition == WireDraftCondition.DraftMinOrPositivePressureMax &&
                                r.loadQty == WireLoadQty.Nominal
                        ) match
                            case Some(r) =>
                                r.result.pu match
                                    case WireValidated.Valid(qty)   =>
                                        qty.value.toDoubleOption.getOrElse(fail(s"$what: non-numeric pu"))
                                    case WireValidated.Invalid(err) =>
                                        fail(s"$what: invalid pu at draft-min/nominal: $err")
                            case None    => fail(s"$what: no draft-min/nominal entry")
                    case other                                                          =>
                        fail(s"$what: expected an AsResult grid from the private server, got $other")

            val caPu = draftMinNominalPu(response.combustionAirContribution, "combustionAirContribution")
            caPu.isNaN shouldBe false
            caPu.isInfinite shouldBe false
            math.abs(caPu - 1.5457395011587576) should be <= 1e-9

            val fbPu = draftMinNominalPu(response.fireboxContribution, "fireboxContribution")
            fbPu.isNaN shouldBe false
            fbPu.isInfinite shouldBe false
            math.abs(fbPu) should be <= 1e-9
        }

        "returns the full Door15aCatalog AsDescr with the catalog pressure loss over HTTP" in withLiveClient { client =>
            val response = client
                .calculate(
                    FireboxCalcRequest        (
                        apiVersion         = ApiVersion.v1,
                        kind               = WireFireboxKind.Door15aCatalog,
                        firebox            = door15aFixture,
                        params             = Standard15544Params(m_B = None, n_min = None),
                        airIntakePipeShape = Some(WirePipeShape.Circle(WireQty("0.2", "meter"))),
                        locale             = "en",
                        standard           = WireStandardVariant.Strict,
                        operatingEnvelope  = None
                    )
                )
                .unsafeRunSync()
                .toOption
                .get

            response.kind shouldBe WireFireboxKind.Door15aCatalog

            // Firebox gas path: the shared builder's single straight section.
            response.fireboxContribution match
                case WireValidated.Valid(WireFireboxContribution.AsDescr(descr)) =>
                    descr.elements.map(_.name) shouldBe List("ascension dans foyer")
                case other                                                       =>
                    fail(s"expected the full AsDescr arm for the Door15aCatalog firebox path, got $other")

            // Combustion-air path: the catalog pressure loss — bilinear
            // interpolation of the fixture table at (mB = 15 kg, SB = 2.4 cm)
            // → 8.666… Pa, the normative value pinned by the private
            // FireboxCalcPolicySuite.
            response.combustionAirContribution match
                case WireValidated.Valid(WireFireboxContribution.AsDescr(descr)) =>
                    descr.elements.map(e => (e.name, e.el)) match
                        case List(("door_15a_pressure_loss", WirePipeElDescr.PressureDiff(pa, _))) =>
                            pa.unit shouldBe "pascal"
                            math.abs(pa.value.toDouble - 8.666666666666666) should be <= 1e-9
                        case other                                                                 =>
                            fail(s"expected the door_15a_pressure_loss PressureDiff, got $other")
                case other                                                       =>
                    fail(s"expected the full AsDescr arm for the Door15aCatalog combustion-air path, got $other")
        }
    }

end CrossRepositoryAcceptanceSuite
