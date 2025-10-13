/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32

import scala.collection.mutable.ListBuffer

import cats.data.NonEmptyList
import cats.data.ValidatedNel
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.biblio.kov.firebox_emissions.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en15544.firebox.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.ShowAsTable

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import io.taig.babel.Locale

sealed trait EcoLabeled extends From_CalculPdM_V_0_2_32:
    val emissions_values: EmissionsAndEfficiencyValues = EcoPlus_Combustion_Firebox
    val pn_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal
    val arriveeAirGeometryOpt: Option[PipeShape]
    // val h11_profondeurDuFoyer: QtyD[Meter]
    // val h12_largeurDuFoyer: QtyD[Meter]
    // val h13_hauteurDuFoyer: QtyD[Meter]
    val h70_largeurPorteDansMaconnerie: Length
    val h71_largeurVitre: Length
    val h72_hauteurVitre: Length
    val h74_hauteur_de_cendrier_AF: Length
    val h75_hauteurArriveeConduitAir_DessousSoleFoyer_W: Length
    val h76_epaisseurSole: Length
    val h77_epaisseurParoiInterneFoyer_D1: Length
    val epaisseurParoiExterneFoyer_D2: Length
    val h78_largeurEspaceInterparoisDuFoyer_S: Length
    val h79_largeurRenfortMedianLateraux: Length
    val h80_largeurRenfortMedianArriere: Length
    val h81_debordDesRenfortsDansLesAngles: Length
    val h82_hauteurDesInjecteurs_Z: Length
    val version: EcoLabeled.Version

    val area_calc_method                          = AreaCalcMethod.AutoIfCubic
    val largeurVitre                              = h71_largeurVitre
    val hauteurVitre                              = h72_hauteurVitre

    lazy val c2_largeurFoyer                      = h12_largeurDuFoyer
    lazy val c3_profondeurFoyer                   = h11_profondeurDuFoyer
    lazy val c4_largeurPorteDansMaconnerie        = h70_largeurPorteDansMaconnerie
    lazy val c7_hauteurDeCendrier                 = h74_hauteur_de_cendrier_AF

    lazy val c10_epaisseurParoiInterneDuFoyer     = h77_epaisseurParoiInterneFoyer_D1
    lazy val c11_largeurEspaceInterParoisFoyer_S  = h78_largeurEspaceInterparoisDuFoyer_S
    lazy val c12_largeurRenfortMedianLateraux     = h79_largeurRenfortMedianLateraux
    lazy val c13_largeurRenfortMedianArriere      = h80_largeurRenfortMedianArriere
    lazy val c14_debordDesRenfortsDansLesAngles   = h81_debordDesRenfortsDansLesAngles

    lazy val c15_hauterDesInjecteurs              = h82_hauteurDesInjecteurs_Z
    lazy val c18_hauteurEntreLesInjecteurs        = (-0.257142 * c7_hauteurDeCendrier.toUnit[Centi * Meter].value + 10.585714).cm
    
    lazy val c19_largeurDesInjecteursLateraux     = c3_profondeurFoyer - 9.cm
    lazy val c20_largeurDesInjecteursArrieres     = c2_largeurFoyer - 9.cm
    
    lazy val c21_largeurDesInjecteursSousPorte    = c4_largeurPorteDansMaconnerie - 6.cm

    lazy val c24_largeurDesColonnesAirLaterales   = 
        c3_profondeurFoyer    - c14_debordDesRenfortsDansLesAngles - c12_largeurRenfortMedianLateraux
    lazy val c25_largeurDesColonnesAirArrieres    = 
        c2_largeurFoyer - 2.0 * c14_debordDesRenfortsDansLesAngles - c13_largeurRenfortMedianArriere

    // vitesse injection
    def air_injector_surface_area: Area = 
        val largeurFenteAirFoyer = 2 * c19_largeurDesInjecteursLateraux + c20_largeurDesInjecteursArrieres
        val nbRangsFenteInjectionAirFoyer = 4.0
        val surfaceInjectionAirFoyer = nbRangsFenteInjectionAirFoyer * (largeurFenteAirFoyer * h82_hauteurDesInjecteurs_Z)
        val surfaceInjectionAirPorte = 1.0 * (c21_largeurDesInjecteursSousPorte * h82_hauteurDesInjecteurs_Z)
        surfaceInjectionAirFoyer + surfaceInjectionAirPorte

    def validate(m_B: m_B): Locale ?=> ValidatedNel[FireboxError, Unit] = 
        val mB: Mass = m_B
        val buf = new ListBuffer[FireboxError]()

        if (mB < 6.kg)
            // buf.append(s"mB < 6 kg : la charge maximale de bois doit être supérieure à 6 kg")
            buf.append(TermValueShouldBeGreaterOrEqThan(I18N.en15544.terms.m_B.name, mB, 6.kg))
        
        if (mB > 40.kg)
            // buf.append(s"mB > 40 kg : la charge maximale de bois doit être inférieure à 40 kg")
            buf.append(TermValueShouldBeLessOrEqThan(I18N.en15544.terms.m_B.name, mB, 40.kg))

        val (d1, d2) = (h77_epaisseurParoiInterneFoyer_D1, epaisseurParoiExterneFoyer_D2)
        val (d1_min, d2_min) = 
            if (6.kg <= mB && mB < 12.9.kg)
                (4.cm, 5.cm)
            else
                (5.cm, 5.cm)
            
        if (d1 < d1_min)
            // buf.append(s"D1 < ${d1_min.showP} : l'épaisseur de la paroi interne ${d1.showP} doit être supérieure à ${d1_min.showP}")
            buf.append(TermValueShouldBeGreaterOrEqThan(I18N.firebox.ecolabeled.inner_wall_thickness_D1, d1.to_cm, d1_min.to_cm))
        if (d2 < d2_min)
            buf.append(TermValueShouldBeGreaterOrEqThan(I18N.firebox.ecolabeled.outer_wall_thickness_D2, d2.to_cm, d2_min.to_cm))
            // buf.append(s"D2 < ${d2_min.showP} : l'épaisseur de la paroi externe ${d2.showP} doit être supérieure à ${d2_min.showP}")

        val w = h75_hauteurArriveeConduitAir_DessousSoleFoyer_W
        val w_min = 5.cm
        
        if (w < w_min)
            buf.append(TermValueShouldBeGreaterOrEqThan(I18N.firebox.ecolabeled.air_manifold_height_W, w.to_cm, w_min.to_cm))
            // buf.append(s"W < ${w_min.showP} : la hauteur minimale de passage de l'air ${w.showP} sous la sole doit être supérieure à ${w_min.showP}")

        val A = h12_largeurDuFoyer
        val B = h11_profondeurDuFoyer
        val ratio = (A / B).value
        if ( !( (0.5 <= ratio) && (ratio <= 2.0)) )
            buf.append(TermValueShouldBeBetweenInclusive(I18N.firebox.traditional.width_to_depth_ratio, ratio, minValue = 0.5, maxValue = 2.0))
            // buf.append(s"le ratio largeur / profondeur doit être compris entre 0.5 et 2 (valeur calculé = $ratioShow)")

        val AF = h74_hauteur_de_cendrier_AF
        val AF_min = 5.cm
        val AF_max = 12.cm
        if (AF < AF_min)
            buf.append(TermValueShouldBeGreaterOrEqThan(I18N.firebox.ecolabeled.ash_pit_height_AF, AF.to_cm, AF_min.to_cm))
            // buf.append(s"AF < ${AF_min.showP} : la profondeur du cendrier ${AF.showP} doit être supérieure à ${AF_min.showP}")
        if (AF > AF_max)
            buf.append(TermValueShouldBeLessOrEqThan(I18N.firebox.ecolabeled.ash_pit_height_AF, AF.to_cm, AF_max.to_cm))
            // buf.append(s"AF > ${AF_max.showP} : la profondeur du cendrier ${AF.showP} doit être inférieure à ${AF_max.showP}")

        val H_min = (25.cm.value + mB.toUnit[Kilogram].value).cm
        val H = h13_hauteurDuFoyer
        if (H < H_min)
            buf.append(TermValueShouldBeGreaterOrEqThan(I18N.firebox.ecolabeled.height, H.to_cm, H_min.to_cm))
            // buf.append(s"H < ${H_min.showP} : La hauteur minimum du foyer ${H.showP} doit être au minimum de 25 cm + la charge de bois maximale en kg.")

        val E_max = 6.cm
        if (h79_largeurRenfortMedianLateraux > E_max)
            buf.append(TermValueShouldBeLessOrEqThan(I18N.firebox.ecolabeled.width_between_two_air_columns_sides_E, h79_largeurRenfortMedianLateraux.to_cm, E_max.to_cm))
            // buf.append(s"E > ${E_max.showP} : la largeur de la barre de renfort latérale E doit être inférieure à ${E_max.showP}")
        if (h80_largeurRenfortMedianArriere > E_max)
            buf.append(TermValueShouldBeLessOrEqThan(I18N.firebox.ecolabeled.width_between_two_air_columns_rear_E, h80_largeurRenfortMedianArriere.to_cm, E_max.to_cm))
            // buf.append(s"E > ${E_max.showP} : la largeur de la barre de renfort arrière E doit être inférieure à ${E_max.showP}")

        val DEBORD_MAX = 4.5.cm // pour ne pas que les débords fassent que les colonnes d'air soient moins larges que les injecteurs d'air
        if (c14_debordDesRenfortsDansLesAngles > DEBORD_MAX)
            buf.append(TermValueShouldBeLessOrEqThan(I18N.firebox.ecolabeled.reinforcement_bars_offset_in_corners, c14_debordDesRenfortsDansLesAngles.to_cm, DEBORD_MAX.to_cm))
            // buf.append(s"Débord > ${DEBORD_MAX.showP} : le débord dans les angles de doit pas dépasser ${DEBORD_MAX.showP}")

        // La somme des surfaces des fentes d'air de combustion bouchées par les barres de renfort ne doit pas
        // excéder 20% de la surface totale des fentes d'air de combustion..
        val largeurFenteAirFoyer = 2 * c19_largeurDesInjecteursLateraux + c20_largeurDesInjecteursArrieres
        val largeurObstructionAirFoyer = 2 * h79_largeurRenfortMedianLateraux + h80_largeurRenfortMedianArriere 
        val ratioSurfaceFenteAir = (largeurObstructionAirFoyer / largeurFenteAirFoyer)
        val ratioSurfaceFenteAir_max = 0.20
        if (ratioSurfaceFenteAir > ratioSurfaceFenteAir_max)
            buf.append(TermValueCustom(
                I18N.firebox.ecolabeled.injector_surface_area, 
                ratioSurfaceFenteAir,
                I18N.firebox.ecolabeled.injector_surface_area_obstructed_max_perc(ratioSurfaceFenteAir.toPercent.showP))
            )
            // buf.append(s"La somme des surfaces des fentes d'air de combustion bouchées par les barres de renfort ne doit pas excéder 20% de la surface totale des fentes d'air de combustion. (ratio calculé = ${ratioSurfaceFenteAir.toPercent.showP})")

        val errors = buf.toList
        if (errors.size > 0) NonEmptyList.fromListUnsafe(errors).invalid else ().validNel
        
