/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog

import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path

import scala.jdk.CollectionConverters.*

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFSheet
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
                case CellType.STRING =>
                    val s = cell.getStringCellValue.trim
                    s.toDoubleOption.orElse(s.replace(",", ".").toDoubleOption)
                case _ => None

    def readInt(row: Row, col: Int): Option[Int] =
        readDouble(row, col).map(_.toInt)

    def getRow(sheet: Sheet, rowIdx: Int): Option[Row] =
        Option(sheet.getRow(rowIdx))

    def readFormValue[A](sheet: Sheet, rowIdx: Int, col: Int, reader: (Row, Int) => Option[A]): Option[A] =
        getRow(sheet, rowIdx).flatMap(reader(_, col))

    // ---- Image reading helpers ----

    /** Convert picture bytes + MIME type to a data URI string. */
    private def toDataUri(pic: XSSFPicture): String =
        val data = pic.getPictureData
        val mime = data.getMimeType
        val base64 = java.util.Base64.getEncoder.encodeToString(data.getData)
        s"data:$mime;base64,$base64"

    /** Read the first embedded picture from a sheet as a data URI.
      * Used for form-based templates (one entry per workbook).
      */
    def readFirstPicture(wb: XSSFWorkbook, sheetIndex: Int): Option[String] =
        val sheet = wb.getSheetAt(sheetIndex).asInstanceOf[XSSFSheet]
        Option(sheet.getDrawingPatriarch()).flatMap: drawing =>
            drawing.getShapes.asScala.collectFirst:
                case pic: XSSFPicture => toDataUri(pic)

    /** Read all embedded pictures from a sheet, keyed by their anchor row.
      * Only includes pictures whose anchor column matches `imageCol`.
      * Used for tabular templates (multiple entries per workbook).
      */
    def readPicturesByRow(wb: XSSFWorkbook, sheetIndex: Int, imageCol: Int): Map[Int, String] =
        val sheet = wb.getSheetAt(sheetIndex).asInstanceOf[XSSFSheet]
        Option(sheet.getDrawingPatriarch()).map: drawing =>
            drawing.getShapes.asScala.collect:
                case pic: XSSFPicture if pic.getClientAnchor.getCol1 == imageCol =>
                    pic.getClientAnchor.getRow1 -> toDataUri(pic)
            .toMap
        .getOrElse(Map.empty)

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

    // ---- Image writing helpers ----

    private val DataUriPattern = "data:(image/[^;]+);base64,(.+)".r

    /** Map MIME type to POI picture type constant. */
    private def mimeToPoiType(mime: String): Int = mime match
        case "image/png"  => Workbook.PICTURE_TYPE_PNG
        case "image/jpeg" => Workbook.PICTURE_TYPE_JPEG
        case _            => Workbook.PICTURE_TYPE_PNG // best-effort fallback

    /** Embed a base64 data URI image into a sheet at the given anchor position.
      * Used for export flow (DTO → xlsx).
      */
    def embedDataUriImage(wb: XSSFWorkbook, sheet: Sheet, dataUri: String, row: Int, col: Int): Unit =
        dataUri match
            case DataUriPattern(mime, base64) =>
                val bytes = java.util.Base64.getDecoder.decode(base64)
                val pictureIdx = wb.addPicture(bytes, mimeToPoiType(mime))
                val drawing = sheet.createDrawingPatriarch()
                val anchor = wb.getCreationHelper.createClientAnchor()
                anchor.setRow1(row)
                anchor.setCol1(col)
                anchor.setRow2(row + 15)
                anchor.setCol2(col + 3)
                drawing.createPicture(anchor, pictureIdx)
            case _ => () // silently skip malformed data URIs

    // ---- File I/O ----

    def openWorkbook(path: Path): XSSFWorkbook =
        val fis = new FileInputStream(path.toFile)
        try new XSSFWorkbook(fis)
        finally fis.close()

    def saveWorkbook(wb: XSSFWorkbook, path: Path): Unit =
        val fos = new FileOutputStream(path.toFile)
        try wb.write(fos)
        finally fos.close()
