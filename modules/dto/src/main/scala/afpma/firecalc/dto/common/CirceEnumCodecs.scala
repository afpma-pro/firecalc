/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import io.circe.{Decoder, Json}

/**
 * Shared helpers for encoding/decoding ADT enums that mix unit cases (e.g. `Auto`)
 * with Position3D payload cases (e.g. `Manual(Position3D)`).
 *
 * Unit cases encode as `Json.obj("CaseName" -> true)` so YAML preserves them
 * (empty `{}` becomes null). Decoding accepts `true`, `null`, and `{}` for
 * backward compatibility.
 *
 * Payload cases encode/decode as `Json.obj("CaseName" -> Json.obj("position" -> ...))`.
 *
 * Each enum still writes its own encoder/decoder (the encoder requires pattern
 * matching on the concrete enum), but reuses these shared helpers.
 */
object CirceEnumCodecs:

    /** Check whether a Json value represents a unit case (true, null, or empty object). */
    def isUnitJson(json: Json): Boolean =
        json == Json.fromBoolean(true) || json == Json.Null || json == Json.obj()

    /**
     * Decode a Position3D from a payload case's inner Json.
     *
     * Expects `Json.obj("position" -> <Position3D json>)`.
     */
    def decodeManualPos(innerJson: Json): Decoder.Result[Position3D] =
        innerJson.hcursor.downField("position").as[Position3D]
