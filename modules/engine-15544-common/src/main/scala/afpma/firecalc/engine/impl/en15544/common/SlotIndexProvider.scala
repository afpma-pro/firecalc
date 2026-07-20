/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import afpma.firecalc.engine.standard.SlotIndex

/**
 * Type-safe provider for mapping local fold indices to global SlotIndex values.
 *
 * Encapsulates the local→global offset arithmetic so fold bodies can't
 * accidentally use a local index where a global one is required. Prevents
 * cross-panel error leakage in PanelScope.SlotScope (which matches by index
 * only, no pipe type check).
 *
 * Usage:
 *   // Prefix fold (local == global):
 *   val provider = SlotIndexProvider.prefix
 *   slots.zipWithIndex.foldLeft(…) { (acc, (slot, i)) =>
 *     slot.doSomething(using provider(i))
 *   }
 *
 *   // Suffix fold (local needs offset):
 *   val provider = SlotIndexProvider.suffix(baseOffset = lastFluePipeSlotIdx + 1)
 *   slots.zipWithIndex.foldLeft(…) { (acc, (slot, i)) =>
 *     slot.doSomething(using provider(i))
 *   }
 */
object SlotIndexProvider:

    /**
     * Provider for prefix folds where local index equals global index.
     *
     * Used when iterating over `pfbSlots.take(lastFluePipeSlotIdx + 1)` —
     * the flue region starting at global index 0.
     */
    def prefix: Int => SlotIndex =
        i => SlotIndex.unsafe(i)

    /**
     * Provider for suffix folds where local index needs an offset.
     *
     * Used when iterating over `pfbSlots.drop(baseOffset)` — the post-flue
     * region (connector + chimney) where local index 0 corresponds to
     * global index `baseOffset`.
     *
     * @param baseOffset the global index of the first slot in the suffix
     */
    def suffix(baseOffset: Int): Int => SlotIndex =
        i => SlotIndex.unsafe(baseOffset + i)
