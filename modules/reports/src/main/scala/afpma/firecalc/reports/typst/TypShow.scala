/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports.typst

import TypShow.*
import afpma.firecalc.engine.utils.ShowAsTable
import io.taig.babel.Locale

trait TypShow[A]:
    def showAsTyp(a: A): SanitizedString

object TypShow:

    opaque type SanitizedString = String
    given Conversion[SanitizedString, String] = identity
    extension (raw: String)
        def sanitized: SanitizedString = 
            raw
            .replaceAll("/", "\\\\/") // remove Typst comment in string (to prevent Typst compiler issues)
    
    def apply[A](using ev: TypShow[A]) = ev
    
    def make[A](f: A => String) = new TypShow[A]:
        def showAsTyp(a: A): String = f(a).sanitized

    def mkForTable[A](
        title: Option[String],
        headers: Option[Seq[String]],
        toRows: A => Seq[Seq[String]],
        maxWidthForFirstColumn: Boolean = true,
    )(using Locale): TypShow[A] = 
        TypShow.make: a =>
            val rows = toRows(a)
            require(rows.headOption.isDefined, s"expected at least one row but got :\n${rows}")

            val tableTitleString = title
                .map: t => 
                    s"""|  table.cell(inset: (y: 10pt), colspan: ${rows.head.length})[
                        |    #text(1.2em)[*$t*]
                        |  ],""".stripMargin
                .getOrElse("")

            val tableHeaderArg =
                if (headers.isEmpty) 
                    ""
                else  
                    s"""${headers.get.mkString("[*", "*], [*", "*]")},"""

            val columnsAuto = rows.head.map(_ => "auto")
            val columnsUpdated = 
                if (maxWidthForFirstColumn)
                    columnsAuto.updated(0, "1fr")
                else
                    columnsAuto

            s"""|#table(
                |  columns: ${columnsUpdated.mkString("(", ",", ")")},
                |  inset: 4pt,
                |  align: horizon,
                |$tableTitleString
                |  $tableHeaderArg
                |${
                    rows
                    .map(_.mkString("[", "], [", "],"))
                    .mkString("  ", "\n  ", "")
                }  
                |)""".stripMargin

    def mkFromShowAsTable[A: ShowAsTable as sat](maxWidthForFirstColumn: Boolean = true)(using Locale): TypShow[A] = 
        TypShow.mkForTable(
            title                   = sat.header,
            headers                 = sat.colHeaders,
            toRows                  = sat.rowsOfCells(_),
            maxWidthForFirstColumn  = maxWidthForFirstColumn
        )

    given fromShowAsTable: [A] => Locale => (sat: ShowAsTable[A]) => TypShow[A] =
        mkFromShowAsTable()

extension [A: TypShow](a: A)
    def typ: SanitizedString = TypShow[A].showAsTyp(a)

extension [A: ShowAsTable as sat](a: A)
    def asTypShow(using Locale): TypShow[A] = 
        TypShow.fromShowAsTable[A]
