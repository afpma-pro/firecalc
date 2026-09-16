/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.alg.en15544.RemoteFireboxData
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544

import cats.data.Validated

import coulomb.syntax.withUnit

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Phase 1 acceptance (fix-plan §2): the remote-backed registry built by
 * `EN15544_Strict_Application.withRemoteData` must
 *  - list all 6 closed kinds (dev. 2),
 *  - pass `checkTotality()` (dev. 2),
 *  - preserve `FireboxEngineRegistry_Strict` order (invariant 7),
 *  - register the actual kind via `RemoteBackedFireboxEngine` (dev. 1, 10),
 *    with narrow `F` + `TypeTest` (no `FireboxEngine[Firebox_15544]` widening).
 */
class RemoteRegistrySuite extends AnyFreeSpec with Matchers:

    private def stubEcolabeledDTO: Firebox.Ecolabeled =
        Firebox.Ecolabeled                    (
            heat_output_reduced                     = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            version                                 = Left("Version 1"),
            air_intake_shape                        = None,
            firebox_depth                           = 0.54.meters,
            firebox_width                           = 0.54.meters,
            firebox_height                          = 0.813.meters,
            height_of_first_row_of_air_injectors    = 0.10.meters,
            door_opening_width                      = 0.52.meters,
            glass_width                             = 0.50.meters,
            glass_height                            = 0.40.meters,
            ash_pit_height                          = 0.08.meters,
            air_manifold_height                     = 0.11.meters,
            firebox_floor_thickness                 = 0.08.meters,
            firebox_inner_wall_thickness            = 0.06.meters,
            firebox_outer_wall_thickness            = 0.06.meters,
            air_column_thickness                    = 0.035.meters,
            width_between_two_air_columns_sides     = 0.045.meters,
            width_between_two_air_columns_rear      = 0.045.meters,
            reinforcement_bars_offset_in_corners_R1 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R2 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R3 = 0.045.meters,
            injector_height                         = 0.008.meters
        )

    private def stubEmis: EmissionsAndEfficiencyValues_DTO =
        EmissionsAndEfficiencyValues_DTO               (
            firebox_name                = "test",
            accredited_or_notified_body = "test",
            test_reports                = Nil,
            emissions_values            = EmissionValues_DTO(
                co   = TestEmissionValue_DTO(PolluantName.CO, None, "", 0.0.percent),
                dust = TestEmissionValue_DTO(PolluantName.Dust, None, "", 0.0.percent),
                ogc  = TestEmissionValue_DTO(PolluantName.OGC, None, "", 0.0.percent),
                nox  = TestEmissionValue_DTO(PolluantName.NOx, None, "", 0.0.percent)
            )
        )

    private def stubDoor15aDTO: Firebox.Door15aFirebox_Catalog =
        Firebox.Door15aFirebox_Catalog                  (
            reference                   = "test",
            firebox_depth               = 1.0.cm,
            firebox_width               = 1.0.cm,
            firebox_height              = 1.0.cm,
            load_size_nominal           = Some(10.0.kg),
            sb                          = 2.4.withUnit[Centimeter],
            sb_min                      = None,
            sb_max                      = None,
            mb_min                      = None,
            mb_max                      = None,
            pressure_loss_table_raw     = "mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0\n10\t4\t3\t2\t1\n25\t22\t20\t18\t16",
            expectedAirIntakePipeShapes = List(PipeShape.Circle(10.0.cm)),
            actualAirIntakePipeShape    = PipeShape.Circle(10.0.cm),
            co2_dry_nominal             = 0.1.percent,
            co2_dry_lowest              = None,
            emissions_values            = stubEmis,
            glass_area                  = 1.0.cm2,
            height_of_lowest_opening    = 1.0.cm,
            heat_output_reduced         = HeatOutputReduced.NotDefined,
            image                       = None
        )

    private def stubStoveParams: StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = 26.0.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    private def buildEngineState(firebox: Firebox): FireCalcYAML_V7 =
        FireCalcYAML_V7                        (
            locale                         = Locale(Languages.Fr),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = ProjectDescr(
                reference = TestScaffold_15544.project.reference,
                date      = TestScaffold_15544.project.date,
                country   = TestScaffold_15544.project.country
            ),
            local_conditions               = TestScaffold_15544.localConditions,
            stove_params                   = stubStoveParams,
            air_intake_pipes               = FramedAirIntakePipes.fromLegacy(TestScaffold_15544.conduit_air_descr),
            firebox                        = firebox,
            post_firebox_pipes             = FramedPostFireboxPipes(
                PipeInitialDirection.default,
                PostFireboxStartPosition.Auto,
                slots = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot     (TestScaffold_15544.accumulateur_descr        ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(TestScaffold_15544.conduit_raccordement_descr),
                    PostFireboxPipeDescrSlot.ChimneySlot  (TestScaffold_15544.conduit_fumees_descr      )
                )
            )
        )

    /** A minimal RemoteFireboxData — empty contributions + verdicts, stub report. */
    private def stubRemoteData: RemoteFireboxData[
        FireboxPipe_Module_15544.FullDescr,
        CombustionAirPipe_Module_15544.FullDescr
    ] =
        RemoteFireboxData      (
            fireboxContribution       = Validated.valid(FireboxContribution.Without),
            combustionAirContribution = Validated.valid(FireboxContribution.Without),
            constraints               = Nil,
            reportData                = FireboxReportData(
                fireboxDescription     = WireTable(header = None, colHeaders = None, rows = Nil),
                emissionsAndEfficiency = WireTable(header = None, colHeaders = None, rows = Nil),
                combustionAirPipeRows  = Nil,
                fireboxPipeRows        = Nil,
                ecolabeledOutputs      = None
            )
        )

    "Remote-backed registry (Phase 1)" - {

        "builds a 6-entry registry that passes checkTotality()" in {
            val yaml     = buildEngineState(stubEcolabeledDTO)
            val loader   = FireCalcYAML_Loader(yaml)
            val appV     = loader.make_en15544_Strict_Application
            appV.isValid shouldBe true
            val base     = appV.toOption.get
            val remote   = EN15544_Strict_Application.withRemoteData(base, stubRemoteData)
            val registry = remote.activeRegistry.asInstanceOf[
                afpma.firecalc.engine.alg.en15544.FireboxEngineRegistry[
                    FireboxPipe_Module_15544.FullDescr,
                    CombustionAirPipe_Module_15544.FullDescr
                ]
            ]
            // Dev 2: 6 closed kinds, totality checked at construction (no throw).
            registry.all.size shouldBe 6
            registry.name shouldBe "RemoteBackedFireboxEngineRegistry"
        }

        "preserves FireboxEngineRegistry_Strict order (invariant 7)" in {
            val yaml        = buildEngineState(stubEcolabeledDTO)
            val loader      = FireCalcYAML_Loader(yaml)
            val base        = loader.make_en15544_Strict_Application.toOption.get
            val remote      = EN15544_Strict_Application.withRemoteData(base, stubRemoteData)
            val registry    = remote.activeRegistry.asInstanceOf[
                afpma.firecalc.engine.alg.en15544.FireboxEngineRegistry[
                    FireboxPipe_Module_15544.FullDescr,
                    CombustionAirPipe_Module_15544.FullDescr
                ]
            ]
            val remoteKinds = registry.all.map(_.kind)
            val strictKinds = FireboxEngineRegistry_Strict.all.map(_.kind)
            remoteKinds shouldBe strictKinds
        }

        "registers a RemoteBackedFireboxEngine for the configured Ecolabeled kind (dev. 1, 10)" in {
            // Ecolabeled configured Remote: the entry for FireboxKind.Ecolabeled
            // must be the remote-backed engine, identifiable by its kind and by
            // dispatch returning the stubbed Without contribution.
            val yaml   = buildEngineState(stubEcolabeledDTO)
            val loader = FireCalcYAML_Loader(yaml)
            val base   = loader.make_en15544_Strict_Application.toOption.get

            val remote     = EN15544_Strict_Application.withRemoteData(base, stubRemoteData)
            val registry   = remote.activeRegistry.asInstanceOf[
                afpma.firecalc.engine.alg.en15544.FireboxEngineRegistry[
                    FireboxPipe_Module_15544.FullDescr,
                    CombustionAirPipe_Module_15544.FullDescr
                ]
            ]
            val ecoEntry   = registry.all.find(_.kind == FireboxKind.Ecolabeled)
            ecoEntry.isDefined shouldBe true
            // The remote-backed engine returns the stubbed Without contribution,
            // unlike the carrier's generic AsDescr contribution. This is the spy.
            val firebox    = base.inputs.design.firebox
            val dispatched = registry.engineFor(firebox)
            dispatched.isRight shouldBe true
            val pair       = dispatched.toOption.get
            val contrib    = pair.engine.fireboxContribution(pair.firebox)
            contrib.toOption.get shouldBe FireboxContribution.Without
        }

        "resolves a Door15a catalog firebox to FireboxKind.Door15aCatalog without MatchError (dev. 7)" in {
            val yaml       = buildEngineState(stubDoor15aDTO)
            val loader     = FireCalcYAML_Loader(yaml)
            val appV       = loader.make_en15544_Strict_Application
            appV.isValid shouldBe true
            val base       = appV.toOption.get
            // Before the Phase 3 fix the next line threw MatchError on a Door15a
            // firebox: the fireboxKind match omitted the Door15aFirebox_Catalog arm.
            val remote     = EN15544_Strict_Application.withRemoteData(base, stubRemoteData)
            val registry   = remote.activeRegistry.asInstanceOf[
                afpma.firecalc.engine.alg.en15544.FireboxEngineRegistry[
                    FireboxPipe_Module_15544.FullDescr,
                    CombustionAirPipe_Module_15544.FullDescr
                ]
            ]
            registry.all.size shouldBe 6
            // fireboxKind resolved to Door15aCatalog: the Door15a entry is the
            // remote-backed engine, which returns the stubbed Without contribution
            // (the carrier's generic engine returns AsDescr). This is the remote spy.
            val firebox    = base.inputs.design.firebox
            val dispatched = registry.engineFor(firebox)
            dispatched.isRight shouldBe true
            val pair       = dispatched.toOption.get
            val contrib    = pair.engine.fireboxContribution(pair.firebox)
            contrib.toOption.get shouldBe FireboxContribution.Without
        }
    }

    "narrow is non-vacuous under TypeTest: an entry accepts its own kind and rejects a different kind" in {
        // Two engine-model fireboxes of different kinds, built the same way the
        // dispatch tests above build them (DTO → loader → inputs.design.firebox).
        val ecoFb  = FireCalcYAML_Loader(
            buildEngineState(stubEcolabeledDTO)
        ).make_en15544_Strict_Application.toOption.get.inputs.design.firebox
        val doorFb = FireCalcYAML_Loader(
            buildEngineState(stubDoor15aDTO)
        ).make_en15544_Strict_Application.toOption.get.inputs.design.firebox

        val entries   = FireboxEngineRegistry_Strict.all
        val ecoEntry  = entries.find(_.kind == FireboxKind.Ecolabeled).get
        val doorEntry = entries.find(_.kind == FireboxKind.Door15aCatalog).get

        // Each entry accepts its own kind ...
        ecoEntry.narrow(ecoFb).isDefined shouldBe true
        doorEntry.narrow(doorFb).isDefined shouldBe true
        // ... and rejects a different kind. This is the unsoundness guard the
        // `FireboxEngineAny` scaladoc warns about: had `narrow` been
        // `fb.isInstanceOf[FB]` with `FB` erased, every entry would answer every
        // firebox and `withEngine`'s `collectFirst` would always pick the first.
        ecoEntry.narrow(doorFb).isEmpty shouldBe true
        doorEntry.narrow(ecoFb).isEmpty shouldBe true
    }
