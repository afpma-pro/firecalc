/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.engine.models

/**
 * Shared predicates for inspecting pipe descriptor sequences.
 *
 * Property elements (`SetInnerShape`, `SetOuterShape`, etc.) are metadata
 * that the incremental builder consumes before producing engine elements.
 * When checking "is the first element a split?", property elements must be
 * skipped so the first *real* element is inspected.
 *
 * Used by:
 *   - `FireboxSplitFrame.startsWithSplit` (engine-kernel)
 *   - `PanelStatusHelper.fireboxSplitWarning` (ui)
 */
object ElementPredicates:

    /**
     * Get the first non-property element if it matches the given predicate.
     *
     * Only inspects the first element after property ops are skipped.
     * Does NOT search the rest of the sequence.
     *
     * @param elems the descriptor sequence
     * @param isProperty predicate identifying property elements to skip
     * @param predicate predicate identifying the target element
     * @return `Some(elem)` if the first non-property element matches, `None` otherwise
     */
    def findFirstAddElement[E](
        elems     : Seq[E],
        isProperty: E => Boolean,
        predicate : E => Boolean
    ): Option[E] =
        elems.dropWhile(isProperty).headOption.filter(predicate)

    /**
     * Check if the first non-property element matches the given predicate.
     *
     * @return `true` if the first real element (skipping property ops) matches `predicate`
     */
    def startsWith[E](
        elems     : Seq[E],
        isProperty: E => Boolean,
        predicate : E => Boolean
    ): Boolean =
        findFirstAddElement(elems, isProperty, predicate).isDefined

end ElementPredicates
