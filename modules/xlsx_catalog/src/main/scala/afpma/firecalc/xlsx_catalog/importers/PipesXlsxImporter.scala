/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.importers

import java.nio.file.Path

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import coulomb.*
import coulomb.syntax.*

import afpma.firecalc.xlsx_catalog.PoiHelpers.*
import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.PipeCols

object PipesXlsxImporter:

    def read(path: Path): Seq[SetThermalPipeProp_13384.SetPropertiesInBatch] =
        val wb = openWorkbook(path)
        try
            val sheet = wb.getSheetAt(0)
            val pictures = readPicturesByRow(wb, 0, PipeCols.Image)
            val rows = (3 to sheet.getLastRowNum).flatMap: rowIdx =>
                Option(sheet.getRow(rowIdx)).flatMap(parseRow(_, pictures.get(rowIdx)))
            rows
        finally wb.close()

    private def parseRow(row: org.apache.poi.ss.usermodel.Row, image: Option[String]): Option[SetThermalPipeProp_13384.SetPropertiesInBatch] =
        readString(row, PipeCols.BatchName).map: batchName =>
            val props = scala.collection.mutable.ListBuffer[SetThermalPipeProp_13384.SetSingleProp]()

            // Material
            readString(row, PipeCols.Material).foreach: matName =>
                val roughnessOverride = readDouble(row, PipeCols.Roughness)
                val material = parseMaterial(matName, roughnessOverride)
                props += SetThermalPipeProp_13384.SetMaterial(material)

            // Inner shape
            readString(row, PipeCols.InnerShape).foreach: shapeName =>
                val dim1 = readDouble(row, PipeCols.Dim1)
                val dim2 = readDouble(row, PipeCols.Dim2)
                parseShape(shapeName, dim1, dim2).foreach: shape =>
                    props += SetThermalPipeProp_13384.SetInnerShape(shape)

            // Layers (up to 3)
            val layerOffsets = Seq(
                (PipeCols.Layer1Thick, PipeCols.Layer1Type, PipeCols.Layer1Value),
                (PipeCols.Layer2Thick, PipeCols.Layer2Type, PipeCols.Layer2Value),
                (PipeCols.Layer3Thick, PipeCols.Layer3Type, PipeCols.Layer3Value),
            )
            val layers = layerOffsets.flatMap: (thickCol, typeCol, valueCol) =>
                parseLayer(row, thickCol, typeCol, valueCol)

            if layers.nonEmpty then
                props += SetThermalPipeProp_13384.SetLayers(layers.toList)

            SetThermalPipeProp_13384.SetPropertiesInBatch(batchName, props.toSeq, image = image)

    private[importers] def parseMaterial(name: String, roughnessOverride: Option[Double]): Material_13384 =
        val rough = roughnessOverride.map(_.withUnit[Meter])
        name match
            case "WeldedSteel"    => rough.fold(Material_13384.WeldedSteel())(r => Material_13384.WeldedSteel(r))
            case "Glass"          => rough.fold(Material_13384.Glass())(r => Material_13384.Glass(r))
            case "Plastic"        => rough.fold(Material_13384.Plastic())(r => Material_13384.Plastic(r))
            case "Aluminium"      => rough.fold(Material_13384.Aluminium())(r => Material_13384.Aluminium(r))
            case "ClayFlueLiners" => rough.fold(Material_13384.ClayFlueLiners())(r => Material_13384.ClayFlueLiners(r))
            case "Bricks"         => rough.fold(Material_13384.Bricks())(r => Material_13384.Bricks(r))
            case "SolderedMetal"  => rough.fold(Material_13384.SolderedMetal())(r => Material_13384.SolderedMetal(r))
            case "Concrete"       => rough.fold(Material_13384.Concrete())(r => Material_13384.Concrete(r))
            case "Fibrociment"    => rough.fold(Material_13384.Fibrociment())(r => Material_13384.Fibrociment(r))
            case "Masonry"        => rough.fold(Material_13384.Masonry())(r => Material_13384.Masonry(r))
            case "CorrugatedMetal" => rough.fold(Material_13384.CorrugatedMetal())(r => Material_13384.CorrugatedMetal(r))
            case other => throw IllegalArgumentException(s"Unknown material: $other")

    private[importers] def parseShape(name: String, dim1: Option[Double], dim2: Option[Double]): Option[PipeShape] =
        name match
            case "Circle"    => dim1.map(d => PipeShape.Circle(d.withUnit[Meter]))
            case "Square"    => dim1.map(d => PipeShape.Square(d.withUnit[Meter]))
            case "Rectangle" =>
                for d1 <- dim1; d2 <- dim2
                yield PipeShape.Rectangle(d1.withUnit[Meter], d2.withUnit[Meter])
            case _ => None

    private def parseLayer(
        row: org.apache.poi.ss.usermodel.Row,
        thickCol: Int, typeCol: Int, valueCol: Int,
    ): Option[AppendLayerDescr] =
        for
            thickness <- readDouble(row, thickCol)
            thermalType <- readString(row, typeCol)
            thermalValue <- readDouble(row, valueCol)
        yield thermalType match
            case "Rth" =>
                AppendLayerDescr.FromThermalResistanceUsingThickness(
                    thickness.withUnit[Meter],
                    thermalValue.withUnit[(Meter ^ 2) * Kelvin / Watt],
                )
            case "lambda" =>
                AppendLayerDescr.FromLambdaUsingThickness(
                    thickness.withUnit[Meter],
                    thermalValue.withUnit[Watt / (Meter * Kelvin)],
                )
            case other => throw IllegalArgumentException(s"Unknown thermal type: $other (expected Rth or lambda)")
