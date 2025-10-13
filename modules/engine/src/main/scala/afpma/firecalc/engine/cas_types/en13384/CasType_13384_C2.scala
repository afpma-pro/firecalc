/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en13384.v20241001

import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.api.*
import afpma.firecalc.engine.cas_types.en13384.*
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.typedefs.*

import afpma.firecalc.i18n.LocalizedString

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import io.taig.babel.Languages

object CasType_13384_C2
    extends v2024_10_Alg 
    with v0_2024_10.StoveProjectDescr_EN13384_Strict_Alg:

    val language = Languages.Fr

    val cas_type_name = "v20241001 // cas type EN13384 // C2"

    override val project = ProjectDescr(
        reference = "Cas Type EN13384 // C2",
        date = "13/01/2025",
        country = Country.France
    )

    // St Martin Longueau	60700	Région non côtière
    // hyp: 40m
    // altitude max. 53m selon wikipedia https://fr.wikipedia.org/wiki/Saint-Martin-Longueau
    val localConditions = LocalConditions(
        altitude            = 40.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic,
    )

    override val en13384NationalAcceptedData = NationalAcceptedData.noOverride

    val typeOfAppliance = TypeOfAppliance.Pellets
    val fuelType = FuelType.Pellets
    val flueGasCondition = FlueGasCondition.Wet_Condensing

    // Rika FILO 8kW : cf Z35937_FR_Filo.pdf
    val heatingAppliance = HeatingAppliance(
        reference                               = LocalizedString(_ => "Rika FILO 8kW"),
        type_of_appliance                       = TypeOfAppliance.Pellets,
        efficiency                              = 
            // HeatingAppliance.Efficiency(
            //     perc_nominal = 91.5.percent,
            //     perc_lowest  = None
            // )
            // QC2
            HeatingAppliance.Efficiency(
                perc_nominal = 91.5.percent,
                perc_lowest  = 75.percent.some // in QC2 source ??
            ),
        fluegas         = 
            // HeatingAppliance.FlueGas(
            //     co2_dry_perc_nominal                = 12.2.percent, // 11.6% in KESA ?
            //     co2_dry_perc_reduced                 = None,
            //     h2o_perc_nominal                    = None,
            //     h2o_perc_reduced                     = None,
            // ),
            // QC2
            HeatingAppliance.FlueGas(
                co2_dry_perc_nominal                = 12.2.percent,
                co2_dry_perc_reduced                 = 12.2.percent.some,
                h2o_perc_nominal                    = None,
                h2o_perc_reduced                     = None,
            ),
        powers          = HeatingAppliance.Powers(
            heat_output_nominal                 = 8.kW,
            heat_output_reduced                  = 2.5.kW.some
        ),
        temperatures    = 
            // HeatingAppliance.Temperatures(
            //     flue_gas_temp_nominal               = 155.2.degreesCelsius, // 186.2°C selon doc mais température ajustée à 20% quand prise de mesure et point d'entrée sont séparées de 1m ou 1m50
            //     flue_gas_temp_reduced                = None, // 103.3 °C in QC2 (source ???)
            // ),
            // QC2
            HeatingAppliance.Temperatures(
                flue_gas_temp_nominal               = 155.2.degreesCelsius,      // 186.2°C selon doc mais température ajustée à 20% quand prise de mesure et point d'entrée sont séparées de 1m ou 1m50
                flue_gas_temp_reduced                = 103.3.degreesCelsius.some  // in QC2 (source ???)
            ),
        massFlows       = 
            // HeatingAppliance.MassFlows(
            //     flue_gas_mass_flow_nominal          = (5.2.g_per_s: MassFlow).some,
            //     flue_gas_mass_flow_reduced           = None, // 1.7 g/s in QC2 (source ???)
            //     combustion_air_mass_flow_nominal    = None, // 8.45 g/s in KESA-C2.pdf ??? 4.7 g/s in QC2 ???
            //     combustion_air_mass_flow_reduced     = None, // 1.57 g/s in QC2 ???
            // ),
            HeatingAppliance.MassFlows(
                flue_gas_mass_flow_nominal          = (5.2.g_per_s: MassFlow).some,
                flue_gas_mass_flow_reduced           = (1.7.g_per_s: MassFlow).some, // in QC2 (source ???)
                combustion_air_mass_flow_nominal    = (4.70.g_per_s: MassFlow).some, // 8.45 g/s in KESA-C2.pdf ??? 4.7 g/s in QC2 ???
                combustion_air_mass_flow_reduced     = (1.54.g_per_s: MassFlow).some, // 1.57 g/s in QC2 ???
            ),
        pressures       = HeatingAppliance.Pressures(
            underPressure                       = UnderPressure.Negative,
            flue_gas_draft_min                 = 0.pascals.some,  // 0 Pa in QC2 (Source ???)
            flue_gas_draft_max                 = 15.pascals.some, // cf p22 https://rika.ams3.cdn.digitaloceanspaces.com/rika-web/ovens/sumo/pdf/FR/Z35963_FR_Sumo.pdf
            flue_gas_pdiff_min                  = None,
            flue_gas_pdiff_max                  = None,
        )
    ).validNel

    val airIntakePipe =
        import AirIntakePipe_Module.*
        define(
            pipeLocation(PipeLocation.HeatedArea),

            roughness(5.mm), // TubeFlexEnPE = 5.mm

            addFlowResistance("grille (ζ = 1.7)", zeta = 1.7.unitless, hydraulic_diameter = 50.mm), // ajouté dans QC2 (cf hypothese général entrée d'air avec zeta = 1.7)

            innerShape(circle(50.mm)),
            layer(
                e = 0.1.mm,       // ???
                λ = 0.51.W_per_mK // PE-HD selon Wikipedia, valeur haute (https://fr.wikipedia.org/wiki/Poly%C3%A9thyl%C3%A8ne_haute_densit%C3%A9)
            ), 
            addSectionHorizontal("hz", 30.cm),
        ).toFullDescr().extractPipe

    val connectorPipe = 
        import ConnectorPipe_Module.*
        ConnectorPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(100.mm)),
            layer(
                e = 1.mm, // 1mm in QC2
                tr = 0.0.m2_K_per_W // R = 0 car conduit métallique non isolé
            ),
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("montée", 1.34.m),
        )
        .toFullDescr().extractPipe

    // T450 N1 W Vm L50012 G
    // (source: https://www.poujoulat.be/wp-content/uploads/2023/10/10-03-069_V7_DI001615_UKCA_FLEXIBLES_POUJOULAT_juillet23.pdf)
    val chimneyPipe = 
        import ChimneyPipe_Module.*
        ChimneyPipe_Module.incremental.define(
            roughness(2.mm), 
            innerShape(circle(100.mm)),

            // uncomment to match QC2
            // layer(
            //     e = 4.mm + 5.cm + 5.cm,
            //     tr = 0.177.m2_K_per_W,
            //     // tr = 0.18.m2_K_per_W,
            // ),

            layers(
                // tubaginox
                FromThermalResistanceUsingThickness(
                    thickness   = 2.4.mm, 
                    thermal_resistance          = 0.0.m2_K_per_W
                ), 

                // lame d'air ventilée selon DTU 24.1 (ouverture de 20cm2 en bas et 5cm2 en haut)
                AirSpaceUsingOuterShape(
                    rectangle(20.cm, 20.cm), 
                    VentilDirection.SameDirAsFlueGas, 
                    VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1
                ),

                // boisseau
                FromThermalResistanceUsingThickness(
                    thickness   = 11.5.cm,
                    thermal_resistance          = 0.12.m2_K_per_W,
                )
            ),

            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("partie en ambiance chaude", 4.505.m * 54.percent.asRatio),

            pipeLocation(PipeLocation.UnheatedInside),
            addSectionVertical("partie en ambiance froide", 4.505.m * 22.percent.asRatio),

            pipeLocation(PipeLocation.OutsideOrExterior),
            addSectionVertical("partie en extérieur", 4.505.m * 24.percent.asRatio),

            // zeta = 0.81 dans KESA (selon note de calcul)
            addRainCapEN13384_withHeightEquals2Diameter("element terminal (ζ = 1.5)") // ζ = 1.5 (QC2) 
        )
        .toFullDescr().extractPipe