/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.models.schema.AppStateSchema

/**
 * The three-way result of reading a project's persisted state.
 *
 * The read used to be an `Option[AppStateSchema]`, which collapsed two different
 * situations into one: "this project was never stored" and "it IS stored, but its
 * bytes no longer decode" (schema drift, a half-written payload, an external edit).
 * Only the first is absent. A corrupt payload is data the user still owns — the app
 * must say so and point at the projects list (deleting the card is the recovery),
 * instead of bouncing to the selector as if the project had never existed.
 *
 * A corrupt payload is never rewritten or dropped by a read: the bytes stay put.
 */
enum ProjectLoadOutcome:
    /** No state stored for that id (no key, or an empty value). */
    case Missing

    /** Something is stored, but it does not decode to any known schema version. */
    case Corrupt

    /** Something is stored and it decoded (after schema migration). */
    case Ok(schema: AppStateSchema)
end ProjectLoadOutcome
