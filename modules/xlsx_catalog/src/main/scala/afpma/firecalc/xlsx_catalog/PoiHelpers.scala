/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog

import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object PoiHelpers:

    // ---- Reading helpers ----

    def readString(row: Row, col: Int): Option[String] =
        Option(row.getCell(col)).flatMap: cell =>
            cell.getCellType match
                case CellType.STRING  => Some(cell.getStringCellValue).filter(_.nonEmpty)
                case CellType.NUMERIC => Some(cell.getNumericCellValue.toString)
                case _                => None

    def readDouble(row: Row, col: Int): Option[Double] =
        Option(row.getCell(col)).flatMap: cell =>
            cell.getCellType match
                case CellType.NUMERIC => Some(cell.getNumericCellValue)
                case CellType.STRING  =>
                    cell.getStringCellValue.trim.toDoubleOption
                case _ => None

    def readInt(row: Row, col: Int): Option[Int] =
        readDouble(row, col).map(_.toInt)

    def getRow(sheet: Sheet, rowIdx: Int): Option[Row] =
        Option(sheet.getRow(rowIdx))

    def readFormValue[A](sheet: Sheet, rowIdx: Int, col: Int, reader: (Row, Int) => Option[A]): Option[A] =
        getRow(sheet, rowIdx).flatMap(reader(_, col))

    // ---- Writing helpers ----

    def writeString(row: Row, col: Int, value: String): Cell =
        val cell = row.createCell(col)
        cell.setCellValue(value)
        cell

    def writeDouble(row: Row, col: Int, value: Double): Cell =
        val cell = row.createCell(col)
        cell.setCellValue(value)
        cell

    def writeInt(row: Row, col: Int, value: Int): Cell =
        val cell = row.createCell(col)
        cell.setCellValue(value.toDouble)
        cell

    def mergeRegion(sheet: Sheet, firstRow: Int, lastRow: Int, firstCol: Int, lastCol: Int): Unit =
        sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol))

    // ---- File I/O ----

    def openWorkbook(path: Path): XSSFWorkbook =
        val fis = new FileInputStream(path.toFile)
        try new XSSFWorkbook(fis)
        finally fis.close()

    def saveWorkbook(wb: XSSFWorkbook, path: Path): Unit =
        val fos = new FileOutputStream(path.toFile)
        try wb.write(fos)
        finally fos.close()
