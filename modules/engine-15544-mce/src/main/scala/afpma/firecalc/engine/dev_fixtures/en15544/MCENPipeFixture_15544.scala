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
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot

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
 * `WithPipeChain_15544_MCE` chain path with a multi-`ThermalFlueSlot` topology
 * (N ≥ 4 post-firebox slots)
 * during engine development.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy.
 *
 * ── Topology ──────────────────────────────────────────────────────────────
 * N-pipe MCE topology with multiple thermal flue segments (4 slots total).
 *
 * `PipeChain_15544_MCE.toSlots` maps the entire `fluePipeDescr` Seq into a
 * single `ThermalFlueSlot`, so this fixture overrides `postFireboxPipeSlots`
 * directly to produce two distinct `ThermalFlueSlot` entries — the minimum
 * required to exercise the Stage 1 chain fold over multiple thermal flue
 * slots.
 *
 *   Slot 0 — ThermalFlueSlot #1 : horizontal exit from firebox (Right/Horizontal, 28.1 cm)
 *                                  + 90° turn upward
 *   Slot 1 — ThermalFlueSlot #2 : ascending vertical column (11.1 × 11.1 cm, 3.737 m)
 *   Slot 2 — ConnectorSlot      : short steel connector (Ø 130 mm, 6 cm)
 *   Slot 3 — ChimneySlot        : insulated steel chimney (Ø 130 mm, 57 cm + 93 cm)
 *
 * Field values mirror `MCEBaselineFixture_15544` (realistic but not physically
 * validated).
 */