end EcoLabeled

sealed trait EcoLabeled_V1 extends EcoLabeled:
    override val reference = LocalizedString.from(I18N.firebox_names.ecolabeled_v1)
    override val type_of_appliance  = TypeOfAppliance.WoodLogs
    final val version = EcoLabeled.Version.V1

sealed trait EcoLabeled_V2 extends EcoLabeled:
    override val reference = LocalizedString.from(I18N.firebox_names.ecolabeled_v2)
    override val type_of_appliance  = TypeOfAppliance.WoodLogs
    final val version = EcoLabeled.Version.V2

object EcoLabeled:
    
    enum Version:
        case V1, V2

    given showAsTable: Locale => ShowAsTable[EcoLabeled] = 
        ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
            import x.*
            val I = I18N.firebox.ecolabeled
            // val IV1 = I18N.firebox.ecolobaled_v1
            val version = x.version match
                case Version.V1 => I.version_1_with_airbox
                case Version.V2 => I.version_2_without_airbox
            val version_details = x.version match
                case Version.V1 => Nil
                case Version.V2 => (I.version_2_air_intake_shape :: "" :: x.arriveeAirGeometryOpt.map(_.showP).getOrElse("-") :: Nil)                
            val list = 
                (I18N.firebox.typ           :: ""         :: I18N.firebox_names.ecolabeled_v1                   :: Nil) ::
                (I.version                   :: ""         :: version                                                       :: Nil) ::
                version_details                                                                                                                ::
                (I.width                                :: "h11 / A"  :: h12_largeurDuFoyer                               .to_cm.showP :: Nil) ::
                (I.depth                                :: "h11 / B"  :: h11_profondeurDuFoyer                            .to_cm.showP :: Nil) ::
                (I.height                               :: "h11 / H"  :: h13_hauteurDuFoyer                               .to_cm.showP :: Nil) ::
                (I.door_opening_width                   :: "h70"      :: h70_largeurPorteDansMaconnerie                   .to_cm.showP :: Nil) ::
                (I.glass_width                          :: "h71"      :: h71_largeurVitre                                 .to_cm.showP :: Nil) ::
                (I.glass_height                         :: "h72"      :: h72_hauteurVitre                                 .to_cm.showP :: Nil) ::
                (I.ash_pit_height_AF                          :: "h74 / AF" :: h74_hauteur_de_cendrier_AF                       .to_cm.showP :: Nil) ::
                (I.air_manifold_height_W           :: "h75 / W"  :: h75_hauteurArriveeConduitAir_DessousSoleFoyer_W  .to_cm.showP :: Nil) ::
                (I.firebox_floor_thickness                       :: "h76"      :: h76_epaisseurSole                                .to_cm.showP :: Nil) ::
                (I.inner_wall_thickness_D1              :: "h77 / D1" :: h77_epaisseurParoiInterneFoyer_D1                .to_cm.showP :: Nil) ::
                (I.air_column_thickness_S       :: "h78 / S"  :: h78_largeurEspaceInterparoisDuFoyer_S            .to_cm.showP :: Nil) ::
                (I.width_between_two_air_columns_sides_E      :: "h79 / E"  :: h79_largeurRenfortMedianLateraux                 .to_cm.showP :: Nil) ::
                (I.width_between_two_air_columns_rear_E      :: "h80 / E"  :: h80_largeurRenfortMedianArriere                  .to_cm.showP :: Nil) ::
                (I.reinforcement_bars_offset_in_corners :: "h81"      :: h81_debordDesRenfortsDansLesAngles               .to_cm.showP :: Nil) ::
                (I.injector_height_Z                    :: "h82 / Z"  :: h82_hauteurDesInjecteurs_Z                       .to_mm.showP :: Nil) ::
                Nil
            list.filter(_.nonEmpty)

