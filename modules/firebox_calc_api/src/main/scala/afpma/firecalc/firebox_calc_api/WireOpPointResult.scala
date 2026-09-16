/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * One entry of the operating-point-keyed `asResult` grid (round-3 decision D3).
 *
 * Each entry is a solved [[WirePipeResult]] valid at EXACTLY ONE operating
 * point — the (draftCondition, loadQty) pair it is keyed by. The
 * [[WireFireboxContribution.AsResult]] list covers every point the standard
 * evaluates for the requested firebox; (draftCondition, loadQty) pairs are
 * unique within the list (enforced by the decoder — a duplicate key is a
 * decoding failure).
 */
final case class WireOpPointResult(
    draftCondition: WireDraftCondition,
    loadQty       : WireLoadQty,
    result        : WirePipeResult
)
