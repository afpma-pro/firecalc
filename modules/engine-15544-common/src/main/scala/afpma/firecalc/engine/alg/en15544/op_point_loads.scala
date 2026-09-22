/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.engine.models.LoadQty
import afpma.firecalc.engine.models.OpPoint
import afpma.firecalc.engine.models.en15544.typedefs.{m_B, m_B_min}
import afpma.firecalc.units.coulombutils.Mass

import algebra.instances.all.given

import coulomb.*
import coulomb.ops.standard.all.{given}

/**
 * The appliance's declared operating-point load masses (ADR-0008) and the
 * section-wise effective load mass derived from them.
 *
 * `m_B` is the declared maximum wood charge; `m_B_min` the declared minimum
 * load (EN 15544 §4.2.2) — `None` when the appliance declares no reduced
 * load (then no reduced operating point is ever built).
 */
final case class OpPointLoads(m_B: m_B, m_B_min: Option[m_B_min]):

    /**
     * The declared load mass for a load axis (ADR-0008): `m_B` nominal,
     * `m_B_min` reduced — `None` when the appliance declares no reduced load
     * (then no reduced operating point is ever built). Single source of truth
     * for the "which mass per load axis" policy: the §4 formulas mixin's
     * `given Conversion[LoadQty, Option[Mass]]` delegates here, and
     * `effective_load_mass` is derived from it.
     */
    def load_mass(lq: LoadQty): Option[Mass] = lq match
        case LoadQty.Nominal => Some(m_B)
        case LoadQty.Reduced => m_B_min

    /**
     * The load mass an operating point declares as-is (no flow division):
     * `m_B` nominal, `m_B_min` reduced.
     *
     * A reduced point without a declared `m_B_min` is unreachable by
     * invariant (strict constructs its `LoadReduced` instance only via
     * `m_B_min.map`); the `require` below fails loudly if that invariant
     * ever breaks, turning a would-be wrong-but-plausible output into a
     * caught violation (ADR-0008).
     */
    def load_mass_or_nominal(op: OpPoint): Mass =
        require                        (op.loadQty == LoadQty.Nominal || m_B_min.isDefined, "reduced point without declared m_B_min")
        load_mass(op.loadQty).getOrElse(m_B                                                                                         )

    /**
     * Effective load mass of a section carrying `nf` flows at operating point
     * `op` (ADR-0008): the point's load mass divided by `nf`. A two-flow
     * branch cools as if fired at half charge.
     *
     * The load mass is draft-independent: identical at min and max draft for
     * a given load axis, so only `op.loadQty` is consulted.
     *
     * `nf` has no default on purpose: every call site states its flow count
     * explicitly, so a split-flow section silently passing `NbOfFlows(1)`
     * stays visible at the signature.
     */
    def effective_load_mass(op: OpPoint, nf: NbOfFlows): Mass =
        load_mass_or_nominal(op) / nf.asQty
