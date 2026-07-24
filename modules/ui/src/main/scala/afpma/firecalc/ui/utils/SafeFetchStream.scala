/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import com.raquo.airstream.core.{EventStream, WritableStream, Transaction}
import org.scalajs.dom

import scala.scalajs.js

/**
 * Fork of Airstream's FetchStream that prevents unhandled promise rejections.
 *
 * The original FetchStream calls `dom.Fetch.fetch()` without a `.catch()` handler,
 * causing the browser to log "Uncaught (in promise)" when the fetch fails.
 * The error is caught downstream by `.recoverToTry`, but the unhandled rejection
 * message persists in the console.
 *
 * This fork attaches a no-op rejection handler to the fetch promise at creation,
 * so the browser never reports an unhandled rejection — neither when the rejection
 * beats the deferred `fireValue` (throttled timers / busy main thread), nor when
 * the stream is stopped in the deferral window and downstream handlers never attach.
 * Promise reactions are independent: the handlers `JsPromiseStream` attaches later
 * still receive the rejection, so downstream `.recoverToTry` keeps working.
 *
 * Simplified fork: abort stream functionality removed (not used in this codebase).
 */
object SafeFetchStream
    extends SafeFetchBuilder[dom.BodyInit, String] (
        encodeRequest  = identity,
        decodeResponse = response => EventStream.fromJsPromise(response.text())
    ):

    lazy val raw: SafeFetchBuilder[dom.BodyInit, dom.Response] =
        new SafeFetchBuilder(identity, EventStream.fromValue(_))

    def withCodec[In, Out](
        encodeRequest : In => dom.BodyInit,
        decodeResponse: dom.Response => EventStream[Out]
    ): SafeFetchBuilder[In, Out] =
        new SafeFetchBuilder(encodeRequest, decodeResponse)

    def withEncoder[In](
        encodeRequest: In => dom.BodyInit
    ): SafeFetchBuilder[In, String] =
        new SafeFetchBuilder(encodeRequest, response => EventStream.fromJsPromise(response.text()))

    def withDecoder[Out](
        decodeResponse: dom.Response => EventStream[Out]
    ): SafeFetchBuilder[dom.BodyInit, Out] =
        new SafeFetchBuilder(encodeRequest = identity, decodeResponse)

end SafeFetchStream

class SafeFetchBuilder[In, Out](
    encodeRequest : In => dom.BodyInit,
    decodeResponse: dom.Response => EventStream[Out]
):

    def get(
        url       : String,
        setOptions: SafeFetchOptions[In] => Unit*
    ): EventStream[Out] =
        apply(_.GET, url, setOptions*)

    def post(
        url       : String,
        setOptions: SafeFetchOptions[In] => Unit*
    ): EventStream[Out] =
        apply(_.POST, url, setOptions*)

    def put(
        url       : String,
        setOptions: SafeFetchOptions[In] => Unit*
    ): EventStream[Out] =
        apply(_.PUT, url, setOptions*)

    def apply(
        method    : dom.HttpMethod.type => dom.HttpMethod,
        url       : String,
        setOptions: SafeFetchOptions[In] => Unit*
    ): EventStream[Out] =
        val options = SafeFetchOptions(encodeRequest)
        setOptions.foreach(setOption => setOption(options))
        options.request.method = method(dom.HttpMethod)

        new SafeFetchStream(url, options.request).flatMapSwitch { promise =>
            EventStream.fromJsPromise(promise).flatMapSwitch(decodeResponse)
        }

end SafeFetchBuilder

/**
 * Safe version of FetchStream that prevents unhandled promise rejections.
 *
 * Simplified: no abort stream support (not used in this codebase).
 */
class SafeFetchStream(
    url        : String,
    requestInit: dom.RequestInit
) extends WritableStream[js.Promise[dom.Response]]:

    override protected val topoRank: Int = 1

    override protected def onWillStart(): Unit =
        // Guard: mark the promise as handled AT CREATION so the browser never reports
        // "Uncaught (in promise)". Wrapping with .catch(e => Promise.reject(e)) does NOT
        // work — the derived promise rejects with the same error and has the same exposure
        // (verified empirically: identical console-error counts to the raw pattern).
        // The no-op handler does not consume the error for downstream: JsPromiseStream's
        // .then(onF, onR), attached when it starts, is an independent reaction and still
        // receives the rejection.
        val promise = dom.Fetch.fetch(url, requestInit)
        promise.`catch`((_: Any) => ()): Unit

        js.timers.setTimeout(0) {
            Transaction(fireValue(promise, _))
        }

end SafeFetchStream

class SafeFetchOptions[In](encodeRequest: In => dom.BodyInit):

    private[utils] val request: dom.RequestInit = new dom.RequestInit {}

    private var maybeHeaders: js.UndefOr[dom.Headers] = js.undefined

    def headers(kvs: (String, String)*): Unit =
        if maybeHeaders.isEmpty then
            val headers = new dom.Headers()
            maybeHeaders    = headers
            request.headers = headers
        kvs.foreach { kv =>
            maybeHeaders.get.set(name = kv._1, value = kv._2)
        }

    def headersAppend(kvs: (String, String)*): Unit =
        if maybeHeaders.isEmpty then
            val headers = new dom.Headers()
            maybeHeaders    = headers
            request.headers = headers
        kvs.foreach { kv =>
            maybeHeaders.get.append(name = kv._1, value = kv._2)
        }

    def body(content: In): Unit =
        request.body = encodeRequest(content)

    def mode(get: dom.RequestMode.type => dom.RequestMode): Unit =
        request.mode = get(dom.RequestMode)

    def credentials(get: dom.RequestCredentials.type => dom.RequestCredentials): Unit =
        request.credentials = get(dom.RequestCredentials)

    def cache(get: dom.RequestCache.type => dom.RequestCache): Unit =
        request.cache = get(dom.RequestCache)

    def redirect(get: dom.RequestRedirect.type => dom.RequestRedirect): Unit =
        request.redirect = get(dom.RequestRedirect)

    def referrer(url: String): Unit =
        request.referrer = url

    def referrerClear(): Unit =
        request.referrer = ""

    def referrerPolicy(get: dom.ReferrerPolicy.type => dom.ReferrerPolicy): Unit =
        request.referrerPolicy = get(dom.ReferrerPolicy)

    def integrity(hash: String): Unit =
        request.integrity = hash

    def keepAlive(value: Boolean): Unit =
        request.keepalive = value

    def empty(): Unit = ()

object SafeFetchOptions:
    def apply[In](encodeRequest: In => dom.BodyInit): SafeFetchOptions[In] =
        new SafeFetchOptions(encodeRequest)
