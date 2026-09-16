/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox
import afpma.firecalc.engine.models.en15544.std.*

import io.scalaland.chimney.*
import io.scalaland.chimney.dsl.*

object FireboxTransformers:

    /**
     * Aggregate transformer from sealed DTO `Firebox_V4` to `Firebox_15544`.
     *
     * The Austrian arms (Ecolabeled, Door15aFirebox_Catalog) arrive as given parameters
     * so this shared module never names the Austrian engine models directly.
     * The match stays exhaustive over the five sealed DTO subtypes; adding a sixth
     * will break compilation intentionally.
     */
    given transformer_Firebox_Firebox_15544(using
        ecolabeledT: Transformer[Firebox.Ecolabeled, Firebox_15544],
        door15aT   : Transformer[Firebox.Door15aFirebox_Catalog, Firebox_15544]
    ): Transformer[Firebox, Firebox_15544] = { fb =>
        fb match
            case x : Firebox.Traditional            =>
                transformer_Standard_TraditionalFirebox.transform(x)
            case x : Firebox.Ecolabeled             =>
                ecolabeledT.transform(x)
            case x : Firebox.AFPMA_PRSE             =>
                transformer_AFPMA_PRSE.transform(x)
            case st: Firebox.SingleTested           =>
                val singleTestedT = afpma.firecalc.engine.models.en15544.firebox.single_tested.transformer_SingleTested
                singleTestedT.transform(st)
                // throw new UnsupportedOperationException(
                //     "SingleTested fireboxes cannot be converted to Firebox_15544 — they use their own test data"
                // )
            case x : Firebox.Door15aFirebox_Catalog =>
                door15aT.transform(x)
    }

    // mappings to engine model
    given transformer_Standard_TraditionalFirebox: Transformer[Firebox.Traditional, firebox.TraditionalFirebox] =
        Transformer
            .define[Firebox.Traditional, firebox.TraditionalFirebox]
            .enableDefaultValues
            .withFieldRenamed(_.firebox_depth, _.firebox_depth_B)
            .withFieldRenamed(_.firebox_width, _.firebox_width_A)
            .withFieldRenamed(_.firebox_height, _.firebox_height_H)
            .withFieldRenamed(_.height_of_lowest_opening, _.ash_pit_height_AF)
            .buildTransformer

    given transformer_inv_TraditionalFirebox_Standard: Transformer[firebox.TraditionalFirebox, Firebox.Traditional] =
        import HeatOutputReduced.{NotDefined, HalfOfNominal}
        Transformer
            .define[firebox.TraditionalFirebox, Firebox.Traditional]
            .enableDefaultValues
            .withFieldComputed(
                _.heat_output_reduced,
                trad =>
                    val res: NotDefined | HalfOfNominal =
                        legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(trad.pn_reduced)
                    res
            )
            .withFieldRenamed(_.firebox_depth_B, _.firebox_depth)
            .withFieldRenamed(_.firebox_width_A, _.firebox_width)
            .withFieldRenamed(_.firebox_height_H, _.firebox_height)
            .withFieldRenamed(_.ash_pit_height_AF, _.height_of_lowest_opening)
            .buildTransformer

    private[firebox] def legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(
        p: HeatOutputReduced
    ): HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal =
        p match
            case nd: HeatOutputReduced.NotDefined    => nd
            case h : HeatOutputReduced.HalfOfNominal => h
            case HeatOutputReduced.FromTypeTest(v) =>
                HeatOutputReduced.HalfOfNominal.makeFromValue(v) // safe default, prevent throwing

    given Transformer[OutsideAirLocationInHeater, firebox.AFPMA_PRSE.AirIntakeDirection] =
        x =>
            x match
                case OutsideAirLocationInHeater.FromBottom =>
                    firebox.AFPMA_PRSE.AirIntakeDirection.FromBottom

    given transformer_AFPMA_PRSE: Transformer[Firebox.AFPMA_PRSE, firebox.AFPMA_PRSE] =
        Transformer
            .define[Firebox.AFPMA_PRSE, firebox.AFPMA_PRSE]
            .enableDefaultValues
            .withFieldRenamed(_.heat_output_reduced, _.pn_reduced)
            .withFieldRenamed(_.outside_air_location_in_heater, _.air_intake_direction)
            .withFieldRenamed(_.outside_air_conduit_shape, _.actual_air_intake_pipe_shape)
            .withFieldRenamed(_.firebox_depth, _.firebox_depth_B)
            .withFieldRenamed(_.firebox_width, _.firebox_width_A)
            .withFieldRenamed(_.firebox_height, _.firebox_height_H)
            .withFieldRenamed(_.height_of_first_row_of_air_injectors, _.height_of_first_row_of_air_injectors_X)
            .withFieldRenamed(_.glass_width, _.glass_width)
            .withFieldRenamed(_.glass_height, _.glass_height)
            .withFieldRenamed(_.ash_pit_height, _.ash_pit_height_AF)
            .withFieldRenamed(_.floor_thickness, _.firebox_floor_thickness)
            .withFieldRenamed(_.combustion_air_manifold_height, _.outside_air_inlet_lip_U)
            .withFieldRenamed(_.outside_air_inlet_lip, _.height_of_air_feed_to_columns_W)
            .withFieldRenamed(_.height_of_air_feed_to_columns, _.air_manifold_height_V)
            .withFieldRenamed(_.number_of_air_columns_feeding_firebox, _.nb_of_air_columns_feeding_firebox)
            .withFieldRenamed(_.number_of_air_columns_feeding_door, _.nb_of_air_columns_feeding_door)
            .buildTransformer

    private[firebox] def toTestEmissionValue(dto: TestEmissionValue_DTO): TestEmissionValue =
        TestEmissionValue(
            polluant_name = dto.polluant_name,
            valueO        = dto.valueO,
            test_method   = dto.test_method,
            o2ref         = dto.o2ref
        )
