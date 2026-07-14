/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.dev_fixtures.en15544.v20241001

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
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
 * has NOT been cross-validated; it exists to exercise interleaved
 * `ConnectorSlot` support in the Strict flue region during engine development.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy.
 *
 * ── Topology ──────────────────────────────────────────────────────────
 * 5-slot N-pipe topology with an interleaved connector in the flue region.
 *
 * Field values mirror `CasType_15544_C1` (colonne ascendante). The flue
 * path includes a ConnectorSlot between two FlueSlots so that Stage 1
 * processes the interleaved connector using Thermal 13384 computation.
 *
 *   Slot 0 — FlueSlot        : horizontal exit from firebox (flow-only)
 *   Slot 1 — ConnectorSlot   : interleaved connector in flue region (thermal)
 *   Slot 2 — FlueSlot        : ascending vertical column (flow-only, LAST flue → end of flue region)
 *   Slot 3 — ConnectorSlot   : standard connector after flue region (thermal)
 *   Slot 4 — ChimneySlot     : insulated chimney
 */
object StrictInterleavedConnectorFixture_15544
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
        "v20241001 // dev fixture EN15544 // Strict interleaved ConnectorSlot in flue region"

    override val project = ProjectDescr(
        reference = "Dev Fixture EN15544 // Strict interleaved ConnectorSlot in flue region",
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
    // The 5-slot override below supersedes the chain's default toSlots mapping.

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

    // ── 5-slot N-pipe override ───────────────────────────────────────────────────
    // Bypasses PipeChain_15544_Strict.toSlots with a hand-crafted 5-slot vector
    // that includes an interleaved ConnectorSlot between two FlueSlots in the
    // flue region.
    //
    // This exercises the Stage 1 interleaved-connector fold path (ConnectorSlot
    // computed via Thermal 13384 inside the flue region).
    //
    // Topology:
    //   Slot 0 — FlueSlot: horizontal exit + 90° turn upward + short vertical section
    //   Slot 1 — ConnectorSlot: interleaved connector in flue region (thermal)
    //   Slot 2 — FlueSlot: ascending vertical column (flow-only, LAST flue)
    //   Slot 3 — ConnectorSlot: standard connector after flue region (thermal)
    //   Slot 4 — ChimneySlot: insulated chimney

    override val postFireboxPipeSlots: Seq[PostFireboxPipeSlot] =
        import ConnectorPipe_Module as CPM
        import ChimneyPipe_Module as CHPM
        val slot0 =
            import FluePipe_Module_15544.*
            PostFireboxPipeSlot.FlueSlot(
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
            import ConnectorPipe_Module as CPM
            PostFireboxPipeSlot.ConnectorSlot(
                Seq (
                    CPM.roughness (Material_13384.WeldedSteel()),
                    CPM.innerShape(circle(130.mm)              ),
                    CPM.layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
                    CPM.pipeLocation      (PipeLocation.HeatedArea                     ),
                    CPM.addSectionVertical("C1-interleaved-in-flue", 5.cm              )
                )
            )
        val slot2 =
            import FluePipe_Module_15544.*
            PostFireboxPipeSlot.FlueSlot(
                Seq(
                    innerShape(rectangle(11.1.cm, 11.1.cm)),
                    roughness         (3.mm                           ),
                    addSectionVertical("F2-colonne ascendante", 3.20.m)
                )
            )
        Seq(
            // Slot 0 — FlueSlot: horizontal exit from firebox + 90° turn upward (flow-only)
            slot0,
            // Slot 1 — ConnectorSlot: interleaved connector in flue region (thermal)
            slot1,
            // Slot 2 — FlueSlot: ascending vertical column (flow-only, LAST flue → end of flue region)
            slot2,
            // Slot 3 — ConnectorSlot: standard connector after flue region (thermal)
            PostFireboxPipeSlot.ConnectorSlot(
                Seq (
                    CPM.roughness (Material_13384.WeldedSteel()),
                    CPM.innerShape(circle(130.mm)              ),
                    CPM.layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
                    CPM.pipeLocation      (PipeLocation.HeatedArea                     ),
                    CPM.addSectionVertical("C2-buse", 5.cm                             )
                )
            ),
            // Slot 4 — ChimneySlot: insulated chimney
            PostFireboxPipeSlot.ChimneySlot  (
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

end StrictInterleavedConnectorFixture_15544
