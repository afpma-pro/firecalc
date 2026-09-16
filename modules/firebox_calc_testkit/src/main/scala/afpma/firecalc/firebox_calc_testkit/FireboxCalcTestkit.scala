/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_testkit

import cats.effect.{IO, Resource}

import afpma.firecalc.firebox_calc_api.FireboxCalcClientConfig
import afpma.firecalc.firebox_calc_client.Http4sCalcClient
import org.http4s.HttpApp
import org.http4s.client.Client

/**
 * Public black-box test harness for the normative Firebox Calculation API
 * (ticket 17).
 *
 * The testkit drives the open protocol — `GET /v1/kinds` and
 * `POST /v1/calculate` — over HTTP, against ANY conforming server. It has NO
 * Austrian implementation dependency: no `FireboxCalcCore`, no Strict registry,
 * no proprietary fixture, no numerical oracle, no in-process Austrian adapter.
 * Numerical and engine correctness lives only in the private repository.
 *
 * Two ways to obtain a client:
 *   - [[referenceClient]] — an in-process client bound to the Austrian-free
 *     [[ReferenceConformingServer]], for fast, deterministic contract tests;
 *   - [[client]] — a real-HTTP client bound to an arbitrary base URI, for
 *     cross-repository acceptance against a live deployment (e.g. the private
 *     Austrian server), proving public requests complete through HTTP without
 *     sharing compiled private classes.
 */
object FireboxCalcTestkit:

    /** The Austrian-free reference server as an `HttpApp`. */
    def referenceApp: IO[HttpApp[IO]] =
        ReferenceConformingServer.routes[IO].map(_.orNotFound)

    /**
     * An in-process client bound to the reference server. Requests flow through
     * the full http4s client stack (`Client.fromHttpApp`) — encoding, decoding,
     * and the typed failure taxonomy — without touching a network socket.
     */
    def referenceClient: Http4sCalcClient =
        val app = referenceApp.unsafeRunSync()(using cats.effect.unsafe.implicits.global)
        Http4sCalcClient(FireboxCalcClientConfig.localhost(), Client.fromHttpApp(app))

    /**
     * A real-HTTP client for an arbitrary conforming deployment, as a managed
     * `Resource`. Used for cross-repository acceptance: point `baseUri` at a
     * running private server and drive the normative seam end to end.
     */
    def client(baseUri: String): Resource[IO, Http4sCalcClient] =
        Http4sCalcClient.resource(FireboxCalcClientConfig(baseUri = baseUri))

end FireboxCalcTestkit
