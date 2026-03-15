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

sealed trait Ecolabeled extends CertifiedDesign:
    override val emissions_values   = EcoPlus_Combustion_Firebox
    override val min_load           = MinLoad.HalfOfMaxLoad.makeWithoutValue
    val pn_reduced                                     : HeatOutputReduced
    val co2_dry_nominal                                : σ_CO2 = 7.05.percent
    val co2_dry_lowest                                 : Option[σ_CO2] = None
    val arriveeAirGeometryOpt                          : Option[PipeShape]
    val h11_profondeurDuFoyer                          : QtyD[Meter]
    val h12_largeurDuFoyer                             : QtyD[Meter]
    val h13_hauteurDuFoyer                             : QtyD[Meter]
    val h70_largeurPorteDansMaconnerie                 : Length
    val h71_largeurVitre                               : Length
    val h72_hauteurVitre                               : Length
    val h74_hauteur_de_cendrier_AF                     : Length
    val h75_hauteurArriveeConduitAir_DessousSoleFoyer_W: Length
    val h76_epaisseurSole                              : Length
    val h77_epaisseurParoiInterneFoyer_D1              : Length
    val epaisseurParoiExterneFoyer_D2                  : Length
    val h78_largeurEspaceInterparoisDuFoyer_S          : Length
    val h79_largeurRenfortMedianLateraux               : Length
    val h80_largeurRenfortMedianArriere                : Length
    val h81_debordDesRenfortsDansLesAngles             : Length
    val h82_hauteurDesInjecteurs_Z                     : Length
    val h83_hauteurEntreLaSoleEtLe1erInjecteur_X       : Length
    val version: Ecolabeled.Version

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
    lazy val c14_debordDesRenfortsDansLesAngles  = h81_debordDesRenfortsDansLesAngles

    lazy val c15_hauterDesInjecteurs       = h82_hauteurDesInjecteurs_Z

    // TODO: Term defined as "Y", should be surfaced / shown in the UI for user
    lazy val c18_hauteurEntreLesInjecteurs =
        // TOFIX: c7_hauteurDeCendrier seems wrong, h83_hauteurEntreLaSoleEtLe1erInjecteur_X is more likely
        (-0.257142 * c7_hauteurDeCendrier.toUnit[Centi * Meter].value + 10.585714).cm

    lazy val c19_largeurDesInjecteursLateraux = c3_profondeurFoyer - 9.cm
    lazy val c20_largeurDesInjecteursArrieres = c2_largeurFoyer - 9.cm

    lazy val c21_largeurDesInjecteursSousPorte = c4_largeurPorteDansMaconnerie - 6.cm

    lazy val c24_largeurDesColonnesAirLaterales =
        c3_profondeurFoyer - c14_debordDesRenfortsDansLesAngles - c12_largeurRenfortMedianLateraux
    lazy val c25_largeurDesColonnesAirArrieres  =
        c2_largeurFoyer - 2.0 * c14_debordDesRenfortsDansLesAngles - c13_largeurRenfortMedianArriere

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
                (I18N.firebox.typ                             :: ""           :: I18N.firebox_names.ecolabeled_v1                            :: Nil) ::
                    (I.version                                :: ""           :: version                                                     :: Nil) ::
                    version_details                           ::  
                    (I18N.firebox.firebox_width               :: "h11 / A"    :: h12_largeurDuFoyer.to_cm.showP                              :: Nil) ::
                    (I18N.firebox.firebox_depth               :: "h11 / B"    :: h11_profondeurDuFoyer.to_cm.showP                           :: Nil) ::
                    (I18N.firebox.firebox_height              :: "h11 / H"    :: h13_hauteurDuFoyer.to_cm.showP                              :: Nil) ::
                    (I.door_opening_width                     :: "h70"        :: h70_largeurPorteDansMaconnerie.to_cm.showP                  :: Nil) ::
                    (I.glass_width                            :: "h71"        :: h71_largeurVitre.to_cm.showP                                :: Nil) ::
                    (I.glass_height                           :: "h72"        :: h72_hauteurVitre.to_cm.showP                                :: Nil) ::
                    (I.ash_pit_height_AF                      :: "h74 / AF"   :: h74_hauteur_de_cendrier_AF.to_cm.showP                      :: Nil) ::
                    (I.air_manifold_height_W                  :: "h75 / W"    :: h75_hauteurArriveeConduitAir_DessousSoleFoyer_W.to_cm.showP :: Nil) ::
                    (I.firebox_floor_thickness                :: "h76"        :: h76_epaisseurSole.to_cm.showP                               :: Nil) ::
                    (I.inner_wall_thickness_D1                :: "h77 / D1"   :: h77_epaisseurParoiInterneFoyer_D1.to_cm.showP               :: Nil) ::
                    (I.air_column_thickness_S                 :: "h78 / S"    :: h78_largeurEspaceInterparoisDuFoyer_S.to_cm.showP           :: Nil) ::
                    (I.width_between_two_air_columns_sides_E  :: "h79 / E"    :: h79_largeurRenfortMedianLateraux.to_cm.showP                :: Nil) ::
                    (I.width_between_two_air_columns_rear_E   :: "h80 / E"    :: h80_largeurRenfortMedianArriere.to_cm.showP                 :: Nil) ::
                    (I.reinforcement_bars_offset_in_corners   :: "h81"        :: h81_debordDesRenfortsDansLesAngles.to_cm.showP              :: Nil) ::
                    (I.injector_height_Z                      :: "h82 / Z"    :: h82_hauteurDesInjecteurs_Z.to_mm.showP                      :: Nil) ::
                    (I.height_of_first_row_of_air_injectors_X :: "h83 / X"    :: h83_hauteurEntreLaSoleEtLe1erInjecteur_X.to_cm.showP        :: Nil) ::
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
        h81_debordDesRenfortsDansLesAngles             : Length,
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
            h81_debordDesRenfortsDansLesAngles,
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
        h81_debordDesRenfortsDansLesAngles             : Length,
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
            h81_debordDesRenfortsDansLesAngles,
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
    val h81_debordDesRenfortsDansLesAngles             : Length,
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

