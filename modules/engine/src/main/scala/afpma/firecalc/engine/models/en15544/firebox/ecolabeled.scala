/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.given

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.alg.en15544.FireboxFormulas
import afpma.firecalc.engine.biblio.kov.firebox_emissions.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.utils.ShowAsTable

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

import io.taig.babel.Locale
import afpma.firecalc.i18n.*
import magnolia1.Transl


sealed trait Ecolabeled extends CertifiedDesign:

    override val emissions_values = EcoPlus_Combustion_Firebox
    override val min_load         = MinLoad.HalfOfMaxLoad.makeWithoutValue
    
    val pn_reduced                                      : HeatOutputReduced
    val co2_dry_nominal                                 : σ_CO2 = 7.05.percent // TOFIX
    val co2_dry_lowest                                  : Option[σ_CO2] = None
    val arriveeAirGeometryOpt                           : Option[PipeShape]
    val h11_profondeurDuFoyer                           : QtyD[Meter]
    val h12_largeurDuFoyer                              : QtyD[Meter]
    val h13_hauteurDuFoyer                              : QtyD[Meter]
    val h70_largeurPorteDansMaconnerie                  : Length
    val h71_largeurVitre                                : Length
    val h72_hauteurVitre                                : Length
    val h74_hauteur_de_cendrier_AF                      : Length
    val h75_hauteurArriveeConduitAir_DessousSoleFoyer_W : Length
    val h76_epaisseurSole                               : Length
    val h77_epaisseurParoiInterneFoyer_D1               : Length
    val epaisseurParoiExterneFoyer_D2                   : Length
    val h78_largeurEspaceInterparoisDuFoyer_S           : Length
    private val air_column_thickness_door_wall_St = h78_largeurEspaceInterparoisDuFoyer_S // TOCHECK
    val h79_largeurRenfortMedianLateraux                : Length
    val h80_largeurRenfortMedianArriere                 : Length
    val r1                                              : Length
    val r2                                              : Length
    val r3                                              : Length
    val h82_hauteurDesInjecteurs_Z                      : Length
    private val injector_height_door_wall_Zt: Length = h82_hauteurDesInjecteurs_Z // TOCHECK
    val h83_hauteurEntreLaSoleEtLe1erInjecteur_X        : Length
    val version: Ecolabeled.Version

    val outputs: Ecolabeled.Outputs = Ecolabeled.Outputs(
        distance_between_air_injectors_Y  = c18_hauteurEntreLesInjecteurs_Y,
        injector_width_rear_wall_Lr       = c20_largeurDesInjecteursArrieres,
        injector_width_side_wall_Ls       = c19_largeurDesInjecteursLateraux,
        injector_width_door_wall_Lt       = c21_largeurDesInjecteursSousPorte,
        injector_height_door_wall_Zt      = injector_height_door_wall_Zt,
        air_column_thickness_door_wall_St = air_column_thickness_door_wall_St,
    )

    override val dimensions: Dimensions = Dimensions(
        base   = Dimensions.Base.Squared(
            width = h12_largeurDuFoyer,
            depth = h11_profondeurDuFoyer
        ),
        height = h13_hauteurDuFoyer
    )
    override val glass_area: GlassArea = h71_largeurVitre * h72_hauteurVitre

    lazy val c2_largeurFoyer               = h12_largeurDuFoyer
    lazy val c3_profondeurFoyer            = h11_profondeurDuFoyer
    lazy val c4_largeurPorteDansMaconnerie = h70_largeurPorteDansMaconnerie
    lazy val c7_hauteurDeCendrier          = h74_hauteur_de_cendrier_AF

    lazy val c10_epaisseurParoiInterneDuFoyer    = h77_epaisseurParoiInterneFoyer_D1
    lazy val c11_largeurEspaceInterParoisFoyer_S = h78_largeurEspaceInterparoisDuFoyer_S
    lazy val c12_largeurRenfortMedianLateraux    = h79_largeurRenfortMedianLateraux
    lazy val c13_largeurRenfortMedianArriere     = h80_largeurRenfortMedianArriere

    lazy val c15_hauterDesInjecteurs       = h82_hauteurDesInjecteurs_Z

    lazy val c18_hauteurEntreLesInjecteurs_Y =
        // TODO: check Y formula
        (-0.257142 * h83_hauteurEntreLaSoleEtLe1erInjecteur_X.toUnit[Centi * Meter].value + 10.585714).cm

    lazy val c19_largeurDesInjecteursLateraux = c3_profondeurFoyer - 9.cm
    lazy val c20_largeurDesInjecteursArrieres = c2_largeurFoyer - 9.cm

    lazy val c21_largeurDesInjecteursSousPorte = c4_largeurPorteDansMaconnerie - 6.cm

    lazy val c24_largeurDesColonnesAirLaterales =
        c3_profondeurFoyer - r2 - c12_largeurRenfortMedianLateraux - r3
    lazy val c25_largeurDesColonnesAirArrieres  =
        c2_largeurFoyer - 2.0 * r1 - c13_largeurRenfortMedianArriere

