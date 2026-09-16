/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_Module_13384 as AirIntakePipe_Module

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

/**
 * Generic, Austrian-free EN 15544 strict project scaffolding for tests
 * (ticket 11).
 *
 * Provides the project metadata and the air-intake / post-firebox pipe
 * descriptors that the remote-machinery suites use to assemble a project. The
 * suites substitute their own stub firebox, so no firebox engine model is
 * referenced here — the object is plain data, identical on the JVM and the
 * browser (Scala.js). It replaces the former reliance on the proprietary
 * `CasType_15544_C3` golden fixture (now private) for this scaffolding, so the
 * generic remote-engine tests stay Austrian-free. The pipe geometry is the
 * same as C3's.
 */
object TestScaffold_15544:

    val project: ProjectDescr = ProjectDescr(
        reference = "Test scaffold EN15544 (generic, ticket 11)",
        date      = "13/01/2025",
        country   = Country.France
    )

    val localConditions: LocalConditions = LocalConditions(
        altitude            = 1500.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

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

    val accumulateur_descr =
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

    val conduit_raccordement_descr =
        import ConnectorPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(25.cm)               ),
            layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
            pipeLocation      (PipeLocation.HeatedArea                     ),
            addSectionVertical("Car. 12", 5.cm                             )
        )

    val conduit_fumees_descr =
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

end TestScaffold_15544
