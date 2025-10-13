/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.shortsection

import cats.*
import cats.data.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.models.en15544.pipedescr.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortOrRegular.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.standard.*

import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

// see EN15544 - 4.9.5
enum ShortOrRegular:
    case Short, Regular

sealed trait ShortOrRegularOps[El]:
    extension (el: El)
        def shortOrRegular: ShortOrRegular
        def isShort: Boolean = shortOrRegular == Short
        def isRegular: Boolean = shortOrRegular == Regular

object ShortOrRegularOps:
    given forStraightSection: ShortOrRegularOps[StraightSection]:
        extension (el: StraightSection)
            def shortOrRegular = 
                if (el.length < el.geometry.dh) Short else Regular

trait ShortSectionAlg:
    import ShortSection.* 

    def intermediateValuesFromWindow(w: PipeDescrWindow): VNel[IntermediateValues]
    def resultFromIntermediateValues(ivalues: IntermediateValues): Result
    def resultFromWindow(w: PipeDescrWindow): VNel[Result]

object ShortSection:

    type VNel[A] = ValidatedNel[PressureLossCoeff_Error, A]
    
    case class Result private[shortsection] (ζ1: ζ, ζ2: ζ)
    case class IntermediateValues(
        ζα1: ζ, ζα2: ζ, ζα3: ζ, α1: QtyD[Degree], α2: QtyD[Degree], lz: QtyD[Meter], dh: D_h
    )
    case class PipeDescrWindow private(
        ζα1_prev: ζ,
        ζα2_prev: ζ,
        dc01: DirectionChange,
        s1: StraightSection,
        o_dc12: Option[DirectionChange]
    )

    object PipeDescrWindow:


        def from(
            ζα1_prev: ζ,
            ζα2_prev: ζ,
            dc01: DirectionChange,
            s1: StraightSection,
            o_dc12: Option[DirectionChange],
            o_dc12_name: Option[String]
        ): ValidatedNel[PressureLossCoeff_Error, PipeDescrWindow] = 
            if (o_dc12.isDefined && o_dc12.get.angleN2.isEmpty) new MissingAlpha3AngleForShortFluePipeSection(s"angleN2 should be defined for '${o_dc12_name.get}'").invalidNel
            else PipeDescrWindow(ζα1_prev, ζα2_prev, dc01, s1, o_dc12).validNel

    end PipeDescrWindow
    
    def makeImpl(using 
        en15544: afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
    ): ShortSectionAlg = new ShortSectionAlg:
        
        def intermediateValuesFromWindow(window: PipeDescrWindow): VNel[IntermediateValues] =
            import window.*

            import afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff

            val ζα2 = 
                o_dc12 match
                    case None       => (0.0.unitless: ζ)
                    case Some(_) => ζα2_prev

            val dc02 = DirectionChange.AngleVifDe0A180(
                o_dc12 match
                    case None       => 
                        // dc12 is not present or not considered so we assume α2 is zero
                        // so α3 = α1
                        dc01.angleN1
                    case Some(dc12) => dc12.angleN2.getOrElse(throw new Exception("bad validation (TOFIX by @dev)")),
                angleN2 = None
            ) 
            val ζα3_v = dynamicfrictioncoeff.whenRegularFor(dc02)
            val α1 = dc01.angleN1
            val α2 =
                o_dc12 match
                    case None       => 0.0.degrees // dc12 is not present or not considered so we assume α2 is zero
                    case Some(dc12) => dc12.angleN1
            val lz = s1.length
            val dh = s1.geometry.dh
            // require(lz < dh, "TOFIX: not a straight section (dev error)") // TOFIX (require this should work on full test suite)

            (ζα3_v).map: ζα3 =>
                IntermediateValues(ζα1_prev, ζα2, ζα3, α1, α2, lz, dh)
            
        def resultFromIntermediateValues(ivalues: IntermediateValues): Result = 
            import ivalues.*
            val ζ1 = en15544.ζ1_modified_calc(ζα1, ζα2, ζα3, α1, α2, lz, dh)
            val ζ2 = en15544.ζ2_modified_calc(ζα1, ζα2, ζα3, α1, α2, lz, dh)
            ShortSection.Result(ζ1, ζ2)

        def resultFromWindow(w: PipeDescrWindow): VNel[Result] = 
            val out = intermediateValuesFromWindow(w) map resultFromIntermediateValues
            out