end Ecolabeled

sealed trait Ecolabeled_V1 extends Ecolabeled:
    override val reference         = LocalizedString.from(I18N.firebox_names.ecolabeled_v1)
    override val type_of_appliance = TypeOfAppliance.WoodLogs
    final val version              = Ecolabeled.Version.V1

sealed trait Ecolabeled_V2 extends Ecolabeled:
    override val reference         = LocalizedString.from(I18N.firebox_names.ecolabeled_v2)
    override val type_of_appliance = TypeOfAppliance.WoodLogs
    final val version              = Ecolabeled.Version.V2

object Ecolabeled:

    enum Version:
        case V1, V2

    @Transl(I(_.firebox.ecolabeled.computed_values))
    case class Outputs(
        @Transl(I(_.firebox.ecolabeled.distance_between_air_injectors_Y))
        distance_between_air_injectors_Y  : Length,
        @Transl(I(_.firebox.ecolabeled.injector_width_rear_wall_Lr))
        injector_width_rear_wall_Lr       : Length,
        @Transl(I(_.firebox.ecolabeled.injector_width_side_wall_Ls))
        injector_width_side_wall_Ls       : Length,
        @Transl(I(_.firebox.ecolabeled.injector_width_door_wall_Lt))
        injector_width_door_wall_Lt       : Length,
        @Transl(I(_.firebox.ecolabeled.injector_height_door_wall_Zt))
        injector_height_door_wall_Zt      : Length,
        @Transl(I(_.firebox.ecolabeled.air_column_thickness_door_wall_St))
        air_column_thickness_door_wall_St : Length,
    )

    given showAsTable: Locale => ShowAsTable[Ecolabeled] =
        ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
            import x.*
            val I               = I18N.firebox.ecolabeled
            // val IV1 = I18N.firebox.ecolobaled_v1
            val version         = x.version match
                case Version.V1 => I.version_1_with_airbox
                case Version.V2 => I.version_2_without_airbox
            val version_details = x.version match
                case Version.V1 => Nil
                case Version.V2 => (
                    I.version_2_air_intake_shape :: "" :: x.arriveeAirGeometryOpt.map(_.showP).getOrElse("-") :: Nil
                )
            val list            =
                (I18N.firebox.typ                              :: ""   :: I18N.firebox_names.ecolabeled_v1                            :: Nil) ::
                    (I.version                                 :: ""   :: version                                                     :: Nil) ::
                    version_details                            ::  
                    (I18N.firebox.firebox_width                :: "A"  :: h12_largeurDuFoyer.to_cm.showP                              :: Nil) ::
                    (I18N.firebox.firebox_depth                :: "B"  :: h11_profondeurDuFoyer.to_cm.showP                           :: Nil) ::
                    (I18N.firebox.firebox_height               :: "H"  :: h13_hauteurDuFoyer.to_cm.showP                              :: Nil) ::
                    (I.door_opening_width                      :: ""   :: h70_largeurPorteDansMaconnerie.to_cm.showP                  :: Nil) ::
                    (I.glass_width                             :: ""   :: h71_largeurVitre.to_cm.showP                                :: Nil) ::
                    (I.glass_height                            :: ""   :: h72_hauteurVitre.to_cm.showP                                :: Nil) ::
                    (I.ash_pit_height_AF                       :: "AF" :: h74_hauteur_de_cendrier_AF.to_cm.showP                      :: Nil) ::
                    (I.air_manifold_height_W                   :: "W"  :: h75_hauteurArriveeConduitAir_DessousSoleFoyer_W.to_cm.showP :: Nil) ::
                    (I.firebox_floor_thickness                 :: ""   :: h76_epaisseurSole.to_cm.showP                               :: Nil) ::
                    (I.inner_wall_thickness_D1                 :: "D1" :: h77_epaisseurParoiInterneFoyer_D1.to_cm.showP               :: Nil) ::
                    (I.air_column_thickness_S                  :: "S"  :: h78_largeurEspaceInterparoisDuFoyer_S.to_cm.showP           :: Nil) ::
                    (I.width_between_two_air_columns_sides_E   :: "E"  :: h79_largeurRenfortMedianLateraux.to_cm.showP                :: Nil) ::
                    (I.width_between_two_air_columns_rear_E    :: "E"  :: h80_largeurRenfortMedianArriere.to_cm.showP                 :: Nil) ::
                    (I.reinforcement_bars_offset_in_corners_R1 :: "R1" :: r1.to_cm.showP                                              :: Nil) ::
                    (I.reinforcement_bars_offset_in_corners_R2 :: "R2" :: r2.to_cm.showP                                              :: Nil) ::
                    (I.reinforcement_bars_offset_in_corners_R3 :: "R3" :: r3.to_cm.showP                                              :: Nil) ::
                    (I.injector_height_Z                       :: "Z"  :: h82_hauteurDesInjecteurs_Z.to_mm.showP                      :: Nil) ::
                    (I.distance_between_air_injectors_Y        :: "Y"  :: x.outputs.distance_between_air_injectors_Y.to_cm.showP      :: Nil) ::
                    (I.injector_width_rear_wall_Lr             :: "Lr" :: x.outputs.injector_width_rear_wall_Lr.to_cm.showP           :: Nil) ::
                    (I.injector_width_side_wall_Ls             :: "Ls" :: x.outputs.injector_width_side_wall_Ls.to_cm.showP           :: Nil) ::
                    (I.injector_width_door_wall_Lt             :: "Lt" :: x.outputs.injector_width_door_wall_Lt.to_cm.showP           :: Nil) ::
                    (I.injector_height_door_wall_Zt            :: "Zt" :: x.outputs.injector_height_door_wall_Zt.to_mm.showP          :: Nil) ::
                    (I.air_column_thickness_door_wall_St       :: "St" :: x.outputs.air_column_thickness_door_wall_St.to_cm.showP     :: Nil) ::
                    (I.height_of_first_row_of_air_injectors_X  :: "X"  :: h83_hauteurEntreLaSoleEtLe1erInjecteur_X.to_cm.showP        :: Nil) ::
                    Nil
            list.filter(_.nonEmpty)


