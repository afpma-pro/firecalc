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
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4 as FP4
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4 as EP4
import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4 as SP4
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4 as TP4

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled_V1

import io.taig.babel.Languages

/**
 * Dev fixture — NOT a golden validation fixture.
 *
 * The names `cas_type`, `CasType_*`, and the `cas_types/` directory are
 * reserved for golden fixtures that are byte-cross-validated against
 * independent EN 13384 / EN 15544 reference implementations. This fixture
 * has NOT been cross-validated; it exists to exercise the N-pipe topology
 * code path during engine development.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy.
 *
 * ── Topology ──────────────────────────────────────────────────────────
 * N-pipe topology: 3 FlueSlots + 1 ConnectorSlot + 1 ChimneySlot.
 *
 * Legacy 3 descriptors (fluePipeDescr / connectorPipeDescr / chimneyPipeDescr)
 * are identical to C3 so that the legacy 3-pipe path stays exercised. The
 * 5-slot override activates the chain-aware N-pipe path
 * (see en15544_strict_application.scala).
 *
 * Slot geometry (all rectangular / horizontal first, then vertical):
 *   Slot 0 — FlueSlot #1 : horizontal segment exiting firebox (Right, 34.8 cm)
 *   Slot 1 — FlueSlot #2 : downward vertical segment (two sections, -109 + -244 cm)
 *   Slot 2 — FlueSlot #3 : upward vertical column back to top (two sections, +244 + +128 cm)
 *   Slot 3 — ConnectorSlot: short vertical steel connector (5 cm, Ø 25 cm)
 *   Slot 4 — ChimneySlot  : insulated chimney (Ø 250 mm, 6 m + 30 cm + 150 cm)
 */
