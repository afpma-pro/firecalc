/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import cats.data.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.models.PipeResult

import afpma.firecalc.units.coulombutils.*
import coulomb.ops.standard.all.given
import afpma.firecalc.engine.standard.MecaFlu_Error

trait Pressures_EN13384:
    type A
    extension (a: A)
        def en13384_ph: ValidatedNel[MecaFlu_Error, Pressure]
        def en13384_pr_all: ValidatedNel[MecaFlu_Error, Pressure]

        final def `en13384_pr_all-ph`: ValidatedNel[MecaFlu_Error, Pressure] =
            (en13384_pr_all, en13384_ph).mapN: 
                (pr_all, ph) => pr_all - ph


object Pressures_EN13384:

    type ForPipeResult = Pressures_EN13384 {
        type A = PipeResult
    }

    given ForPipeResult = new Pressures_EN13384 {
        type A = PipeResult
        extension (a: A)
            def en13384_ph: ValidatedNel[MecaFlu_Error, Pressure] =
                a.ph.validNel

            def en13384_pr_all: ValidatedNel[MecaFlu_Error, Pressure] =
                a.`pR+pu`
    }