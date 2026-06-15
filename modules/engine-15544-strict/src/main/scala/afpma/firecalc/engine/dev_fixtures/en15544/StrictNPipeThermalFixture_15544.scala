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
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7

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
 * has NOT been cross-validated; it exists to exercise the mixed
 * `FlueSlot` + `ThermalFlueSlot` topology in the Strict flue region
 * (Step 6 of the N-pipe topology remediation) during engine development.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy.
 *
 * ── Topology ──────────────────────────────────────────────────────────
 * Mixed flow-only / thermal N-pipe topology (4 slots total).
 *
 * Field values mirror `CasType_15544_C1` (colonne ascendante). The flue
 * path is split into two slots so that Stage 1 processes both a
 * `FlueSlot` (flow-only, `FluePipe_Module_15544`) and a `ThermalFlueSlot`
 * (`FluePipe_Module_13384`) — the new code path unlocked by Step 6.
 *
 *   Slot 0 — FlueSlot        : horizontal exit from firebox (Right/Horizontal, 28.1 cm)
 *                              + 90° turn upward + short vertical section (10 cm)
 *                              — flow-only (FluePipe_Module_15544)
 *   Slot 1 — ThermalFlueSlot : ascending vertical column (11.1 × 11.1 cm, 3.20 m)
 *                              — thermal (FluePipe_Module_13384)
 *   Slot 2 — ConnectorSlot   : short steel connector (Ø 130 mm, 5 cm)
 *   Slot 3 — ChimneySlot     : insulated chimney (Ø 130 mm, 90 cm + 60 cm)
 */
object StrictNPipeThermalFixture_15544
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
        "v20241001 // dev fixture EN15544 // Strict N-pipe mixed FlueSlot+ThermalFlueSlot (Step 6)"

    override val project = ProjectDescr(
        reference = "Dev Fixture EN15544 // Strict N-pipe mixed FlueSlot+ThermalFlueSlot",
        date      = "11/04/2026",
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
    // The 4-slot override below supersedes the chain's default toSlots mapping.

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

    // ── 4-slot N-pipe override ───────────────────────────────────────────────────
    // Bypasses PipeChain_15544_Strict.toSlots (which collapses the entire
    // fluePipeDescr Seq into a single FlueSlot) with a hand-crafted 4-slot
    // vector containing a FlueSlot followed by a ThermalFlueSlot.
    //
    // This exercises the Stage 1 mixed-slot fold path (FlueSlot → ThermalFlueSlot)
    // unlocked by Step 6 of the N-pipe topology remediation.
    //
    // Topology:
    //   Slot 0 — FlueSlot: horizontal exit + 90° turn upward + short vertical section
    //                      (direction change must be between two length-bearing elements)
    //   Slot 1 — ThermalFlueSlot: ascending vertical column (thermal)
    //   Slot 2 — ConnectorSlot: short steel connector (inherits Up frame)
    //   Slot 3 — ChimneySlot: insulated chimney (inherits Up frame)

    override val postFireboxPipeSlots: Seq[PostFireboxPipeDescrSlot_V7] =
        import ConnectorPipe_Module as CPM
        import ChimneyPipe_Module as CHPM
        val slot0 =
            import FluePipe_Module_15544.*
            PostFireboxPipeDescrSlot_V7.FlueSlot(
                Seq(
                    roughness           (3.mm                      ),
                    innerShape(rectangle(11.1.cm, 12.2.cm)),
                    addSectionHorizontal("F1-sortie foyer", 28.1.cm),
                    addSharpAngle_90deg (
                        "F1-virage 90 deg (-> Haut)",
                        AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
                    ),
                    addSectionVertical  ("F1-colonne courte", 10.cm)
                )
            )
        val slot1 =
            import FluePipe_Module_13384.*
            PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(
                Seq(
                    innerShape(rectangle(11.1.cm, 11.1.cm)),
                    pipeLocation      (PipeLocation.HeatedArea        ),
                    roughness         (3.mm                           ),
                    layer             (e = 1.cm, λ = 0.89.W_per_mK    ),
                    addSectionVertical("F2-colonne ascendante", 3.20.m)
                )
            )
        Seq(
            // Slot 0 — FlueSlot: horizontal exit from firebox + 90° turn upward (flow-only)
            slot0,
            // Slot 1 — ThermalFlueSlot: ascending vertical column (thermal, inherits Up frame)
            slot1,
            // Slot 2 — ConnectorSlot: short steel connector (inherits Up frame)
            PostFireboxPipeDescrSlot_V7.ConnectorSlot(
                Seq (
                    CPM.roughness (Material_13384.WeldedSteel()),
                    CPM.innerShape(circle(130.mm)              ),
                    CPM.layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
                    CPM.pipeLocation      (PipeLocation.HeatedArea                     ),
                    CPM.addSectionVertical("C-buse", 5.cm                              )
                )
            ),
            // Slot 3 — ChimneySlot: insulated chimney (inherits Up frame)
            PostFireboxPipeDescrSlot_V7.ChimneySlot  (
                Seq(
                    CHPM.roughness         (1.mm                                           ),
                    CHPM.innerShape(circle(130.mm)),
                    CHPM.layer             (e = 26.mm, tr = SquareMeterKelvinPerWatt(0.260)),
                    CHPM.pipeLocation      (PipeLocation.HeatedArea                        ),
                    CHPM.addSectionVertical("CH-etage", 90.cm                              ),
                    CHPM.pipeLocation      (PipeLocation.OutsideOrExterior                 ),
                    CHPM.addSectionVertical("CH-sortie de toit", 60.cm                     ),
                    CHPM.addFlowResistance ("CH-element terminal", 1.461.unitless: ζ)
                )
            )
        )

end StrictNPipeThermalFixture_15544
