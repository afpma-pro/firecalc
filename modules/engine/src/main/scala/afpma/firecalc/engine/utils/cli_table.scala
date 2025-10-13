/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import afpma.firecalc.engine.utils.cli_table.CliTable

// Credits : https://stackoverflow.com/questions/7539831/scala-draw-table-to-console

private object cli_table:
    type ColumnSep = (Char, Char, Char)

    case class TableSeparator(horizontal: Char, vertical: Char, upLeft: Char, upMiddle: Char, upRight: Char, middleLeft: Char, middleMiddle: Char, middleRight: Char, downLeft: Char, downMiddle: Char, downRight: Char):

        def separate(sep: TableSeparator => ColumnSep)(seq: Seq[Any]): String =
            val (a, b, c) = sep(this)
            seq.mkString(a.toString, b.toString, c.toString)

        def separateRows(posicao: TableSeparator => ColumnSep)(colSizes: Seq[Int]): String =
            separate(posicao)(colSizes.map(horizontal.toString * _))

        def up: ColumnSep = (upLeft, upMiddle, upRight)

        def middle: ColumnSep = (middleLeft, middleMiddle, middleRight)

        def down: ColumnSep = (downLeft, downMiddle, downRight)

        def verticals: ColumnSep = (vertical, vertical, vertical)

    object TableSeparator:

        lazy val simple = TableSeparator(
            '-', '|',
            '+', '+', '+',
            '+', '+', '+',
            '+', '+', '+'
        )

        lazy val light = TableSeparator(
            '─', '│',
            '┌', '┬', '┐',
            '├', '┼', '┤',
            '└', '┴', '┘'
        )

        lazy val heavy = TableSeparator(
            '━', '┃',
            '┏', '┳', '┓',
            '┣', '╋', '┫',
            '┗', '┻', '┛'
        )

        lazy val dottedLight = TableSeparator(
            '┄', '┆',
            '┌', '┬', '┐',
            '├', '┼', '┤',
            '└', '┴', '┘'
        )

        lazy val dottedHeavy = TableSeparator(
            '┅', '┇',
            '┏', '┳', '┓',
            '┣', '╋', '┫',
            '┗', '┻', '┛'
        )

        lazy val double = TableSeparator(
            '═', '║',
            '╔', '╦', '╗',
            '╠', '╬', '╣',
            '╚', '╩', '╝'
        )

    class CliTable(val separators: TableSeparator):

        def format(table: Seq[Seq[Any]], header: Option[String]): String = table match
            case Seq() => ""
            case _ =>
                val sizes = 
                    for (row <- table) 
                    yield 
                        for (cell <- row) 
                        yield 
                            if cell == null then 0 else cell.toString.length
                val colSizes = for (col <- sizes.transpose) yield col.max
                val updatedColSizes = header match
                    case Some(h) => 
                        if (h.size > colSizes.sum)                              // if header is bigger than sum of columns
                            colSizes.updated(0, h.size - colSizes.tail.size)    // expand first column size 
                        else 
                            colSizes
                    case None    => colSizes

                val rows = for (row <- table) yield formatRow(row, updatedColSizes)
                (
                    (if (header.isDefined) formatHeader(header.get, updatedColSizes) else Nil) ::
                    formatRows(updatedColSizes, rows) ::
                    Nil
                ).mkString("\n")

        private def alignLeft(text: String, width: Int): String =
            val space: Int = width - text.length
            val prefix: Int = 0
            val suffix: Int = (space)
            if width > text.length 
            then " ".repeat(prefix + 1) + text + " ".repeat(suffix + 1) 
            else " ".repeat(1) + text + " ".repeat(1)

        private def formatHeader(header: String, colSizes: Seq[Int]): String =
            val colSizesP2 = colSizes.map(_ + 2)
            val colSizesP2Sum = colSizesP2.sum + (colSizes.size - 1)
            val cell = if colSizesP2Sum == 0 then "" else alignLeft(header.toString, colSizesP2Sum - 2)
            (
                separators.separateRows(_.up)(Seq(colSizesP2Sum)) ::
                separators.separate(_.verticals)(Seq(cell)) ::
                // separators.separateRows(_.down)(Seq(colSizesP2Sum)) ::
                Nil
            ).mkString("\n")

        private def formatRows(colSizes: Seq[Int], rows: Seq[String]): String =
            val colSizesP2 = colSizes.map(_ + 2)
            val separatorsBetweenRows = separators.separateRows(_.middle)(colSizesP2)
            val row_tails = rows.tail.toList
                    .map(r => separatorsBetweenRows :: r :: Nil)
                    .flatten
                    .mkString("\n")
            (
                separators.separateRows(_.up)(colSizesP2) ::
                (
                    if (row_tails.isEmpty) rows.head
                    else (rows.head :: row_tails :: Nil).mkString("\n")
                ) ::
                separators.separateRows(_.down)(colSizesP2) ::
                List()
            ).mkString("\n")

        private def formatRow(row: Seq[Any], colSizes: Seq[Int]): String =
            val cells = for (item, size) <- row zip colSizes yield if size == 0 then "" else alignLeft(item.toString, size)
            separators.separate(_.verticals)(cells)

    object CliTable:
        val light = new CliTable(TableSeparator.light)

end cli_table

extension [A: ShowAsTable as sat](a: A)
    def showAsCliTable: String = 
        val ss = sat.rowsOfCells(a)
        val header_ss = if (sat.colHeaders.isDefined) Seq(sat.colHeaders.get) ++ ss else ss
        CliTable.light.format(header_ss, header = sat.header)