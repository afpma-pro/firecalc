/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.dto.all.{HeatOutputReduced, MinLoad}
import afpma.firecalc.engine.models.LoadQty
import afpma.firecalc.engine.models.OpPoint
import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.en13384.typedefs.T_L_override
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.gtypedefs.z_geodetical_height
import afpma.firecalc.engine.standard.VNelMcalcErr
import afpma.firecalc.units.coulombutils.Mass

/**
 * EN 15544-specific operating-point helpers: the grid axes implied by a
 * firebox (which depend on `Firebox_15544` + the DTO reduced-load types).
 */
object OpPoint15544:

    /**
     * The grid axes implied by a firebox.
     *
     * Both draft conditions are always present. The reduced-load axis is
     * present exactly when the firebox declares a reduced heat output AND a
     * determinable minimum load — the same criterion the common application
     * uses to decide whether `atDraft*_LoadReduced` vals exist (`m_B_min.isDefined`
     * in `EN15544_Common_Application_Formulas`).
     *
     * Returns 2 points (nominal only) or 4 points (nominal + reduced).
     */
    def axesFor(firebox: Firebox_15544): List[OpPoint] =
        val hasReduced = declaresReducedLoad(firebox)
        val loads      = if hasReduced then OpPoint.loadQtys else List(LoadQty.Nominal)
        for
            dc <- OpPoint.draftConditions
            lq <- loads
        yield OpPoint(dc, lq)

    /**
     * Whether the firebox declares a reduced heat output with a determinable
     * minimum load — the exact criterion from
     * `EN15544_Common_Application_Formulas.m_B_min`.
     */
    private def declaresReducedLoad(firebox: Firebox_15544): Boolean =
        firebox.pn_reduced match
            case _: (HeatOutputReduced.HalfOfNominal | HeatOutputReduced.FromTypeTest) =>
                firebox.min_load match
                    case MinLoad.NotDefined => false
                    case _: MinLoad.HalfOfMaxLoad => true
                    case _: MinLoad.FromTypeTest  => true
            case HeatOutputReduced.NotDefined => false

end OpPoint15544

/**
 * Site conditions the firebox solve needs beyond the firebox and `m_B`.
 *
 * Geodetic altitude (the same quantity type the formulas consume for
 * `z_geodetical_height`) plus optional per-draft-condition outside-air
 * temperature overrides mirroring the [[T_L_override]] vocabulary.
 *
 * Nothing else — do not grow speculatively.
 */
final case class FireboxOperatingEnvelope(
    altitude    : z_geodetical_height,
    T_L_override: T_L_override
)

object FireboxOperatingEnvelope:

    /** An envelope with no temperature overrides (standard default applies). */
    def atAltitude(altitude: z_geodetical_height): FireboxOperatingEnvelope =
        FireboxOperatingEnvelope(altitude, T_L_override.none)

end FireboxOperatingEnvelope

/**
 * The generic firebox-solving interface.
 *
 * One essential operation: given a firebox (bounded exactly as
 * [[FireboxEngineAny.from]] bounds it), an optional `m_B`, and a
 * [[FireboxOperatingEnvelope]], produce either a typed error list or a total
 * grid mapping each required [[OpPoint]] to the solved firebox gas-path
 * [[PipeResult]] and the optional combustion-air [[PipeResult]] (absence
 * mirrors `FireboxContribution.Without`, typed as `Option` — never an empty
 * collection).
 *
 * '''Totality contract:''' the returned grid covers exactly the [[OpPoint]]s
 * implied by the firebox (see [[OpPoint15544.axesFor]]); a consumer indexing a
 * covered point never misses.
 *
 * Error channel: the existing incremental-validation / engine-error vocabulary
 * ([[VNelMcalcErr]]) — no new error ADT.
 */
trait FireboxCalculation[F <: Firebox_15544]:

    /**
     * Solve the firebox-scope pipe results at every operating point implied by
     * the firebox.
     *
     * @param firebox  the firebox descriptor (bounded as [[FireboxEngineAny.from]])
     * @param m_B      the maximum fuel load, or `None` when not yet available
     * @param envelope site conditions (altitude, optional temperature overrides)
     * @return a total grid keyed by [[OpPoint]], or a typed error list
     */
    def solve(
        firebox : F,
        m_B     : Option[Mass],
        envelope: FireboxOperatingEnvelope
    ): VNelMcalcErr[Map[OpPoint, (PipeResult, Option[PipeResult])]]

end FireboxCalculation
