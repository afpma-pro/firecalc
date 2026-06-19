/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.dev_fixtures.en15544.v20241001

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import cats.syntax.all.*

import io.taig.babel.Languages

/**
 * Dev fixture — NOT a golden validation fixture.
 *
 * The names `cas_type`, `CasType_*`, and the `cas_types/` directory are
 * reserved for golden fixtures that are byte-cross-validated against
 * independent EN 13384 / EN 15544 reference implementations. This fixture
 * has NOT been cross-validated; it exists to prove that a `ConnectorSlot`
 * with empty descriptors (`Seq.empty`) computes as a stable no-op and
 * does not break downstream computation.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy.
 *
 * ── Topology ──────────────────────────────────────────────────────────
 * Minimal 3-slot N-pipe topology with an empty connector.
 *
 * Field values mirror `CasType_15544_C1` (colonne ascendante). The
 * connector slot is intentionally empty so that it builds to a Without
 * (PipeSlot.noop) — proving that upstream temperature, density, and
 * velocity propagate through unchanged to the chimney.
 *
 *   Slot 0 — FlueSlot          : full flue path (same as default single-slot)
 *   Slot 1 — ConnectorSlot     : empty descriptors → PipeSlot.noop
 *   Slot 2 — ChimneySlot       : standard chimney
 */
object StrictWithoutConnectorFixture_15544
    extends v2024_10_Alg
    with v0_2024_10_strict.Firebox_15544_Strict_Alg
    with v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
    with v0_2024_10_strict.WithPipeChain_15544_Strict:
    self =>

    import afpma.firecalc.engine.impl.en15544.strict.given
    import gtypedefs.ζ

    type FB = TraditionalFirebox
    protected val toCombustionAirPipeTC = summon
    protected val toFireboxPipeTC       = summon

    val language = Languages.Fr

    val cas_type_name: String =
        "v20241001 // dev fixture EN15544 // Strict Without connector (ConnectorSlot empty)"

    override val project = ProjectDescr(
        reference = "Dev Fixture EN15544 // Strict Without connector (ConnectorSlot empty)",
        date      = "12/04/2026",
        country   = Country.France
    )

    // Field values from CasType_15544_C1 (colonne ascendante)
    val localConditions = LocalConditions(
        altitude            = 0.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 10.02.kg,
        heating_cycle  = 12.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        h11_profondeurDuFoyer            = 33.2.cm,
        h12_largeurDuFoyer               = 33.2.cm,
        h13_hauteurDuFoyer               = 51.3.cm,
        h66_coeffPerteDeChargePorte      = 0.3.unitless,
        h67_sectionCumuleeEntreeAirPorte = 92.cm2,
        h71_largeurVitre                 = 0.cm,
        h72_hauteurVitre                 = 0.cm,
        ash_pit_height                   = 5.cm
    )

    // ── Legacy 3-pipe descriptors (required by WithPipeChain_15544_Strict) ────────
    // These provide the fallback path used when postFireboxPipeSlots is not overridden.
    // The 3-slot override below supersedes the chain's default toSlots mapping.

    override def postFireboxInitialDirection = Some(
        PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
    )

    val fluePipeDescr =
        import FluePipe_Module_15544.*
        Seq(
            roughness           (3.mm                        ),
            innerShape(rectangle(11.1.cm, 12.2.cm)),
            addSectionHorizontal("sortie foyer", 28.1.cm     ),
            addSharpAngle_90deg (
                "virage 90 deg",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
            ),
            innerShape(rectangle(11.1.cm, 11.1.cm)),
            addSectionVertical  ("colonne ascendante", 3.20.m)
        )

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
            pipeLocation      (PipeLocation.HeatedArea                     ),
            addSectionVertical("buse", 5.cm                                )
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq(
            roughness         (1.mm                                           ),
            innerShape(circle(130.mm)),
            layer             (e = 26.mm, tr = SquareMeterKelvinPerWatt(0.260)),
            pipeLocation      (PipeLocation.HeatedArea                        ),
            addSectionVertical("etage", 90.cm                                 ),
            pipeLocation      (PipeLocation.OutsideOrExterior                 ),
            addSectionVertical("sortie de toit", 60.cm                        ),
            addFlowResistance ("element terminal", 1.461.unitless: ζ)
        )

    // ── 3-slot N-pipe override (FlueSlot, ConnectorSlot-empty, ChimneySlot) ─────
    // Bypasses PipeChain_15544_Strict.toSlots with a hand-crafted 3-slot vector
    // where the ConnectorSlot carries empty descriptors.
    //
    // This exercises the ConnectorSlot(Seq.empty) → PipeSlot.noop code path,
    // proving that an empty connector propagates upstream state unchanged.
    //
    // Topology:
    //   Slot 0 — FlueSlot: full flue path (flow-only, same as default single-slot)
    //   Slot 1 — ConnectorSlot: empty descriptors → Without → PipeSlot.noop
    //   Slot 2 — ChimneySlot: insulated chimney (inherits state from flue)

    override val postFireboxPipeSlots: Seq[PostFireboxPipeSlot] =
        Seq(
            // Slot 0 — FlueSlot: use the FULL fluePipeDescr (same as default single-slot)
            PostFireboxPipeSlot.FlueSlot     (fluePipeDescr   ),
            // Slot 1 — ConnectorSlot: empty descriptors → builds to Without → PipeSlot.noop
            PostFireboxPipeSlot.ConnectorSlot(Seq.empty       ),
            // Slot 2 — ChimneySlot: standard chimney
            PostFireboxPipeSlot.ChimneySlot  (chimneyPipeDescr)
        )

end StrictWithoutConnectorFixture_15544
