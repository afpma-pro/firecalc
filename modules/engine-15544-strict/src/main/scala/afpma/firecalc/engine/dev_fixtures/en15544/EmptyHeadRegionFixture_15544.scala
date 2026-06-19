/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.dev_fixtures.en15544.v20241001

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4 as SP4
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4 as EP4

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import cats.syntax.all.*

import io.taig.babel.Languages

/**
 * Dev fixture — NOT a golden validation fixture.
 *
 * The names `cas_type`, `CasType_*`, and the `cas_types/` directory are
 * reserved for golden fixtures that are byte-cross-validated against
 * independent EN 13384 / EN 15544 reference implementations. This fixture
 * has NOT been cross-validated; it exists to exercise the empty HEAD_REGION
 * code path (zero flue slots) introduced by the connector-first grammar change.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy.
 *
 * ── Topology ──────────────────────────────────────────────────────────
 * Empty HEAD_REGION topology: 0 flue slots.
 *
 *   Slot 0 — ConnectorSlot : short vertical steel connector (Ø 130 mm, 5 cm)
 *   Slot 1 — ChimneySlot   : insulated chimney (Ø 130 mm, 90 cm + 60 cm)
 *
 * Physical interpretation: the firebox outlet connects directly to the
 * terminal connector (no masonry flue tunnels). The "last flue exit state"
 * degenerates to the firebox outlet state — verified by the EmptyHeadRegionSuite.
 *
 * Field values mirror `StrictNPipeThermalFixture_15544` (CasType_15544_C1 base).
 */
object EmptyHeadRegionFixture_15544
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
        "v20241001 // dev fixture EN15544 // empty HEAD_REGION (connector-first)"

    override val project = ProjectDescr(
        reference = "Dev Fixture EN15544 // empty HEAD_REGION",
        date      = "11/04/2026",
        country   = Country.France
    )

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
    // The postFireboxPipeSlots override below supersedes these.

    override def postFireboxInitialDirection = Some(
        PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Up)
    )

    val fluePipeDescr =
        import FluePipe_Module_15544.*
        Seq(
            roughness         (3.mm                        ),
            innerShape(rectangle(11.1.cm, 11.1.cm)),
            addSectionVertical("colonne ascendante", 3.20.m)
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

    // ── 2-slot empty-head override ───────────────────────────────────────────────
    // Topology: no flue region → ConnectorSlot → ChimneySlot.
    // This exercises the empty-HEAD_REGION fallback paths added to:
    //   - flueRegionPipeResults  (returns Valid(empty, None))
    //   - Stage-2 seeding        (seeds from firebox_PipeResult)
    //   - conceptualFluePipeResult (falls back to firebox_PipeResult)
    //   - t_F                    (falls back to firebox_PipeResult.gas_temp_end)

    override val postFireboxPipeSlots: Seq[PostFireboxPipeSlot] =
        Seq(
            // Slot 0 — ConnectorSlot: short vertical steel connector
            PostFireboxPipeSlot.ConnectorSlot(
                Seq (
                    SP4.SetRoughness (Material_13384.WeldedSteel()),
                    SP4.SetInnerShape(circle(130.mm)              ),
                    SP4.SetLayer          (2.mm, WattsPerMeterKelvin(0.0)),
                    SP4.SetPipeLocation   (PipeLocation.HeatedArea       ),
                    EP4.AddSectionVertical("C-buse", 5.cm                )
                )
            ),
            // Slot 1 — ChimneySlot: insulated chimney
            PostFireboxPipeSlot.ChimneySlot  (
                Seq(
                    SP4.SetRoughness      (1.mm                                          ),
                    SP4.SetInnerShape(circle(130.mm)),
                    SP4.SetLayer          (26.mm, WattsPerMeterKelvin(0.260)             ),
                    SP4.SetPipeLocation   (PipeLocation.HeatedArea                       ),
                    EP4.AddSectionVertical("CH-etage", 90.cm                             ),
                    SP4.SetPipeLocation   (PipeLocation.OutsideOrExterior                ),
                    EP4.AddSectionVertical("CH-sortie de toit", 60.cm                    ),
                    EP4.AddFlowResistance ("CH-element terminal", 1.461.unitless: ζ, None)
                )
            )
        )

end EmptyHeadRegionFixture_15544