import EcoLabeled.*

object EcoLabeled_V1:
    def apply(
        pn_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        h11_profondeurDuFoyer: QtyD[Meter],
        h12_largeurDuFoyer: QtyD[Meter],
        h13_hauteurDuFoyer: QtyD[Meter],
        h70_largeurPorteDansMaconnerie: Length,
        h71_largeurVitre: Length,
        h72_hauteurVitre: Length,
        h74_hauteur_de_cendrier_AF: Length,
        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W: Length,
        h76_epaisseurSole: Length,
        h77_epaisseurParoiInterneFoyer_D1: Length,
        epaisseurParoiExterneFoyer_D2: Length,
        h78_largeurEspaceInterparoisDuFoyer_S: Length,
        h79_largeurRenfortMedianLateraux: Length,
        h80_largeurRenfortMedianArriere: Length,
        h81_debordDesRenfortsDansLesAngles: Length,
        h82_hauteurDesInjecteurs_Z: Length,
    ): EcoLabeled_V1 = new EcoLabeled_V1_or_V2_Impl(
        pn_reduced,
        None,
        h11_profondeurDuFoyer,
        h12_largeurDuFoyer,
        h13_hauteurDuFoyer,
        h70_largeurPorteDansMaconnerie,
        h71_largeurVitre: Length,
        h72_hauteurVitre: Length,
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
    ) with EcoLabeled_V1

