/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

/** Unit display metadata for numeric form fields. */
case class UnitDisplay(label: String, abbreviation: String)

/**
 * Typeclass for unit-aware numeric values.
 *
 * Provides conversion to/from Double and unit display information.
 * Implementations live in laminar-form-coulomb for coulomb types,
 * but the typeclass is defined here so derivation can use it generically.
 */
trait NumericFormValue[A]:
    def toDouble  (a: A     ): Double
    def fromDouble(d: Double): A
    def unitDisplays: List[UnitDisplay]
