/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

trait ShowAsTable[A]:
    def rowsOfCells(a: A): Seq[Seq[String]]
    val header: Option[String]
    val colHeaders: Option[Seq[String]]
    extension (a: A)
        def showOnlyRows: Seq[Seq[String]] = 
            rowsOfCells(a)

object ShowAsTable:

    def apply[A](using ev: ShowAsTable[A]) = ev

    def mkLightForCol3[A](xs: Seq[ShowAsSeqOf3[A]]): ShowAsTable[A] = 
        mkLightFor[A](a => xs.map(_.seqOf3Cells(a)))

    def mkLightFor[A](toRows: A => Seq[Seq[String]]): ShowAsTable[A] = 
        mkLightFor(None, None, toRows)

    def mkLightFor[A](header: String)(toRows: A => Seq[Seq[String]]): ShowAsTable[A] = 
        mkLightFor(Some(header), None, toRows)
    
    def mkLightFor[A](
        header: String,
        colHeaders: Seq[String],
        toRows: A => Seq[Seq[String]]
    ): ShowAsTable[A] = 
        mkLightFor(Some(header), Some(colHeaders), toRows)

    private def mkLightFor[A](
        headerOpt: Option[String],
        colHeadersOpt: Option[Seq[String]],
        toRows: A => Seq[Seq[String]]
    ): ShowAsTable[A] = 
            new ShowAsTable[A]:
                val header = headerOpt.map(_.toUpperCase())
                val colHeaders: Option[Seq[String]] = colHeadersOpt
                def rowsOfCells(a: A): Seq[Seq[String]] = toRows(a)