object Ecolabeled_V1:
    def apply(
        pn_reduced                                     : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        h11_profondeurDuFoyer                          : QtyD[Meter],
        h12_largeurDuFoyer                             : QtyD[Meter],
        h13_hauteurDuFoyer                             : QtyD[Meter],
        h70_largeurPorteDansMaconnerie                 : Length,
        h71_largeurVitre                               : Length,
        h72_hauteurVitre                               : Length,
        h74_hauteur_de_cendrier_AF                     : Length,
        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W: Length,
        h76_epaisseurSole                              : Length,
        h77_epaisseurParoiInterneFoyer_D1              : Length,
        epaisseurParoiExterneFoyer_D2                  : Length,
        h78_largeurEspaceInterparoisDuFoyer_S          : Length,
        h79_largeurRenfortMedianLateraux               : Length,
        h80_largeurRenfortMedianArriere                : Length,
        r1                                             : Length,
        r2                                             : Length,
        r3                                             : Length,
        h82_hauteurDesInjecteurs_Z                     : Length,
        h83_hauteurEntreLaSoleEtLe1erInjecteur_X       : Length
    ): Ecolabeled_V1 =
        new Ecolabeled_V1_or_V2_Impl(
            pn_reduced,
            None,
            h11_profondeurDuFoyer,
            h12_largeurDuFoyer,
            h13_hauteurDuFoyer,
            h70_largeurPorteDansMaconnerie,
            h71_largeurVitre,
            h72_hauteurVitre,
            h74_hauteur_de_cendrier_AF,
            h75_hauteurArriveeConduitAir_DessousSoleFoyer_W,
            h76_epaisseurSole,
            h77_epaisseurParoiInterneFoyer_D1,
            epaisseurParoiExterneFoyer_D2,
            h78_largeurEspaceInterparoisDuFoyer_S,
            h79_largeurRenfortMedianLateraux,
            h80_largeurRenfortMedianArriere,
            r1,
            r2,
            r3,
            h82_hauteurDesInjecteurs_Z,
            h83_hauteurEntreLaSoleEtLe1erInjecteur_X
        ) with Ecolabeled_V1 {
            override val firebox_type: Locale ?=> String = I18N.firebox_names.ecolabeled_v1
        }

