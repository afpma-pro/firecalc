/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en13384.v20241001

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}

import afpma.firecalc.i18n.LocalizedString

import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.engine.cas_types.en13384.*
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.models.en13384.typedefs.*

import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Languages

object CasType_13384_C16
    extends v2024_10_Alg
    with v0_2024_10.StoveProjectDescr_13384_WithThermalAirIntake_Alg
    with v0_2024_10.WithPipeChain_13384:

    val language = Languages.Fr

    val cas_type_name = "v20241001 // cas type EN13384 // C16"

    override val project = ProjectDescr(
        reference = "Cas Type EN13384 // C16",
        date      = "13/01/2025",
        country   = Country.France
    )

    override val en13384NationalAcceptedData = NationalAcceptedData.noOverride

    // Régusse
    // hypothèse: 540m
    val localConditions = LocalConditions(
        altitude            = 540.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

    val typeOfAppliance  = TypeOfAppliance.Pellets
    val fuelType         = FuelType.Pellets
    val flueGasCondition = FlueGasCondition.Wet_Condensing

    val heatingAppliance = HeatingAppliance(
        reference         = LocalizedString(_ => "Rika SUMO 9kW"),
        type_of_appliance = TypeOfAppliance.Pellets,
        efficiency        =
            // HeatingAppliance.Efficiency(
            //     perc_nominal = 90.8.percent,
            //     perc_lowest  = None,
            // ),
            // QC2
            HeatingAppliance.Efficiency(
                perc_nominal = 90.8.percent,
                perc_lowest  = 81.7.percent.some // in QC2 source ??
            ),
        fluegas           =
            // HeatingAppliance.FlueGas(
            //     co2_dry_perc_nominal                = 12.5.percent,
            //     co2_dry_perc_reduced                 = None,
            //     h2o_perc_nominal                    = None,
            //     h2o_perc_reduced                     = None,
            // ),
            // QC2
            HeatingAppliance.FlueGas(
                co2_dry_perc_nominal = 12.5.percent,
                co2_dry_perc_reduced = 8.0.percent.some,
                h2o_perc_nominal     = None,
                h2o_perc_reduced     = None
            ),
        powers            = HeatingAppliance.Powers(
            heat_output_nominal = 9.kW,
            heat_output_reduced = 2.7.kW.some
        ),
        temperatures      =
            // HeatingAppliance.Temperatures(
            //     flue_gas_temp_nominal               = 168.9.degreesCelsius,
            //     flue_gas_temp_reduced                = None,
            // ),
            // QC2
            HeatingAppliance.Temperatures(
                flue_gas_temp_nominal = 168.9.degreesCelsius,
                flue_gas_temp_reduced = 112.6.degreesCelsius.some
            ),
        massFlows         =
            // HeatingAppliance.MassFlows(
            //     flue_gas_mass_flow_nominal          = (5.7.g_per_s: MassFlow).some,
            //     flue_gas_mass_flow_reduced           = None,
            //     combustion_air_mass_flow_nominal    = None,
            //     combustion_air_mass_flow_reduced     = None,
            // ),
            // QC2
            HeatingAppliance.MassFlows      (
                flue_gas_mass_flow_nominal       = (5.7.g_per_s: MassFlow).some,
                flue_gas_mass_flow_reduced       = (1.9.g_per_s: MassFlow).some,
                combustion_air_mass_flow_nominal = (5.14.g_per_s: MassFlow).some,
                combustion_air_mass_flow_reduced = (1.78.g_per_s: MassFlow).some
            ),
        pressures         = HeatingAppliance.Pressures(
            underPressure      = UnderPressure.Negative,
            flue_gas_draft_min = 0.pascals.some, // 0 Pa in QC2 (Source CSTB)
            flue_gas_draft_max =
                15.pascals.some, // cf p22 https://rika.ams3.cdn.digitaloceanspaces.com/rika-web/ovens/sumo/pdf/FR/Z35963_FR_Sumo.pdf
            flue_gas_pdiff_min = None,
            flue_gas_pdiff_max = None
        )
    ).validNel

    // specific TURNS were not specified when defining the reference examples for the engine
    // only number of 90° turn (x4), total length 2m35 and vertical length 0,35m
    val airIntakePipe = // "Tube Flexible en Inox"
        import AirIntakePipe_Module.*
        define(
            setInitialDirection(azimuth = AzimuthDirection.Front, inclination = InclinationDirection.Horizontal), // Front

            pipeLocation(PipeLocation.HeatedArea), // to check

            roughness(5.mm), // ConduitFlexibleInox = 5mm

            addFlowResistance              (
                "grille (ζ = 1.7)",
                zeta               = 1.7.unitless,
                hydraulic_diameter = 50.mm
            ), // ajouté dans QC2 (cf hypothese général entrée d'air avec zeta = 1.7)

            innerShape(circle(50.mm)),
            layer                          (e                 = 0.2.mm, tr = 0.0.m2_K_per_W),
            addSectionHorizontal           ("hz", 25.cm                                    ),
            // turn right - final direction = 'Left'
            addCoudeCourbe90               ("coude 90° #1", R = 50.mm, absDir = AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)),
            addSectionHorizontal           ("hz", 50.cm                                    ),
            // turn left - final direction = 'Front'
            addCoudeCourbe90               ("coude 90° #2", R = 50.mm, absDir = AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)),
            addSectionHorizontal           ("hz", 50.cm                                    ),
            // turn upwards - final direction = 'Up'
            addCoudeCourbe90               ("coude 90° #3", R = 50.mm, absDir = AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Up)),
            addSectionVertical             ("vertical", 35.cm                              ),
            // turn - final direction = 'Right'
            addCoudeCourbe90               ("coude 90° #4", R = 50.mm, absDir = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)),
            addSectionHorizontal           ("hz", 50.cm                                    ),
            addSectionHorizontal           ("hz", 25.cm                                    )
        ).toFullDescr().extractPipe

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq(
            setInitialDirection(azimuth = AzimuthDirection.Rear, inclination = InclinationDirection.Horizontal), // Rear
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(100.mm)              ),
            layer       (e = 1.mm, tr = 0.0.m2_K_per_W),
            pipeLocation(PipeLocation.HeatedArea      ),

            // pb: sortie arrière sans longueur horizontale ???
            // car sinon impossible d'avoir un enchainement horizontal + coude 90 + dévoiement à 45°
            // tel que H utile = 2.10 et L developée = 2.18 m

            addSectionHorizontal("avant té ?",         8.cm                                   ),
            addSharpAngle_90deg ("té 90°",             AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up)), // Up
            addSectionVertical  ("montée",             70.cm                                  ),
            addSharpAngle_45deg ("dévoiement 45°",     AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Custom(45.degrees))), // Rear-Up (azimuth=0° inclination=45°)
            addSectionSloppedForceManualElevationGain("dévoiement", 70.cm, 70.cm), // approx to match C16 (50cm otherwise)
            addSharpAngle_45deg ("fin dévoiement 45°", AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up)), // Up
            addSectionVertical  ("avant plafond",      70.cm)
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq(
            // initial direction inherited from last connector pipe element = Up
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(100.mm)              ),
            layer(
                // T450 N1 W V3 L50040 G50 (source: Therminox TI on https://legal.poujoulat.com/fr)
                e = 0.4.mm * 2.0 + 30.0.mm,

                // 0.523 selon fiche technique qui ne dissocie pas Rth selon si ep=25mm ou ep=30mm
                // 0.45 dans QC2
                tr = 0.45.m2_K_per_W
            ),
            pipeLocation                               (PipeLocation.HeatedArea                                 ),
            addSectionVertical                         ("partie en ambiance chaude", 2.93.m * 63.percent.asRatio),
            pipeLocation                               (PipeLocation.OutsideOrExterior                          ),
            addSectionVertical                         ("partie en extérieur", 2.93.m * 38.percent.asRatio      ),
            addRainCapEN13384_withHeightEquals2Diameter("element terminal"                                      )
        )
