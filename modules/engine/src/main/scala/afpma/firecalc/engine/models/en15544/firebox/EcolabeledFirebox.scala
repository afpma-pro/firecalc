/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}


trait EcolabeledFirebox:
    import EcolabeledFirebox.*

    def circonferenceConduitArriveeAir: Option[QtyD[Meter]]

    def typeArriveeAir: TypeArriveeAir
    def largeurFoyer: QtyD[Meter]
    def profondeurFoyer: QtyD[Meter]

    def largeurPorteDansLaMaconnerie: QtyD[Meter]
    // def largeurPorte: QtyD[Meter]
    def largeurVitre: QtyD[Meter]

    def hauteurVitre: QtyD[Meter]

    /** AF */
    def hauteurCendrier: QtyD[Meter]
    def AF = hauteurCendrier

    def surfaceVitre: QtyD[(Meter ^ 2)] = largeurVitre * hauteurVitre

    /** W */
    def hauteurArriveeConduitAirDessousSole: QtyD[Meter]
    def W = hauteurArriveeConduitAirDessousSole

    def epaisseurSole: QtyD[Meter]

    /** D1 */
    def epaisseurParoiInterneDuFoyer: QtyD[Meter]
    def D1 = epaisseurParoiInterneDuFoyer

    /** S */
    def largeurEspaceInterParoi: QtyD[Meter]
    def S = largeurEspaceInterParoi

    /** St */
    def largeurEspaceInterParoiPorte: QtyD[Meter]
    def St = largeurEspaceInterParoiPorte

    def largeurRenfortsMediansLateraux: QtyD[Meter]
    def largeurRenfortMedianArriere: QtyD[Meter]
    def debordRenfortsDansLesAngles: QtyD[Meter]

    /** Z */
    def hauteurInjecteurs: QtyD[Meter]
    def Z = hauteurInjecteurs

    /** Zt */
    def hauteurInjecteurPorte: QtyD[Meter]
    def Zt = hauteurInjecteurPorte

    /** X */
    def hauteurEntreSoleEtPremierInjecteur: QtyD[Meter]
    def X = hauteurEntreSoleEtPremierInjecteur

    /** Y */
    def hauteurEntreInjecteurs: QtyD[Meter] =
        hauteurCendrier * -0.257142 + 10.585714.cm
    def Y = hauteurEntreInjecteurs

    /** Ls */
    def largeurInjecteursLateraux: QtyD[Meter] = profondeurFoyer - 9.cm
    def Ls = largeurInjecteursLateraux

    /** Lr */
    def largeurInjecteursArrieres: QtyD[Meter] = largeurFoyer - 9.cm
    def Lr = largeurInjecteursArrieres

    /** Lt */
    def largeurInjecteurSousPorte: QtyD[Meter] = largeurPorteDansLaMaconnerie - 6.cm
    def Lt: QtyD[Meter] = largeurInjecteurSousPorte

    def largeurMaxRenfortsMediansLateraux: QtyD[Meter] =
        20.percent * largeurInjecteursLateraux
    def largeurMaxRenfortsMediansArriere: QtyD[Meter] =
        20.percent * largeurInjecteursArrieres

    def largeurColonnesAirLaterales: QtyD[Meter] =
        profondeurFoyer - (debordRenfortsDansLesAngles * 2 + largeurRenfortsMediansLateraux)
    def largeurColonneAirArriere: QtyD[Meter] =
        largeurFoyer - (debordRenfortsDansLesAngles * 2 + largeurRenfortMedianArriere)

object EcolabeledFirebox:

    enum TypeArriveeAir:
        case Version1, Version2

    case class UserInput(
        largeurFoyer: QtyD[Meter],
        profondeurFoyer: QtyD[Meter],
        largeurPorteDansLaMaconnerie: QtyD[Meter],
        // largeurPorte: QtyD[Meter],
        largeurVitre: QtyD[Meter],
        hauteurVitre: QtyD[Meter],
        hauteurCendrier: QtyD[Meter],
        hauteurArriveeConduitAirDessousSole: QtyD[Meter],
        epaisseurSole: QtyD[Meter],
        epaisseurParoiInterneDuFoyer: QtyD[Meter],
        largeurEspaceInterParoi: QtyD[Meter],
        largeurEspaceInterParoiPorte: QtyD[Meter],
        largeurRenfortsMediansLateraux: QtyD[Meter],
        largeurRenfortMedianArriere: QtyD[Meter],
        debordRenfortsDansLesAngles: QtyD[Meter],
        hauteurInjecteurs: QtyD[Meter],
        hauteurInjecteurPorte: QtyD[Meter],
        hauteurEntreSoleEtPremierInjecteur: QtyD[Meter]
    )

    def version1: UserInput => EcolabeledFirebox =
        fromVersion(TypeArriveeAir.Version1, None)

    def version2(
        circonferenceConduitArriveeAir: QtyD[Meter]
    ): UserInput => EcolabeledFirebox =
        fromVersion(
            TypeArriveeAir.Version2,
            Some(circonferenceConduitArriveeAir)
        )

    private def fromVersion(
        _typeArriveeAir: TypeArriveeAir,
        _circonferenceConduitArriveeAir: Option[QtyD[Meter]]
    )(
        input: UserInput
    ): EcolabeledFirebox =
        new EcolabeledFirebox:
            def circonferenceConduitArriveeAir =
                require(_typeArriveeAir == TypeArriveeAir.Version2)
                _circonferenceConduitArriveeAir
            val typeArriveeAir: TypeArriveeAir = _typeArriveeAir
            val largeurFoyer: QtyD[Meter] = input.largeurFoyer
            val profondeurFoyer: QtyD[Meter] = input.profondeurFoyer
            val largeurPorteDansLaMaconnerie: QtyD[Meter] =
                input.largeurPorteDansLaMaconnerie
            // val largeurPorte: QtyD[Meter]                        = input.largeurPorte
            val largeurVitre: QtyD[Meter] = input.largeurVitre
            val hauteurVitre: QtyD[Meter] = input.hauteurVitre
            val hauteurCendrier: QtyD[Meter] = input.hauteurCendrier
            val hauteurArriveeConduitAirDessousSole: QtyD[Meter] =
                input.hauteurArriveeConduitAirDessousSole
            val epaisseurSole: QtyD[Meter] = input.epaisseurSole
            val epaisseurParoiInterneDuFoyer: QtyD[Meter] =
                input.epaisseurParoiInterneDuFoyer
            val largeurEspaceInterParoi: QtyD[Meter] = input.largeurEspaceInterParoi
            val largeurEspaceInterParoiPorte: QtyD[Meter] =
                input.largeurEspaceInterParoiPorte
            val largeurRenfortsMediansLateraux: QtyD[Meter] =
                input.largeurRenfortsMediansLateraux
            val largeurRenfortMedianArriere: QtyD[Meter] =
                input.largeurRenfortMedianArriere
            val debordRenfortsDansLesAngles: QtyD[Meter] =
                input.debordRenfortsDansLesAngles
            val hauteurInjecteurs: QtyD[Meter] = input.hauteurInjecteurs
            val hauteurInjecteurPorte: QtyD[Meter] = input.hauteurInjecteurPorte
            val hauteurEntreSoleEtPremierInjecteur: QtyD[Meter] =
                input.hauteurEntreSoleEtPremierInjecteur

end EcolabeledFirebox
