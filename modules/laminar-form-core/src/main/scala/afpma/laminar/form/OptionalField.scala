/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

/** Marker for whether a form field is optional with an explanatory hint. */
enum OptionalField(val hint: Option[String]):
    case Yes(h: String) extends OptionalField(Some(h))
    case No             extends OptionalField(None)

object OptionalField:
    extension (opt: OptionalField)
        def isOptional: Boolean = opt match
            case Yes(_) => true
            case No     => false
