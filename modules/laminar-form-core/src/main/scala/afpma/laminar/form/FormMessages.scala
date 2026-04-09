/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

/** I18n abstraction for form error/status messages.
  *
  * Implement this typeclass in firecalc-ui and wire to I18N_UI.
  */
trait FormMessages:
    def valueIsUndefined: String
    def notImplementedYet: String