object Ecolabeled_V2:
    def apply(
        pn_reduced                                     : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        arriveeAirGeometry                             : PipeShape,
        h11_profondeurDuFoyer                          : QtyD[Meter],
        h12_largeurDuFoyer                             : QtyD[Meter],
        h13_hauteurDuFoyer                             : QtyD[Meter],
        h70_largeurPorteDansMaconnerie                 : Length,
        h71_largeurVitre                               : Length,
        h72_hauteurVitre                               : Length,
        h74_hauteur_de_cendrier_AF                     : Length,
        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W: Length,
        h76_epaisseurSole                              : Length,
        h77_epaisseurParoiInterneFoyer_D1              : Length,
        epaisseurParoiExterneFoyer_D2                  : Length,
        h78_largeurEspaceInterparoisDuFoyer_S          : Length,
        h79_largeurRenfortMedianLateraux               : Length,
        h80_largeurRenfortMedianArriere                : Length,
        r1                                             : Length,
        r2                                             : Length,
        r3                                             : Length,
        h82_hauteurDesInjecteurs_Z                     : Length,
        h83_hauteurEntreLaSoleEtLe1erInjecteur_X       : Length
    ): Ecolabeled_V2 =
        new Ecolabeled_V1_or_V2_Impl(
            pn_reduced,
            Some(arriveeAirGeometry),
            h11_profondeurDuFoyer,
            h12_largeurDuFoyer,
            h13_hauteurDuFoyer,
            h70_largeurPorteDansMaconnerie,
            h71_largeurVitre,
            h72_hauteurVitre,
            h74_hauteur_de_cendrier_AF,
            h75_hauteurArriveeConduitAir_DessousSoleFoyer_W,
            h76_epaisseurSole,
            h77_epaisseurParoiInterneFoyer_D1,
            epaisseurParoiExterneFoyer_D2,
            h78_largeurEspaceInterparoisDuFoyer_S,
            h79_largeurRenfortMedianLateraux,
            h80_largeurRenfortMedianArriere,
            r1,
            r2,
            r3,
            h82_hauteurDesInjecteurs_Z,
            h83_hauteurEntreLaSoleEtLe1erInjecteur_X
        ) with Ecolabeled_V2 {
            override val firebox_type: Locale ?=> String = I18N.firebox_names.ecolabeled_v2
        }

/** 'Ecolabeled' Firebox according to EN15544 */
private sealed abstract class Ecolabeled_V1_or_V2_Impl(
    val pn_reduced                                     : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
    val arriveeAirGeometryOpt                          : Option[PipeShape], // defined only for V2
    val h11_profondeurDuFoyer                          : QtyD[Meter],
    val h12_largeurDuFoyer                             : QtyD[Meter],
    val h13_hauteurDuFoyer                             : QtyD[Meter],
    val h70_largeurPorteDansMaconnerie                 : Length,
    val h71_largeurVitre                               : Length,
    val h72_hauteurVitre                               : Length,
    val h74_hauteur_de_cendrier_AF                     : Length,
    val h75_hauteurArriveeConduitAir_DessousSoleFoyer_W: Length,
    val h76_epaisseurSole                              : Length,
    val h77_epaisseurParoiInterneFoyer_D1              : Length,
    val epaisseurParoiExterneFoyer_D2                  : Length,
    val h78_largeurEspaceInterparoisDuFoyer_S          : Length,
    val h79_largeurRenfortMedianLateraux               : Length,
    val h80_largeurRenfortMedianArriere                : Length,
    val r1                                             : Length,
    val r2                                             : Length,
    val r3                                             : Length,
    val h82_hauteurDesInjecteurs_Z                     : Length,
    val h83_hauteurEntreLaSoleEtLe1erInjecteur_X       : Length
) extends Ecolabeled {
    type Self = Ecolabeled
    def height_of_lowest_opening: Length = h74_hauteur_de_cendrier_AF

    override def formulas: FireboxFormulas[Self] =
        import afpma.firecalc.engine.impl.en15544.common.fireboxFormulas_Strict
        fireboxFormulas_Strict

    override def constraints: FireboxConstraints[Self] =
        import afpma.firecalc.engine.impl.en15544.instances.ecoLabeledConstraints
        ecoLabeledConstraints
}

