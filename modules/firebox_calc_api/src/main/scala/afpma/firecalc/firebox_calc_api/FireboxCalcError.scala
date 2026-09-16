/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/** Wire-stable API contract. Any shape change requires a matching OpenAPI document change in the same commit. */
final case class FireboxCalcError(
    error  : String,
    message: String,
    details: Option[List[WireErrorItem]]
)

/**
 * Simple error envelope for errors without per-item details.
 * Used by: unknown_kind, kind_not_served, unsupported_locale,
 * unsupported_api_version, payload_too_large, rate_limited, internal_error.
 *
 * Defined here (firebox_calc_api) as part of the public wire contract.
 * The `payments-shared` module re-exports this type for use by the payments
 * routes. Any shape change requires a matching OpenAPI document change.
 */
final case class ErrorResponseEnvelope(
    error  : String,
    message: String
)

object ErrorResponseEnvelope:
    import io.circe.{Decoder, Encoder}
    import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

    given Encoder[ErrorResponseEnvelope] = deriveEncoder
    given Decoder[ErrorResponseEnvelope] = deriveDecoder
