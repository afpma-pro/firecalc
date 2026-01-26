/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import cats.data.ValidatedNel

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox
import afpma.firecalc.engine.models.en15544.ConstraintSlots
import afpma.firecalc.engine.models.en15544.FireboxModule_15544_MCE
import afpma.firecalc.engine.models.en15544.FireboxModule_15544_Strict
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.*
import afpma.firecalc.engine.models.en15544.typedefs.GlassArea

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import io.scalaland.chimney.*
import io.scalaland.chimney.dsl.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions.Base
import afpma.firecalc.engine.standard.FireboxBaseRatioInvalid
import afpma.firecalc.engine.standard.GlassSurfaceRatioNotConfirmed

trait From_CalculPdM_V_0_2_32 extends OneOff:
    def h11_profondeurDuFoyer: QtyD[Meter]
    def h12_largeurDuFoyer: QtyD[Meter]
    def h13_hauteurDuFoyer: QtyD[Meter]

    def largeurVitre: Length
    def hauteurVitre: Length
    def surfaceVitre: Area = largeurVitre * hauteurVitre
    def glass_area: GlassArea = surfaceVitre
    def dimensions = Dimensions(
        base = Dimensions.Base.Squared(
            width = h12_largeurDuFoyer,
            depth = h11_profondeurDuFoyer
        ),
        height = h13_hauteurDuFoyer,
    )

    lazy val fireboxDimensions_Base_constraint_ratioWhenSquared: TermConstraint[Base] =
        TermConstraint.GenericTyped(
            value = dimensions.base,
            isValid =
                case sqBase: Base.Squared =>
                    val (l, w) = (sqBase.depth, sqBase.width)
                    val ratio: Dimensionless = l / w
                    ratio.value match
                        case r if r < 0.5 || r > 2 =>
                            Left(
                                FireboxBaseRatioInvalid(r.showP, l.showP, w.showP)
                            )
                        case _ =>
                            Right(sqBase)
        )

    override def fireboxDimensions_Base_constraintSlots: ConstraintSlots.FireboxDimensionsBase =
        ConstraintSlots.FireboxDimensionsBase(
            ratioWhenSquared = Some(fireboxDimensions_Base_constraint_ratioWhenSquared)
        )

    def area_calc_method: AreaCalcMethod
    /** See EN 15544 - Section 4.3.1.2 */
    def area_O_BR: Area = area_calc_method match
        case AreaCalcMethod.AutoIfCubic =>
            val base_or_ceiling = h11_profondeurDuFoyer * h12_largeurDuFoyer
            val left_or_right   = h13_hauteurDuFoyer    * h11_profondeurDuFoyer
            val front_or_back   = h13_hauteurDuFoyer    * h12_largeurDuFoyer
            2 * (base_or_ceiling + left_or_right + front_or_back)
        case AreaCalcMethod.Manual(v) => v

    def firebox_glass_surface_ratio_below_one_fifth: Boolean = 
        (glass_area / area_O_BR) <= (1.0 / 5.0)

    override def firebox_glass_surface_ratio_below_one_fifth_constraint: Option[TermConstraint[Unit]] =
        import afpma.firecalc.engine.models.en15544.typedefs.{given_TermDef_Unit, given_TermDefDetails_Unit}
        Some(TermConstraint.GenericTyped[Unit, GlassSurfaceRatioNotConfirmed](
            value = (),
            isValid = _ =>
                if firebox_glass_surface_ratio_below_one_fifth then Right(())
                else Left(GlassSurfaceRatioNotConfirmed())
        ))

