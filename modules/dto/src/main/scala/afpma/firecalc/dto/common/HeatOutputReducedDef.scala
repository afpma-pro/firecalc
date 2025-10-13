/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import cats.*
import cats.data.*
import cats.derived.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import io.taig.babel.Locale

enum HeatOutputReduced:
    case NotDefined
    case HalfOfNominal private (pn_reduced: Option[Power])
    case FromTypeTest(pn_reduced: Power)

object HeatOutputReduced:

    object HalfOfNominal:
        val makeWithoutValue: HalfOfNominal                                 = HalfOfNominal(None)
        def makeFromValue(pn_reduced: Power): HalfOfNominal                 = HalfOfNominal(pn_reduced.some)
        def makeFromNominal(pn_nominal: Power): HalfOfNominal               = HalfOfNominal((pn_nominal / 2.0).some)
        def makeFromNominalO(pn_nominal_o: Option[Power]): HalfOfNominal    = pn_nominal_o.fold(makeWithoutValue)(makeFromNominal)

    type NotDefined         = NotDefined.type

    type NotDefined_Or_Default = NotDefined | HalfOfNominal
    type NotDefined_Or_Tested = NotDefined | FromTypeTest

    given show_NotDefined: Locale => Show[NotDefined] = 
        Show.show(_ => I18N.heat_output_reduced.not_defined)

    given show_HalfOfNominal: Locale => Show[HalfOfNominal] = 
        Show.show(_ => 
            I18N.heat_output_reduced.defined_as_half_of_nominal
            // I18N.heat_output_reduced.defined_as_default(x.pn_reduced.map(_.showP).getOrElse("???"))
        )

    given show_FromTypeTest: Locale => Show[FromTypeTest] = 
        Show.show(x => I18N.heat_output_reduced.defined_when_tested(x.pn_reduced.showP))

    given show_NotDefined_Or_Default: Locale => Show[NotDefined_Or_Default] = 
        Show.show:
            case x: NotDefined => 
                show_NotDefined.show(x)
            case x: HalfOfNominal => 
                show_HalfOfNominal.show(x)

    given show_NotDefined_Or_Tested: Locale => Show[NotDefined_Or_Tested] = 
        Show.show:
            case x: NotDefined => 
                show_NotDefined.show(x)
            case x: FromTypeTest => 
                show_FromTypeTest.show(x)

    given show_HeatOutputReduced: Locale => Show[HeatOutputReduced] = 
        Show.show:
            case x: NotDefined => 
                show_NotDefined.show(x)
            case x: HalfOfNominal => 
                show_HalfOfNominal.show(x)
            case x: FromTypeTest => 
                show_FromTypeTest.show(x)
