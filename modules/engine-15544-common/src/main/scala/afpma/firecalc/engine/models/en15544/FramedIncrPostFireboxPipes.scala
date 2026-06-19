/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.geometry.PostFireboxStartPositionMode

/**
 * Engine-side framed wrapper for the '''raw IncrDescr/seed input layer''' of the
 * post-firebox pipe chain — mirrors the DTO wire-format wrapper
 * `afpma.firecalc.dto.v7.FramedPostFireboxPipes`, but carries engine-typed payloads.
 *
 * '''Layer distinction.''' This wrapper belongs to the '''IncrDescr-input/seed layer'''
 * (`IncrementalPipeInputs_15544`), NOT the FullDescr-result layer (`Pipes_15544_*`).
 * It carries:
 *   - the raw user-intent mode ([[positionMode]]) and the loader's resolved projection
 *     ([[resolvedPosition]]),
 *   - the wrapper-level initial direction seed ([[initialDirection]]) for the first
 *     pipe's initial frame in `flueRegionPipeResults` folds,
 *   - the ordered post-firebox pipe descriptor slots ([[slots]]) typed against the
 *     engine-side [[PostFireboxPipeSlot]] enum.
 *
 * These are consumed by two SEPARATE validation pipelines that both walk the raw
 * `IncrDescr` feedstock:
 *   1. ''Incremental assembly'' (`mkPipeFromIncrDescr` → `FullDescr` in
 *      `pipes.airIntake`/flue-region results) — structural validation.
 *   2. ''Direction reachability'' (`DirectionReachability.checkPostFireboxChain`) —
 *      geometric validation; operates on `slots` + `initialDirection`, NOT on FullDescr.
 *
 * @param initialDirection wrapper-level initial direction seed for the first pipe's frame
 * @param positionMode     raw user-intent mode (Auto vs Manual override) — mirrors DTO `PostFireboxStartPosition`
 * @param resolvedPosition loader's resolved `Option[Position3D]` projection (None for Auto modes;
 *                         `Some(pos)` for Manual) — co-located with `positionMode` per the (β) decision
 * @param slots            ordered post-firebox pipe descriptor slots (engine enum)
 */
final case class FramedIncrPostFireboxPipes(
    initialDirection: Option[PipeInitialDirection],
    positionMode    : PostFireboxStartPositionMode,
    resolvedPosition: Option[Position3D],
    slots           : Seq[PostFireboxPipeSlot]
)
