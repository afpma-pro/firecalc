/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en15544.v20241001

import cats.syntax.all.*

import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.EcoLabeled_V1
import afpma.firecalc.engine.models.en15544.std

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import io.taig.babel.Languages

object ExampleProject_15544
    extends v2024_10_Alg
    with v0_2024_10.Firebox_15544_Strict_OneOff_Alg
    with v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg:
    self =>

    import std.*
    import gtypedefs.ζ

    val language = Languages.Fr

    val cas_type_name: String = "PROJET EXEMPLE 15544"

    override val project = ProjectDescr(
        reference   = "PROJET EXEMPLE 15544",
        date        = "01/01/2025",
        country     = Country.France
    )

    val localConditions = LocalConditions(
        altitude            = 1500.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic,
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load      = 24.kg,
        heating_cycle     = 12.hours,
        min_efficiency    = 78.percent,
        facing_type       = FacingType.WithoutAirGap
    )

    val conduit_air_descr = 
        import AirIntakePipe_Module.*
        Seq(
            addFlowResistance("1. grille", 0.61.unitless: ζ, hydraulic_diameter = 20.cm),

            roughness(2.mm),
            pipeLocation(PipeLocation.OutsideOrExterior),

            innerShape(circle(20.cm)),

            layer(
                e = 2.mm, // ???
                tr = SquareMeterKelvinPerWatt(0.0) // ???
            ), 

            addSectionHorizontal("Car. 2", 253.cm),

            addAngleSpecifique("3. angle 90° (ζ=0.9)", 90.degrees, zeta = 0.9),

            innerShape(circle(20.cm)), // why ???
            addSectionHorizontal("Car. 4", 40.cm),

            addFlowResistance("5. clapet", 0.25.unitless: ζ, hydraulic_diameter = 20.cm),
        )

    val airIntakePipe = 
        import AirIntakePipe_Module.*
        define(conduit_air_descr*).toFullDescr().extractPipe

    val foyer_descr = EcoLabeled_V1(
        pn_reduced                                          = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        h11_profondeurDuFoyer                               = 54.cm,
        h12_largeurDuFoyer                                  = 54.cm,
        h13_hauteurDuFoyer                                  = 75.cm,
        h70_largeurPorteDansMaconnerie                      = 52.cm,
        // hauteurporte = 42 ???
        h71_largeurVitre                                    = 50.cm,
        h72_hauteurVitre                                    = 40.cm,
        h74_hauteur_de_cendrier_AF                          = 8.cm,
        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W     = 11.cm,
        h76_epaisseurSole                                   = 8.cm,
        h77_epaisseurParoiInterneFoyer_D1                   = 6.cm,
        epaisseurParoiExterneFoyer_D2                       = 6.cm,
        h78_largeurEspaceInterparoisDuFoyer_S               = 3.5.cm,
        h79_largeurRenfortMedianLateraux                    = 4.5.cm, // prop 10% ???
        h80_largeurRenfortMedianArriere                     = 4.5.cm, // prop 10% ???
        h81_debordDesRenfortsDansLesAngles                  = 4.5.cm, // prop 10% ???
        h82_hauteurDesInjecteurs_Z                          = 0.6.cm,
    )

    val firebox = foyer_descr

    val accumulateur_descr = 
        import FluePipe_Module_EN15544.*
        Seq(
            roughness(3.mm),

            innerShape(rectangle(36.cm, 40.cm)),

            addSectionHorizontal("Car. 1",  34.8.cm),
            addSharpAngle_90deg("virage 90° 1-2"),

            addSectionVertical("Car. 2",  -109.cm),
            addSectionVertical("Car. 3",  -244.cm),

            addSharpAngle_90deg("virage 90° 3-4", angleN2 = 0.degrees.some),

            innerShape(rectangle(27.cm, 40.cm)),
            addSectionHorizontal("Car. 4",  50.cm),

            addSharpAngle_90deg("virage 90° 4-5", angleN2 = 90.degrees.some),

            innerShape(rectangle(27.cm, 27.cm)),
            addSectionHorizontal("Car. 5",  5.cm),

            addSharpAngle_90deg("virage 90° 5-6", angleN2 = 0.degrees.some),

            addSectionHorizontal("Car. 6",  50.cm),

            addSharpAngle_90deg("virage 90° 6-7", angleN2 = 0.degrees.some),

            addSectionHorizontal("Car. 7",  34.cm),

            addSharpAngle_45deg("virage 45° 7-8", angleN2 = 135.degrees.some),

            addSectionHorizontal("Car. 8",  14.1.cm),

            addSharpAngle_45deg("virage 45° 8-9", angleN2 = 90.degrees.some),

            addSectionHorizontal("Car. 9",  100.cm),

            addSharpAngle_90deg("virage 90° 9-10", angleN2 = 90.degrees.some),

            innerShape(rectangle(21.cm, 32.cm)),

            addSectionVertical("Car. 10", 244.cm),

            addSectionVertical("Car. 11", 128.cm),
        )

    val fluePipe = 
        import FluePipe_Module_EN15544.*
        define(accumulateur_descr*).toFullDescr().extractPipe

    val conduit_raccordement_descr =
        import ConnectorPipe_Module.*
        Seq(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(25.cm)),
            layer(e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("Car. 12", 5.cm),
        )
    
    val connectorPipe = 
        import ConnectorPipe_Module.*
        define(conduit_raccordement_descr*).toFullDescr().extractPipe

    val conduit_fumees_descr = 
        import ChimneyPipe_Module.*
        Seq(
            roughness(1.mm),
            innerShape(circle(250.mm)),
            layer(e = 26.mm, tr = SquareMeterKelvinPerWatt(0.44)),

            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("chauff.", 7.5.m),

            pipeLocation(PipeLocation.UnheatedInside),
            addSectionVertical("non-chauff", 30.cm),

            pipeLocation(PipeLocation.OutsideOrExterior),
            addSectionVertical("ext.", 150.cm),

            addFlowResistance("element terminal", 1.48.unitless: ζ) // cf fichier .k10
        )

    val chimneyPipe = 
        import ChimneyPipe_Module.*
        define(conduit_fumees_descr*).toFullDescr().extractPipe

end ExampleProject_15544


