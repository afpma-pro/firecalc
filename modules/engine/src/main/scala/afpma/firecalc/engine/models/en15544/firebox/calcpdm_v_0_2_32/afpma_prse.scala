/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32

import cats.data.ValidatedNel
import cats.syntax.validated.catsSyntaxValidatedId

import algebra.instances.all.given

import afpma.firecalc.engine.biblio
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en15544.firebox.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*

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

/**
 * AFPMA (avec briques injecteurs PRSE) Firebox according to EN15544
 */
case class AFPMA_PRSE(
    emissions_values: EmissionsAndEfficiencyValues = biblio.afpma.firebox_emissions.AFPMA_PRSE,
    pn_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
    origineArriveeAir: AFPMA_PRSE.OutsideAirLocationInHeater,
    arriveeAirGeometry: PipeShape,
    h11_profondeurDuFoyer: QtyD[Meter],
    h12_largeurDuFoyer: QtyD[Meter],
    h13_hauteurDuFoyer: QtyD[Meter],
    h83_hauteurEntreSoleEt1erInjecteur_X: Length,
    h88_largeurVitre: Length,
    h89_hauteurVitre: Length,
    h91_hauteurDuCendrier_AF: QtyD[Meter],
    h92_epaisseurSole_S: QtyD[Meter],
    h93_hauteurEmbaseDessousSoleFoyer_V: QtyD[Meter],
    h94_hauteurDepassementArriveeAirFoyer_U: QtyD[Meter],
    h95_hauteurPassageVersColonneAir_W: QtyD[Meter],
    h96_nbColonnesAirFoyer: Int,
    h97_nbColonnesAirPorte: Int,
) extends From_CalculPdM_V_0_2_32 {
    override val reference           = LocalizedString.from(I18N.firebox_names.afpma_prse)
    override val type_of_appliance   = TypeOfAppliance.WoodLogs
    def heightOfLowestOpening: Length = h83_hauteurEntreSoleEt1erInjecteur_X
    val area_calc_method = AreaCalcMethod.AutoIfCubic
    val largeurVitre = h88_largeurVitre
    val hauteurVitre = h89_hauteurVitre

    final def geometrieEquivalenteDesInjecteursAir: PipeShape = rectangle(
        a = 
            // TOFIX: found in CalculPdM-v0.2.30 
            // - why 1.0cm ?? hauteur ?
            5.mm, 
        b =
            // TOFIX: found in CalculPdM-v0.2.30
            // - why x4 and /4 ?
            9.3.cm * 4 * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0)
    )

    def air_injector_surface_area = geometrieEquivalenteDesInjecteursAir.area

    def validate(m_B: m_B): Locale ?=> ValidatedNel[FireboxError, Unit] =
        new FireboxErrorCustom(
            I18N.warnings.firebox_afpma_prse_not_validated
        ).invalidNel
}

object AFPMA_PRSE:
    enum OutsideAirLocationInHeater:
        case FromBottom
    object OutsideAirLocationInHeater:
        type FromBottom = FromBottom.type

