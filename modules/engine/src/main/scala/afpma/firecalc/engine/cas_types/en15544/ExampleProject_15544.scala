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
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.TraditionalFirebox

object ExampleProject_15544
    extends v2024_10_Alg
    with v0_2024_10.Firebox_15544_Strict_OneOff_Alg
    with v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg:
    self =>

    import afpma.firecalc.engine.models.en15544.std.*
    import gtypedefs.ζ

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
        altitude                    = 128.meters,
        coastal_region              = false,
        chimney_termination         = ChimneyTermination.Classic,
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load    = 18.46.kg,
        heating_cycle   = 12.hours,
        min_efficiency  = 78.percent,
        facing_type     = FacingType.WithoutAirGap
    )

    val conduit_air_descr = Seq()

    val airIntakePipe = 
        import AirIntakePipe_Module.*
        define(conduit_air_descr*).toFullDescr().extractPipe

    val foyer_descr = TraditionalFirebox(
        pn_reduced                                          = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        h11_profondeurDuFoyer                               = 44.cm,
        h12_largeurDuFoyer                                  = 42.cm,
        h13_hauteurDuFoyer                                  = 78.cm,
        h66_coeffPerteDeChargePorte                         = 0.3.unitless,
        h67_sectionCumuleeEntreeAirPorte                    = 170.cm2,
        h71_largeurVitre                                    = 15.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                                    = 20.cm, // TODO: à spécifier (nouveauté EN15544:2023)
    )

    val firebox = foyer_descr

    val accumulateur_descr = 
        import FluePipe_Module_EN15544.*
        Seq(
            roughness(3.mm),

            innerShape(rectangle(25.1.cm, 23.cm)),
            addSectionHorizontal("sortie foyer", 32.cm),

            addSharpAngle_90deg("virage avant descente"),

            innerShape(rectangle(25.1.cm, 22.cm)),
            addSectionVertical("descente", -81.cm),

            addSharpAngle_90deg("virage avant banc avant"),

            innerShape(rectangle(22.cm, 24.cm)),
            addSectionHorizontal("banc avant", 1.79.meters),

            addSharpAngle_90deg("virage avant bout du banc"),

            innerShape(rectangle(20.cm, 24.cm)),
            addSectionHorizontal("bout du banc", 44.cm),

            addSharpAngle_90deg("virage avant banc arrière"),

            innerShape(rectangle(19.cm, 24.cm)),
            addSectionHorizontal("arrière banc", 2.07.meters),

            addSharpAngle_90deg("virage avant vers remontée"),

            innerShape(rectangle(21.cm, 24.cm)),
            addSectionHorizontal("vers remontée", 44.cm),

            addSharpAngle_90deg("virage avant remontée"),

            innerShape(rectangle(21.cm, 22.cm)),
            addSectionVertical("remontée", 98.cm),
        )

    val fluePipe = 
        import FluePipe_Module_EN15544.*
        define(accumulateur_descr*).toFullDescr().extractPipe

    val conduit_raccordement_descr =
        import ConnectorPipe_Module.*
        Seq(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(200.mm)),
            layer(e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)), // TOFIX:
            pipeLocation(PipeLocation.HeatedArea),

            addSectionVertical("conduit simple peau 1 ", 39.cm),

            addSharpAngle_30deg("coude angle vif 30°"),

            addSectionSlopped("conduit simple peau 2", 58.cm, elevation_gain = 48.8.cm),

            addSharpAngle_30deg_unsafe("coude angle vif 30°"),

            addSectionVertical("conduit simple peau 2", 26.cm)
        )
    
    val connectorPipe = 
        import ConnectorPipe_Module.*
        define(conduit_raccordement_descr*).toFullDescr().extractPipe

    val conduit_fumees_descr = 
        import ChimneyPipe_Module.*
        Seq(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(200.mm)),
            layer(e = 2.5.cm, tr = SquareMeterKelvinPerWatt(0.440)),

            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("intérieur", 50.cm),

            pipeLocation(PipeLocation.OutsideOrExterior), // plutot NON CHAUFFEE car combles ???
            addSectionVertical("combles", 90.cm), // deviation from `strict_ex02_kachelofen` (90 cm instead of 50 cm) to get proper pressure equilibrium

            pipeLocation(PipeLocation.OutsideOrExterior), // plutot NON CHAUFFEE car combles ???
            addSectionVertical("extérieur", 1.10.m),

            addFlowResistance("element terminal", 0.6.unitless: ζ)
        )

    val chimneyPipe = 
        import ChimneyPipe_Module.*
        define(conduit_fumees_descr*).toFullDescr().extractPipe

end ExampleProject_15544


