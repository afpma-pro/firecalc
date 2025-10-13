/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.labo.`01_echangeur`

import afpma.firecalc.engine.impl.en15544.labo.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en13384.std.Wood
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en15544.std
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import algebra.instances.all.given

import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.biblio.kov.firebox_emissions
import afpma.firecalc.dto.all.*
import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.wood_combustion.WoodCombustionImpl

import afpma.firecalc.engine.api.v0_2024_10

object `07_cloche_intermediaire_entre_basse_avec_colonne_config_1` 
    extends v0_2024_10.SimpleStoveProjectDescrFr_EN15544_Labo_Alg:
    self =>

    import std.*
    import gtypedefs.ζ

    val exercice_name: String = "labo // 01_echangeur // 07_cloche_intermediaire_entre_basse_avec_colonne // config 1"

    val wComb = new WoodCombustionImpl

    val wood = Wood(
        humidity = 12.percent,
        composition = Wood.Composition(
            xC      = 50.percent,
            xH      = 6.percent,
            xO      = 42.percent,
            xN      = 1.percent,
            xOther  = 1.percent,
        )
    )

    lazy val computeWoodCalorificValueUsingComposition = "Yes"

    val combustion_duration = 1.0.hours
    
    val combustion_lambda_nominal = 1.6
    val combustion_lambda_lowest  = None
    
    val exterior_air_temperature    = 14.degreesCelsius
    val exterior_air_pressure       = 102183.pascals
    val exterior_air_rel_hum        = 74.percent

    val ambiant_air_temperatures = AmbiantAir_Temperatures(
        EnterreExterieur    = 15.degreesCelsius,
        EnterreInterieur    = 15.degreesCelsius,
        AirDansLePoele      = 15.degreesCelsius,
        Foyer               = 15.degreesCelsius,
        Accumulateur        = 15.degreesCelsius,
        DansLaPieceDuPoele  = 15.degreesCelsius,
        Chauffee            = 15.degreesCelsius,
        NonChauffee         = 15.degreesCelsius,
        Exterieure          = exterior_air_temperature
    )

    val localConditions = LocalConditions(
        altitude         = 250.0.meters,
        coastal_region              = false,
        chimney_termination    = ChimneyTermination.Classic,
    )

    override def en13384NationalAcceptedData: NationalAcceptedData = 
        super.en13384NationalAcceptedData // copy me and override some values if needed ?

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 14.kg,
        heating_cycle  = 8.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

    val fluegas_h2o_perc_vol_nominal = None
    val fluegas_h2o_perc_vol_lowest = None

    val airIntakePipe = 
        import AirIntakePipe_Module.*
        define(
            addFlowResistance("grille", 1.680.unitless, hydraulic_diameter = 11.9.cm),

            pipeLocation(Area.Exterieure),
            roughness(Tube_PVC), // Tube PVC
            innerShape(circle(11.9.cm)),
            layer(e = 2.mm, λ = 1.3.W_per_mK),

            addSectionVertical("entrée verticale", -120.5.cm),

            addCoudeCourbe90_unsafe("H", 9.6.cm),

            pipeLocation(Area.NonChauffee),
            addSectionHorizontal("traversée mur", 68.8.cm),

            addSectionHorizontal("horizontal en combles", 0.0.cm),

            pipeLocation(Area.DansLaPieceDuPoele),
            addCoudeCourbe90_unsafe("vertical en combles", 9.6.cm),
            
            addSectionVertical("P01", -196.1.cm),
            addSectionVertical("anémomètre", -39.5.cm),
            addSectionVertical("capteur humidité", -104.cm),

            addCoudeCourbe90_unsafe("coude courbe 90 avt descente 4", 9.6.cm),

            addSectionVertical("descente 4", -38.7.cm),
            addCoudeCourbe90_unsafe("coude courbe 90 après descente 4", 9.6.cm),
            // addFlowResistance("clapet zeta = 0.3!", 0.3.unitless: ζ), // déjà avec la grille à supprimer car déjà dans la grille
            addSectionVertical("clapet zeta = 0.3!", 23.7.cm),

            addSectionVertical("P02!", 55.2.cm),
            addSectionVertical("final vers foyer", 63.8.cm),
        )
        .toFullDescr().extractPipe

    val combustionAirPipe = 
        import CombustionAirPipe_Module_EN13384.*
        define(
            // addPressureDiff("dispositif de réglage d'air", 3.2.unitless: ζ), // ???

            pipeLocation(Area.AirDansLePoele),
            roughness(Material_13384.WeldedSteel),
            innerShape(rectangle(27.cm, 16.cm)),
            layer(e = 1.cm, λ = 1.3.W_per_mK),
            addSectionHorizontal("entrée P03 TC03", 18.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),

            innerShape(rectangle(36.cm, 36.cm)),
            addSectionVertical("montée", 7.3.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),
            
            channelsSplit(11),

            innerShape(rectangle(6.6.cm, 8.7.cm)),
            addSectionHorizontal("sous sole", 25.cm),

            addSharpAngle_90deg("angle vif 90°"),

            innerShape(rectangle(6.6.cm, 3.3.cm)),
            addSectionVertical("montée", 27.3.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),
            
            innerShape(rectangle(26.4.cm, 1.8.cm)),
            addSectionHorizontal("injecteurs", 2.cm),

            innerShape(rectangle(31.6.cm, 1.cm)),
            addSectionHorizontal("injecteurs", 2.cm),

            innerShape(rectangle(37.2.cm, 0.4.cm)),
            addSectionHorizontal("injecteurs", 1.5.cm),
        )
        .toFullDescr().extractPipe

    val firebox_mean_temp = 750.degreesCelsius

    val fireboxPipe = 
        import FireboxPipe_Module_EN13384.*
        define(
            pipeLocation(Area.Foyer),
            roughness(Refractory_Bricks),
            innerShape(rectangle(39.cm, 55.cm)),
            layer(e = 1.cm, λ = 1.3.W_per_mK),
            addSectionVertical("foyer P05", 58.3.cm),
        )
        .toFullDescr().extractPipe

    val firebox_output_temp = 785.degreesCelsius

    val fluePipe = 
        import FluePipe_Module_EN13384.*
        FluePipe_Module_EN13384
        .incremental
        .define(
            pipeLocation(Area.Accumulateur),
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(18.cm)),
            layer(e = 3.cm, λ = 1.3.W_per_mK),
            addSectionHorizontal("sortie foyer", 11.cm),

            pipeLocation(Area.DansLaPieceDuPoele),
            layer(e = 2.5.cm, λ = 0.057.W_per_mK),
            addSectionHorizontal("horizontal TC04", 30.5.cm),

            addSectionHorizontal("horizontal vers échangeur", 30.9.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),

            roughness(Refractory_Bricks),
            pipeLocation(Area.Accumulateur),
            layer(e = 2.cm, λ = 1.3.W_per_mK),
            innerShape(rectangle(16.2.cm, 22.2.cm)),
            addSectionVertical("descente P06", -44.4.cm),

            addSectionVertical("descente", -44.4.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),

            innerShape(rectangle(18.2.cm, 22.2.cm)),
            addSectionHorizontal("horizontal vers cloche", 7.6.cm),

            innerShape(rectangle(16.2.cm, 22.1.cm)),
            addSectionVertical("horizontal vers colonne", 28.2.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),

            innerShape(rectangle(16.1.cm, 16.1.cm)),
            addSectionVertical("colonne T30", 88.8.cm),

            addSectionVertical("colonne", 49.9.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),

            innerShape(rectangle(11.1.cm, 64.4.cm)),
            addSectionHorizontal("haut de cloche", 18.1.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),

            innerShape(rectangle(136.6.cm, 8.6.cm)),
            addSectionVertical("descente cloche", -88.8.cm), 

            addSectionVertical("descente cloche", -44.4.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),

            innerShape(rectangle(16.1.cm, 22.2.cm)),
            addSectionHorizontal("horizontal sortie cloche", 18.1.cm),
            
            addSharpAngle_90deg_unsafe("angle vif 90°"),

            addSectionVertical("vers colonne", 42.8.cm),

            addSharpAngle_90deg_unsafe("angle vif 90°"),

            innerShape(rectangle(16.1.cm, 16.1.cm)),

            addSectionVertical("colonne P09", 55.5.cm),
            addSectionVertical("colonne", 113.7.cm),
        )
        .toFullDescr().extractPipe

    val connectorPipe = 
        import ConnectorPipe_Module.*
        ConnectorPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(18.cm)),
            layer(e = 0.1.cm, λ = 15.W_per_mK),
            pipeLocation(Area.DansLaPieceDuPoele),

            addSectionVertical("raccord", 4.cm)
        )
        .toFullDescr().extractPipe

    val chimneyPipe = 
        import ChimneyPipe_Module.*
        ChimneyPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(18.cm)),
            layer(e = 2.5.cm, λ = 0.096.W_per_mK),
            pipeLocation(Area.DansLaPieceDuPoele),

            addSectionVertical("CF analyseur de comb"    , 18.5.cm),
            addSectionVertical("CF prise de pression"    , 19.0.cm),
            addSectionVertical("CF intérieur"            , 20.5.cm),
            pipeLocation(Area.NonChauffee),
            addSectionVertical("CF combles"              , 170.0.cm),
            addSectionVertical("CF traversée de toiture"  , 33.7.cm),
            pipeLocation(Area.Exterieure),
            addSectionVertical("CF P11"                  , 160.6.cm),
            addSectionVertical("CF débouché"             , 44.3.cm),

            addFlowResistance("element terminal", 0.6.unitless: ζ)
        )
        .toFullDescr().extractPipe

    override lazy val design = Design(
        firebox = Firebox_15544.OneOff.CustomForLab(
            reference = LocalizedString(_ => "???"),
            type_of_appliance = TypeOfAppliance.WoodLogs,
            emissions_values = firebox_emissions.Standing_Standard_Burning_Firebox,
            pn_reduced = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            dimensions = Dimensions(
                base = Dimensions.Base.Squared(
                    width = 39.cm,
                    depth = 55.cm,
                ),
                height = 58.3.cm
            ),
            glass_area = 200.cm2,
            air_injector_surface_area = 37.2.cm * 4.mm // checked in echangeur #6 and #7
        )
    )