/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import cats.Show

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
trait IsBackendForbidden:

    /**
     * Kind of forbidden DTO, used for display purposes.
     * Each concrete forbidden DTO returns the kind corresponding to its role.
     */
    def forbiddenKind: IsBackendForbidden.Kind

object IsBackendForbidden:

    /**
     * Enumeration of forbidden DTO kinds. Each kind maps to a specific
     * i18n message in `ShowUsingLocale[IsBackendForbidden]`.
     *
     * New kinds are added when a new category of forbidden DTO is introduced.
     * The concrete DTO mixes in `IsBackendForbidden` and returns the appropriate
     * kind from its `forbiddenKind` method.
     */
    sealed trait Kind
    case object SetsNumberOfFlowsKind         extends Kind
    case object SetsInnerShapePreventAutoKind extends Kind

    /**
     * Pairs a forbidden DTO with its position in the descriptor sequence.
     * Used by `BackendForbiddenDtoChecker` and UI error display so the user
     * knows which element in the pipe to look at.
     *
     * @param dto           The forbidden DTO instance
     * @param elementIndex  0-based index in the descriptor sequence (position of the
     *                      top-level descriptor element containing or being the DTO)
     */
    case class ForbiddenDtoFound(dto: IsBackendForbidden, elementIndex: Int)

    /**
     * Human-readable label for a forbidden DTO instance.
     * Dispatches on `forbiddenKind` declared by each concrete forbidden DTO.
     */
    given ShowUsingLocale[IsBackendForbidden] = showUsingLocale: (dto: IsBackendForbidden) =>
        dto.forbiddenKind match
            case SetsNumberOfFlowsKind         =>
                I18N.forbidden_dto.set_number_of_flows(
                    I18N.set_prop.SetNumberOfFlows,
                    I18N.split_merge.SplitSingleFlowIntoTwoFlowsWith90DegTurn,
                    I18N.split_merge.MergeTwoFlowsIntoSingleWith90DegTurn
                )
            case SetsInnerShapePreventAutoKind =>
                I18N.forbidden_dto.set_inner_shape_prevent_auto(
                    I18N.set_prop.SetInnerShape
                )

    /**
     * Human-readable label for a found forbidden DTO, including its position
     * in the descriptor sequence.
     */
    given ShowUsingLocale[ForbiddenDtoFound] = showUsingLocale: (found: ForbiddenDtoFound) =>
        val base   = Show[IsBackendForbidden].show(found.dto)
        val suffix = I18N.forbidden_dto.element_at((found.elementIndex + 1).toString)
        base + suffix