object MCENPipeFixture_15544
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
        "v20241001 // dev fixture EN15544 MCE // N-pipe multi-ThermalFlueSlot (chain Stage 1 fold test)"

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

    // ── Legacy 3-pipe descriptors (required by WithPipeChain_15544_MCE) ──────────
    // These are used by the trait's pipeChain (fluePipe / connectorPipe / chimneyPipe
    // accessors). The `postFireboxPipeSlots` override below supersedes the trait's
    // default toSlots mapping (which would produce a single ThermalFlueSlot from the
    // entire fluePipeDescr Seq) with a hand-crafted 4-slot vector.

    val fluePipeDescr =
        import FluePipe_Module_13384.*
        Seq(
            setInitialDirection    (
                azimuth     = AzimuthDirection.Right,
                inclination = InclinationDirection.Horizontal
            ),
            pipeLocation           (PipeLocation.HeatedArea      ),
            roughness              (3.mm                         ),
            innerShape(rectangle(11.1.cm, 15.3.cm)),
            layer                  (e = 1.cm, λ = 0.89.W_per_mK  ),
            addSectionHorizontal   ("sortie foyer", 28.1.cm      ),
            addSharpAngle_90deg    (
                "virage 90 deg",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
            ),
            innerShape(rectangle(11.1.cm, 11.1.cm)),
            addSectionVertical     ("colonne ascendante", 3.737.m)
        )

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq (
            setInitialDirection(azimuth = AzimuthDirection.Rear, inclination = InclinationDirection.Up        ),
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer              (e       = 2.mm, tr                           = SquareMeterKelvinPerWatt(0.001)),
            pipeLocation       (PipeLocation.HeatedArea                                                       ),
            addSectionVertical ("buse", 6.cm                                                                  )
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq (
            setInitialDirection(azimuth = AzimuthDirection.Rear, inclination = InclinationDirection.Up        ),
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer              (e       = 2.5.cm, tr                         = SquareMeterKelvinPerWatt(0.260)),
            pipeLocation       (PipeLocation.HeatedArea                                                       ),
            addSectionVertical ("etage", 57.cm                                                                ),
            pipeLocation       (PipeLocation.OutsideOrExterior                                                ),
            addSectionVertical ("sortie de toit", 93.cm                                                       ),
            addFlowResistance  ("element terminal", 1.423.unitless: ζ)
        )

    // ── 4-slot N-pipe override ────────────────────────────────────────────────────
    // Overrides the trait's default (`PipeChain_15544_MCE.toSlots`, which collapses
    // the entire fluePipeDescr Seq into a single ThermalFlueSlot) with an explicit
    // 4-slot vector containing two distinct ThermalFlueSlot entries.
    //
    // This exercises the Stage 1 chain fold over multiple thermal flue slots —
    // the whole point of this fixture.
    //
    // Topology:
    //   Slot 0 — ThermalFlueSlot #1: horizontal exit + 90° turn upward
    //   Slot 1 — ThermalFlueSlot #2: ascending vertical column (inherits Up frame)
    //   Slot 2 — ConnectorSlot: short steel connector (inherits Up frame)
    //   Slot 3 — ChimneySlot: insulated steel chimney (inherits Up frame)

    override val postFireboxPipeSlots: Seq[PostFireboxPipeDescrSlot] =
        import FluePipe_Module_13384.*
        import ConnectorPipe_Module as CPM
        import ChimneyPipe_Module as CHPM
        Seq(
            // Slot 0 — ThermalFlueSlot #1: horizontal exit from firebox + 90° turn upward
            PostFireboxPipeDescrSlot.ThermalFlueSlot(
                Seq(
                    setInitialDirection    (
                        azimuth     = AzimuthDirection.Right,
                        inclination = InclinationDirection.Horizontal
                    ),
                    pipeLocation           (PipeLocation.HeatedArea    ),
                    roughness              (3.mm                       ),
                    innerShape(rectangle(11.1.cm, 15.3.cm)),
                    layer                  (e = 1.cm, λ = 0.89.W_per_mK),
                    addSectionHorizontal   ("F1-sortie foyer", 28.1.cm ),
                    addSharpAngle_90deg    (
                        "F1-virage 90 deg (-> Haut)",
                        AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
                    )
                )
            ),
            // Slot 1 — ThermalFlueSlot #2: ascending vertical column (continues from Up frame)
            PostFireboxPipeDescrSlot.ThermalFlueSlot(
                Seq(
                    innerShape(rectangle(11.1.cm, 11.1.cm)),
                    addSectionVertical("F2-colonne ascendante", 3.737.m)
                )
            ),
            // Slot 2 — ConnectorSlot: short steel connector (inherits Up frame from Slot 1)
            PostFireboxPipeDescrSlot.ConnectorSlot  (
                Seq (
                    CPM.roughness (Material_13384.WeldedSteel()),
                    CPM.innerShape(circle(130.mm)              ),
                    CPM.layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)),
                    CPM.pipeLocation      (PipeLocation.HeatedArea                       ),
                    CPM.addSectionVertical("C-buse", 6.cm                                )
                )
            ),
            // Slot 3 — ChimneySlot: insulated steel chimney
            PostFireboxPipeDescrSlot.ChimneySlot    (
                Seq (
                    CHPM.roughness (Material_13384.WeldedSteel()),
                    CHPM.innerShape(circle(130.mm)              ),
                    CHPM.layer             (e = 2.5.cm, tr = SquareMeterKelvinPerWatt(0.260)),
                    CHPM.pipeLocation      (PipeLocation.HeatedArea                         ),
                    CHPM.addSectionVertical("CH-etage", 57.cm                               ),
                    CHPM.pipeLocation      (PipeLocation.OutsideOrExterior                  ),
                    CHPM.addSectionVertical("CH-sortie de toit", 93.cm                      ),
                    CHPM.addFlowResistance ("CH-element terminal", 1.423.unitless: ζ)
                )
            )
        )

end MCENPipeFixture_15544

