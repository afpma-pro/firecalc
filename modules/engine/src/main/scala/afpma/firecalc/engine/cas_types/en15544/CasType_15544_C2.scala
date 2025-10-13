/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en15544.v20241001

import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.TraditionalFirebox
import afpma.firecalc.engine.models.en15544.std

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import io.taig.babel.Languages

object CasType_15544_C2 
    extends v2024_10_Alg
    with v0_2024_10.Firebox_15544_Strict_OneOff_Alg
    with v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg:
    self =>

    import std.*
    import gtypedefs.ζ

    val language = Languages.Fr

    val cas_type_name: String = "v20241001 // cas type EN15544 // C2 = '02-Kachelofen'"

    override val project = ProjectDescr(
        reference = "Cas Type EN15544 // C2 = '02-Kachelofen'",
        date = "13/01/2025",
        country = Country.France
    )

    val localConditions = LocalConditions(
        altitude         = 700.meters,
        coastal_region              = false,
        chimney_termination    = ChimneyTermination.Classic,
    )

    val stoveParams = StoveParams.fromNominalHeatOutput(
        nominal_heat_output  = 5.0.kW,
        heating_cycle        = 12.hours,
        min_efficiency       = 78.percent,
        facing_type          = FacingType.WithAirGap
    )

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        pn_reduced                                          = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        h11_profondeurDuFoyer                               = 44.1.cm,
        h12_largeurDuFoyer                                  = 42.1.cm,
        h13_hauteurDuFoyer                                  = 75.0.cm,
        h66_coeffPerteDeChargePorte                         = 0.3.unitless, // ??? in basic2plus, not specified in EN15544 (what about in some test report ?)
        h67_sectionCumuleeEntreeAirPorte                    = 250.cm2,
        h71_largeurVitre                                    = 0.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                                    = 0.cm, // TODO: à spécifier (nouveauté EN15544:2023)
    )

    val fluePipe = 
        import FluePipe_Module_EN15544.*
        FluePipe_Module_EN15544
        .incremental
        .define(
            roughness(3.mm),
            
            innerShape(rectangle(23.cm, 25.1.cm)),
            addSectionHorizontal("Car. 1", 31.7.cm),
            
            addSharpAngle_90deg("virage 90° 1-2"),

            innerShape(rectangle(25.1.cm, 22.cm)),
            addSectionVertical("Car. 2", -81.5.cm),

            addSharpAngle_90deg("virage 90° 2-3"),

            innerShape(rectangle(24.cm, 20.cm)),
            addSectionHorizontal("Car. 3", 179.2.cm),

            addSharpAngle_90deg("virage 90° 3-4", angleN2 = 90.degrees.some),

            addSectionHorizontal("Car. 4", 22.cm),

            addSharpAngle_90deg("virage 90° 4-5", angleN2 = 0.degrees.some),

            addSectionHorizontal("Car. 5", 8.cm),

            addSharpAngle_90deg("virage 90° 5-6", angleN2 = 0.degrees.some),

            addSectionHorizontal("Car. 6", 22.cm),

            addSharpAngle_90deg("virage 90° 6-7", angleN2 = 180.degrees.some),

            innerShape(rectangle(24.cm, 19.cm)),
            addSectionHorizontal("Car. 7", 190.cm),

            addSharpAngle_0_to_180deg("virage 20°", 20.degrees),

            addSectionHorizontal("Car. 8", 30.cm),

            addSharpAngle_0_to_180deg("virage 70°", 70.degrees, angleN2 = 90.degrees.some),

            innerShape(rectangle(24.cm, 21.cm)),
            addSectionHorizontal("Car. 9", 33.7.cm),

            addSharpAngle_90deg("virage 90° 9-10"),

            innerShape(rectangle(21.cm, 22.cm)),
            addSectionVertical("Car. 10", 98.cm),
        )   
        .toFullDescr().extractPipe

    val connectorPipe = 
        import ConnectorPipe_Module.*
        ConnectorPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(200.mm)),
            layer(e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
            pipeLocation(PipeLocation.HeatedArea),

            addSectionVertical("Car. 11", 60.cm),

            addSharpAngle_45deg("virage 45° 11-12"),
            
            addSectionSlopped("Car. 12", 50.cm, elevation_gain = 50.cm * math.cos(math.Pi / 4)),

            addSharpAngle_45deg("virage 45° 12-13"),

            addSectionVertical("Car. 13", 60.cm),
        )
        .toFullDescr().extractPipe

    val chimneyPipe = 
        import ChimneyPipe_Module.*
        ChimneyPipe_Module.incremental.define(
            roughness(0.1.mm), // 0.1mm de rugosité pour le EKA selon Basic2+ ??
            innerShape(circle(200.mm)),
            layer(e = 25.mm, tr = SquareMeterKelvinPerWatt(0.44)),
            
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("chauff.", 120.cm),

            pipeLocation(PipeLocation.UnheatedInside),
            addSectionVertical("non-chauff", 50.cm),

            pipeLocation(PipeLocation.OutsideOrExterior),
            addSectionVertical("ext.", 150.cm),

            addFlowResistance("element terminal", 1.0.unitless: ζ)
        )
        .toFullDescr().extractPipe

end CasType_15544_C2


