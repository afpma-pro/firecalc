/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

/** laminar-form-derivation — magnolia-based auto-derivation for Form[A].
  *
  * Provides:
  *   - FormDerivation: magnolia AutoDerivation[Form] with join/split
  *   - Primitive given instances (String, Double, Int, Boolean, LocalDate, etc.)
  *   - Factory methods: mkFromOptionFor, mk_AlwaysValid, conditionalOn, eitherFromOption
  *   - autoOverwriteFieldNames extension for i18n
  *   - OptionOfEither types for 3-way selects
  */
package object derivation:

    // Re-export key derivation extension
    export FormDerivation.autoOverwriteFieldNames