/**
 * Dev fixture — NOT a golden validation fixture.
 *
 * MCE N-pipe topology with a Connector-first head region exercising the
 * full `[ConnectorPipe, FluePipe, ConnectorPipe, FluePipe]` alternation
 * (6 slots total including the terminal connector and chimney).
 *
 * This is the Phase 7 T2 extension: the first non-trivial MCE case that
 * drives the Connector-first head path (path `(b)` in
 * `en15544_mce_application.flueRegionPipeResults` — seeds UpstreamState
 * directly from firebox outlet without a leading flue recomputation).
 *
 * ── Topology ──────────────────────────────────────────────────────────────
 *   Slot 0 — ConnectorSlot #1   : head connector directly off firebox
 *                                 (Ø 130 mm, 20 cm, HeatedArea)
 *   Slot 1 — ThermalFlueSlot #1 : horizontal exit + 90° turn upward
 *                                 (rect 11.1 × 15.3 cm then 11.1 × 11.1 cm,
 *                                  28.1 cm horizontal)
 *   Slot 2 — ConnectorSlot #2   : interleaved head connector (Ø 130 mm, 15 cm,
 *                                 HeatedArea)
 *   Slot 3 — ThermalFlueSlot #2 : ascending vertical column
 *                                 (rect 11.1 × 11.1 cm, 3.737 m)
 *   Slot 4 — ConnectorSlot      : terminal connector (Ø 130 mm, 6 cm,
 *                                 HeatedArea)
 *   Slot 5 — ChimneySlot        : insulated steel chimney (Ø 130 mm,
 *                                 57 cm HeatedArea + 93 cm Outside)
 *
 * Field values mirror `MCENPipeFixture_15544` (realistic but not physically
 * validated). The head-connector dimensions are conservative placeholders
 * that keep pressure drops and thermal losses modest.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy.
 */
