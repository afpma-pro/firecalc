/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.filaire

/** Opaque types for FireCalc Filaire domain model
  * All units are in centimeters (cm)
  * Coordinate system: Z-up, Y-rear (common in construction)
  */
object FilaireTypes:

  /** Distance in centimeters */
  opaque type Cm = Double
  object Cm:
    def apply(value: Double): Cm = value
    extension (cm: Cm)
      def value: Double = cm
      def +(other: Cm): Cm = cm + other
      def -(other: Cm): Cm = cm - other
      def *(factor: Double): Cm = cm * factor

  /** 3D origin point in construction coordinates (Z-up, Y-rear) */
  opaque type Origin = (Cm, Cm, Cm)
  object Origin:
    @scala.annotation.targetName("applyCm")
    def apply(x: Cm, y: Cm, z: Cm): Origin = (x, y, z)
    def apply(x: Double, y: Double, z: Double): Origin =
      (Cm(x), Cm(y), Cm(z))
    extension (o: Origin)
      def x: Cm = o._1
      def y: Cm = o._2
      def z: Cm = o._3

  /** Direction vector (not necessarily normalized) */
  opaque type Vector = (Double, Double, Double)
  object Vector:
    def apply(dx: Double, dy: Double, dz: Double): Vector = (dx, dy, dz)
    extension (v: Vector)
      def dx: Double = v._1
      def dy: Double = v._2
      def dz: Double = v._3
      def dot(other: Vector): Double = v._1 * other._1 + v._2 * other._2 + v._3 * other._3
      def cross(other: Vector): Vector =
        (v._2 * other._3 - v._3 * other._2,
         v._3 * other._1 - v._1 * other._3,
         v._1 * other._2 - v._2 * other._1)
      def +(other: Vector): Vector = (v._1 + other._1, v._2 + other._2, v._3 + other._3)
      def -(other: Vector): Vector = (v._1 - other._1, v._2 - other._2, v._3 - other._3)
      def *(scalar: Double): Vector = (v._1 * scalar, v._2 * scalar, v._3 * scalar)
      def normalized: Vector =
        val mag = math.sqrt(v._1 * v._1 + v._2 * v._2 + v._3 * v._3)
        if mag == 0 then (0.0, 0.0, 0.0) else (v._1 / mag, v._2 / mag, v._3 / mag)

  /** Length of a line segment in centimeters */
  opaque type Length = Double
  object Length:
    def apply(value: Double): Length = value
    extension (l: Length)
      def value: Double = l

  /** CSS color string for line rendering */
  opaque type LineColor = String
  object LineColor:
    def apply(hex: String): LineColor = hex
    val Orange: LineColor = "#E62"
    val Garnet: LineColor = "#C25"
    val Eggplant: LineColor = "#636"
    val Gold: LineColor = "#EA0"
    val Blue: LineColor = "#19F"
    val Red: LineColor = "#BC2132"
    val OrangeYellow: LineColor = "#F59331"
    val Yellow: LineColor = "#FFDC38"
    val Brown: LineColor = "#3B2416"
    extension (c: LineColor)
      def value: String = c

  /** Rotation angle for cross-section orientation around pipe axis in degrees */
  opaque type ShapeOrientation = Double
  object ShapeOrientation:
    def apply(degrees: Double): ShapeOrientation = degrees
    def default: ShapeOrientation = ShapeOrientation(0.0)
    extension (o: ShapeOrientation)
      def degrees: Double = o

  /** Cross-section shape for 3D rendering */
  sealed trait CrossSection
  object CrossSection:
    // Import extension methods for use in this object
    import Vector.*
    import Cm.*
    import ShapeOrientation.*

    /** Square cross-section with equal sides */
    case class Square(side: Cm) extends CrossSection
    /** Rectangular cross-section with width and height */
    case class Rectangle(width: Cm, height: Cm) extends CrossSection
    /** Circular cross-section with diameter */
    case class Circle(diameter: Cm) extends CrossSection

    /** Epsilon for floating-point comparison when checking axis alignment */
    private val AxisEpsilon = 1e-9

    /** Returns the cross-section extent along `queryAxis`, given the pipe runs along `pipeDirection`.
      *
      * For axis-aligned pipe directions, the cross-section's local width/height map to construction axes:
      *   - Pipe ±X (Right/Left): width → Y (Rear), height → Z (Up)
      *   - Pipe ±Y (Rear/Front): width → X (Right), height → Z (Up)
      *   - Pipe ±Z (Up/Down): width → X (Right), height → Y (Rear)
      *
      * When orientation ≠ 0°, the rectangle is rotated around the pipe axis, causing
      * the bounding extent to change: extent = |w·cos(θ)| + |h·sin(θ)| or |w·sin(θ)| + |h·cos(θ)|
      * depending on which perpendicular axis is being queried.
      *
      * @param shape
      *   The cross-section shape
      * @param pipeDirection
      *   The normalized direction vector along which the pipe runs
      * @param queryAxis
      *   The normalized axis vector to query the extent along
      * @param orientation
      *   The rotation angle of the cross-section around the pipe axis (in degrees)
      * @return
      *   The extent of the cross-section along the query axis in centimeters
      */
    def crossSectionSizeAlongAxis(
        shape: CrossSection,
        pipeDirection: Vector,
        queryAxis: Vector,
        orientation: ShapeOrientation = ShapeOrientation.default
    ): Cm =
      // Normalize inputs for consistent comparison
      val normPipe = pipeDirection.normalized
      val normQuery = queryAxis.normalized

      shape match
        case Circle(diameter) => 
          // Circle size is invariant under rotation
          diameter
        case Square(side)     => 
          // Square bounding box is invariant under rotation around its center
          side
        case Rectangle(width, height) =>
          // Convert orientation to radians
          val thetaRad = math.toRadians(orientation.degrees)
          val cos = math.abs(math.cos(thetaRad))
          val sin = math.abs(math.sin(thetaRad))
          
          // Determine which dimension corresponds to the query axis
          // based on the pipe direction and axis mapping
          val queryDim = determineQueryDimension(normPipe, normQuery)
          
          // Apply rotation formula: rotated bounding extent is a mix of width and height
          queryDim match
            case Dimension.Width  => Cm(width.value * cos + height.value * sin)
            case Dimension.Height => Cm(width.value * sin + height.value * cos)

    /** Internal enum to track which dimension of a rectangle we're querying */
    private enum Dimension:
      case Width, Height

    /** Determines which dimension (width or height) corresponds to the query axis.
      *
      * Axis mapping table:
      * | Pipe direction | width maps to | height maps to |
      * |----------------|---------------|----------------|
      * | ±X (Right/Left)| Y (Rear)      | Z (Up)         |
      * | ±Y (Rear/Front)| X (Right)     | Z (Up)         |
      * | ±Z (Up/Down)   | X (Right)     | Y (Rear)       |
      */
    private def determineQueryDimension(pipeDir: Vector, queryAxis: Vector): Dimension =
      // Check which axis the pipe runs along
      val isPipeX = math.abs(pipeDir.dx) > 1.0 - AxisEpsilon
      val isPipeY = math.abs(pipeDir.dy) > 1.0 - AxisEpsilon
      val isPipeZ = math.abs(pipeDir.dz) > 1.0 - AxisEpsilon

      // Check which axis we're querying
      val isQueryX = math.abs(queryAxis.dx) > 1.0 - AxisEpsilon
      val isQueryY = math.abs(queryAxis.dy) > 1.0 - AxisEpsilon
      val isQueryZ = math.abs(queryAxis.dz) > 1.0 - AxisEpsilon

      if isPipeX then
        // Pipe along X: width → Y, height → Z
        if isQueryY then Dimension.Width
        else if isQueryZ then Dimension.Height
        else Dimension.Width // fallback
      else if isPipeY then
        // Pipe along Y: width → X, height → Z
        if isQueryX then Dimension.Width
        else if isQueryZ then Dimension.Height
        else Dimension.Width // fallback
      else if isPipeZ then
        // Pipe along Z: width → X, height → Y
        if isQueryX then Dimension.Width
        else if isQueryY then Dimension.Height
        else Dimension.Width // fallback
      else
        // Non-axis-aligned pipe direction - default to width
        Dimension.Width

  /** A single line segment with origin, direction, length, color, and shape */
  case class FireCalcFilaireLine(
      origin: Origin,
      direction: Vector,
      length: Length,
      color: LineColor = LineColor.Orange,
      shape: CrossSection,
      shapeOrientation: ShapeOrientation = ShapeOrientation.default,
      name: Option[String] = None,
      displayName: Option[String] = None,
      onClick: Option[FireCalcFilaireLine => Unit] = None
  )

  /** Type alias for a collection of lines */
  type FireCalcFilaireLines = List[FireCalcFilaireLine]

  /** A group of spatially-connected pipe lines. Miter joints only form between
    * consecutive pipes within the same group — group boundaries get flat ends.
    */
  case class FireCalcFilaireGroup(
      lines: FireCalcFilaireLines,
      name: Option[String] = None
  )

  /** Type alias for a collection of pipe groups */
  type FireCalcFilaireGroups = List[FireCalcFilaireGroup]

end FilaireTypes