object NPipeTopologyFixture_15544
    extends v2024_10_Alg
    with v0_2024_10_strict.Firebox_15544_Strict_Alg
    with v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
    with v0_2024_10_strict.WithPipeChain_15544_Strict:
    self =>

    import afpma.firecalc.engine.impl.en15544.strict.given
    import gtypedefs.ζ

    type FB = Ecolabeled
    protected val toCombustionAirPipeTC = summon
    protected val toFireboxPipeTC       = summon

    val language = Languages.Fr

    val cas_type_name: String = "v20241001 // dev fixture EN15544 // N-pipe topology"

    override val project = ProjectDescr(
        reference = "Dev Fixture EN15544 // N-pipe topology",
        date      = "11/04/2026",
        country   = Country.France
    )

    val localConditions = LocalConditions(
        altitude            = 1500.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 26.kg,
        heating_cycle  = 12.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

    override def postFireboxInitialDirection = Some(
        PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
    )
    // postFireboxInitialPosition uses default (None) — loader resolves Auto correctly

    val conduit_air_descr =
        import AirIntakePipe_Module.*
        Seq(
            addFlowResistance     ("1. grille", 0.61.unitless: ζ, hydraulic_diameter = 20.cm),
            roughness             (2.mm                                                     ),
            innerShape(circle(20.cm)),
            addSectionHorizontal  ("Car. 2", 253.cm                                         ),
            addAngleSpecifique    (
                "3. angle 90° (ζ=0.9)",
                90.degrees,
                zeta   = 0.9,
                absDir = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ),
            innerShape(circle(20.cm)),
            addSectionHorizontal  ("Car. 4", 40.cm                                          ),
            addFlowResistance     ("5. clapet", 0.25.unitless: ζ, hydraulic_diameter = 20.cm)
        )

    val airIntakePipe =
        import AirIntakePipe_Module.*
        define(conduit_air_descr*).toFullDescr().extractPipe

    val foyer_descr = Ecolabeled_V1(
        pn_reduced                                      = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        h11_profondeurDuFoyer                           = 54.cm,
        h12_largeurDuFoyer                              = 54.cm,
        h13_hauteurDuFoyer                              = 81.3.cm,
        h70_largeurPorteDansMaconnerie                  = 52.cm,
        h71_largeurVitre                                = 50.cm,
        h72_hauteurVitre                                = 40.cm,
        h74_hauteur_de_cendrier_AF                      = 8.cm,
        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W = 11.cm,
        h76_epaisseurSole                               = 8.cm,
        h77_epaisseurParoiInterneFoyer_D1               = 6.cm,
        epaisseurParoiExterneFoyer_D2                   = 6.cm,
        h78_largeurEspaceInterparoisDuFoyer_S           = 3.5.cm,
        h79_largeurRenfortMedianLateraux                = 4.5.cm,
        h80_largeurRenfortMedianArriere                 = 4.5.cm,
        r1                                              = 4.5.cm,
        r2                                              = 4.5.cm,
        r3                                              = 4.5.cm,
        h82_hauteurDesInjecteurs_Z                      = 0.8.cm,
        h83_hauteurEntreLaSoleEtLe1erInjecteur_X        = 10.cm
    )

    val firebox: Ecolabeled = foyer_descr

    // ── Legacy 3-pipe descriptors (identical to C3, required by WithPipeChain_15544_Strict) ──

    val fluePipeDescr =
        import FluePipe_Module_15544.*
        Seq(
            roughness           (3.mm             ),
            innerShape(rectangle(43.cm, 40.cm)),
            addSectionHorizontal("Car. 1", 34.8.cm),
            addSharpAngle_90deg (
                "virage 90° 1-2 (-> Bas)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Down)
            ),
            addSectionVertical  ("Car. 2", -109.cm),
            addSectionVertical  ("Car. 3", -244.cm),
            addSharpAngle_90deg (
                "virage 90° 3-4 (-> Droite)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ),
            innerShape(rectangle(27.cm, 40.cm)),
            addSectionHorizontal("Car. 4", 50.cm  ),
            addSharpAngle_90deg (
                "virage 90° 4-5 (-> Avant)",
                AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
            ),
            innerShape(rectangle(27.cm, 27.cm)),
            addSectionHorizontal("Car. 5", 5.cm   ),
            addSharpAngle_90deg (
                "virage 90° 5-6 (-> Droite)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ),
            addSectionHorizontal("Car. 6", 50.cm  ),
            addSharpAngle_90deg (
                "virage 90° 6-7 (-> Avant)",
                AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
            ),
            addSectionHorizontal("Car. 7", 34.cm  ),
            addSharpAngle_45deg (
                "virage 45° 7-8 (-> Avant+Gauche)",
                AbsoluteDirection(AzimuthDirection.FrontLeft, InclinationDirection.Horizontal)
            ),
            addSectionHorizontal("Car. 8", 14.1.cm),
            addSharpAngle_45deg (
                "virage 45° 8-9 (-> Gauche)",
                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
            ),
            addSectionHorizontal("Car. 9", 100.cm ),
            addSharpAngle_90deg (
                "virage 90° 9-10 (-> Haut)",
                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Up)
            ),
            innerShape(rectangle(21.cm, 32.cm)),
            addSectionVertical  ("Car. 10", 244.cm),
            addSectionVertical  ("Car. 11", 128.cm)
        )

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(25.cm)               ),
            layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
            pipeLocation      (PipeLocation.HeatedArea                     ),
            addSectionVertical("Car. 12", 5.cm                             )
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq(
            roughness         (1.mm                                          ),
            innerShape(circle(250.mm)),
            layer             (e = 26.mm, tr = SquareMeterKelvinPerWatt(0.44)),
            pipeLocation      (PipeLocation.HeatedArea                       ),
            addSectionVertical("chauff.", 6.m                                ),
            pipeLocation      (PipeLocation.UnheatedInside                   ),
            addSectionVertical("non-chauff", 30.cm                           ),
            pipeLocation      (PipeLocation.OutsideOrExterior                ),
            addSectionVertical("ext.", 150.cm                                ),
            addFlowResistance ("element terminal", 1.48.unitless: ζ)
        )

    // ── 5-slot N-pipe override ───────────────────────────────────────────────────
    // Activates the chain-aware path in en15544_strict_application.scala:311-317.
    //
    // Topology: FlueSlot#1 (horizontal exit) → FlueSlot#2 (descending column)
    //         → FlueSlot#3 (ascending column) → ConnectorSlot → ChimneySlot
    //
    // Directions chain naturally through frame propagation:
    //   Slot 0 starts "Right/Horizontal", ends "Right/Down" after 90° bend
    //   Slot 1 inherits "Right/Down", ends "Right/Horizontal" after 90° bend
    //   Slot 2 inherits "Right/Horizontal", ends "Left/Up" after bends
    //   Slot 3/4 inherit "Left/Up" (vertical)

    override val postFireboxPipeSlots: Seq[PostFireboxPipeDescrSlot_V7] =
        Seq     (
            // Slot 0 — FlueSlot #1: horizontal exit from firebox, then turn downward
            PostFireboxPipeDescrSlot_V7.FlueSlot     (
                Seq(
                    FP4.SetRoughness           (3.mm                ),
                    FP4.SetInnerShape(rectangle(43.cm, 40.cm)),
                    EP4.AddSectionHorizontal   ("F1-Car. 1", 34.8.cm),
                    EP4.AddSharpeAngle_0_to_180(
                        "F1-virage 90° (-> Bas)",
                        90.degrees,
                        Some(AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Down))
                    )
                )
            ),
            // Slot 1 — FlueSlot #2: descending vertical column (continues from Down)
            PostFireboxPipeDescrSlot_V7.FlueSlot     (
                Seq(
                    EP4.AddSectionVertical     ("F2-Car. 2", -109.cm),
                    EP4.AddSectionVertical     ("F2-Car. 3", -244.cm),
                    EP4.AddSharpeAngle_0_to_180(
                        "F2-virage 90° (-> Droite)",
                        90.degrees,
                        Some(AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal))
                    ),
                    FP4.SetInnerShape(rectangle(27.cm, 40.cm)),
                    EP4.AddSectionHorizontal   ("F2-Car. 4", 50.cm  ),
                    EP4.AddSharpeAngle_0_to_180(
                        "F2-virage 90° (-> Gauche montant)",
                        90.degrees,
                        Some(AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Up)         )
                    )
                )
            ),
            // Slot 2 — FlueSlot #3: ascending vertical column back to top
            PostFireboxPipeDescrSlot_V7.FlueSlot     (
                Seq(
                    FP4.SetInnerShape(rectangle(21.cm, 32.cm)),
                    EP4.AddSectionVertical("F3-Car. 10", 244.cm),
                    EP4.AddSectionVertical("F3-Car. 11", 128.cm)
                )
            ),
            // Slot 3 — ConnectorSlot: short vertical steel connector
            PostFireboxPipeDescrSlot_V7.ConnectorSlot(
                Seq (
                    SP4.SetRoughness (Material_13384.WeldedSteel()),
                    SP4.SetInnerShape(circle(25.cm)               ),
                    SP4.SetLayer          (2.mm, WattsPerMeterKelvin(0.0)),
                    SP4.SetPipeLocation   (PipeLocation.HeatedArea       ),
                    TP4.AddSectionVertical("C-Car. 12", 5.cm             )
                )
            ),
            // Slot 4 — ChimneySlot: insulated chimney pipe
            PostFireboxPipeDescrSlot_V7.ChimneySlot  (
                Seq(
                    SP4.SetRoughness      (1.mm                                         ),
                    SP4.SetInnerShape(circle(250.mm)),
                    SP4.SetLayer          (26.mm, WattsPerMeterKelvin(0.44)             ),
                    SP4.SetPipeLocation   (PipeLocation.HeatedArea                      ),
                    TP4.AddSectionVertical("CH-chauff.", 6.m                            ),
                    SP4.SetPipeLocation   (PipeLocation.UnheatedInside                  ),
                    TP4.AddSectionVertical("CH-non-chauff", 30.cm                       ),
                    SP4.SetPipeLocation   (PipeLocation.OutsideOrExterior               ),
                    TP4.AddSectionVertical("CH-ext.", 150.cm                            ),
                    TP4.AddFlowResistance ("CH-element terminal", 1.48.unitless: ζ, None)
                )
            )
        )

end NPipeTopologyFixture_15544
