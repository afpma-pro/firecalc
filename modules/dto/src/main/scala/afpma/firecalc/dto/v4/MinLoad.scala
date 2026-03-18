/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.i18n.implicits.I18N

import cats.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale

enum MinLoad:
    case NotDefined
    case HalfOfMaxLoad private (min_load: Option[Mass])
    case FromTypeTest(min_load: Mass)

object MinLoad:

    object HalfOfMaxLoad:
        val makeWithoutValue: HalfOfMaxLoad = HalfOfMaxLoad(None)
        def makeFromValue  (min_load: Mass): HalfOfMaxLoad = HalfOfMaxLoad(min_load.some)
        def makeFromMaxLoad(max_load: Mass): HalfOfMaxLoad = HalfOfMaxLoad((max_load / 2.0).some)
        // def makeFromNominalO(max_load_o: Option[Mass]): HalfOfMaxLoad =
        //     max_load_o.fold(makeWithoutValue)(makeFromNominal)

    type NotDefined = NotDefined.type

    // type NotDefined_Or_Default = NotDefined | HalfOfMaxLoad
    // type NotDefined_Or_Tested  = NotDefined | FromTypeTest

    given show_NotDefined: Locale => Show[NotDefined] =
        Show.show(_ => I18N.not_defined)

    given show_HalfOfMaxLoad: Locale => Show[HalfOfMaxLoad] =
        Show.show(x =>
            I18N.min_load.defined_as_half_of_nominal(x.showP)
        )

    given show_FromTypeTest: Locale => Show[FromTypeTest] =
        Show.show(x => I18N.min_load.defined_when_tested(x.min_load.showP))

    // given show_NotDefined_Or_Default: Locale => Show[NotDefined_Or_Default] =
    //     Show.show:
    //         case x: NotDefined    =>
    //             show_NotDefined.show(x)
    //         case x: HalfOfMaxLoad =>
    //             show_HalfOfMaxLoad.show(x)

    // given show_NotDefined_Or_Tested: Locale => Show[NotDefined_Or_Tested] =
    //     Show.show:
    //         case x: NotDefined   =>
    //             show_NotDefined.show(x)
    //         case x: FromTypeTest =>
    //             show_FromTypeTest.show(x)

    given show_MinLoad: Locale => Show[MinLoad] =
        Show.show:
            case x: NotDefined    =>
                show_NotDefined.show(x)
            case x: HalfOfMaxLoad =>
                show_HalfOfMaxLoad.show(x)
            case x: FromTypeTest  =>
                show_FromTypeTest.show(x)