object From_CalculPdM_V_0_2_32:

    given transformer_Firebox_From_CalculPdM_V_0_2_32: Transformer[Firebox, From_CalculPdM_V_0_2_32] = { ccui => ccui match
        case x: Firebox.Traditional => x.into[firebox.calcpdm_v_0_2_32.TraditionalFirebox].transform
        case x: Firebox.EcoLabeled  => x.into[firebox.calcpdm_v_0_2_32.EcoLabeled].transform
        case x: Firebox.AFPMA_PRSE  => x.into[firebox.calcpdm_v_0_2_32.AFPMA_PRSE].transform
    }

    // mappings to engine model
    given transformer_Standard_TraditionalFirebox: Transformer[Firebox.Traditional, firebox.calcpdm_v_0_2_32.TraditionalFirebox] =
        Transformer.define[Firebox.Traditional, firebox.calcpdm_v_0_2_32.TraditionalFirebox]
        .enableDefaultValues
        .withFieldRenamed(_.heat_output_reduced,                   _.pn_reduced)
        .withFieldRenamed(_.firebox_depth,                         _.h11_profondeurDuFoyer)
        .withFieldRenamed(_.firebox_width,                         _.h12_largeurDuFoyer)
        .withFieldRenamed(_.firebox_height,                        _.h13_hauteurDuFoyer)
        .withFieldRenamed(_.pressure_loss_coefficient_from_door,   _.h66_coeffPerteDeChargePorte)
        .withFieldRenamed(_.total_air_intake_surface_area_on_door, _.h67_sectionCumuleeEntreeAirPorte)
        .withFieldRenamed(_.glass_width,                           _.h71_largeurVitre)
        .withFieldRenamed(_.glass_height,                          _.h72_hauteurVitre)
        .buildTransformer

    given transformer_inv_TraditionalFirebox_Standard: Transformer[firebox.calcpdm_v_0_2_32.TraditionalFirebox, Firebox.Traditional] = 
        Transformer.define[firebox.calcpdm_v_0_2_32.TraditionalFirebox, Firebox.Traditional]
        .enableDefaultValues
        .withFieldRenamed(_.pn_reduced,                       _.heat_output_reduced)
        .withFieldRenamed(_.h11_profondeurDuFoyer,            _.firebox_depth)
        .withFieldRenamed(_.h12_largeurDuFoyer,               _.firebox_width)
        .withFieldRenamed(_.h13_hauteurDuFoyer,               _.firebox_height)
        .withFieldRenamed(_.h66_coeffPerteDeChargePorte,      _.pressure_loss_coefficient_from_door)
        .withFieldRenamed(_.h67_sectionCumuleeEntreeAirPorte, _.total_air_intake_surface_area_on_door)
        .withFieldRenamed(_.h71_largeurVitre,                 _.glass_width)
        .withFieldRenamed(_.h72_hauteurVitre,                 _.glass_height)
        .buildTransformer
    
    given transformer_EcoLabeled_EcoLabeled: Transformer[Firebox.EcoLabeled, firebox.calcpdm_v_0_2_32.EcoLabeled] = e =>
        import e.* 
        e.version match
            case Left("Version 1") => firebox.calcpdm_v_0_2_32.EcoLabeled_V1(
                pn_reduced                                       = heat_output_reduced,
                h11_profondeurDuFoyer                            = firebox_depth,
                h12_largeurDuFoyer                               = firebox_width,
                h13_hauteurDuFoyer                               = firebox_height,
                h70_largeurPorteDansMaconnerie                   = door_opening_width,
                h71_largeurVitre                                 = glass_width,
                h72_hauteurVitre                                 = glass_height,
                h74_hauteur_de_cendrier_AF                       = ash_pit_height,
                h75_hauteurArriveeConduitAir_DessousSoleFoyer_W  = air_manifold_height,
                h76_epaisseurSole                                = firebox_floor_thickness,
                h77_epaisseurParoiInterneFoyer_D1                = firebox_inner_wall_thickness,
                epaisseurParoiExterneFoyer_D2                    = firebox_outer_wall_thickness,
                h78_largeurEspaceInterparoisDuFoyer_S            = air_column_thickness,
                h79_largeurRenfortMedianLateraux                 = width_between_two_air_columns_sides,
                h80_largeurRenfortMedianArriere                  = width_between_two_air_columns_rear,
                h81_debordDesRenfortsDansLesAngles               = reinforcement_bars_offset_in_corners,
                h82_hauteurDesInjecteurs_Z                       = injector_height,
                h83_hauteurEntreLaSoleEtLe1erInjecteur           = height_of_first_row_of_air_injectors,
            )
            case Right("Version 2") => firebox.calcpdm_v_0_2_32.EcoLabeled_V2(
                pn_reduced                                       = heat_output_reduced,
                arriveeAirGeometry                               = air_intake_shape.getOrElse(Circle(200.mm)),
                h11_profondeurDuFoyer                            = firebox_depth,
                h12_largeurDuFoyer                               = firebox_width,
                h13_hauteurDuFoyer                               = firebox_height,
                h70_largeurPorteDansMaconnerie                   = door_opening_width,
                h71_largeurVitre                                 = glass_width,
                h72_hauteurVitre                                 = glass_height,
                h74_hauteur_de_cendrier_AF                       = ash_pit_height,
                h75_hauteurArriveeConduitAir_DessousSoleFoyer_W  = air_manifold_height,
                h76_epaisseurSole                                = firebox_floor_thickness,
                h77_epaisseurParoiInterneFoyer_D1                = firebox_inner_wall_thickness,
                epaisseurParoiExterneFoyer_D2                    = firebox_outer_wall_thickness,
                h78_largeurEspaceInterparoisDuFoyer_S            = air_column_thickness,
                h79_largeurRenfortMedianLateraux                 = width_between_two_air_columns_sides,
                h80_largeurRenfortMedianArriere                  = width_between_two_air_columns_rear,
                h81_debordDesRenfortsDansLesAngles               = reinforcement_bars_offset_in_corners,
                h82_hauteurDesInjecteurs_Z                       = injector_height,
                h83_hauteurEntreLaSoleEtLe1erInjecteur           = height_of_first_row_of_air_injectors,
            )

    given transformer_inv_EcoLabeled: Transformer[firebox.calcpdm_v_0_2_32.EcoLabeled, Firebox.EcoLabeled] = e =>
        import e.* 
        e match
            case _: firebox.calcpdm_v_0_2_32.EcoLabeled_V1 => Firebox.EcoLabeled(
                heat_output_reduced                    =  pn_reduced,
                version                                =  Left("Version 1"),
                air_intake_shape                       =  None,
                firebox_depth                          =  h11_profondeurDuFoyer,
                firebox_width                          =  h12_largeurDuFoyer,
                firebox_height                         =  h13_hauteurDuFoyer,
                door_opening_width                     =  h70_largeurPorteDansMaconnerie,
                glass_width                            =  h71_largeurVitre,
                glass_height                           =  h72_hauteurVitre,
                ash_pit_height                         =  h74_hauteur_de_cendrier_AF,
                air_manifold_height                    =  h75_hauteurArriveeConduitAir_DessousSoleFoyer_W,
                firebox_floor_thickness                =  h76_epaisseurSole,
                firebox_inner_wall_thickness           =  h77_epaisseurParoiInterneFoyer_D1,
                firebox_outer_wall_thickness           =  epaisseurParoiExterneFoyer_D2,
                air_column_thickness                   =  h78_largeurEspaceInterparoisDuFoyer_S,
                width_between_two_air_columns_sides    =  h79_largeurRenfortMedianLateraux,
                width_between_two_air_columns_rear     =  h80_largeurRenfortMedianArriere,
                reinforcement_bars_offset_in_corners   =  h81_debordDesRenfortsDansLesAngles,
                injector_height                        =  h82_hauteurDesInjecteurs_Z,
                height_of_first_row_of_air_injectors   =  h83_hauteurEntreLaSoleEtLe1erInjecteur 
            )
            case _: firebox.calcpdm_v_0_2_32.EcoLabeled_V2 => Firebox.EcoLabeled(
                heat_output_reduced                    =  pn_reduced,
                version                                =  Right("Version 2"),
                air_intake_shape                       =  arriveeAirGeometryOpt,
                firebox_depth                          =  h11_profondeurDuFoyer,
                firebox_width                          =  h12_largeurDuFoyer,
                firebox_height                         =  h13_hauteurDuFoyer,
                door_opening_width                     =  h70_largeurPorteDansMaconnerie,
                glass_width                            =  h71_largeurVitre,
                glass_height                           =  h72_hauteurVitre,
                ash_pit_height                         =  h74_hauteur_de_cendrier_AF,
                air_manifold_height                    =  h75_hauteurArriveeConduitAir_DessousSoleFoyer_W,
                firebox_floor_thickness                =  h76_epaisseurSole,
                firebox_inner_wall_thickness           =  h77_epaisseurParoiInterneFoyer_D1,
                firebox_outer_wall_thickness           =  epaisseurParoiExterneFoyer_D2,
                air_column_thickness                   =  h78_largeurEspaceInterparoisDuFoyer_S,
                width_between_two_air_columns_sides    =  h79_largeurRenfortMedianLateraux,
                width_between_two_air_columns_rear     =  h80_largeurRenfortMedianArriere,
                reinforcement_bars_offset_in_corners   =  h81_debordDesRenfortsDansLesAngles,
                injector_height                        =  h82_hauteurDesInjecteurs_Z,
                height_of_first_row_of_air_injectors   =  h83_hauteurEntreLaSoleEtLe1erInjecteur
            )
            case _ => throw new Exception("not implemented")
    
    given Transformer[OutsideAirLocationInHeater, firebox.calcpdm_v_0_2_32.AFPMA_PRSE.OutsideAirLocationInHeater] = 
        x =>
            x match
                case OutsideAirLocationInHeater.FromBottom => firebox.calcpdm_v_0_2_32.AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom
            
    given transformer_AFPMA_PRSE: Transformer[Firebox.AFPMA_PRSE, firebox.calcpdm_v_0_2_32.AFPMA_PRSE] = 
        Transformer.define[Firebox.AFPMA_PRSE, firebox.calcpdm_v_0_2_32.AFPMA_PRSE]
        .enableDefaultValues
        .withFieldRenamed(_.heat_output_reduced,                     _.pn_reduced                              )
        .withFieldRenamed(_.outside_air_location_in_heater,          _.origineArriveeAir                       )
        .withFieldRenamed(_.outside_air_conduit_shape,               _.arriveeAirGeometry                      )
        .withFieldRenamed(_.firebox_depth,                           _.h11_profondeurDuFoyer                   )
        .withFieldRenamed(_.firebox_width,                           _.h12_largeurDuFoyer                      )
        .withFieldRenamed(_.firebox_height,                          _.h13_hauteurDuFoyer                      )
        .withFieldRenamed(_.height_of_first_row_of_air_injectors,    _.h83_hauteurEntreSoleEt1erInjecteur_X    )
        .withFieldRenamed(_.glass_width,                             _.h88_largeurVitre                        )
        .withFieldRenamed(_.glass_height,                            _.h89_hauteurVitre                        )
        .withFieldRenamed(_.ash_pit_height,                          _.h91_hauteurDuCendrier_AF                )
        .withFieldRenamed(_.floor_thickness,                         _.h92_epaisseurSole_S                     )
        .withFieldRenamed(_.combustion_air_manifold_height,          _.h93_hauteurEmbaseDessousSoleFoyer_V     )
        .withFieldRenamed(_.outside_air_inlet_lip,                   _.h94_hauteurDepassementArriveeAirFoyer_U )
        .withFieldRenamed(_.height_of_air_feed_to_columns,           _.h95_hauteurPassageVersColonneAir_W      )
        .withFieldRenamed(_.number_of_air_columns_feeding_firebox,   _.h96_nbColonnesAirFoyer                  )
        .withFieldRenamed(_.number_of_air_columns_feeding_door,      _.h97_nbColonnesAirPorte                  )
        .buildTransformer


