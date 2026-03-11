/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.FireCalcYAML_V4

import afpma.firecalc.engine.cas_types.en15544.v20241001.CasPratique_15544_FDIM_EX_03
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3
import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers

import afpma.firecalc.ui.instances.defaultable

import io.scalaland.chimney.dsl.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_15544_V3.SetInitialDirection

type EngineState = FireCalcYAML

object EngineState:

    // given Decoder[AppState] = FireCalcYAML.decoder
    // given Encoder[AppState] = FireCalcYAML.encoder

    lazy val example_projet_15544: EngineState = FireCalcYAML_V4(
        locale                         = Locale(Languages.Fr),
        display_units                  = DisplayUnits.SI,
        standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
        project_description            = ProjectDescr(
            reference = ExampleProject_15544.project.reference,
            date      = ExampleProject_15544.project.date,
            country   = ExampleProject_15544.project.country
        ),
        local_conditions               = ExampleProject_15544.localConditions,
        stove_params                   = ExampleProject_15544.stoveParams,
        air_intake_descr               = ExampleProject_15544.conduit_air_descr,
        firebox                        = ExampleProject_15544.foyer_descr.transformInto[Firebox.Traditional](using
            FireboxTransformers.transformer_inv_TraditionalFirebox_Standard
        ),
        flue_pipe_descr                = ExampleProject_15544.accumulateur_descr,
        connector_pipe_descr           = ExampleProject_15544.conduit_raccordement_descr,
        chimney_pipe_descr             = ExampleProject_15544.conduit_fumees_descr
    )

    lazy val init_as_CasType_15544_C3: EngineState = FireCalcYAML_V4(
        locale                         = Locale(Languages.Fr),
        display_units                  = DisplayUnits.SI,
        standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
        project_description            = ProjectDescr(
            reference = CasType_15544_C3.project.reference,
            date      = CasType_15544_C3.project.date,
            country   = CasType_15544_C3.project.country
        ),
        local_conditions               = CasType_15544_C3.localConditions,
        stove_params                   = CasType_15544_C3.stoveParams,
        air_intake_descr               = CasType_15544_C3.conduit_air_descr,
        firebox                        = CasType_15544_C3.foyer_descr.transformInto[Firebox.Ecolabeled](using
            FireboxTransformers.transformer_inv_Ecolabeled
        ),
        flue_pipe_descr                = CasType_15544_C3.accumulateur_descr,
        connector_pipe_descr           = CasType_15544_C3.conduit_raccordement_descr,
        chimney_pipe_descr             = CasType_15544_C3.conduit_fumees_descr
    )

    lazy val init_as_CasPratique_15544_FDIM_EX_03: EngineState = FireCalcYAML_V4(
        locale                         = Locale(Languages.Fr),
        display_units                  = DisplayUnits.SI,
        standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
        project_description            = ProjectDescr(
            reference = CasPratique_15544_FDIM_EX_03.project.reference,
            date      = CasPratique_15544_FDIM_EX_03.project.date,
            country   = CasPratique_15544_FDIM_EX_03.project.country
        ),
        local_conditions               = CasPratique_15544_FDIM_EX_03.localConditions,
        stove_params                   = CasPratique_15544_FDIM_EX_03.stoveParams,
        air_intake_descr               = CasPratique_15544_FDIM_EX_03.conduit_air_descr,
        firebox                        = CasPratique_15544_FDIM_EX_03.foyer_descr.transformInto[Firebox.Ecolabeled](using
            FireboxTransformers.transformer_inv_Ecolabeled
        ),
        flue_pipe_descr                = CasPratique_15544_FDIM_EX_03.accumulateur_descr,
        connector_pipe_descr           = CasPratique_15544_FDIM_EX_03.conduit_raccordement_descr,
        chimney_pipe_descr             = CasPratique_15544_FDIM_EX_03.conduit_fumees_descr
    )

    lazy val empty: EngineState = FireCalcYAML_V4(
        locale                         = Locale(Languages.Fr),
        display_units                  = DisplayUnits.SI,
        standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
        project_description            = ProjectDescr.empty, // Now only contains reference, date, country
        local_conditions               = LocalConditions.default,
        stove_params                   = StoveParamsUI.default_StoveParams.default,
        air_intake_descr               = Seq.empty,
        firebox                        = defaultable.firebox_traditional_empty.default,
        flue_pipe_descr                = Seq.empty,
        connector_pipe_descr           = Seq.empty,
        chimney_pipe_descr             = Seq.empty
    )

    lazy val minimal: EngineState = FireCalcYAML_V4(
        locale                         = Locale(Languages.Fr),
        display_units                  = DisplayUnits.SI,
        standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
        project_description            = ProjectDescr.empty, // Now only contains reference, date, country
        local_conditions               = LocalConditions.default,
        stove_params                   = StoveParamsUI.default_StoveParams.default,
        air_intake_descr               = Seq.empty,
        firebox                        = defaultable.firebox_traditional_minimal.default,
        flue_pipe_descr                =
            import FluePipe_Module_15544.*
            Seq(
                SetInitialDirection (azimuth = 90.degrees, inclination = 0.degrees),
                roughness           (3.mm                         ),
                innerShape(rectangle(18.cm, 18.cm)),
                addSectionHorizontal("sortie de foyer", 30.cm     ),
                addSharpAngle_90deg ("vers descente"              ),
                addSectionVertical  ("descente", -100.cm          ),
                addSharpAngle_90deg ("vers section horizontale"   ),
                addSectionHorizontal("section horizontale", 200.cm),
                addSharpAngle_90deg ("vers remontée"              ),
                addSectionVertical  ("remontée", 200.cm           )
            )
        ,
        connector_pipe_descr           =
            import ConnectorPipe_Module.*
            Seq (
                roughness (Material_13384.WeldedSteel()),
                innerShape(circle(20.cm)               ),
                layer             (e = 1.mm, tr = SquareMeterKelvinPerWatt(56.0)),
                pipeLocation      (PipeLocation.HeatedArea                      ),
                addSectionVertical("connecteur", 5.cm                           )
            )
        ,
        chimney_pipe_descr             =
            import ChimneyPipe_Module.*
            Seq (
                roughness (Material_13384.WeldedSteel()),
                innerShape(circle(200.mm)              ),
                layer             (e = 26.mm, tr = SquareMeterKelvinPerWatt(0.44)),
                pipeLocation      (PipeLocation.HeatedArea                       ),
                addSectionVertical("conduit double peau isolé.", 6.m             )
            )
    )

    lazy val init = minimal

end EngineState