object EcoLabeled_V2:
    def apply(
        pn_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        arriveeAirGeometry: PipeShape,
        h11_profondeurDuFoyer: QtyD[Meter],
        h12_largeurDuFoyer: QtyD[Meter],
        h13_hauteurDuFoyer: QtyD[Meter],
        h70_largeurPorteDansMaconnerie: Length,
        h71_largeurVitre: Length,
        h72_hauteurVitre: Length,
        h74_hauteur_de_cendrier_AF: Length,
        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W: Length,
        h76_epaisseurSole: Length,
        h77_epaisseurParoiInterneFoyer_D1: Length,
        epaisseurParoiExterneFoyer_D2: Length,
        h78_largeurEspaceInterparoisDuFoyer_S: Length,
        h79_largeurRenfortMedianLateraux: Length,
        h80_largeurRenfortMedianArriere: Length,
        h81_debordDesRenfortsDansLesAngles: Length,
        h82_hauteurDesInjecteurs_Z: Length,
    ): EcoLabeled_V2 = new EcoLabeled_V1_or_V2_Impl(
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
    ) with EcoLabeled_V2

/**
 * 'EcoLabeled' Firebox according to EN15544
 */
private sealed abstract class EcoLabeled_V1_or_V2_Impl (
    val pn_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
    val arriveeAirGeometryOpt: Option[PipeShape], // defined only for V2
    val h11_profondeurDuFoyer: QtyD[Meter],
    val h12_largeurDuFoyer: QtyD[Meter],
    val h13_hauteurDuFoyer: QtyD[Meter],
    val h70_largeurPorteDansMaconnerie: Length,
    val h71_largeurVitre: Length,
    val h72_hauteurVitre: Length,
    val h74_hauteur_de_cendrier_AF: Length,
    val h75_hauteurArriveeConduitAir_DessousSoleFoyer_W: Length,
    val h76_epaisseurSole: Length,
    val h77_epaisseurParoiInterneFoyer_D1: Length,
    val epaisseurParoiExterneFoyer_D2: Length,
    val h78_largeurEspaceInterparoisDuFoyer_S: Length,
    val h79_largeurRenfortMedianLateraux: Length,
    val h80_largeurRenfortMedianArriere: Length,
    val h81_debordDesRenfortsDansLesAngles: Length,
    val h82_hauteurDesInjecteurs_Z: Length,
) extends EcoLabeled {
    def heightOfLowestOpening: Length = h74_hauteur_de_cendrier_AF + 2.cm // TODO: pourquoi +2 ???
}

