/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

enum OptionalField(val hint: Option[String]):
    case Yes(h: String) extends OptionalField(Some(h))
    case No extends OptionalField(None)

object OptionalField:
    extension (opt: OptionalField)
        def isOptional: Boolean = opt match
            case Yes(_) => true
            case No => false
