/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.dev_fixtures.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.engine.api.v0_2024_10_mce
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.Wood
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox
import afpma.firecalc.engine.models.gtypedefs.KindOfWood
import afpma.firecalc.engine.wood_combustion.WoodCombustionAlg
import afpma.firecalc.engine.wood_combustion.WoodCombustionImpl

import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

/**
 * Dev fixture — NOT a golden validation fixture.
 *
 * The names `cas_type`, `CasType_*`, and the `cas_types/` directory are
 * reserved for golden fixtures that are byte-cross-validated against
 * independent EN 13384 / EN 15544 reference implementations. This fixture
 * has NOT been cross-validated; it exists solely to exercise the MCE
 * `WithPipeChain_15544_MCE` N-pipe chain path during engine development.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy.
 *
 * ── Topology ──────────────────────────────────────────────────────────
 * Baseline 3-pipe topology (smoke test, not N-pipe):
 *   • flue      : 1 ThermalPipeDescr_13384 (horizontal exit + ascending column)
 *   • connector : 1 ThermalPipeDescr_13384 (short steel connector)
 *   • chimney   : 1 ThermalPipeDescr_13384 (steel chimney)
 *
 * By mixing in `WithPipeChain_15544_MCE`, `postFireboxPipeSlots` is
 * populated from `PipeChain_15544_MCE.toSlots` as
 *   [ThermalFlueSlot, ConnectorSlot, ChimneySlot]
 * which forces `flueRegionPipeResults` onto the non-empty chain-aware
 * branch (not the legacy `pfbSlots.isEmpty` fallback). This is the whole
 * point of the fixture.
 *
 * Field values mirror `mce_ex01_colonne_ascendante` (modules/fdim/…/p1_decouverte/)
 * — realistic but not physically validated.
 */
object MCEBaselineFixture_15544
    extends v0_2024_10_mce.SimpleStoveProjectDescrFr_15544_MCE_Alg
    with v0_2024_10_mce.Firebox_15544_MCE_Alg
    with v0_2024_10_mce.WithPipeChain_15544_MCE:
    self =>

    import afpma.firecalc.engine.impl.en15544.mce.given
    import gtypedefs.ζ

    type FB = TraditionalFirebox
    protected val toCombustionAirPipeTC = summon
    protected val toFireboxPipeTC       = summon

    val exercice_name =
        "v20241001 // dev fixture EN15544 MCE // baseline 3-pipe (chain path smoke test)"

    val wComb: WoodCombustionAlg = new WoodCombustionImpl

    val wood                                      = Wood.from_ONORM_B_8303(humidity = 20.percent)
    val kindOfWood                                = KindOfWood.HardWood
    val computeWoodCalorificValueUsingComposition = "Yes"

    val combustion_duration = (1 / 0.78).hours

    val combustion_lambda_nominal = 2.95
    val combustion_lambda_lowest  = None

    val exterior_air = afpma.firecalc.engine.wood_combustion.ExteriorAir(
        temperature       = 0.degreesCelsius,
        relative_humidity = 74.percent,
        pressure          = 101300.pascals
    )

    val localConditions = LocalConditions(
        altitude            = 200.0.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 10.kg,
        heating_cycle  = 12.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

    override def postFireboxInitialDirection = Some(
        PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
    )
    // postFireboxInitialPosition uses default (None) — loader resolves Auto correctly

    val fluegas_h2o_perc_vol_nominal = None
    val fluegas_h2o_perc_vol_lowest  = None

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        h11_profondeurDuFoyer            = 33.2.cm,
        h12_largeurDuFoyer               = 33.2.cm,
        h13_hauteurDuFoyer               = 51.9.cm,
        h66_coeffPerteDeChargePorte      = 0.3.unitless,
        h67_sectionCumuleeEntreeAirPorte = 94.cm2,
        h71_largeurVitre                 = 15.cm,
        h72_hauteurVitre                 = 20.cm,
        ash_pit_height                   = 5.cm
    )

    // ── 3-pipe chain descriptors (consumed by WithPipeChain_15544_MCE) ──
    // These flow into PipeChain_15544_MCE.Descriptors and, via
    // `WithPipeChain_15544_MCE.postFireboxPipeSlots`, produce a
    // [ThermalFlueSlot, ConnectorSlot, ChimneySlot] slot vector that drives
    // the chain-aware `flueRegionPipeResults` branch.

    val fluePipeDescr =
        import FluePipe_Module_13384.*
        Seq(
            pipeLocation        (PipeLocation.HeatedArea      ),
            roughness           (3.mm                         ),
            innerShape(rectangle(11.1.cm, 15.3.cm)),
            layer               (e = 1.cm, λ = 0.89.W_per_mK  ),
            addSectionHorizontal("sortie foyer", 28.1.cm      ),
            addSharpAngle_90deg (
                "virage 90 deg",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
            ),
            innerShape(rectangle(11.1.cm, 11.1.cm)),
            addSectionVertical  ("colonne ascendante", 3.737.m)
        )

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)),
            pipeLocation      (PipeLocation.HeatedArea                       ),
            addSectionVertical("buse", 6.cm                                  )
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer             (e = 2.5.cm, tr = SquareMeterKelvinPerWatt(0.260)),
            pipeLocation      (PipeLocation.HeatedArea                         ),
            addSectionVertical("etage", 57.cm                                  ),
            pipeLocation      (PipeLocation.OutsideOrExterior                  ),
            addSectionVertical("sortie de toit", 93.cm                         ),
            addFlowResistance ("element terminal", 1.423.unitless: ζ)
        )

end MCEBaselineFixture_15544
