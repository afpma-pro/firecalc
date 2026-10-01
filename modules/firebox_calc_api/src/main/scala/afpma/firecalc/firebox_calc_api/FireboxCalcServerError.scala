/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Typed error taxonomy for the Firebox Calculation API (§7).
 *
 * Maps to HTTP status codes in the route layer (S4.2):
 *   - UnknownKind         → 400  (ErrorResponseEnvelope, error="unknown_kind")
 *   - KindNotServed       → 404  (ErrorResponseEnvelope, error="kind_not_served")
 *   - MalformedRequest    → 400  (FireboxCalcError with details, error="malformed_request")
 *   - UnsupportedLocale   → 400  (ErrorResponseEnvelope, error="unsupported_locale")
 *   - UnsupportedApiVersion → 400  (ErrorResponseEnvelope, error="unsupported_api_version")
 *   - UnsupportedStandard → 400  (ErrorResponseEnvelope, error="unsupported_standard")
 *   - CalculationFailed   → 422  (FireboxCalcError with details, error="calculation_failed")
 *   - InternalError       → 500  (ErrorResponseEnvelope, error="internal_error")
 *
 * Missing m_B and blocking-constraint-violation are NOT errors (200 OK).
 * Rate-limited (429) and payload-too-large (413) are handled by middleware.
 */
sealed trait FireboxCalcServerError extends Product with Serializable

object FireboxCalcServerError:

    case class UnknownKind(
        received : String,
        supported: List[String]
    ) extends FireboxCalcServerError

    case class KindNotServed(
        kind    : String,
        registry: String
    ) extends FireboxCalcServerError

    case class MalformedRequest(
        details: List[WireErrorItem]
    ) extends FireboxCalcServerError

    case class UnsupportedLocale(
        requested: String,
        supported: List[String]
    ) extends FireboxCalcServerError

    case class UnsupportedApiVersion(
        requested: String,
        supported: List[String]
    ) extends FireboxCalcServerError

    case class UnsupportedStandard(
        requested: String,
        supported: List[String]
    ) extends FireboxCalcServerError

    case class CalculationFailed(
        details: List[WireErrorItem]
    ) extends FireboxCalcServerError

    case class InternalError(
        message: String
    ) extends FireboxCalcServerError

end FireboxCalcServerError
