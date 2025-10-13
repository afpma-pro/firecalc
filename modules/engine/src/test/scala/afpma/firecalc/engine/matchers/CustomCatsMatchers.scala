/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.matchers

import cats.data.Validated

import org.scalatest.matchers.*

trait CustomCatsMatchers {

    // cats 'Validated' matchers

    def beValid[E, A] =
        Matcher { (v: Validated[E, A]) =>
            MatchResult(
            v.toOption.isDefined == true,
            s"${v} was not valid",
            s"${v} was valid"
            )
        }

    def beInvalid[E, A] =
        Matcher { (v: Validated[E, A]) =>
            MatchResult(
            v.toOption.isDefined == false,
            s"${v} was valid",
            s"${v} was invalid"
            )
        }
}

object CustomCatsMatchers extends CustomCatsMatchers