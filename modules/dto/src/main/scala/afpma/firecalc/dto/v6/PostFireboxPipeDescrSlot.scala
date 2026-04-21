/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v6

import afpma.firecalc.dto.v4.FlowOnlyPipeDescr_15544_V3
import afpma.firecalc.dto.v4.ThermalPipeDescr_13384_V3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

/**
 * A tagged pipe descriptor slot for the post-firebox topology.
 *
 * Each slot pairs a pipe type with its descriptor sequence. Used internally for
 * generic topology processing. Will become the serialized format in DTO V5.
 *
 * TODO(Phase3/Phase5): Retire this flat enum in favour of the structured
 * [[PostFireboxChain_V3]] typed chain introduced below. Phase 3 migrates the engine
 * builders (`PipeChain_15544_Strict`, `PipeChain_15544_MCE`, `PipeChain_13384`),
 * the YAML normaliser and the physics seed; Phase 5 migrates the UI panels. Once
 * every consumer reads from `PostFireboxChain_V3`, this enum plus the `Seq[...]`
 * wire format can be deleted (and its circe codec replaced by one derived from
 * `PostFireboxChain_V3`).
 */
enum PostFireboxPipeDescrSlot:
    case FlueSlot(descr: Seq[FlowOnlyPipeDescr_15544_V3])
    case ThermalFlueSlot(descr: Seq[ThermalPipeDescr_13384_V3])
    case ConnectorSlot(descr: Seq[ThermalPipeDescr_13384_V3])
    case ChimneySlot(descr: Seq[ThermalPipeDescr_13384_V3])

object PostFireboxPipeDescrSlot:
    import afpma.firecalc.dto.instances.CommonInstances.given
    import afpma.firecalc.dto.instances.V4Instances.given

    given Encoder[PostFireboxPipeDescrSlot] = Encoder.instance {
        case PostFireboxPipeDescrSlot.FlueSlot(d)        =>
            Json.obj("FlueSlot" -> Encoder[Seq[FlowOnlyPipeDescr_15544_V3]].apply(d))
        case PostFireboxPipeDescrSlot.ThermalFlueSlot(d) =>
            Json.obj("ThermalFlueSlot" -> Encoder[Seq[ThermalPipeDescr_13384_V3]].apply(d))
        case PostFireboxPipeDescrSlot.ConnectorSlot(d)   =>
            Json.obj("ConnectorSlot" -> Encoder[Seq[ThermalPipeDescr_13384_V3]].apply(d))
        case PostFireboxPipeDescrSlot.ChimneySlot(d)     =>
            Json.obj("ChimneySlot" -> Encoder[Seq[ThermalPipeDescr_13384_V3]].apply(d))
    }

    given Decoder[PostFireboxPipeDescrSlot] = Decoder.instance { c =>
        c.downField("FlueSlot")
            .as[Seq[FlowOnlyPipeDescr_15544_V3]]
            .map(PostFireboxPipeDescrSlot.FlueSlot(_))
            .orElse(
                c.downField("ThermalFlueSlot")
                    .as[Seq[ThermalPipeDescr_13384_V3]]
                    .map(PostFireboxPipeDescrSlot.ThermalFlueSlot(_))
            )
            .orElse(
                c.downField("ConnectorSlot")
                    .as[Seq[ThermalPipeDescr_13384_V3]]
                    .map(PostFireboxPipeDescrSlot.ConnectorSlot(_))
            )
            .orElse(
                c.downField("ChimneySlot")
                    .as[Seq[ThermalPipeDescr_13384_V3]]
                    .map(PostFireboxPipeDescrSlot.ChimneySlot(_))
            )
    }

