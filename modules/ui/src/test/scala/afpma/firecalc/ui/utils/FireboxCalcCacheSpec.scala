/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.firebox_calc_api.WireFireboxKind

import io.circe.syntax.*

import utest.*

/**
 * Unit tests for [[FireboxCalcCache]] — the pure key/fingerprint logic behind
 * the per-project firebox-calc response cache (Q10-d part B).
 *
 * Everything asserted here is DOM-free by construction: the `localStorage`
 * side effects live in `FireboxCalcCacheStore`, which contains no decisions.
 *
 * Acceptance covered:
 *  - the key includes BOTH the project id and the kind, so two projects — and
 *    two kinds within one project — can never read each other's results;
 *  - a fingerprint mismatch (i.e. the firebox inputs changed) is a MISS, so
 *    stale numbers are never replayed as if freshly computed.
 */
object FireboxCalcCacheSpec extends TestSuite:

    private val bodyA = """{"kind":"ecolabeled","firebox":{"width":500}}"""
    private val bodyB = """{"kind":"ecolabeled","firebox":{"width":501}}"""

    private def entry(
        projectId  : String,
        kind       : WireFireboxKind,
        requestBody: String,
        response   : String = """{"ok":true}"""
    ): FireboxCalcCacheEntry =
        FireboxCalcCache.entryFor(projectId, kind, requestBody, response, savedAtMs = 1_700_000_000_000d)

    val tests = Tests:

        // ── Key identity: project id AND kind both participate ─────────────

        test("key — includes the project id") {
            val k1 = FireboxCalcCache.key("prj-1", WireFireboxKind.Ecolabeled)
            val k2 = FireboxCalcCache.key("prj-2", WireFireboxKind.Ecolabeled)
            assert(k1 != k2            )
            assert(k1.contains("prj-1"))
            assert(k2.contains("prj-2"))
        }

        test("key — includes the firebox kind") {
            val eco  = FireboxCalcCache.key("prj-1", WireFireboxKind.Ecolabeled)
            val trad = FireboxCalcCache.key("prj-1", WireFireboxKind.Traditional)
            assert(eco != trad                                    )
            assert(eco.contains(WireFireboxKind.Ecolabeled.wire)  )
            assert(trad.contains(WireFireboxKind.Traditional.wire))
        }

        test("key — same project and kind is stable across calls") {
            assert(
                FireboxCalcCache.key("prj-1", WireFireboxKind.AfpmaPrse) ==
                    FireboxCalcCache.key("prj-1", WireFireboxKind.AfpmaPrse)
            )
        }

        // ── Fingerprint: identical inputs match, changed inputs do not ─────

        test("fingerprint — identical request bodies agree") {
            assert(FireboxCalcCache.fingerprint(bodyA) == FireboxCalcCache.fingerprint(bodyA))
        }

        test("fingerprint — a one-character input change differs") {
            assert(FireboxCalcCache.fingerprint(bodyA) != FireboxCalcCache.fingerprint(bodyB))
        }

        test("fingerprint — order of fields matters (different body, different digest)") {
            val reordered = """{"firebox":{"width":500},"kind":"ecolabeled"}"""
            assert(FireboxCalcCache.fingerprint(bodyA) != FireboxCalcCache.fingerprint(reordered))
        }

        // ── validate: hit only on exact (project, kind, fingerprint) ───────

        test("validate — same project, kind and inputs is a HIT") {
            val e = entry("prj-1", WireFireboxKind.Ecolabeled, bodyA)
            assert(FireboxCalcCache.validate(e, "prj-1", WireFireboxKind.Ecolabeled, bodyA).isRight)
        }

        test("validate — changed inputs is a fingerprint-mismatch MISS") {
            val e = entry("prj-1", WireFireboxKind.Ecolabeled, bodyA)
            FireboxCalcCache.validate(e, "prj-1", WireFireboxKind.Ecolabeled, bodyB) match
                case Left(FireboxCalcCache.Miss.FingerprintMismatch(stored, expected)) =>
                    assert(stored == FireboxCalcCache.fingerprint(bodyA)  )
                    assert(expected == FireboxCalcCache.fingerprint(bodyB))
                case other                                                             =>
                    throw new java.lang.AssertionError(s"expected FingerprintMismatch, got $other")
        }

        test("validate — another project's entry is a MISS even with identical inputs") {
            val e = entry("prj-OTHER", WireFireboxKind.Ecolabeled, bodyA)
            FireboxCalcCache.validate(e, "prj-1", WireFireboxKind.Ecolabeled, bodyA) match
                case Left(FireboxCalcCache.Miss.WrongOwner(prj, _)) => assert(prj == "prj-OTHER")
                case other                                          => throw new java.lang.AssertionError(s"expected WrongOwner, got $other")
        }

        test("validate — another kind's entry is a MISS even with identical inputs") {
            val e = entry("prj-1", WireFireboxKind.Traditional, bodyA)
            FireboxCalcCache.validate(e, "prj-1", WireFireboxKind.Ecolabeled, bodyA) match
                case Left(FireboxCalcCache.Miss.WrongOwner(_, kind)) =>
                    assert(kind == WireFireboxKind.Traditional.wire)
                case other                                           => throw new java.lang.AssertionError(s"expected WrongOwner, got $other")
        }

        // ── read: raw-string entry point, round-trip through the codec ─────

        test("read — round-trips a written entry and serves the response body") {
            val e   = entry("prj-1", WireFireboxKind.Ecolabeled, bodyA, response = """{"ok":1}""")
            val raw = e.asJson.noSpaces
            FireboxCalcCache.read(raw, "prj-1", WireFireboxKind.Ecolabeled, bodyA) match
                case Right(hit) =>
                    assert(hit.responseBody == """{"ok":1}""" )
                    assert(hit.savedAtMs == 1_700_000_000_000d)
                case Left(miss) => throw new java.lang.AssertionError(s"expected a hit, got $miss")
        }

        test("read — a stored entry does NOT satisfy a request with different inputs") {
            val raw = entry("prj-1", WireFireboxKind.Ecolabeled, bodyA).asJson.noSpaces
            assert(FireboxCalcCache.read(raw, "prj-1", WireFireboxKind.Ecolabeled, bodyB).isLeft)
        }

        test("read — absent key (null / blank) is NoEntry, not an exception") {
            assert(
                FireboxCalcCache.read(null, "prj-1", WireFireboxKind.Ecolabeled, bodyA) ==
                    Left(FireboxCalcCache.Miss.NoEntry)
            )
            assert(
                FireboxCalcCache.read("   ", "prj-1", WireFireboxKind.Ecolabeled, bodyA) ==
                    Left(FireboxCalcCache.Miss.NoEntry)
            )
        }

        test("read — undecodable JSON is Corrupt, not an exception") {
            FireboxCalcCache.read("{not json", "prj-1", WireFireboxKind.Ecolabeled, bodyA) match
                case Left(FireboxCalcCache.Miss.Corrupt(_)) => ()
                case other                                  => throw new java.lang.AssertionError(s"expected Corrupt, got $other")
        }

        // ── Size budget: an oversized payload is not cached ────────────────

        test("exceedsBudget — a small payload fits, an oversized one does not") {
            assert(!FireboxCalcCache.exceedsBudget("""{"ok":true}""")                         )
            assert(FireboxCalcCache.exceedsBudget("x" * (FireboxCalcCache.MaxCachedChars + 1)))
        }

        test("entryFor — stamps the request's fingerprint, project and kind") {
            val e = entry("prj-7", WireFireboxKind.Door15aCatalog, bodyA)
            assert(e.projectId == "prj-7"                              )
            assert(e.kind == WireFireboxKind.Door15aCatalog.wire       )
            assert(e.fingerprint == FireboxCalcCache.fingerprint(bodyA))
        }

end FireboxCalcCacheSpec
