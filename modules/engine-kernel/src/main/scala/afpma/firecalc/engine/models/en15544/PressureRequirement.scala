/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import coulomb.*
import coulomb.policy.standard.given

// Section "4.10.1", "Pressure Condition"

case class PressureRequirement(
    sum_pr_pu          : Pressure,
    sum_ph             : Pressure,
    sum_ph_min_expected: Pressure,
    sum_ph_max_expected: Pressure
) {
    val min     = sum_ph_min_expected
    val current = sum_ph
    val max     = sum_ph_max_expected

    val `min-current` = min - current

    val `current-min` = current - min
    val `current-max` = current - max

    val isInValidRange: Boolean =
        if (min <= current && current <= max) true else false

    val isTooMuchDraft      = if (current > max) true else false
    val isTooMuchResistance = if (min > current) true else false
}

object PressureRequirement:
    given ShowUsingLocale[PressureRequirement] = showUsingLocale: p =>
        I18N.en15544_errors.pressure_requirement_display(p.current.show, p.min.show, p.max.show)
