/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Door15aFirebox_Catalog.SB
import afpma.firecalc.engine.models.en15544.typedefs.{σ_CO2, GlassArea}

import io.scalaland.chimney.*
import io.scalaland.chimney.dsl.*

object FireboxTransformers:

    given transformer_Firebox_Firebox_15544: Transformer[Firebox, Firebox_15544] = { fb =>
        fb match
            case x: Firebox.Traditional  =>
                transformer_Standard_TraditionalFirebox.transform(x)
            case x: Firebox.Ecolabeled   =>
                transformer_Ecolabeled_Ecolabeled.transform(x)
            case x: Firebox.AFPMA_PRSE   =>
                transformer_AFPMA_PRSE.transform(x)
            case st: Firebox.SingleTested =>
                val singleTestedT = afpma.firecalc.engine.models.en15544.firebox.single_tested.transformer_SingleTested
                singleTestedT.transform(st)
                // throw new UnsupportedOperationException(
                //     "SingleTested fireboxes cannot be converted to Firebox_15544 — they use their own test data"
                // )
            case x: Firebox.Door15aFirebox_Catalog =>
                transformer_dto_Door15aFirebox_Catalog_to_Door15aFirebox_Catalog.transform(x)
    }

    // mappings to engine model
    given transformer_Standard_TraditionalFirebox
        : Transformer[Firebox.Traditional, firebox.TraditionalFirebox] =
        Transformer
            .define[Firebox.Traditional, firebox.TraditionalFirebox]
            .enableDefaultValues
            .withFieldRenamed(_.firebox_depth, _.h11_profondeurDuFoyer)
            .withFieldRenamed(_.firebox_width, _.h12_largeurDuFoyer)
            .withFieldRenamed(_.firebox_height, _.h13_hauteurDuFoyer)
            .withFieldRenamed(_.height_of_lowest_opening, _.ash_pit_height)
            .withFieldRenamed(_.pressure_loss_coefficient_from_door, _.h66_coeffPerteDeChargePorte)
            .withFieldRenamed(_.total_air_intake_surface_area_on_door, _.h67_sectionCumuleeEntreeAirPorte)
            .withFieldRenamed(_.glass_width, _.h71_largeurVitre)
            .withFieldRenamed(_.glass_height, _.h72_hauteurVitre)
            .buildTransformer

    given transformer_inv_TraditionalFirebox_Standard
        : Transformer[firebox.TraditionalFirebox, Firebox.Traditional] =
        import HeatOutputReduced.{NotDefined, HalfOfNominal}
        Transformer
            .define[firebox.TraditionalFirebox, Firebox.Traditional]
            .enableDefaultValues
            .withFieldComputed(
                _.heat_output_reduced, 
                trad => 
                    val res: NotDefined | HalfOfNominal = legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(trad.pn_reduced)
                    res
                )
            .withFieldRenamed(_.h11_profondeurDuFoyer, _.firebox_depth)
            .withFieldRenamed(_.h12_largeurDuFoyer, _.firebox_width)
            .withFieldRenamed(_.h13_hauteurDuFoyer, _.firebox_height)
            .withFieldRenamed(_.h66_coeffPerteDeChargePorte, _.pressure_loss_coefficient_from_door)
            .withFieldRenamed(_.h67_sectionCumuleeEntreeAirPorte, _.total_air_intake_surface_area_on_door)
            .withFieldRenamed(_.h71_largeurVitre, _.glass_width)
            .withFieldRenamed(_.h72_hauteurVitre, _.glass_height)
            .buildTransformer

    given transformer_Ecolabeled_Ecolabeled: Transformer[Firebox.Ecolabeled, firebox.Ecolabeled] = e =>
        import e.*
        e.version match
            case Left("Version 1")  =>
                firebox.Ecolabeled_V1                                     (
                    pn_reduced                                      = heat_output_reduced,
                    h11_profondeurDuFoyer                           = firebox_depth,
                    h12_largeurDuFoyer                              = firebox_width,
                    h13_hauteurDuFoyer                              = firebox_height,
                    h70_largeurPorteDansMaconnerie                  = door_opening_width,
                    h71_largeurVitre                                = glass_width,
                    h72_hauteurVitre                                = glass_height,
                    h74_hauteur_de_cendrier_AF                      = ash_pit_height,
                    h75_hauteurArriveeConduitAir_DessousSoleFoyer_W = air_manifold_height,
                    h76_epaisseurSole                               = firebox_floor_thickness,
                    h77_epaisseurParoiInterneFoyer_D1               = firebox_inner_wall_thickness,
                    epaisseurParoiExterneFoyer_D2                   = firebox_outer_wall_thickness,
                    h78_largeurEspaceInterparoisDuFoyer_S           = air_column_thickness,
                    h79_largeurRenfortMedianLateraux                = width_between_two_air_columns_sides,
                    h80_largeurRenfortMedianArriere                 = width_between_two_air_columns_rear,
                    r1                                              = reinforcement_bars_offset_in_corners_R1,
                    r2                                              = reinforcement_bars_offset_in_corners_R2,
                    r3                                              = reinforcement_bars_offset_in_corners_R3,
                    h82_hauteurDesInjecteurs_Z                      = injector_height,
                    h83_hauteurEntreLaSoleEtLe1erInjecteur_X        = height_of_first_row_of_air_injectors
                )
            case Right("Version 2") =>
                firebox.Ecolabeled_V2                                     (
                    pn_reduced                                      = heat_output_reduced,
                    arriveeAirGeometry                              = air_intake_shape.getOrElse(Circle(200.mm)),
                    h11_profondeurDuFoyer                           = firebox_depth,
                    h12_largeurDuFoyer                              = firebox_width,
                    h13_hauteurDuFoyer                              = firebox_height,
                    h70_largeurPorteDansMaconnerie                  = door_opening_width,
                    h71_largeurVitre                                = glass_width,
                    h72_hauteurVitre                                = glass_height,
                    h74_hauteur_de_cendrier_AF                      = ash_pit_height,
                    h75_hauteurArriveeConduitAir_DessousSoleFoyer_W = air_manifold_height,
                    h76_epaisseurSole                               = firebox_floor_thickness,
                    h77_epaisseurParoiInterneFoyer_D1               = firebox_inner_wall_thickness,
                    epaisseurParoiExterneFoyer_D2                   = firebox_outer_wall_thickness,
                    h78_largeurEspaceInterparoisDuFoyer_S           = air_column_thickness,
                    h79_largeurRenfortMedianLateraux                = width_between_two_air_columns_sides,
                    h80_largeurRenfortMedianArriere                 = width_between_two_air_columns_rear,
                    r1                                              = reinforcement_bars_offset_in_corners_R1,
                    r2                                              = reinforcement_bars_offset_in_corners_R2,
                    r3                                              = reinforcement_bars_offset_in_corners_R3,
                    h82_hauteurDesInjecteurs_Z                      = injector_height,
                    h83_hauteurEntreLaSoleEtLe1erInjecteur_X        = height_of_first_row_of_air_injectors
                )

    private def legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(p: HeatOutputReduced): HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal =
        p match
            case nd: HeatOutputReduced.NotDefined    => nd
            case h: HeatOutputReduced.HalfOfNominal  => h
            case HeatOutputReduced.FromTypeTest(v)   => HeatOutputReduced.HalfOfNominal.makeFromValue(v) // safe default, prevent throwing

    given transformer_inv_Ecolabeled: Transformer[firebox.Ecolabeled, Firebox.Ecolabeled] = e =>
        import e.*
        e match
            case _: firebox.Ecolabeled_V1 =>
                Firebox.Ecolabeled                 (
                    heat_output_reduced                     = legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(pn_reduced),
                    version                                 = Left("Version 1"),
                    air_intake_shape                        = None,
                    firebox_depth                           = h11_profondeurDuFoyer,
                    firebox_width                           = h12_largeurDuFoyer,
                    firebox_height                          = h13_hauteurDuFoyer,
                    door_opening_width                      = h70_largeurPorteDansMaconnerie,
                    glass_width                             = h71_largeurVitre,
                    glass_height                            = h72_hauteurVitre,
                    ash_pit_height                          = h74_hauteur_de_cendrier_AF,
                    air_manifold_height                     = h75_hauteurArriveeConduitAir_DessousSoleFoyer_W,
                    firebox_floor_thickness                 = h76_epaisseurSole,
                    firebox_inner_wall_thickness            = h77_epaisseurParoiInterneFoyer_D1,
                    firebox_outer_wall_thickness            = epaisseurParoiExterneFoyer_D2,
                    air_column_thickness                    = h78_largeurEspaceInterparoisDuFoyer_S,
                    width_between_two_air_columns_sides     = h79_largeurRenfortMedianLateraux,
                    width_between_two_air_columns_rear      = h80_largeurRenfortMedianArriere,
                    reinforcement_bars_offset_in_corners_R1 = r1,
                    reinforcement_bars_offset_in_corners_R2 = r2,
                    reinforcement_bars_offset_in_corners_R3 = r3,
                    injector_height                         = h82_hauteurDesInjecteurs_Z,
                    height_of_first_row_of_air_injectors    = h83_hauteurEntreLaSoleEtLe1erInjecteur_X
                )
            case _: firebox.Ecolabeled_V2 =>
                Firebox.Ecolabeled                 (
                    heat_output_reduced                     = legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(pn_reduced),
                    version                                 = Right("Version 2"),
                    air_intake_shape                        = arriveeAirGeometryOpt,
                    firebox_depth                           = h11_profondeurDuFoyer,
                    firebox_width                           = h12_largeurDuFoyer,
                    firebox_height                          = h13_hauteurDuFoyer,
                    door_opening_width                      = h70_largeurPorteDansMaconnerie,
                    glass_width                             = h71_largeurVitre,
                    glass_height                            = h72_hauteurVitre,
                    ash_pit_height                          = h74_hauteur_de_cendrier_AF,
                    air_manifold_height                     = h75_hauteurArriveeConduitAir_DessousSoleFoyer_W,
                    firebox_floor_thickness                 = h76_epaisseurSole,
                    firebox_inner_wall_thickness            = h77_epaisseurParoiInterneFoyer_D1,
                    firebox_outer_wall_thickness            = epaisseurParoiExterneFoyer_D2,
                    air_column_thickness                    = h78_largeurEspaceInterparoisDuFoyer_S,
                    width_between_two_air_columns_sides     = h79_largeurRenfortMedianLateraux,
                    width_between_two_air_columns_rear      = h80_largeurRenfortMedianArriere,
                    reinforcement_bars_offset_in_corners_R1 = r1,
                    reinforcement_bars_offset_in_corners_R2 = r2,
                    reinforcement_bars_offset_in_corners_R3 = r3,
                    injector_height                         = h82_hauteurDesInjecteurs_Z,
                    height_of_first_row_of_air_injectors    = h83_hauteurEntreLaSoleEtLe1erInjecteur_X
                )
            case _ => throw new Exception("not implemented")

    given Transformer[OutsideAirLocationInHeater, firebox.AFPMA_PRSE.OutsideAirLocationInHeater] =
        x =>
            x match
                case OutsideAirLocationInHeater.FromBottom =>
                    firebox.AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom

    given transformer_AFPMA_PRSE: Transformer[Firebox.AFPMA_PRSE, firebox.AFPMA_PRSE] =
        Transformer
            .define[Firebox.AFPMA_PRSE, firebox.AFPMA_PRSE]
            .enableDefaultValues
            .withFieldRenamed(_.heat_output_reduced, _.pn_reduced)
            .withFieldRenamed(_.outside_air_location_in_heater, _.origineArriveeAir)
            .withFieldRenamed(_.outside_air_conduit_shape, _.arriveeAirGeometry)
            .withFieldRenamed(_.firebox_depth, _.h11_profondeurDuFoyer)
            .withFieldRenamed(_.firebox_width, _.h12_largeurDuFoyer)
            .withFieldRenamed(_.firebox_height, _.h13_hauteurDuFoyer)
            .withFieldRenamed(_.height_of_first_row_of_air_injectors, _.h83_hauteurEntreSoleEt1erInjecteur_X)
            .withFieldRenamed(_.glass_width, _.h88_largeurVitre)
            .withFieldRenamed(_.glass_height, _.h89_hauteurVitre)
            .withFieldRenamed(_.ash_pit_height, _.h91_hauteurDuCendrier_AF)
            .withFieldRenamed(_.floor_thickness, _.h92_epaisseurSole_S)
            .withFieldRenamed(_.combustion_air_manifold_height, _.h93_hauteurEmbaseDessousSoleFoyer_V)
            .withFieldRenamed(_.outside_air_inlet_lip, _.h94_hauteurDepassementArriveeAirFoyer_U)
            .withFieldRenamed(_.height_of_air_feed_to_columns, _.h95_hauteurPassageVersColonneAir_W)
            .withFieldRenamed(_.number_of_air_columns_feeding_firebox, _.h96_nbColonnesAirFoyer)
            .withFieldRenamed(_.number_of_air_columns_feeding_door, _.h97_nbColonnesAirPorte)
            .buildTransformer

    given transformer_dto_Door15aFirebox_Catalog_to_Door15aFirebox_Catalog
        : Transformer[Firebox.Door15aFirebox_Catalog, en15544.std.Door15aFirebox_Catalog] = dto_fb =>
            import dto_fb.*
            import cats.data.Validated.valid
            Door15aFirebox_Catalog_DatabaseEntry(
                uniq_id                    = reference,
                mb                         = load_size_nominal,
                sb                         = sb,
                dimensions                 = Dimensions(
                    base = Dimensions.Base.Squared(
                        width = firebox_width,
                        depth = firebox_depth
                    ),
                    height = firebox_height
                ),
                sb_min                     = sb_min.map(v => v: SB),
                sb_max                     = sb_max.map(v => v: SB),
                mb_min                     = mb_min,
                mb_max                     = mb_max,
                pressure_loss_table_raw    = pressure_loss_table_raw,
                expectedAirIntakePipeShapes = expectedAirIntakePipeShapes,
                actualAirIntakePipeShape   = actualAirIntakePipeShape,
                co2_dry_nominal            = co2_dry_nominal: σ_CO2,
                co2_dry_lowest             = co2_dry_lowest.map(v => v: σ_CO2),
                emissions_values           = EmissionsAndEfficiencyValues(
                    firebox_name                       = emissions_values.firebox_name,
                    accredited_or_notified_body        = emissions_values.accredited_or_notified_body,
                    test_reports                       = emissions_values.test_reports,
                    min_efficiency_firebox_nominal     = None,
                    min_efficiency_full_stove_nominal  = valid(None),
                    min_efficiency_firebox_reduced     = None,
                    min_efficiency_full_stove_reduced  = valid(None),
                    min_seasonal_efficiency_full_stove = valid(None),
                    emissions_values                   = EmissionValues(
                        co   = toTestEmissionValue(emissions_values.emissions_values.co),
                        dust = toTestEmissionValue(emissions_values.emissions_values.dust),
                        ogc  = toTestEmissionValue(emissions_values.emissions_values.ogc),
                        nox  = toTestEmissionValue(emissions_values.emissions_values.nox)
                    )
                ),
                glass_area                 = glass_area: GlassArea,
                height_of_lowest_opening   = height_of_lowest_opening,
                pn_reduced                 = heat_output_reduced,
            )

    private[firebox] def toTestEmissionValue(dto: TestEmissionValue_DTO): TestEmissionValue =
        TestEmissionValue(
            polluant_name = dto.polluant_name,
            valueO        = dto.valueO,
            test_method   = dto.test_method,
            o2ref         = dto.o2ref
        )