object MCENPipeFixture_15544_CFCF
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
        "v20241001 // dev fixture EN15544 MCE // N-pipe [C, F, C, F] head (Connector-first seed)"

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

    // ── Legacy 3-pipe descriptors (required by WithPipeChain_15544_MCE) ──
    // Kept for trait contract compliance. The `postFireboxPipeSlots`
    // override below supersedes the trait's default toSlots mapping.

    val fluePipeDescr =
        import FluePipe_Module_13384.*
        Seq(
            setInitialDirection    (
                azimuth     = AzimuthDirection.Right,
                inclination = InclinationDirection.Horizontal
            ),
            pipeLocation           (PipeLocation.HeatedArea      ),
            roughness              (3.mm                         ),
            innerShape(rectangle(11.1.cm, 15.3.cm)),
            layer                  (e = 1.cm, λ = 0.89.W_per_mK  ),
            addSectionHorizontal   ("sortie foyer", 28.1.cm      ),
            addSharpAngle_90deg    (
                "virage 90 deg",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
            ),
            innerShape(rectangle(11.1.cm, 11.1.cm)),
            addSectionVertical     ("colonne ascendante", 3.737.m)
        )

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq (
            setInitialDirection(azimuth = AzimuthDirection.Rear, inclination = InclinationDirection.Up        ),
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer              (e       = 2.mm, tr                           = SquareMeterKelvinPerWatt(0.001)),
            pipeLocation       (PipeLocation.HeatedArea                                                       ),
            addSectionVertical ("buse", 6.cm                                                                  )
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq (
            setInitialDirection(azimuth = AzimuthDirection.Rear, inclination = InclinationDirection.Up        ),
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer              (e       = 2.5.cm, tr                         = SquareMeterKelvinPerWatt(0.260)),
            pipeLocation       (PipeLocation.HeatedArea                                                       ),
            addSectionVertical ("etage", 57.cm                                                                ),
            pipeLocation       (PipeLocation.OutsideOrExterior                                                ),
            addSectionVertical ("sortie de toit", 93.cm                                                       ),
            addFlowResistance  ("element terminal", 1.423.unitless: ζ)
        )

    // ── 6-slot N-pipe override: [C, F, C, F, Cterm, CH] ──────────────────
    // Exercises the Connector-first head seed path in
    // `en15544_mce_application.flueRegionPipeResults` (path (b)).

    override val postFireboxPipeSlots: Seq[PostFireboxPipeDescrSlot] =
        import FluePipe_Module_13384.*
        import ConnectorPipe_Module as CPM
        import ChimneyPipe_Module as CHPM
        Seq  (
            // Slot 0 — ConnectorSlot #1: head connector (first slot, Connector-first)
            PostFireboxPipeDescrSlot.ConnectorSlot  (
                Seq (
                    CPM.setInitialDirection    (
                        azimuth     = AzimuthDirection.Right,
                        inclination = InclinationDirection.Horizontal
                    ),
                    CPM.roughness (Material_13384.WeldedSteel()),
                    CPM.innerShape(circle(130.mm)              ),
                    CPM.layer                  (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)),
                    CPM.pipeLocation           (PipeLocation.HeatedArea                       ),
                    CPM.addSectionHorizontal   ("C1-head-connector", 20.cm                    )
                )
            ),
            // Slot 1 — ThermalFlueSlot #1: horizontal flue + 90° turn upward
            PostFireboxPipeDescrSlot.ThermalFlueSlot(
                Seq(
                    pipeLocation        (PipeLocation.HeatedArea    ),
                    roughness           (3.mm                       ),
                    innerShape(rectangle(11.1.cm, 15.3.cm)),
                    layer               (e = 1.cm, λ = 0.89.W_per_mK),
                    addSectionHorizontal("F1-sortie foyer", 28.1.cm ),
                    addSharpAngle_90deg (
                        "F1-virage 90 deg (-> Haut)",
                        AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
                    )
                )
            ),
            // Slot 2 — ConnectorSlot #2: interleaved head connector
            PostFireboxPipeDescrSlot.ConnectorSlot  (
                Seq (
                    CPM.roughness (Material_13384.WeldedSteel()),
                    CPM.innerShape(circle(130.mm)              ),
                    CPM.layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)),
                    CPM.pipeLocation      (PipeLocation.HeatedArea                       ),
                    CPM.addSectionVertical("C2-interleaved", 15.cm                       )
                )
            ),
            // Slot 3 — ThermalFlueSlot #2: ascending vertical column
            PostFireboxPipeDescrSlot.ThermalFlueSlot(
                Seq(
                    innerShape(rectangle(11.1.cm, 11.1.cm)),
                    addSectionVertical("F2-colonne ascendante", 3.737.m)
                )
            ),
            // Slot 4 — ConnectorSlot: terminal connector
            PostFireboxPipeDescrSlot.ConnectorSlot  (
                Seq (
                    CPM.roughness (Material_13384.WeldedSteel()),
                    CPM.innerShape(circle(130.mm)              ),
                    CPM.layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)),
                    CPM.pipeLocation      (PipeLocation.HeatedArea                       ),
                    CPM.addSectionVertical("Cterm-buse", 6.cm                            )
                )
            ),
            // Slot 5 — ChimneySlot: insulated steel chimney
            PostFireboxPipeDescrSlot.ChimneySlot    (
                Seq (
                    CHPM.roughness (Material_13384.WeldedSteel()),
                    CHPM.innerShape(circle(130.mm)              ),
                    CHPM.layer             (e = 2.5.cm, tr = SquareMeterKelvinPerWatt(0.260)),
                    CHPM.pipeLocation      (PipeLocation.HeatedArea                         ),
                    CHPM.addSectionVertical("CH-etage", 57.cm                               ),
                    CHPM.pipeLocation      (PipeLocation.OutsideOrExterior                  ),
                    CHPM.addSectionVertical("CH-sortie de toit", 93.cm                      ),
                    CHPM.addFlowResistance ("CH-element terminal", 1.423.unitless: ζ)
                )
            )
        )

end MCENPipeFixture_15544_CFCF