object EcoLabeled_Module extends From_CalculPdM_V_0_2_32_Module:

    type FB = EcoLabeled

    extension (firebox: EcoLabeled)
        def toCombustionAirPipe_EN15544: ValidatedNel[String, CombustionAirPipe_Module_EN15544.FullDescr] = 
            import CombustionAirPipe_Module_EN15544.*
            import firebox.*

            CombustionAirPipe_Module_EN15544.incremental
            .define(
                innerShape(rectangle(
                    a = h12_largeurDuFoyer - 6.cm, 
                    b = h11_profondeurDuFoyer - 6.cm)
                ),
                roughness(3.mm),
                addSectionHorizontal("-", 0.cm), // TOFIX
                {   // angle vif à 90deg si version 1 (chambre de détente), pas d'angle si version 2
                    version match
                        case Version.V1 => addSharpAngle_90deg("angle vif 90°")
                        case Version.V2 => addSectionHorizontal("-", 0.cm)
                },
                {
                    val chambre_detente_long = version match
                        case Version.V1 => 15.cm // pourquoi 15cm ???
                        case Version.V2 => 0.cm
                    addSectionVertical(s"chambre de détente (L = ${chambre_detente_long.showP})", chambre_detente_long) 
                },
                addSharpAngle_90deg("virage 90° vers colonnes d'air", angleN2 = 180.degrees.some),
                innerShape(rectangle(
                    a = {
                        version match
                            case Version.V1 => 
                                c24_largeurDesColonnesAirLaterales * 2.0 + c25_largeurDesColonnesAirArrieres
                                // 2 * h12_largeurDuFoyer + 2 * h11_profondeurDuFoyer
                            case Version.V2 => arriveeAirGeometryOpt.map(_.perimeterWetted).getOrElse(
                                throw new IllegalStateException("dev error: input air geometry should be defined for V2 eco-labeled fireboxs"))
                    },
                    b = h75_hauteurArriveeConduitAir_DessousSoleFoyer_W)),
                addSectionHorizontal(
                    "vers colonnes d'air", 
                    (2.0 * h12_largeurDuFoyer / 2.0 + 2.0 * h11_profondeurDuFoyer / 2.0) / 4.0 + h77_epaisseurParoiInterneFoyer_D1 + h78_largeurEspaceInterparoisDuFoyer_S / 2.0),
                addSharpAngle_90deg("virage au pied des colonnes d'air", angleN2 = Some(0.degrees)),
                innerShape(rectangle(
                    a = 2 * c24_largeurDesColonnesAirLaterales + c25_largeurDesColonnesAirArrieres, 
                    b = c11_largeurEspaceInterParoisFoyer_S)),
                addSectionVertical(
                    "remontée dans les colonnes d'air", 
                    h75_hauteurArriveeConduitAir_DessousSoleFoyer_W / 2.0 + h76_epaisseurSole + c18_hauteurEntreLesInjecteurs * 2.0),
                addSharpAngle_90deg("virage 90° avant injecteur"),
                innerShape(rectangle(
                    a = 
                            c19_largeurDesInjecteursLateraux * 4.0 * 2.0 
                        + c20_largeurDesInjecteursArrieres * 4.0 
                        + c21_largeurDesInjecteursSousPorte 
                        - 8.0 * c12_largeurRenfortMedianLateraux 
                        - 4.0 * c13_largeurRenfortMedianArriere, 
                    b = c15_hauterDesInjecteurs)),
                addSectionHorizontal(
                    "injecteurs", 
                    c10_epaisseurParoiInterneDuFoyer + c11_largeurEspaceInterParoisFoyer_S / 2.0)
            )
            .toFullDescr().extractPipe
        end toCombustionAirPipe_EN15544

        def toCombustionAirPipe_EN13384: ValidatedNel[String, CombustionAirPipe_Module_EN13384.FullDescr] = 
            import CombustionAirPipe_Module_EN13384.*
            import firebox.*

            CombustionAirPipe_Module_EN13384.incremental
            .define(
                roughness(3.mm),
                pipeLocation(PipeLocation.HeatedArea), // added for EN13384
                innerShape(rectangle(
                    a = h12_largeurDuFoyer - 6.cm, 
                    b = h11_profondeurDuFoyer - 6.cm)
                ),
                layer(e = 1.cm, λ = 1.3.W_per_mK), // added for EN13384
                addSectionHorizontal("-", 0.cm), // TOFIX
                {   // angle vif à 90deg si version 1 (chambre de détente), pas d'angle si version 2
                    version match
                        case Version.V1 => addSharpAngle_90deg("angle vif 90°")
                        case Version.V2 => addSectionHorizontal("-", 0.cm)
                },
                {
                    val chambre_detente_long = version match
                        case Version.V1 => 15.cm // pourquoi 15cm ???
                        case Version.V2 => 0.cm
                    addSectionVertical(s"chambre de détente (L = ${chambre_detente_long.showP})", chambre_detente_long) 
                },
                addSharpAngle_90deg("virage 90° vers colonnes d'air"),
                innerShape(rectangle(
                    a = {
                        version match
                            case Version.V1 => 
                                c24_largeurDesColonnesAirLaterales * 2.0 + c25_largeurDesColonnesAirArrieres
                                // 2 * h12_largeurDuFoyer + 2 * h11_profondeurDuFoyer
                            case Version.V2 => arriveeAirGeometryOpt.map(_.perimeterWetted).getOrElse(
                                throw new IllegalStateException("dev error: input air geometry should be defined for V2 eco-labeled fireboxs"))
                    },
                    b = h75_hauteurArriveeConduitAir_DessousSoleFoyer_W)),
                addSectionHorizontal(
                    "vers colonnes d'air", 
                    (2.0 * h12_largeurDuFoyer / 2.0 + 2.0 * h11_profondeurDuFoyer / 2.0) / 4.0 + h77_epaisseurParoiInterneFoyer_D1 + h78_largeurEspaceInterparoisDuFoyer_S / 2.0),
                addSharpAngle_90deg("virage au pied des colonnes d'air"),
                innerShape(rectangle(
                    a = 2 * c24_largeurDesColonnesAirLaterales + c25_largeurDesColonnesAirArrieres, 
                    b = c11_largeurEspaceInterParoisFoyer_S)),
                addSectionVertical(
                    "remontée dans les colonnes d'air", 
                    h75_hauteurArriveeConduitAir_DessousSoleFoyer_W / 2.0 + h76_epaisseurSole + c18_hauteurEntreLesInjecteurs * 2.0),
                addSharpAngle_90deg("virage 90° avant injecteur"),
                innerShape(rectangle(
                    a = 
                            c19_largeurDesInjecteursLateraux * 4.0 * 2.0 
                        + c20_largeurDesInjecteursArrieres * 4.0 
                        + c21_largeurDesInjecteursSousPorte 
                        - 8.0 * c12_largeurRenfortMedianLateraux 
                        - 4.0 * c13_largeurRenfortMedianArriere, 
                    b = c15_hauterDesInjecteurs)),
                addSectionHorizontal(
                    "injecteurs", 
                    c10_epaisseurParoiInterneDuFoyer + c11_largeurEspaceInterParoisFoyer_S / 2.0)
            )
            .toFullDescr().extractPipe
        end toCombustionAirPipe_EN13384


end EcoLabeled_Module