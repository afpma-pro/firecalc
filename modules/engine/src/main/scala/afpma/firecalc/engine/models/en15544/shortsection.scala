/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.shortsection

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortOrRegular.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.standard.*

import cats.*
import cats.data.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

// see EN15544 - 4.9.5
enum ShortOrRegular:
    case Short, Regular

sealed trait ShortOrRegularOps[El]:
    extension (el: El)
        def shortOrRegular: ShortOrRegular
        def isShort       : Boolean = shortOrRegular == Short
        def isRegular     : Boolean = shortOrRegular == Regular

object ShortOrRegularOps:
    given forStraightSection: ShortOrRegularOps[StraightSection]:
        extension (el: StraightSection)
            def shortOrRegular =
                if (el.length < el.geometry.dh) Short else Regular

trait ShortSectionAlg:
    import ShortSection.*

    def intermediateValuesFromWindow(w      : PipeDescrWindow   ): VNel[IntermediateValues]
    def resultFromIntermediateValues(ivalues: IntermediateValues): Result
    def resultFromWindow            (w      : PipeDescrWindow   ): VNel[Result]

object ShortSection:

    type VNel[A] = ValidatedNel[SingularFlowResistanceCoeffErrorI, A]

    case class Result private[engine] (ζ1: ζ, ζ2: ζ)
    case class IntermediateValues(
        ζα1: ζ,
        ζα2: ζ,
        ζα3: ζ,
        α1 : QtyD[Degree],
        α2 : QtyD[Degree],
        lz : QtyD[Meter],
        dh : D_h
    )
    case class PipeDescrWindow private (
        ζα1_prev: ζ,
        ζα2_prev: ζ,
        dc01    : DirectionChange,
        s1      : StraightSection,
        o_dc12  : Option[DirectionChange]
    )

    object PipeDescrWindow:

        def from(
            ζα1_prev   : ζ,
            ζα2_prev   : ζ,
            dc01       : DirectionChange,
            s1         : StraightSection,
            o_dc12     : Option[DirectionChange],
            o_dc12_name: Option[String],
            sectionTyp : PipeType
        ): ValidatedNel[SingularFlowResistanceCoeffError, PipeDescrWindow] =
            if (o_dc12.isDefined && o_dc12.get.angleN2.isEmpty)
                new MissingAlpha3AngleForShortFluePipeSection(
                    s"angleN2 should be defined for '${o_dc12_name.get}'"
                ).invalidNel
            else PipeDescrWindow(ζα1_prev, ζα2_prev, dc01, s1, o_dc12).validNel

    end PipeDescrWindow

