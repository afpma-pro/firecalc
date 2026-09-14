/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.firebox_calc_api.WireFireboxKind

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
 * One cached firebox-calc response, keyed per (project, kind) and stamped with
 * the fingerprint of the request that produced it.
 *
 * `fingerprint` is the whole point of the record: a response computed for
 * *different* firebox geometry is worse than no cache at all, because the UI
 * would show stale numbers as if fresh. Storing the fingerprint alongside the
 * payload lets [[FireboxCalcCache.read]] treat a mismatch as a miss instead of
 * silently serving the wrong geometry.
 *
 * @param projectId   the id the entry was written under, repeated inside the
 *                    record so a mis-keyed read (or a hand-edited
 *                    `localStorage`) cannot pass as a hit
 * @param kind        the wire kind the response was computed for
 * @param fingerprint fingerprint of the exact request body (see
 *                    [[FireboxCalcCache.fingerprint]])
 * @param savedAtMs   epoch milliseconds at write time, surfaced to the user so
 *                    cached results are labelled with their age
 * @param responseBody the verbatim `FireboxCalcResponse` JSON, decoded by the
 *                    same code path as a live response so cache and network
 *                    cannot drift
 */
final case class FireboxCalcCacheEntry(
    projectId   : String,
    kind        : String,
    fingerprint : String,
    savedAtMs   : Double,
    responseBody: String
)

object FireboxCalcCacheEntry:
    given Encoder[FireboxCalcCacheEntry] = deriveEncoder
    given Decoder[FireboxCalcCacheEntry] = deriveDecoder

/**
 * Pure key/fingerprint/validation logic for the firebox-calc response cache.
 * No `localStorage`, no DOM — the storage adapter lives in
 * `FireboxCalcCacheStore`, which is a thin shell over this.
 *
 * Design decisions, stated once here:
 *
 *  - '''Key includes both project id and kind.''' The user was explicit that
 *    caching must be per `project_id`; the kind is in the key too because one
 *    project can be edited across kinds and a Traditional response must never
 *    be served for an Ecolabeled firebox. The project identifier is
 *    `ProjectId#value` — the same string
 *    `LocalStorageKeys.projectFireboxCache` already keys per-project state
 *    with, not an invented id.
 *  - '''Fingerprint invalidation.''' The fingerprint is a 64-bit FNV-1a digest
 *    of the exact request body that was POSTed, rendered hex. It is stored
 *    inside the record (not only in the key) so a read carries proof of what it
 *    was computed for, and [[read]] rejects a mismatch as a miss. FNV-1a is
 *    chosen over a crypto hash because this is a staleness check, not a
 *    security boundary, and it must run synchronously in Scala.js with no
 *    dependency: `crypto.subtle.digest` is Promise-based and therefore unusable
 *    on the synchronous read path.
 *  - '''`localStorage`, not IndexedDB.''' A `FireboxCalcResponse` is a small
 *    JSON document (contributions, verdicts, report data — kilobytes, no
 *    geometry meshes or images), so it fits the ~5 MB synchronous budget many
 *    times over. [[exceedsBudget]] guards the pathological case rather than
 *    assuming: an oversized payload is simply not cached, which degrades to
 *    today's behaviour instead of throwing `QuotaExceededError`.
 */
object FireboxCalcCache:

    /** Key prefix, mirroring `LocalStorageKeys.projectFireboxCache`'s shape. */
    private val KeyPrefix = "project_"

    /**
     * Largest response we will put in `localStorage`, in UTF-16 characters.
     *
     * 512 K chars ≈ 1 MB of the ~5 MB budget, leaving ample room for the app
     * state and catalog that share it. Above this the response is not cached.
     */
    val MaxCachedChars: Int = 512 * 1024

    /**
     * The `localStorage` key for one (project, kind) pair.
     *
     * Both components are in the key, so two projects — and two kinds within
     * one project — can never read each other's results.
     */
    def key(projectId: String, kind: WireFireboxKind): String =
        s"$KeyPrefix${projectId}_firebox_calc_${kind.wire}"

    /**
     * 64-bit FNV-1a digest of `requestBody`, hex-rendered.
     *
     * Pure and total: the same body always yields the same string, and any
     * single-character difference (a changed dimension, a different locale, a
     * different air-intake shape) yields a different one. That is exactly the
     * "inputs changed → cache miss" property required.
     */
    def fingerprint(requestBody: String): String =
        var hash = 0xcbf29ce484222325L
        var i    = 0
        while i < requestBody.length do
            hash = (hash ^ requestBody.charAt(i).toLong) * 0x100000001b3L
            i += 1
        java.lang.Long.toHexString(hash)

    /** Whether a response is too large for the synchronous `localStorage` path. */
    def exceedsBudget(responseBody: String): Boolean =
        responseBody.length > MaxCachedChars

    /**
     * Build the record to persist for a successful response.
     *
     * Takes the request body rather than a pre-computed fingerprint so callers
     * cannot pair a payload with the wrong digest.
     */
    def entryFor(
        projectId      : String,
        kind           : WireFireboxKind,
        requestBody    : String,
        responseBody   : String,
        savedAtMs      : Double
    ): FireboxCalcCacheEntry =
        FireboxCalcCacheEntry(
            projectId    = projectId,
            kind         = kind.wire,
            fingerprint  = fingerprint(requestBody),
            savedAtMs    = savedAtMs,
            responseBody = responseBody
        )

    /**
     * Why a cache read did not produce a usable response.
     *
     * Named rather than collapsed to `Option`, because "no entry" and "the
     * inputs changed" are different diagnostics and the console line that
     * reports them should say which happened.
     */
    enum Miss:
        /** No record stored under this (project, kind) key. */
        case NoEntry

        /** A record exists but was computed for a different request. */
        case FingerprintMismatch(stored: String, expected: String)

        /** A record exists but is keyed for another project or kind. */
        case WrongOwner(storedProjectId: String, storedKind: String)

        /** A record exists but is not decodable — treat as absent. */
        case Corrupt(detail: String)

    /**
     * Validate a stored record against the request we are about to satisfy.
     *
     * `Right(entry)` only when the record belongs to this exact (project, kind)
     * pair AND was computed from a request with the same fingerprint. Everything
     * else is a [[Miss]] — never a partial hit.
     */
    def validate(
        entry      : FireboxCalcCacheEntry,
        projectId  : String,
        kind       : WireFireboxKind,
        requestBody: String
    ): Either[Miss, FireboxCalcCacheEntry] =
        if entry.projectId != projectId || entry.kind != kind.wire then
            Left(Miss.WrongOwner(entry.projectId, entry.kind))
        else
            val expected = fingerprint(requestBody)
            if entry.fingerprint == expected then Right(entry)
            else Left(Miss.FingerprintMismatch(entry.fingerprint, expected))

    /**
     * Decode + validate a raw `localStorage` value.
     *
     * `null`/blank (the shape `localStorage.getItem` returns for an absent key)
     * is [[Miss.NoEntry]]; undecodable JSON is [[Miss.Corrupt]] rather than an
     * exception, so a schema change in a previous build can never break boot.
     */
    def read(
        raw        : String,
        projectId  : String,
        kind       : WireFireboxKind,
        requestBody: String
    ): Either[Miss, FireboxCalcCacheEntry] =
        if raw == null || raw.trim.isEmpty then Left(Miss.NoEntry)
        else
            io.circe.parser.decode[FireboxCalcCacheEntry](raw) match
                case Right(entry) => validate(entry, projectId, kind, requestBody)
                case Left(err)    => Left(Miss.Corrupt(err.getMessage))

end FireboxCalcCache
