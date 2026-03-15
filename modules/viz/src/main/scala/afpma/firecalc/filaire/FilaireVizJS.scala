/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.filaire

import scala.scalajs.js

/** Non-native JS trait facades mirroring the TypeScript interfaces in filaire-viz.ts.
  * These are Scala.js-defined JS types that allow type-safe construction of JS objects.
  */

/** Origin of a pipe in 3D space */
trait PipeOriginJS extends js.Object:
  var x: Double
  var y: Double
  var z: Double

object PipeOriginJS:
  def apply(x: Double, y: Double, z: Double): PipeOriginJS =
    js.Dynamic.literal(
      x = x,
      y = y,
      z = z
    ).asInstanceOf[PipeOriginJS]

/** Direction vector of a pipe */
trait PipeDirectionJS extends js.Object:
  var dx: Double
  var dy: Double
  var dz: Double

object PipeDirectionJS:
  def apply(dx: Double, dy: Double, dz: Double): PipeDirectionJS =
    js.Dynamic.literal(
      dx = dx,
      dy = dy,
      dz = dz
    ).asInstanceOf[PipeDirectionJS]

/** Cross-section shape of a pipe */
trait PipeShapeJS extends js.Object:
  var `type`: String
  // Optional fields depending on type
  var side: js.UndefOr[Double]
  var width: js.UndefOr[Double]
  var height: js.UndefOr[Double]
  var diameter: js.UndefOr[Double]

object PipeShapeJS:
  def square(side: Double): PipeShapeJS =
    js.Dynamic.literal(
      `type` = "square",
      side = side
    ).asInstanceOf[PipeShapeJS]

  def rectangle(width: Double, height: Double): PipeShapeJS =
    js.Dynamic.literal(
      `type` = "rectangle",
      width = width,
      height = height
    ).asInstanceOf[PipeShapeJS]

  def circle(diameter: Double): PipeShapeJS =
    js.Dynamic.literal(
      `type` = "circle",
      diameter = diameter
    ).asInstanceOf[PipeShapeJS]

/** Complete pipe data for visualization */
trait PipeDataJS extends js.Object:
  var origin: PipeOriginJS
  var direction: PipeDirectionJS
  var length: Double
  var color: String
  var shape: PipeShapeJS
  var shapeOrientation: js.UndefOr[Double]
  var lineIndex: Int
  var name: js.UndefOr[String]

object PipeDataJS:
  def apply(
    origin: PipeOriginJS,
    direction: PipeDirectionJS,
    length: Double,
    color: String,
    shape: PipeShapeJS,
    shapeOrientation: js.UndefOr[Double],
    lineIndex: Int,
    name: js.UndefOr[String]
  ): PipeDataJS =
    js.Dynamic.literal(
      origin = origin,
      direction = direction,
      length = length,
      color = color,
      shape = shape,
      shapeOrientation = shapeOrientation,
      lineIndex = lineIndex,
      name = name
    ).asInstanceOf[PipeDataJS]

/** A group of spatially-connected pipes (miter joints only within the group) */
trait PipeGroupJS extends js.Object:
  var pipes: js.Array[PipeDataJS]
  var name: js.UndefOr[String]

object PipeGroupJS:
  def apply(
    pipes: js.Array[PipeDataJS],
    name: js.UndefOr[String] = js.undefined
  ): PipeGroupJS =
    js.Dynamic.literal(pipes = pipes, name = name).asInstanceOf[PipeGroupJS]

/** Visualization configuration */
trait VizConfigJS extends js.Object:
  var canvasWidth: Int
  var canvasHeight: Int
  var shapeColor: String
  var hoverColor: String
  var backgroundColor: String
  var displayType: String
  var viewPadding: js.UndefOr[Double]
  var mixedShapeOpacity: js.UndefOr[Double]
  var centerLineSphereRadius: js.UndefOr[Double]
  var centerLineStrokeWidth: js.UndefOr[Double]
  var displayName: js.UndefOr[Boolean]
  var displayNameInModes: js.UndefOr[js.Array[String]]
  var nameVerticalOffset: js.UndefOr[Double]
  var watermark: js.UndefOr[String]
  var _cameraState: js.UndefOr[CameraStateJS]

object VizConfigJS:
  def apply(
    canvasWidth: Int,
    canvasHeight: Int,
    shapeColor: String,
    hoverColor: String,
    backgroundColor: String,
    displayType: String,
    viewPadding: js.UndefOr[Double] = js.undefined,
    mixedShapeOpacity: js.UndefOr[Double] = js.undefined,
    centerLineSphereRadius: js.UndefOr[Double] = js.undefined,
    centerLineStrokeWidth: js.UndefOr[Double] = js.undefined,
    displayName: js.UndefOr[Boolean] = js.undefined,
    displayNameInModes: js.UndefOr[js.Array[String]] = js.undefined,
    nameVerticalOffset: js.UndefOr[Double] = js.undefined,
    watermark: js.UndefOr[String] = js.undefined,
    _cameraState: js.UndefOr[CameraStateJS] = js.undefined
  ): VizConfigJS =
    js.Dynamic.literal(
      canvasWidth = canvasWidth,
      canvasHeight = canvasHeight,
      shapeColor = shapeColor,
      hoverColor = hoverColor,
      backgroundColor = backgroundColor,
      displayType = displayType,
      viewPadding = viewPadding,
      mixedShapeOpacity = mixedShapeOpacity,
      centerLineSphereRadius = centerLineSphereRadius,
      centerLineStrokeWidth = centerLineStrokeWidth,
      displayName = displayName,
      displayNameInModes = displayNameInModes,
      nameVerticalOffset = nameVerticalOffset,
      watermark = watermark,
      _cameraState = _cameraState
    ).asInstanceOf[VizConfigJS]

/** Camera state returned by getCameraState() */
@js.native
trait CameraStateJS extends js.Object:
    val position: js.Array[Double] = js.native
    val up: js.Array[Double] = js.native
    val target: js.Array[Double] = js.native

/** Handle returned by initFilaireViz for lifecycle management */
@js.native
trait FilaireVizHandleJS extends js.Object:
  def dispose(): Unit = js.native
  def getCameraState(): js.UndefOr[CameraStateJS] = js.native
