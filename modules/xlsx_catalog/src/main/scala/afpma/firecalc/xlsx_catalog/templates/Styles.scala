/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.templates

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/** Shared POI styles matching the template design (bilingual headers, colors, borders). */
object Styles:

    // Color constants (RGB bytes)
    private val DARK_BLUE      = Array[Byte](0x1F.toByte, 0x4E.toByte, 0x79.toByte)
    private val LIGHT_BLUE     = Array[Byte](0xD6.toByte, 0xE4.toByte, 0xF0.toByte)
    private val VERY_LIGHT_GRAY = Array[Byte](0xF2.toByte, 0xF2.toByte, 0xF2.toByte)
    private val YELLOW_HIGHLIGHT = Array[Byte](0xFF.toByte, 0xF2.toByte, 0xCC.toByte)
    private val WHITE          = Array[Byte](0xFF.toByte, 0xFF.toByte, 0xFF.toByte)
    private val GRAY_80        = Array[Byte](0x80.toByte, 0x80.toByte, 0x80.toByte)
    private val GRAY_99        = Array[Byte](0x99.toByte, 0x99.toByte, 0x99.toByte)
    private val GRAY_4D        = Array[Byte](0x4D.toByte, 0x4D.toByte, 0x4D.toByte)
    private val GRAY_66        = Array[Byte](0x66.toByte, 0x66.toByte, 0x66.toByte)
    private def xColor(rgb: Array[Byte]) = new XSSFColor(rgb, null)

    /** Bundle of pre-built cell styles for a workbook. Create once per workbook via `Styles.create(wb)`. */
    case class StyleBundle(
        frHeader: CellStyle,
        enHeader: CellStyle,
        unitRow: CellStyle,
        example: CellStyle,
        section: CellStyle,
        formLabel: CellStyle,
        formLabelOptional: CellStyle,
        title: CellStyle,
        yellowFill: CellStyle,
        lockedGray: CellStyle,
        thinBorder: CellStyle,
        centeredBorder: CellStyle,
    )

    def create(wb: XSSFWorkbook): StyleBundle =
        def font(bold: Boolean = false, italic: Boolean = false, color: Array[Byte] = null, size: Short = 11): XSSFFont =
            val f = wb.createFont().asInstanceOf[XSSFFont]
            f.setBold(bold)
            f.setItalic(italic)
            if color != null then f.setColor(xColor(color))
            f.setFontHeightInPoints(size)
            f

        def style(
            f: XSSFFont = null,
            fill: Array[Byte] = null,
            hAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            vAlign: VerticalAlignment = VerticalAlignment.CENTER,
            wrap: Boolean = true,
            border: Boolean = true,
        ): CellStyle =
            val s = wb.createCellStyle()
            if f != null then s.setFont(f)
            if fill != null then
                s.asInstanceOf[XSSFCellStyle].setFillForegroundColor(xColor(fill))
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            s.setAlignment(hAlign)
            s.setVerticalAlignment(vAlign)
            s.setWrapText(wrap)
            if border then
                val thin = BorderStyle.THIN
                s.setBorderLeft(thin); s.setBorderRight(thin)
                s.setBorderTop(thin); s.setBorderBottom(thin)
                val c = IndexedColors.GREY_25_PERCENT.getIndex
                s.setLeftBorderColor(c); s.setRightBorderColor(c)
                s.setTopBorderColor(c); s.setBottomBorderColor(c)
            s

        StyleBundle(
            frHeader       = style(font(bold = true, color = WHITE), fill = DARK_BLUE, hAlign = HorizontalAlignment.CENTER),
            enHeader       = style(font(bold = true, italic = true, color = GRAY_4D, size = 10), fill = LIGHT_BLUE, hAlign = HorizontalAlignment.CENTER),
            unitRow        = style(font(color = GRAY_80, size = 10), fill = VERY_LIGHT_GRAY, hAlign = HorizontalAlignment.CENTER),
            example        = style(font(italic = true, color = GRAY_99, size = 10)),
            section        = style(font(bold = true, color = DARK_BLUE, size = 12), fill = LIGHT_BLUE),
            formLabel      = style(font(size = 11)),
            formLabelOptional = style(font(italic = true, color = GRAY_66, size = 11)),
            title          = style(font(bold = true, color = DARK_BLUE, size = 14), hAlign = HorizontalAlignment.CENTER, border = false),
            yellowFill     = style(font(bold = true, color = DARK_BLUE), fill = YELLOW_HIGHLIGHT, hAlign = HorizontalAlignment.CENTER),
            lockedGray     = style(font(bold = true), fill = VERY_LIGHT_GRAY, hAlign = HorizontalAlignment.CENTER),
            thinBorder     = style(),
            centeredBorder = style(hAlign = HorizontalAlignment.CENTER),
        )
