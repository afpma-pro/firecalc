/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Site conditions the firebox solve needs beyond the firebox and `m_B`
 * (round-3 decision D4).
 *
 * The client sends RAW standard inputs only — the server derives everything
 * else (`t_combustion_air`, `f_s`, densities, flows) through the standard's
 * own formula chain. `altitude` is a `WireQty` in metres; the optional
 * per-draft-condition outside-air temperature overrides mirror the engine's
 * `T_L_override` vocabulary (`Option[Map[DraftCondition, T_L]]`). Absent
 * overrides mean "standard defaults", exactly as locally.
 *
 * OPTIONAL on the request: an absent envelope is a valid partial request
 * (B.5-A); for an `AsResult` kind the server reports the contribution as
 * `WireValidated.Invalid(MissingOperatingEnvelope)` rather than failing the
 * request.
 */
final case class WireOperatingEnvelope(
    altitude            : WireQty,
    temperatureOverrides: Option[WireTemperatureOverrides]
)

/**
 * Per-draft-condition outside-air temperature overrides (Celsius).
 *
 * Each field is independently optional: an absent override for one draft
 * condition leaves the standard default in place for that condition. When the
 * whole record is absent from the envelope, both conditions use defaults.
 */
final case class WireTemperatureOverrides(
    draftMinOrPositivePressureMax: Option[WireQty],
    draftMaxOrPositivePressureMin: Option[WireQty]
)
