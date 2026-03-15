/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.filaire

/** Display type for pipe rendering */
enum DisplayType:
  case CenterLine
  case FullShape
  case Mixed

/** Configuration for the Filaire 3D visualization */
case class FilaireVizConfig(
  canvasWidth: Int = 750,
  canvasHeight: Int = 600,
  shapeColor: String = "#FF6600",
  hoverColor: String = "#FFAA44",
  backgroundColor: String = "#F0F0F0",
  viewPadding: Double = 1.5,
  mixedShapeOpacity: Double = 0.3,
  centerLineSphereRadius: Double = 1.0,
  centerLineStrokeWidth: Double = 2.0,
  displayName: Boolean = false,
  displayNameInModes: List[DisplayType] = List(DisplayType.CenterLine),
  nameVerticalOffset: Double = 3.0,
  watermark: Option[String] = Some("FireCalc AFPMA ©"),
  _cameraState: Option[CameraStateJS] = None
)
