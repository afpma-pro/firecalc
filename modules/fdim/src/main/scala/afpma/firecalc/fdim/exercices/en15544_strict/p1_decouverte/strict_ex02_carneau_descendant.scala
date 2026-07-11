/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import cats.syntax.all.*

object strict_ex02_carneau_descendant
    extends v0_2024_10_strict.SimpleStoveProjectDescrFr_15544_Strict_Alg
    with v0_2024_10_strict.Firebox_15544_Strict_Alg
    with v0_2024_10_strict.WithPipeChain_15544_Strict:
    self =>

    import afpma.firecalc.engine.impl.en15544.strict.given

    type FB = TraditionalFirebox
    protected val toCombustionAirPipeTC = summon
    protected val toFireboxPipeTC       = summon

    val exercice_name: String = "exercices // p1_decouverte // ex02_carneau_descendant"

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

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        h11_profondeurDuFoyer            = 33.2.cm,
        h12_largeurDuFoyer               = 33.2.cm,
        h13_hauteurDuFoyer               = 51.9.cm,
        h66_coeffPerteDeChargePorte      = 0.3.unitless,
        h67_sectionCumuleeEntreeAirPorte = 94.cm2,
        h71_largeurVitre                 = 15.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                 = 20.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        ash_pit_height                   = 5.cm
    )

    override def postFireboxInitialDirection = Some(
        PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
    )

    val fluePipeDescr =
        import FluePipe_Module_15544.*
        Seq(
            roughness           (3.mm                                                                           ),
            innerShape(rectangle(16.1.cm, 15.3.cm)),
            addSectionHorizontal("sortie foyer", 28.6.cm                                                        ),
            addSharpAngle_90deg (
                "virage avant descente",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Down)
            ), // Down

            innerShape(rectangle(16.1.cm, 11.1.cm)),
            addSectionVertical  ("descente", -36.7.cm                                                           ),
            addSharpAngle_90deg (
                "virage 90° avant colonne",
                AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
            ), // Rear

            addSectionHorizontal("vers colonne", 22.6.cm                                                        ),
            addSharpAngle_90deg ("virage 90°", AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up)), // Up

            addSectionVertical("colonne", 4.134.m)
        )

    val connectorPipeDescr = Seq()

    val chimneyPipeDescr = strict_ex01_colonne_ascendante.chimneyPipeDescr

end strict_ex02_carneau_descendant
