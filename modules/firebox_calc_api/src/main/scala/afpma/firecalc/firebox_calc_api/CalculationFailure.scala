/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Typed failure taxonomy for the shared calculation client.
 *
 * Distinguishes three failure classes so callers can react appropriately:
 *
 *   - [[TransportError]] — the server was unreachable (connection refused,
 *     DNS failure, TLS handshake failure, socket timeout). The caller may
 *     retry with back-off or fall back to a local engine.
 *
 *   - [[ProtocolError]] — the server responded with a documented error status
 *     (400, 404, 413, 422, 429, 500). The `error` field matches the OpenAPI
 *     taxonomy (§7). No failure can be mistaken for a valid calculation.
 *
 *   - [[DecodeError]] — the server responded with 200 OK but the body could
 *     not be decoded as the expected wire type. This indicates a protocol
 *     mismatch or server bug.
 *
 * No subtype carries Austrian model, constraint, formula, transformer,
 * registration, or engine implementation details.
 */
sealed trait CalculationFailure extends Product with Serializable:
    def description: String

    /**
     * The per-item details of a conformant OpenAPI §7 `CalculationFailed`
     * payload, `None` for every other failure.
     *
     * Recognizes the payload SHAPE — HTTP status 422 paired with the
     * `calculation_failed` error discriminator, carrying one [[WireErrorItem]]
     * per item (already localized in the request locale) — which today is
     * produced by the pre-solve geometry pre-check. The shape is not a claim
     * about the cause: the discriminator covers failed computation broadly
     * (the shipped OpenAPI 422 example carries `NonPositivePipeGeometry`
     * and `GeometryViolation:*` items), so treat the details as the
     * server's verdict list, not a promise of geometry
     * violations. This is the single canonical predicate every consumer must
     * use instead of matching on the status itself: a 400 `malformed_request`
     * may also carry per-item details, and those never form the §7 pairing.
     * The pairing is required TOGETHER with non-empty details: conformant
     * server responses are documented to carry items, so a details-less 422
     * yields `None` and keeps generic failure handling. Transport errors,
     * decode errors, and other protocol errors always yield `None`.
     */
    def preCheckDetails: Option[List[WireErrorItem]] = None

object CalculationFailure:

    /**
     * The OpenAPI §7 `CalculationFailed` pairing: HTTP status 422 with the
     * `calculation_failed` error discriminator. See
     * [[CalculationFailure.preCheckDetails]] — the only predicate allowed to
     * classify a pre-check rejection.
     */
    private[CalculationFailure] val PreCheckStatus: Int    = 422
    private[CalculationFailure] val PreCheckError : String = "calculation_failed"

    /**
     * Transport-layer failure: the server was unreachable.
     *
     * Covers connection refused, DNS failure, TLS errors, socket timeout,
     * and any other I/O exception before a response is received.
     */
    final case class TransportError(cause: Throwable) extends CalculationFailure:
        def description: String    =
            s"Transport error: ${cause.getMessage}"
        def causeMsg   : Throwable = cause

    /**
     * Protocol error: the server responded with a documented error status.
     *
     * The `error` field matches the OpenAPI error taxonomy (§7):
     * `unknown_kind`, `kind_not_served`, `malformed_request`,
     * `unsupported_locale`, `unsupported_api_version`, `calculation_failed`,
     * `payload_too_large`, `rate_limited`, `internal_error`.
     *
     * @param status HTTP status code (400, 404, 413, 422, 429, 500)
     * @param error  Error code from the response envelope
     * @param message Human-readable error message
     * @param details Per-item error details (present for malformed_request and calculation_failed)
     */
    final case class ProtocolError(
        status : Int,
        error  : String,
        message: String,
        details: Option[List[WireErrorItem]] = None
    ) extends CalculationFailure:
        def description: String =
            s"Protocol error $status ($error): $message"

        override def preCheckDetails: Option[List[WireErrorItem]] =
            if status == CalculationFailure.PreCheckStatus && error == CalculationFailure.PreCheckError then
                details.filter(_.nonEmpty)
            else None

    /**
     * Decode error: the server responded with success but the body could not
     * be decoded as the expected wire type.
     *
     * This indicates a protocol version mismatch or server bug. The raw
     * response body is captured for diagnostics.
     */
    final case class DecodeError(
        cause  : Throwable,
        rawBody: String
    ) extends CalculationFailure:
        def description: String    =
            s"Decode error: ${cause.getMessage}"
        def causeMsg   : Throwable = cause

end CalculationFailure