trait From_CalculPdM_V_0_2_32_Module 
    extends FireboxModule_15544_Strict
    with FireboxModule_15544_MCE:

    type FB <: From_CalculPdM_V_0_2_32

    // type PipeDescr = en15544.pipedescr.type
    // val pipeDescr: PipeDescr = en15544.pipedescr
    // import pipeDescr.*

    extension (firebox: FB)
        def toFireboxPipe_15544: ValidatedNel[IncrementalValidation_Error, FireboxPipe_Module_15544.FullDescr] = 
            import FireboxPipe_Module_15544.*
            FireboxPipe_Module_15544.incremental.define(
                innerShape(rectangle(firebox.h12_largeurDuFoyer, firebox.h11_profondeurDuFoyer)),
                roughness(2.mm), // TOFIX: 3mm or 2mm ???
                addSectionVertical(
                    "ascension dans foyer", 
                    // TOFIX: found in CalculPdM-v0.2.30
                    // - we consider the whole vertical length ? but different injection height...
                    firebox.h13_hauteurDuFoyer))
            .toFullDescr().extractPipe

        def toFireboxPipe_13384: ValidatedNel[IncrementalValidation_Error, FireboxPipe_Module_13384.FullDescr] = 
            import FireboxPipe_Module_13384.*
            FireboxPipe_Module_13384.incremental.define(
                pipeLocation(PipeLocation.HeatedArea), // added for EN13384
                innerShape(rectangle(firebox.h12_largeurDuFoyer, firebox.h11_profondeurDuFoyer)),
                roughness(2.mm), // TOFIX: 3mm or 2mm ???
                layer(e = 1.cm, λ = 1.3.W_per_mK), // added for EN13384
                addSectionVertical(
                    "ascension dans foyer", 
                    // TOFIX: found in CalculPdM-v0.2.30
                    // - we consider the whole vertical length ? but different injection height...
                    firebox.h13_hauteurDuFoyer))
            .toFullDescr().extractPipe