object AFPMA_PRSE_Module extends From_CalculPdM_V_0_2_32_Module:

    type FB = AFPMA_PRSE


    extension (firebox: AFPMA_PRSE)
        def toCombustionAirPipe_EN15544: ValidatedNel[String, CombustionAirPipe_Module_EN15544.FullDescr] = 
            import CombustionAirPipe_Module_EN15544.*
            import firebox.*

            firebox.origineArriveeAir match
                case AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom => 
                    CombustionAirPipe_Module_EN15544.incremental
                    .define(
                        innerShape(arriveeAirGeometry),
                        roughness(3.mm),
                        addSectionVertical(
                            "remontée dans chambre de détente", 
                            (h93_hauteurEmbaseDessousSoleFoyer_V - h94_hauteurDepassementArriveeAirFoyer_U) / 2.0),
                        addSharpAngle_90deg("virage vers colonnes d'air"),
                        innerShape(rectangle(
                            a = arriveeAirGeometry.perimeterWetted, 
                            b = (h93_hauteurEmbaseDessousSoleFoyer_V - h94_hauteurDepassementArriveeAirFoyer_U))),
                        addSectionHorizontal(
                            "longueur jusqu'au milieu des colonnes d'air", 
                            (2.0 * h12_largeurDuFoyer / 2.0 + 2.0 * h11_profondeurDuFoyer / 2.0) / 4.0 + 7.1.cm),
                        addSharpAngle_90deg("virage au pied des colonnes d'air", angleN2 = Some(0.degrees)),
                        roughness(2.mm),
                        innerShape(rectangle(
                            a = 3.2.cm, 
                            b = 
                                // TOFIX: found in CalculPdM-v0.2.30
                                // - why division by 4.0 ??
                                // - 6.6cm or 6.5cm ??
                                6.5.cm * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0))), 
                            // b = 6.5.cm * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte))), // use this instead ?
                        addSectionVertical(
                            "remontée dans les colonnes d'air", 
                            h91_hauteurDuCendrier_AF + h92_epaisseurSole_S + h93_hauteurEmbaseDessousSoleFoyer_V - h95_hauteurPassageVersColonneAir_W / 2.0 + 13.5.cm),
                        addSharpAngle_90deg("virage avant canal horizontal injecteur"),
                        roughness(1.mm),
                        innerShape(rectangle(
                            a = 
                                // TOFIX: found in CalculPdM-v0.2.30 
                                // - why 1.8cm ?? hauteur ?
                                1.8.cm, 
                            b =
                                // TOFIX: found in CalculPdM-v0.2.30 
                                // - why 6,6cm ? not 6,5cm ?
                                // - why x4 and /4 ?
                                6.6.cm * 4 * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0))), 
                        addSectionHorizontal(
                            "canal injecteurs horizontal 1/3", 
                            2.cm),
                        innerShape(rectangle(
                            a = 
                                // TOFIX: found in CalculPdM-v0.2.30 
                                // - why 1.0cm ?? hauteur ?
                                1.0.cm, 
                            b =
                                // TOFIX: found in CalculPdM-v0.2.30
                                // - why x4 and /4 ?
                                7.9.cm * 4 * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0))), 
                        addSectionHorizontal(
                            "canal injecteurs horizontal 2/3", 
                            2.cm),
                        innerShape(firebox.geometrieEquivalenteDesInjecteursAir),
                        addSectionHorizontal(
                            "canal injecteurs horizontal 3/3", 
                            1.5.cm)
                    )
                    .toFullDescr().extractPipe
        end toCombustionAirPipe_EN15544

        def toCombustionAirPipe_EN13384: ValidatedNel[String, CombustionAirPipe_Module_EN13384.FullDescr] = 
            import CombustionAirPipe_Module_EN13384.*
            import firebox.*

            firebox.origineArriveeAir match
                case AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom => 
                    CombustionAirPipe_Module_EN13384.incremental
                    .define(
                        pipeLocation(PipeLocation.HeatedArea), // added for EN13384
                        innerShape(arriveeAirGeometry),
                        layer(e = 1.cm, λ = 1.3.W_per_mK), // added for EN13384
                        // following is copy/pasted (!) from toCombustionAirPipe_EN15544
                        addSectionVertical(
                            "remontée dans chambre de détente", 
                            (h93_hauteurEmbaseDessousSoleFoyer_V - h94_hauteurDepassementArriveeAirFoyer_U) / 2.0),
                        addSharpAngle_90deg("virage vers colonnes d'air"),
                        innerShape(rectangle(
                            a = arriveeAirGeometry.perimeterWetted, 
                            b = (h93_hauteurEmbaseDessousSoleFoyer_V - h94_hauteurDepassementArriveeAirFoyer_U))),
                        addSectionHorizontal(
                            "longueur jusqu'au milieu des colonnes d'air", 
                            (2.0 * h12_largeurDuFoyer / 2.0 + 2.0 * h11_profondeurDuFoyer / 2.0) / 4.0 + 7.1.cm),
                        addSharpAngle_90deg("virage au pied des colonnes d'air"),
                        innerShape(rectangle(
                            a = 3.2.cm, 
                            b = 
                                // TOFIX: found in CalculPdM-v0.2.30
                                // - why division by 4.0 ??
                                // - 6.6cm or 6.5cm ??
                                6.5.cm * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0))), 
                            // b = 6.5.cm * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte))), // use this instead ?
                        addSectionVertical(
                            "remontée dans les colonnes d'air", 
                            h91_hauteurDuCendrier_AF + h92_epaisseurSole_S + h93_hauteurEmbaseDessousSoleFoyer_V - h95_hauteurPassageVersColonneAir_W / 2.0 + 13.5.cm),
                        addSharpAngle_90deg("virage avant canal horizontal injecteur"),
                        innerShape(rectangle(
                            a = 
                                // TOFIX: found in CalculPdM-v0.2.30 
                                // - why 1.8cm ?? hauteur ?
                                1.8.cm, 
                            b =
                                // TOFIX: found in CalculPdM-v0.2.30 
                                // - why 6,6cm ? not 6,5cm ?
                                // - why x4 and /4 ?
                                6.6.cm * 4 * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0))), 
                        addSectionHorizontal(
                            "canal injecteurs horizontal 1/3", 
                            2.cm),
                        innerShape(rectangle(
                            a = 
                                // TOFIX: found in CalculPdM-v0.2.30 
                                // - why 1.0cm ?? hauteur ?
                                1.0.cm, 
                            b =
                                // TOFIX: found in CalculPdM-v0.2.30
                                // - why x4 and /4 ?
                                7.9.cm * 4 * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0))), 
                        addSectionHorizontal(
                            "canal injecteurs horizontal 2/3", 
                            2.cm),
                        innerShape(rectangle(
                            a = 
                                // TOFIX: found in CalculPdM-v0.2.30 
                                // - why 1.0cm ?? hauteur ?
                                5.mm, 
                            b =
                                // TOFIX: found in CalculPdM-v0.2.30
                                // - why x4 and /4 ?
                                9.3.cm * 4 * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0))), 
                        addSectionHorizontal(
                            "canal injecteurs horizontal 3/3", 
                            1.5.cm)
                    )
                    .toFullDescr()       .extractPipe
end AFPMA_PRSE_Module