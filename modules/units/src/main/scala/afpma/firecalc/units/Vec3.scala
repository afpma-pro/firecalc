/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.units

/**
 * 3D unit vector (dimensionless Double components).
 * Coordinate system (facing the stove):
 *   +X = right, +Y = rear (away from you), +Z = up (gravity)
 */
case class Vec3(x: Double, y: Double, z: Double):
    def dot  (o: Vec3): Double = x * o.x + y * o.y + z * o.z
    def cross(o: Vec3): Vec3   = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    def norm      : Double = math.sqrt(x * x + y * y + z * z)
    def normalized: Vec3   =
        val n = norm
        if n < 1e-12 then Vec3(0, 0, 1) else Vec3(x / n, y / n, z / n)
    def *(s: Double): Vec3 = Vec3(x * s, y * s, z * s)
    def +(o: Vec3): Vec3 = Vec3(x + o.x, y + o.y, z + o.z)
    def -(o: Vec3): Vec3 = Vec3(x - o.x, y - o.y, z - o.z)
    def unary_- : Vec3 = Vec3(-x, -y, -z)

    /** Snap components near 0 or ±1 to exact values (IEEE 754 trig cleanup, ε = 1e-12). */
    def snap: Vec3 =
        def s(v: Double): Double =
            if math.abs(v) < 1e-12 then 0.0
            else if math.abs(v - 1.0) < 1e-12 then 1.0
            else if math.abs(v + 1.0) < 1e-12 then -1.0
            else v
        Vec3(s(x), s(y), s(z))

    /** Angle in degrees between this and another vector */
    def angleTo(o: Vec3): Double =
        val cos = (dot(o) / (norm * o.norm)).max(-1.0).min(1.0)
        math.toDegrees(math.acos(cos))

    /**
     * Convert to (azimuth, elevation) in degrees.
     * azimuth: angle in horizontal plane, 0°=rear(+Y), 90°=right(+X), clockwise
     * elevation: angle from horizontal, +90°=up(+Z), -90°=down
     */
    def toAzimuthElevation: (Double, Double) =
        val n         = normalized
        val elevation = math.toDegrees(math.asin(n.z.max(-1.0).min(1.0)))
        val azimuth   = math.toDegrees(math.atan2(n.x, n.y)) // atan2(East, North) = atan2(x, y)
        val azNorm    = ((azimuth % 360) + 360) % 360
        (azNorm, elevation)

object Vec3:
    // Use lazy val for Scala.js initialization order safety
    lazy val Up   : Vec3 = Vec3(0, 0, 1)
    lazy val Down : Vec3 = Vec3(0, 0, -1)
    lazy val Rear : Vec3 = Vec3(0, 1, 0)
    lazy val Front: Vec3 = Vec3(0, -1, 0)
    lazy val Right: Vec3 = Vec3(1, 0, 0)
    lazy val Left : Vec3 = Vec3(-1, 0, 0)

    /**
     * Create from azimuth + elevation in degrees.
     * azimuth 0°=rear(+Y), 90°=right(+X); elevation 0°=horizontal, 90°=up(+Z)
     */
    def fromAzimuthElevation(azimuth: Double, elevation: Double): Vec3 =
        val az = math.toRadians(azimuth)
        val el = math.toRadians(elevation)
        Vec3(
            x = math.sin(az) * math.cos(el),
            y = math.cos(az) * math.cos(el),
            z = math.sin(el)
        ).normalized.snap
