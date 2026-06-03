/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (c) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v7.PostFireboxInitialDirection
import afpma.firecalc.dto.v7.PostFireboxInitialPosition

import afpma.firecalc.ui.instances.ValidateVarCommonInstances

import cats.Show

import _root_.coulomb.*
import _root_.coulomb.policy.standard.given
import afpma.laminar.form.*
import afpma.laminar.form.derivation.FormDerivation
import io.taig.babel.Locale

/**
 * Form and Show instances for V7 wrapper-level types.
 * PostFireboxInitialDirection/Position are the V7 equivalents of
 * SetInitialDirection/SetInitialPosition but live at the PostFireboxPipes
 * wrapper level rather than inside slot descriptors.
 */
class V7FormInstances(using du: DisplayUnits, loc: Locale):

    private given horizontal_form: HorizontalFormCommonInstances =
        new HorizontalFormCommonInstances(using du, loc)
    import horizontal_form.*

    // ── Show instances ───────────────────────────────────────────

    given Show[PostFireboxInitialDirection] = Show.show: d =>
        import afpma.firecalc.ui.instances.DirectionFormat
        DirectionFormat.compact(d.azimuth, d.inclination)(using loc)

    given Show[PostFireboxInitialPosition] = Show.show: p =>
        val x = p.x.toUnit[Meter].value
        val y = p.y.toUnit[Meter].value
        val z = p.z.toUnit[Meter].value
        f"($x%.2f, $y%.2f, $z%.2f) m"

    // ── Form instances ───────────────────────────────────────────

    given Form[PostFireboxInitialDirection] =
        given Defaultable[PostFireboxInitialDirection] =
            Defaultable(PostFireboxInitialDirection.default)
        given ValidateVar[PostFireboxInitialDirection] =
            ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid[PostFireboxInitialDirection]
        Form.makeFor[PostFireboxInitialDirection](summon[Defaultable[PostFireboxInitialDirection]]): (variable, _) =>
            val azVar   = variable.zoomLazy(_.azimuth)((d, az) => d.copy(azimuth = az))
            val inclVar = variable.zoomLazy(_.inclination)((d, incl) => d.copy(inclination = incl))
            renderInitialDirectionForm(azVar, inclVar)

    given Form[PostFireboxInitialPosition] =
        given Form[Length] = horizontal_form_Length_cm_m
        autoDeriveAndOverwriteFieldNames[PostFireboxInitialPosition]

end V7FormInstances
