/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * What a firebox contributes for one gas path, on the wire.
 *
 * `AsResult` is operating-point-keyed (round-3 decision D3): the grid carries
 * one [[WireOpPointResult]] per operating point the standard evaluates for the
 * requested firebox, and each entry is valid EXACTLY at the point it is keyed
 * by. The list is non-empty (`minItems: 1`) and its (draftCondition, loadQty)
 * keys are unique — uniqueness is a documented semantic constraint enforced by
 * the decoder (a duplicate key is a decoding failure). A bare, unkeyed result
 * is unrepresentable on the wire.
 */
sealed trait WireFireboxContribution extends Product with Serializable

object WireFireboxContribution:
    case object Without extends WireFireboxContribution

    case class AsDescr(descr: WirePipeFullDescr) extends WireFireboxContribution

    case class AsResult(results: List[WireOpPointResult]) extends WireFireboxContribution
