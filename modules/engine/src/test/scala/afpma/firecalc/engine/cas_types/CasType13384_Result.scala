/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.engine.utils.*

import cats.Show
import cats.syntax.all.*

case class CasType13384_Results(
    results: List[CasType13384_Result]
)

object CasType13384_Results:
    given showAsTable_Results: ShowAsTable[CasType13384_Results] =
        ShowAsTable.mkLightFor(
            "Comparaisons (nominal - réduit)",
            List(
                "descr",
                "pz",
                "pze",
                "pb",
                "pz-pze",
                "pz-pb",
                "tg",
                "tob",
                "tiob",
                "tiob-tg"
            ) ::: "-" :: List(
                "pz",
                "pze",
                "pb",
                "pz-pze",
                "pz-pb",
                "tg",
                "tob",
                "tiob",
                "tiob-tg"
            ),
            xs =>
                given Show[QtyD[Pascal]] =
                    afpma.firecalc.units.coulombutils.shows.defaults.show_Pascals_1
                xs.results.map: x =>
                    import x.nominal
                    import x.lowest
                    List(
                        x.descr,
                        nominal.pz.map(_.show).getOrElse("-"),
                        nominal.pze.map(_.show).getOrElse("-"),
                        nominal.pb.map(_.show).getOrElse("-"),
                        nominal.`pz-pze`.map(_.show).getOrElse("-"),
                        nominal.`pz-pb`.map(_.show).getOrElse("-"),
                        nominal.tg.map(_.show).getOrElse("-"),
                        nominal.tob.map(_.show).getOrElse("-"),
                        nominal.tiob.map(_.show).getOrElse("-"),
                        nominal.`tiob-tg`.map(_.show).getOrElse("-"),
                        "-",
                        lowest.pz.map(_.show).getOrElse("-"),
                        lowest.pze.map(_.show).getOrElse("-"),
                        lowest.pb.map(_.show).getOrElse("-"),
                        lowest.`pz-pze`.map(_.show).getOrElse("-"),
                        lowest.`pz-pb`.map(_.show).getOrElse("-"),
                        lowest.tg.map(_.show).getOrElse("-"),
                        lowest.tob.map(_.show).getOrElse("-"),
                        lowest.tiob.map(_.show).getOrElse("-"),
                        lowest.`tiob-tg`.map(_.show).getOrElse("-")
                    )
        )

case class CasType13384_Result(
    val descr: String,
    val nominal: CasType13384_Result.Values,
    val lowest: CasType13384_Result.Values
)

object CasType13384_Result:
    case class Values(
        pz: Option[Pressure],
        pze: Option[Pressure],
        pb: Option[Pressure],
        `pz-pze`: Option[Pressure],
        `pz-pb`: Option[Pressure],
        tg: Option[TCelsius],
        tob: Option[TCelsius],
        tiob: Option[TCelsius],
        `tiob-tg`: Option[TCelsius]
    )
