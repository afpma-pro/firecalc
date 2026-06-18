/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.Position3D

import afpma.firecalc.engine.cas_types.en15544.v20241001.CasPratique_15544_FDIM_EX_03
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3
import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers

import afpma.firecalc.ui.instances.defaultable

import io.scalaland.chimney.dsl.*
import io.taig.babel.Languages
import io.taig.babel.Locale

type EngineState = FireCalcYAML

object EngineState:

    // given Decoder[AppState] = FireCalcYAML.decoder
    // given Encoder[AppState] = FireCalcYAML.encoder

    lazy val example_projet_15544: EngineState = FireCalcYAML_V7(
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
        air_intake_pipes               = FramedAirIntakePipes.fromLegacy(ExampleProject_15544.conduit_air_descr),
        firebox                        = ExampleProject_15544.foyer_descr.transformInto[Firebox.Traditional](using
            FireboxTransformers.transformer_inv_TraditionalFirebox_Standard
        ),
        post_firebox_pipes             = FramedPostFireboxPipes.clean(
            initialDirection = PipeInitialDirection(
                azimuth     = AzimuthDirection.Left,
                inclination = InclinationDirection.Horizontal
            ), // Left,
            initialPosition  = PostFireboxStartPosition.Manual(Position3D(-21.cm, (44 / 2 - 25 / 2).cm, (78 - 15).cm)),
            slots            = Seq(
                PostFireboxPipeDescrSlot_V7.FlueSlot     (ExampleProject_15544.accumulateur_descr        ),
                PostFireboxPipeDescrSlot_V7.ConnectorSlot(ExampleProject_15544.conduit_raccordement_descr),
                PostFireboxPipeDescrSlot_V7.ChimneySlot  (ExampleProject_15544.conduit_fumees_descr      )
            )
        )
    )

    lazy val init_as_CasType_15544_C3: EngineState = FireCalcYAML_V7(
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
        air_intake_pipes               = FramedAirIntakePipes.fromLegacy(CasType_15544_C3.conduit_air_descr),
        firebox                        = CasType_15544_C3.foyer_descr.transformInto[Firebox.Ecolabeled](using
            FireboxTransformers.transformer_inv_Ecolabeled
        ),
        post_firebox_pipes             = FramedPostFireboxPipes.fromLegacySlots(
            Seq(
                PostFireboxPipeDescrSlot_V7.FlueSlot     (CasType_15544_C3.accumulateur_descr        ),
                PostFireboxPipeDescrSlot_V7.ConnectorSlot(CasType_15544_C3.conduit_raccordement_descr),
                PostFireboxPipeDescrSlot_V7.ChimneySlot  (CasType_15544_C3.conduit_fumees_descr      )
            )
        )
    )

    lazy val init_as_CasPratique_15544_FDIM_EX_03: EngineState = FireCalcYAML_V7(
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
        air_intake_pipes               = FramedAirIntakePipes.fromLegacy(CasPratique_15544_FDIM_EX_03.conduit_air_descr),
        firebox                        = CasPratique_15544_FDIM_EX_03.foyer_descr.transformInto[Firebox.Ecolabeled](using
            FireboxTransformers.transformer_inv_Ecolabeled
        ),
        post_firebox_pipes             = FramedPostFireboxPipes.fromLegacySlots(
            Seq(
                PostFireboxPipeDescrSlot_V7.FlueSlot     (CasPratique_15544_FDIM_EX_03.accumulateur_descr        ),
                PostFireboxPipeDescrSlot_V7.ConnectorSlot(CasPratique_15544_FDIM_EX_03.conduit_raccordement_descr),
                PostFireboxPipeDescrSlot_V7.ChimneySlot  (CasPratique_15544_FDIM_EX_03.conduit_fumees_descr      )
            )
        )
    )

    lazy val empty: EngineState = FireCalcYAML_V7(
        locale                         = Locale(Languages.Fr),
        display_units                  = DisplayUnits.SI,
        standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
        project_description            = ProjectDescr.empty, // Now only contains reference, date, country
        local_conditions               = LocalConditions.default,
        stove_params                   = StoveParamsUI.default_StoveParams.default,
        air_intake_pipes               = FramedAirIntakePipes.fromLegacy(Seq.empty),
        firebox                        = defaultable.firebox_traditional_empty.default,
        post_firebox_pipes             = FramedPostFireboxPipes.clean(
            initialDirection = PipeInitialDirection.default,
            initialPosition  = PostFireboxStartPosition.Auto,
            slots            = Seq(
                PostFireboxPipeDescrSlot_V7.FlueSlot     (Seq.empty),
                PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty)
            )
        )
    )

    lazy val minimal: EngineState = FireCalcYAML_V7(
        locale                         = Locale(Languages.Fr),
        display_units                  = DisplayUnits.SI,
        standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
        project_description            = ProjectDescr.empty, // Now only contains reference, date, country
        local_conditions               = LocalConditions.default,
        stove_params                   = StoveParamsUI.default_StoveParams.default,
        air_intake_pipes               = FramedAirIntakePipes.fromLegacy(Seq.empty),
        firebox                        = defaultable.firebox_traditional_minimal.default,
        post_firebox_pipes             = FramedPostFireboxPipes.clean(
            initialDirection = PipeInitialDirection(
                azimuth     = AzimuthDirection.Right,
                inclination = InclinationDirection.Horizontal
            ),
            initialPosition  = PostFireboxStartPosition.Auto,
            slots            = Seq(
                PostFireboxPipeDescrSlot_V7.FlueSlot {
                    import FluePipe_Module_15544.*
                    Seq(
                        roughness           (3.mm                         ),
                        innerShape(rectangle(18.cm, 18.cm)),
                        addSectionHorizontal("sortie de foyer", 30.cm     ),
                        addSharpAngle_90deg (
                            "vers descente",
                            absDir = AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Down)
                        ), // Down
                        addSectionVertical  ("descente", -100.cm          ),
                        addSharpAngle_90deg (
                            "vers section horizontale",
                            absDir = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                        ), // Right
                        addSectionHorizontal("section horizontale", 200.cm),
                        addSharpAngle_90deg (
                            "vers remontée",
                            absDir = AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up)
                        ), // Up
                        addSectionVertical  ("remontée", 200.cm           )
                    )
                },
                PostFireboxPipeDescrSlot_V7.ConnectorSlot {
                    import ConnectorPipe_Module.*
                    Seq (
                        roughness (Material_13384.WeldedSteel()),
                        innerShape(circle(20.cm)               ),
                        layer             (e = 1.mm, tr = SquareMeterKelvinPerWatt(0.44)),
                        pipeLocation      (PipeLocation.HeatedArea                      ),
                        addSectionVertical("connecteur", 5.cm                           )
                    )
                },
                PostFireboxPipeDescrSlot_V7.ChimneySlot {
                    import ChimneyPipe_Module.*
                    Seq (
                        roughness (Material_13384.WeldedSteel()),
                        innerShape(circle(200.mm)              ),
                        layer             (e = 26.mm, tr = SquareMeterKelvinPerWatt(0.44)),
                        pipeLocation      (PipeLocation.HeatedArea                       ),
                        addSectionVertical("conduit double peau isolé.", 6.m             )
                    )
                }
            )
        )
    )

    lazy val init = minimal

end EngineState
