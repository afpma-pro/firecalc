/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Unit tests for [[FireboxEngineSourceConfig]] — the cross-platform per-kind
 * engine-source vocabulary (tickets 09 + 10: Ecolabeled and Door15aCatalog
 * calculation are remote-only).
 *
 * `fromRemoteKeys` is the exact seam the Scala.js UI uses to parse
 * `VITE_FIREBOX_REMOTE_KINDS` + `VITE_FIREBOX_CALC_BASE_URL`, so the deployed
 * env value `ecolabeled,door15a-catalog` must route exactly those two kinds —
 * and no other — to the remote source, and a configuration typo must be
 * reported rather than silently dropped.
 */
class FireboxEngineSourceConfigSuite extends AnyFreeSpec with Matchers:

    private val baseUri = "http://localhost:9000/v1"

    "fromRemoteKeys" - {

        "a lone 'ecolabeled' key routes exactly Ecolabeled to the remote source" in {
            val (cfg, unknown) = FireboxEngineSourceConfig.fromRemoteKeys(List("ecolabeled"), baseUri)
            unknown shouldBe empty
            cfg.sourceFor(FireboxKind.Ecolabeled) shouldBe FireboxEngineSource.Remote(baseUri)
            cfg.isRemote(FireboxKind.Ecolabeled) shouldBe true
        }

        "every other closed kind stays Local under a lone 'ecolabeled' key" in {
            val (cfg, _) = FireboxEngineSourceConfig.fromRemoteKeys(List("ecolabeled"), baseUri)
            (FireboxKind.closed - FireboxKind.Ecolabeled).foreach { kind =>
                cfg.sourceFor(kind) shouldBe FireboxEngineSource.Local
                cfg.isRemote(kind) shouldBe false
            }
        }

        "the deployed value 'ecolabeled,door15a-catalog' routes exactly the two Austrian kinds remotely" in {
            val (cfg, unknown) =
                FireboxEngineSourceConfig.fromRemoteKeys(List("ecolabeled", "door15a-catalog"), baseUri)
            unknown shouldBe empty
            cfg.sourceFor(FireboxKind.Ecolabeled) shouldBe FireboxEngineSource.Remote(baseUri)
            cfg.sourceFor(FireboxKind.Door15aCatalog) shouldBe FireboxEngineSource.Remote(baseUri)
            cfg.isRemote(FireboxKind.Door15aCatalog) shouldBe true
            (FireboxKind.closed -- Set(FireboxKind.Ecolabeled, FireboxKind.Door15aCatalog)).foreach { kind =>
                cfg.sourceFor(kind) shouldBe FireboxEngineSource.Local
                cfg.isRemote(kind) shouldBe false
            }
        }

        "parses comma-split, whitespace-padded keys the way UIConfig splits them" in {
            val (cfg, unknown) =
                FireboxEngineSourceConfig.fromRemoteKeys(" ecolabeled , door15a-catalog ".split(",").toSeq, baseUri)
            unknown shouldBe empty
            cfg.sourceFor(FireboxKind.Ecolabeled) shouldBe FireboxEngineSource.Remote(baseUri)
            cfg.sourceFor(FireboxKind.Door15aCatalog) shouldBe FireboxEngineSource.Remote(baseUri)
            cfg.sourceFor(FireboxKind.Traditional) shouldBe FireboxEngineSource.Local
        }

        "reports unknown keys instead of dropping them silently" in {
            val (cfg, unknown) =
                FireboxEngineSourceConfig.fromRemoteKeys(List("ecolabeled", "ecolabelled", "eco"), baseUri)
            unknown should contain theSameElementsAs List("ecolabelled", "eco")
            cfg.sourceFor(FireboxKind.Ecolabeled) shouldBe FireboxEngineSource.Remote(baseUri)
        }

        "empty input reproduces the historical all-local behaviour" in {
            val (cfg, unknown) = FireboxEngineSourceConfig.fromRemoteKeys(List.empty, baseUri)
            unknown shouldBe empty
            cfg shouldBe FireboxEngineSourceConfig.AllLocal
            FireboxKind.closed.foreach(cfg.sourceFor(_) shouldBe FireboxEngineSource.Local)
        }
    }

    "kindKey" - {

        "is a bijection with byKey over exactly the closed kinds" in {
            FireboxEngineSourceConfig.kindKey.foreach { (kind, key) =>
                FireboxEngineSourceConfig.byKey(key) shouldBe kind
            }
            FireboxEngineSourceConfig.kindKey.keySet shouldBe FireboxKind.closed
        }

        "keys Ecolabeled as 'ecolabeled' — the spelling deployed in VITE_FIREBOX_REMOTE_KINDS" in {
            FireboxEngineSourceConfig.kindKey(FireboxKind.Ecolabeled) shouldBe "ecolabeled"
        }

        "keys Door15aCatalog as 'door15a-catalog' — the spelling deployed in VITE_FIREBOX_REMOTE_KINDS" in {
            FireboxEngineSourceConfig.kindKey(FireboxKind.Door15aCatalog) shouldBe "door15a-catalog"
        }
    }
