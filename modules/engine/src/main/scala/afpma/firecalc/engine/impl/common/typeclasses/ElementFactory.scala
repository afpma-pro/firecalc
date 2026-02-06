/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.typeclasses

import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

/**
 * Typeclass for creating pipe elements from incremental
 * descriptors.
 *
 * @tparam AddOp The "add element" operation type
 *               (e.g., AddSectionSlopped)
 * @tparam El The resulting pipe element type
 *            (e.g., StraightSection)
 * @tparam Ctx The context/state type needed for creation
 */
trait ElementFactory[AddOp, El, Ctx]:
    def make(op: AddOp)(using
        ctx: Ctx
    ): ValidatedNel[IncrementalValidation_Error, El]

object ElementFactory:
    def apply[A, E, C](using
        ev: ElementFactory[A, E, C]
    ): ElementFactory[A, E, C] = ev

    // Summoner with context - primary usage pattern
    def make[A, E, C](op: A)(using
        factory: ElementFactory[A, E, C],
        ctx    : C
    ): ValidatedNel[IncrementalValidation_Error, E] =
        factory.make(op)

    // Alternative: create factory bound to context
    def withContext[A, E, C](ctx: C)(using
        factory: ElementFactory[A, E, C]
    ): A => ValidatedNel[IncrementalValidation_Error, E] =
        op => factory.make(op)(using ctx)
