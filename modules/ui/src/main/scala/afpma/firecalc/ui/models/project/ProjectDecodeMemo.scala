/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

/**
 * One-entry decode memo, held by [[ProjectStorage]].
 *
 * Why it exists: a single page-load reads the same project blob twice — the pre-render
 * `preloadProjectFromUrl` install, then `renderPage`'s existence check — and the mount
 * path reads it a third time. Decoding a (possibly large, possibly corrupt) YAML for
 * every read is pure waste, and it made the corrupt case decode twice per load.
 *
 * The memo keys on the id AND the exact payload that was decoded, so it can never
 * serve a stale outcome after a write: a new payload is a different value, and every
 * store write (`save`/`delete`) clears it anyway. One entry only, because all the
 * reads of a load concern one project.
 *
 * Pure by construction (no DOM): the hit/miss rules are unit-testable.
 */
final class ProjectDecodeMemo:

    private var cached: Option[(ProjectId, String, ProjectLoadOutcome)] = None

    /** The remembered outcome for that id decoded from that exact payload, if any. */
    def get(id: ProjectId, raw: String): Option[ProjectLoadOutcome] =
        cached match
            case Some((cachedId, cachedRaw, outcome)) if cachedId == id && cachedRaw == raw => Some(outcome)
            case _                                                                          => None

    /** Remember the outcome of decoding `raw` for `id`. */
    def remember(id: ProjectId, raw: String, outcome: ProjectLoadOutcome): Unit =
        cached = Some((id, raw, outcome))

    /** Forget everything (the stored payload changed or was removed). */
    def clear(): Unit =
        cached = None
