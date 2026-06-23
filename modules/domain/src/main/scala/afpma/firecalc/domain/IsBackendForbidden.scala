/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

/**
 * Marker trait for DTO types that must never appear in a wire payload sent to the
 * FireCalc payments backend.
 *
 * DTO variants carrying this trait are dev-only escape hatches (e.g. the
 * `SetInnerShapePreventSectionGeometryChangeAuto` DSL-only inner-shape setter).
 * They are legal to construct and run locally (labo / dev DSL), but the payments
 * backend rejects any project whose decoded DTO tree contains an
 * `IsBackendForbidden` instance — early, on the first UI→backend request
 * (`createPurchaseIntent`), before any database side effect — by raising a
 * `ForbiddenDtoException` (HTTP 403, error code `backend_forbidden_dto`).
 *
 * The trait is matched generically by the backend's `BackendForbiddenDtoChecker`
 * tree-walk, so adding a new dev-only DTO variant only requires mixing this trait
 * in — no backend code change.
 *
 * Note: this marker is the *backend rejection* signal. It is intentionally
 * distinct from behavioral engine markers (`SetsInnerShape`, `IsSectionGeometryChange`,
 * ...) and must NOT be consulted by engine code to decide engine behavior — the
 * engine matches the concrete DTO variant it acts on.
 */
trait IsBackendForbidden
