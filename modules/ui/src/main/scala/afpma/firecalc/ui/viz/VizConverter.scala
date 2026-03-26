/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import afpma.firecalc.engine.models.geometry.{PipeSegmentPosition, PipePositionResult, Vec3}
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.filaire.FilaireTypes.*
import afpma.firecalc.filaire.FilaireTypes.CrossSection
import afpma.firecalc.ui.AIR_DISTRIB_HEIGHT_M

object VizConverter:

  private val M_TO_CM = 100.0

  def shapeToCS(shape: PipeShape): CrossSection = shape match
    case PipeShape.Circle(d)       => CrossSection.Circle(Cm(d.value * M_TO_CM))
    case PipeShape.Square(s)       => CrossSection.Square(Cm(s.value * M_TO_CM))
    case PipeShape.Rectangle(a, b) => CrossSection.Rectangle(Cm(a.value * M_TO_CM), Cm(b.value * M_TO_CM))

  def vec3ToOrigin(v: Vec3): Origin =
    Origin(v.x * M_TO_CM, v.y * M_TO_CM, v.z * M_TO_CM)

  def vec3ToVector(v: Vec3): Vector =
    Vector(v.x, v.y, v.z)

  def segmentToLine(seg: PipeSegmentPosition, color: LineColor, pipeName: String, displayPipeName: Option[String] = None): FireCalcFilaireLine =
    FireCalcFilaireLine(
      origin      = vec3ToOrigin(seg.startPoint),
      direction   = vec3ToVector(seg.direction),
      length      = Length(seg.length * M_TO_CM),
      color       = color,
      shape       = seg.innerShape.map(shapeToCS).getOrElse(CrossSection.Circle(Cm(15.0))),
      name        = Some(s"$pipeName #${seg.elementIndex}"),
      displayName = displayPipeName.map(dn => s"$dn #${seg.elementIndex}")
    )

  def pipeToLines(result: PipePositionResult, color: LineColor, pipeName: String, displayPipeName: Option[String] = None): List[FireCalcFilaireLine] =
    result.segments.toList.map(segmentToLine(_, color, pipeName, displayPipeName))

  val FlueColor      : LineColor = LineColor.Orange
  val ConnectorColor : LineColor = LineColor.OrangeYellow
  val ChimneyColor   : LineColor = LineColor.Yellow
  val AirIntakeColor  : LineColor = LineColor.Blue
  val AirDistribColor : LineColor = LineColor("#58B8FF")
  val FireboxColor    : LineColor = LineColor.Red

  /** Build a single FireCalcFilaireLine representing the firebox as a rectangular box.
    * @param widthCm  firebox width in cm (Left-Right axis)
    * @param depthCm  firebox depth in cm (Front-Rear axis)
    * @param heightCm firebox height in cm (Down-Up axis)
    */
  def fireboxToLine(widthCm: Double, depthCm: Double, heightCm: Double, displayName: Option[String] = None): FireCalcFilaireLine =
    FireCalcFilaireLine(
      origin      = Origin(0.0, 0.0, 0.0),
      direction   = Vector(0.0, 0.0, 1.0),
      length      = Length(heightCm),
      color       = FireboxColor,
      shape       = CrossSection.Rectangle(Cm(depthCm), Cm(widthCm)),
      name        = Some("Firebox"),
      displayName = displayName
    )

  /** Build a single FireCalcFilaireLine representing the air distribution box.
    * Same width/depth as the firebox, positioned directly below it.
    * @param widthCm  firebox width in cm (Left-Right axis)
    * @param depthCm  firebox depth in cm (Front-Rear axis)
    */
  def airDistribToLine(widthCm: Double, depthCm: Double, displayName: Option[String] = None): FireCalcFilaireLine =
    val heightCm = AIR_DISTRIB_HEIGHT_M * 100.0
    FireCalcFilaireLine(
      origin      = Origin(0.0, 0.0, -heightCm),
      direction   = Vector(0.0, 0.0, 1.0),
      length      = Length(heightCm),
      color       = AirDistribColor,
      shape       = CrossSection.Rectangle(Cm(depthCm), Cm(widthCm)),
      name        = Some("Air Distribution"),
      displayName = displayName
    )

  case class PipeDisplayNames(
      flue          : String,
      connector     : String,
      chimney       : String,
      airIntake     : String,
      firebox       : String,
      airDistribution: String
  )

  def allPipesToGroups(
      flue          : PipePositionResult,
      connector     : PipePositionResult,
      chimney       : PipePositionResult,
      airIntake     : PipePositionResult,
      fireboxLine   : FireCalcFilaireLine,
      airDistribLine: FireCalcFilaireLine,
      displayNames  : Option[PipeDisplayNames] = None
  ): FireCalcFilaireGroups =
    List(
      FireCalcFilaireGroup(List(airDistribLine), Some("Air Distribution")),
      FireCalcFilaireGroup(List(fireboxLine), Some("Firebox")),
      FireCalcFilaireGroup(
        pipeToLines(flue, FlueColor, "Flue", displayNames.map(_.flue)) ++
        pipeToLines(connector, ConnectorColor, "Connector", displayNames.map(_.connector)) ++
        pipeToLines(chimney, ChimneyColor, "Chimney", displayNames.map(_.chimney)),
        Some("Exhaust")
      ),
      FireCalcFilaireGroup(
        pipeToLines(airIntake, AirIntakeColor, "Air Intake", displayNames.map(_.airIntake)),
        Some("Air Intake")
      )
    )
