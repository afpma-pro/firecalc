/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.api.WireConversions

import afpma.firecalc.firebox_calc_api.WireFireboxKind
import afpma.firecalc.payments.GenerateExampleProjectFixture
import afpma.firecalc.payments.service.impl.CalculationReportServiceImpl

import EcolabeledRemoteFixtures.*
import utest.*

/**
 * Drift guard for the single remote-only spelling (ticket C.1, ADR-0010):
 * payments must never again hand-enumerate the remote-only roster. The
 * routing predicate here and the canonical engine-side set
 * [[FireboxKind.remoteOnly]] are transported against each other through the
 * wire bijection ([[WireConversions]]) in both directions, so a roster edit
 * on either side — or a regression to a hand-listed `isAustrianFirebox`-style
 * pair — fails CI instead of silently mis-routing orders.
 *
 * The routing behavior itself (browser → calc backend vs local report) is
 * covered behaviorally by [[RemotePdfAcceptanceSuite]],
 * [[RemotePdfNoSizingInputSuite]] and [[PdfReportGenerationTest]]; the
 * never-remote direction for non-Austrian kinds is covered by the local-kind
 * suites there, so it is not re-tested.
 */
object RemoteOnlyRosterDriftSuite extends TestSuite:

    val tests = Tests:

        test("payments' remote-only truth set equals FireboxKind.remoteOnly transported through the wire bijection") {
            val viaPredicate = WireFireboxKind.values.filter(CalculationReportServiceImpl.isRemoteOnlyFirebox).toSet
            val transported  = FireboxKind.remoteOnly.map(WireConversions.toWireFireboxKind)
            assert(viaPredicate == transported)
        }

        test("the transported canonical set names today's two Austrian kinds") {
            // Spec witness (ADR-0010): the moment a third kind joins the canonical set,
            // this test goes red — payments' roster *must* follow (bridge) but the
            // routing suites above must then be extended, which is the point of the trip.
            assert(
                FireboxKind.remoteOnly.map(WireConversions.toWireFireboxKind) ==
                    Set(WireFireboxKind.Ecolabeled, WireFireboxKind.Door15aCatalog)
            )
        }

        test("DTO transport: the Ecolabeled fixture routes remote-only, the Traditional example project does not") {
            val ecolabeledIsRemote =
                CalculationReportServiceImpl.isRemoteOnlyFirebox(
                    CalculationReportServiceImpl.fireboxToWireKind(decodedEcolabeled.firebox)
                )
            val traditionalIsRemote =
                CalculationReportServiceImpl.isRemoteOnlyFirebox(
                    CalculationReportServiceImpl.fireboxToWireKind(
                        GenerateExampleProjectFixture.exampleFireCalcYaml.firebox
                    )
                )
            assert                                              (ecolabeledIsRemote && !traditionalIsRemote)
        }