// ──────────────────────────────────────────────────────────────────────────────
// New structured post-firebox chain (introduced Phase 2 of the n-pipe topology
// connector-first plan — see plans/npipe-topology-connector-first.md issue D1).
//
// Grammar enforced by the typed chain:
//
//   PostFireboxChain_V3 := HEAD_REGION  TERMINAL_CONNECTOR  CHIMNEY
//   HEAD_REGION         := alternating Flue/Connector (NON-EMPTY, ends with Flue)
//   TERMINAL_CONNECTOR  := exactly one ConnectorSlot_V3 (descr may be empty)
//   CHIMNEY             := exactly one ChimneySlot_V3
//
// Phase 3 (engine builders + YAML loader + physics seed) and Phase 5 (UI panels)
// will migrate consumers from the flat `Seq[PostFireboxPipeDescrSlot]` above
// to this structured form. Until then the two representations coexist.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * HEAD_REGION pipe slot — either a flow-only flue (EN 15544) or a thermal connector
 * (EN 13384). Alternation between the two subtypes is enforced at the grammar layer.
 *
 * Name-suffixed `_V3` to mirror the descriptor types (`FlowOnlyPipeDescr_15544_V3`,
 * `ThermalPipeDescr_13384_V3`) it wraps. Lives in `dto.v6` (current wire-format
 * version); the wrapped descriptor types remain in `dto.v4` and are imported.
 */
sealed trait HeadSlot_V3

/** HEAD_REGION flue pipe (EN 15544 flow-only). */
final case class FlueSlot_V3(descr: Seq[FlowOnlyPipeDescr_15544_V3]) extends HeadSlot_V3

/**
 * HEAD_REGION flue pipe (EN 13384 thermal) — used by the EN 15544 MCE pipeline, which
 * models the flue as a thermal pipe rather than flow-only.
 *
 * Added during Phase 3 migration to bridge a gap in the Phase 2 DTO locking: the MCE
 * builder needs to represent its flue via a typed head slot, but its descriptor is
 * [[ThermalPipeDescr_13384_V3]] not [[FlowOnlyPipeDescr_15544_V3]]. The [[FlueSlot_V3]]
 * topology role is identical (FluePipeT from the validator's POV); only the descriptor
 * payload differs. Keeping the two subtypes keeps the payload typed.
 */
final case class ThermalFlueSlot_V3(descr: Seq[ThermalPipeDescr_13384_V3]) extends HeadSlot_V3

/**
 * HEAD_REGION or terminal connector pipe (EN 13384 thermal).
 *
 * The same case class is used for HEAD_REGION connectors (interleaved with flues)
 * and for the mandatory terminal connector between the head region and the chimney.
 * The terminal connector's `descr` may be empty — the grammar allows a "marker"
 * terminal slot with no pipe descriptors.
 */
final case class ConnectorSlot_V3(descr: Seq[ThermalPipeDescr_13384_V3]) extends HeadSlot_V3

/**
 * Chimney slot (EN 13384 thermal). Exactly one, always the last slot in a
 * `PostFireboxChain_V3`.
 *
 * Not a `HeadSlot_V3` member: the chimney is a distinct role in the chain and
 * the typed chain keeps it out of the `head` sequence so HEAD_REGION alternation
 * rules cannot confuse it with a head connector.
 */
final case class ChimneySlot_V3(descr: Seq[ThermalPipeDescr_13384_V3])

/**
 * Typed post-firebox pipe chain. Replaces the flat
 * `Seq[PostFireboxPipeDescrSlot]` representation over the course of Phases 3-5.
 *
 * Invariants (NOT currently enforced at the type level — enforced by
 * `PostFireboxPipeChain.validated` in the topology validator):
 *   - `head` is non-empty
 *   - `head` alternates `FlueSlot_V3` / `ConnectorSlot_V3` (no two consecutive
 *     slots of the same subtype)
 *   - `head` ends with a `FlueSlot_V3`
 *   - `terminal` always present; `terminal.descr` may be empty
 *   - `chimney` always present
 */
final case class PostFireboxChain_V3(
    head    : Seq[HeadSlot_V3],
    terminal: ConnectorSlot_V3,
    chimney : ChimneySlot_V3,
)
