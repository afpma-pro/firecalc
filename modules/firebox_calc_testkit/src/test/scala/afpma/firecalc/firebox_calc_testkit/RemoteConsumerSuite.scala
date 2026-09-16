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

import afpma.firecalc.engine.alg.en15544.{
    FireboxContribution,
    FireboxConstraintContext,
    FireboxKind,
    OpPoint,
    RemoteBackedFireboxEngine,
    RemoteFireboxData
}
import afpma.firecalc.engine.api.WireConversions
import afpma.firecalc.engine.models.{CombustionAirPipe_Module_15544, FireboxPipe_Module_15544, LoadQty}
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_client.Http4sCalcClient

import io.taig.babel.Locales
import org.http4s.client.Client
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Generic remote-consumer acceptance (ticket 17).
 *
 * Proves the public-side half of invariant 4: a generic consumer — one that
 * links NO Austrian code — can drive the normative HTTP seam, fold the wire
 * response through the public `WireConversions`, and serve the COMPLETE
 * `AsResult` operating-point continuation state through the generic
 * `RemoteBackedFireboxEngine`, exactly as the browser and `payments` do in
 * production. The server is the Austrian-free [[ReferenceConformingServer]],
 * so the test holds for any conforming deployment.
 *
 * The numerical VALUES are canned (not Austrian-computed); what is asserted is
 * the transport + fold + continuation-state plumbing, which is the public
 * responsibility. Numerical equivalence is private (`LocalRemoteEquivalenceSuite`).
 */
class RemoteConsumerSuite extends AnyFreeSpec with Matchers:

    given io.taig.babel.Locale = Locales.en

    private type StrictFD = FireboxPipe_Module_15544.FullDescr
    private type StrictCD = CombustionAirPipe_Module_15544.FullDescr

    private val dtoFixture: Firebox_V4.Traditional =
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

    /** A concrete generic `Firebox_15544` the remote engine serves (ignored by it). */
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

    private val request: FireboxCalcRequest =
        FireboxCalcRequest        (
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Traditional,
            firebox            = dtoFixture,
            params             = Standard15544Params(m_B = Some(WireQty("15.0", "kilogram")), n_min = None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )

    /** Fold a wire response into the generic remote-data record (production path). */
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

    private def calculateOverHttp(): FireboxCalcResponse =
        val app    = ReferenceConformingServer.routes[IO].map(_.orNotFound).unsafeRunSync()
        val client = Http4sCalcClient(FireboxCalcClientConfig.localhost(), Client.fromHttpApp(app))
        client.calculate(request).unsafeRunSync().toOption.get

    "a generic remote consumer (no Austrian linkage)" - {

        "serves the complete AsResult grid through RemoteBackedFireboxEngine" in {
            val response = calculateOverHttp()
            val engine   =
                RemoteBackedFireboxEngine[TraditionalFirebox, StrictFD, StrictCD](
                    FireboxKind.Traditional,
                    remoteData(response)
                )

            engine.kind shouldBe FireboxKind.Traditional

            engine.fireboxContribution(engineModel).toOption.get match
                case FireboxContribution.AsResult(grid) =>
                    grid.map(_._1).toList.toSet shouldBe Set(
                        OpPoint(DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Nominal),
                        OpPoint(DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Nominal)
                    )
                case other                              =>
                    fail(s"expected an AsResult grid, got $other")
        }

        "preserves the AsResult continuation state across the wire (invariant 4)" in {
            val response = calculateOverHttp()
            val engine   =
                RemoteBackedFireboxEngine[TraditionalFirebox, StrictFD, StrictCD](
                    FireboxKind.Traditional,
                    remoteData(response)
                )

            engine.fireboxContribution(engineModel).toOption.get match
                case FireboxContribution.AsResult(grid) =>
                    grid.forall { case (_, result) =>
                        result.last_density_mean.isDefined &&
                        result.last_density_middle.isDefined &&
                        result.last_velocity_mean.isDefined &&
                        result.last_velocity_middle.isDefined &&
                        result.gas_temp_end.value == 220.0
                    } shouldBe true
                case other                              =>
                    fail(s"expected an AsResult grid, got $other")
        }

        "serves the folded constraint verdicts verbatim" in {
            val response = calculateOverHttp()
            val engine   =
                RemoteBackedFireboxEngine[TraditionalFirebox, StrictFD, StrictCD](
                    FireboxKind.Traditional,
                    remoteData(response)
                )

            val fctx = FireboxConstraintContext(
                mB                 = 15.0.withUnit[Kilogram],
                flow_rate          = None,
                airIntakePipeShape = None
            )
            engine.constraints(engineModel, None, fctx) shouldBe response.constraints
        }

        "serves a Without combustion-air contribution" in {
            val response = calculateOverHttp()
            val engine   =
                RemoteBackedFireboxEngine[TraditionalFirebox, StrictFD, StrictCD](
                    FireboxKind.Traditional,
                    remoteData(response)
                )

            engine.combustionAirContribution(engineModel).toOption.get shouldBe
                FireboxContribution.Without
        }
    }

end RemoteConsumerSuite
