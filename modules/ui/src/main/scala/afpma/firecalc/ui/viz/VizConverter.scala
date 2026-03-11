/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import afpma.firecalc.engine.models.geometry.{PipeSegmentPosition, PipePositionResult, Vec3}
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.filaire.FilaireTypes.*
import afpma.firecalc.filaire.FilaireTypes.CrossSection

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

  def segmentToLine(seg: PipeSegmentPosition, color: LineColor, pipeName: String): FireCalcFilaireLine =
    FireCalcFilaireLine(
      origin    = vec3ToOrigin(seg.startPoint),
      direction = vec3ToVector(seg.direction),
      length    = Length(seg.length * M_TO_CM),
      color     = color,
      shape     = seg.innerShape.map(shapeToCS).getOrElse(CrossSection.Circle(Cm(15.0))),
      name      = Some(s"$pipeName #${seg.elementIndex}")
    )

  def pipeToLines(result: PipePositionResult, color: LineColor, pipeName: String): List[FireCalcFilaireLine] =
    result.segments.toList.map(segmentToLine(_, color, pipeName))

  val FlueColor      : LineColor = LineColor.Orange
  val ConnectorColor : LineColor = LineColor.Garnet
  val ChimneyColor   : LineColor = LineColor.Eggplant
  val AirIntakeColor : LineColor = LineColor.Blue

  def allPipesToLines(
    flue     : PipePositionResult,
    connector: PipePositionResult,
    chimney  : PipePositionResult,
    airIntake: PipePositionResult
  ): FireCalcFilaireLines =
    pipeToLines(flue, FlueColor, "Flue") ++
    pipeToLines(connector, ConnectorColor, "Connector") ++
    pipeToLines(chimney, ChimneyColor, "Chimney") ++
    pipeToLines(airIntake, AirIntakeColor, "Air Intake")
