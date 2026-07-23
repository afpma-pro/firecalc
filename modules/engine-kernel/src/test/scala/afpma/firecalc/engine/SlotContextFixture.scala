/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import afpma.firecalc.engine.standard.*

/**
 * Shared test fixture providing a default SlotContext for slot 0.
 *
 * Most tests build errors for slot 0 (the first post-firebox pipe).
 * Mix in this trait to get `given SlotContext` without repeating
 * `SlotContext.forSlot(SlotIndex.unsafe(0))` at every call site.
 *
 * For unslotted paths (air intake, firebox), use [[UnslottedFixture]].
 * For a specific slot, override `given slotContext`.
 */
trait Slot0ContextFixture:
    given slotContext: SlotContext = SlotContext.forSlot(SlotIndex.unsafe(0))

/** Fixture for unslotted computation paths (air intake, firebox, standalone pipes). */
trait UnslottedFixture:
    given slotContext: SlotContext = SlotContext.unslotted
