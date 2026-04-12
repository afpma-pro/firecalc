/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.templates

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress

/** Shared helpers for building template sheets. */
object TemplateHelpers:

    /** Write bilingual table headers (FR row, EN row, unit row). */
    def writeTableHeaders(
        sheet  : Sheet,
        styles : Styles.StyleBundle,
        rowFr  : Int,
        rowEn  : Int,
        rowUnit: Int,
        headers: Seq[(String, String, String)] // (fr, en, unit)
    ): Unit =
        val rFr   = sheet.createRow(rowFr)
        val rEn   = sheet.createRow(rowEn)
        val rUnit = sheet.createRow(rowUnit)
        for (((fr, en, unit), colIdx) <- headers.zipWithIndex) do
            val cFr   = rFr.createCell(colIdx); cFr.setCellValue    (fr  ); cFr.setCellStyle  (styles.frHeader)
            val cEn   = rEn.createCell(colIdx); cEn.setCellValue    (en  ); cEn.setCellStyle  (styles.enHeader)
            val cUnit = rUnit.createCell(colIdx); cUnit.setCellValue(unit); cUnit.setCellStyle(styles.unitRow )

    /** Write example values in gray italic. */
    def writeExampleRow(sheet: Sheet, styles: Styles.StyleBundle, rowIdx: Int, values: Seq[Option[Any]]): Unit =
        val row = sheet.createRow(rowIdx)
        for ((vOpt, colIdx) <- values.zipWithIndex) do
            vOpt.foreach: v =>
                val cell = row.createCell(colIdx)
                v match
                    case s: String => cell.setCellValue(s)
                    case d: Double => cell.setCellValue(d)
                    case i: Int    => cell.setCellValue(i.toDouble)
                    case _ => cell.setCellValue(v.toString)
                cell.setCellStyle(styles.example)

    /** Add a dropdown data validation to a column range. */
    def addDropdown(sheet: Sheet, firstRow: Int, lastRow: Int, col: Int, options: Seq[String]): Unit =
        val dvHelper    = sheet.getDataValidationHelper
        val constraint  = dvHelper.createExplicitListConstraint(options.toArray)
        val addressList = new org.apache.poi.ss.util.CellRangeAddressList(firstRow, lastRow, col, col)
        val dv          = dvHelper.createValidation(constraint, addressList)
        dv.setShowErrorBox     (true)
        dv.createErrorBox      (
            "Valeur invalide / Invalid value",
            "Veuillez choisir une valeur dans la liste / Please choose from the list"
        )
        sheet.addValidationData(dv  )

    /** Add a cell comment. */
    def addComment(sheet: Sheet, rowIdx: Int, colIdx: Int, text: String): Unit =
        val row     = Option(sheet.getRow(rowIdx)).getOrElse(sheet.createRow(rowIdx))
        val cell    = Option(row.getCell(colIdx) ).getOrElse(row.createCell(colIdx) )
        val factory = sheet.getWorkbook.getCreationHelper
        val anchor  = factory.createClientAnchor()
        anchor.setCol1(colIdx    )
        anchor.setCol2(colIdx + 3)
        anchor.setRow1(rowIdx    )
        anchor.setRow2(rowIdx + 4)
        val drawing = sheet.createDrawingPatriarch()
        val comment = drawing.createCellComment(anchor)
        comment.setString  (factory.createRichTextString(text))
        comment.setAuthor  ("FireCalc Template"               )
        cell.setCellComment(comment                           )

    /** Write a section header row in a form layout (merged across all columns). */
    def writeSection(sheet: Sheet, styles: Styles.StyleBundle, rowIdx: Int, title: String, maxCol: Int): Unit =
        val row  = sheet.createRow(rowIdx)
        val cell = row.createCell(0)
        cell.setCellValue(title         )
        cell.setCellStyle(styles.section)
        if maxCol > 1 then sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, maxCol - 1))

    /**
     * Write a form field row: merged label (A:B), unit (C), value (D).
     * Returns the cell reference string for the value cell (e.g. "D7").
     */
    def writeFormField(
        sheet   : Sheet,
        styles  : Styles.StyleBundle,
        rowIdx  : Int,
        labelFr : String,
        labelEn : String,
        unit    : String,
        value   : Option[Any] = None,
        optional: Boolean     = false
    ): String =
        val row       = sheet.createRow(rowIdx)
        // Label (merged A:B)
        val cellLabel = row.createCell(0)
        cellLabel.setCellValue        (s"$labelFr ($labelEn)"                                         )
        cellLabel.setCellStyle        (if optional then styles.formLabelOptional else styles.formLabel)
        row.createCell(1).setCellStyle(styles.thinBorder                                              ) // empty merged cell still needs border
        sheet.addMergedRegion         (new CellRangeAddress(rowIdx, rowIdx, 0, 1)                     )
        // Unit (C)
        val cellUnit = row.createCell(2)
        cellUnit.setCellValue(unit          )
        cellUnit.setCellStyle(styles.unitRow)
        // Value (D)
        val cellVal = row.createCell(3)
        cellVal.setCellStyle(styles.thinBorder)
        value.foreach:
            case s: String => cellVal.setCellValue(s); cellVal.setCellStyle(styles.example)
            case d: Double => cellVal.setCellValue(d); cellVal.setCellStyle(styles.example)
            case i: Int    => cellVal.setCellValue(i.toDouble); cellVal.setCellStyle(styles.example)
            case v => cellVal.setCellValue(v.toString); cellVal.setCellStyle(styles.example)
        s"D${rowIdx + 1}" // 1-based for POI cell reference (but rowIdx is 0-based in our code)

    /** Set column widths (in characters). */
    def setColumnWidths(sheet: Sheet, widths: Seq[Int]): Unit =
        for ((w, colIdx) <- widths.zipWithIndex) do
            sheet.setColumnWidth(colIdx, w * 256) // POI uses 1/256th of character width
