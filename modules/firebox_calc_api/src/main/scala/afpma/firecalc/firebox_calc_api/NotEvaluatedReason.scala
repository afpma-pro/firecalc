/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Reason why a constraint could not be evaluated. `wire` is the normative
 * OpenAPI enum value (`NotEvaluatedReason` in `openapi/firebox-calc-api.yaml`).
 *
 * Wire-stable API contract. Any shape change requires a matching OpenAPI document change in the same commit.
 */
enum NotEvaluatedReason(val wire: String):
    case MissingMB            extends NotEvaluatedReason("missingMB")
    case MissingFlowRate      extends NotEvaluatedReason("missingFlowRate")
    case MissingTopologyShape extends NotEvaluatedReason("missingTopologyShape")
