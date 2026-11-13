package com.anpaiss.routeprofile

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Where the rider is along the route from a GPS fix, used when the Karoo gives no distance
 * to destination. Nearest point on the route line, searched first around the last position
 * (a loop passes near itself), then everywhere. Null when the fix is too far from the route.
 */
class Projection(points: List<Pair<Double, Double>>, routeLengthM: Double? = null) {

    private val lat = points.map { it.first }.toDoubleArray()
    private val lon = points.map { it.second }.toDoubleArray()
    private val cum = DoubleArray(points.size)
    /** Scale from our summed length to the Karoo's route length (the elevation axis). */
    private val scale: Double

    init {
        for (i in 1 until points.size) cum[i] = cum[i - 1] + metres(i - 1, i)
        val ours = if (points.isEmpty()) 0.0 else cum.last()
        scale = if (routeLengthM != null && routeLengthM > 0 && ours > 0) routeLengthM / ours else 1.0
    }

    private fun metres(i: Int, j: Int): Double {
        val kx = 111_320.0 * cos(Math.toRadians((lat[i] + lat[j]) / 2))
        val dx = (lon[j] - lon[i]) * kx
        val dy = (lat[j] - lat[i]) * 111_320.0
        return sqrt(dx * dx + dy * dy)
    }

    /** Along-route metres of the fix, or null if farther than [maxOffM] from the route. */
    fun along(fixLat: Double, fixLon: Double, hintM: Double? = null, maxOffM: Double = 80.0): Double? {
        if (lat.size < 2) return null
        if (hintM != null) {
            val h = hintM / scale
            val near = best(fixLat, fixLon, h - 1_500.0, h + 3_000.0)
            if (near != null && near.second <= maxOffM) return near.first * scale
        }
        val all = best(fixLat, fixLon, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY) ?: return null
        return if (all.second <= maxOffM) all.first * scale else null
    }

    /** (along, offset) of the nearest segment whose start lies in [fromM, toM]. */
    private fun best(fLat: Double, fLon: Double, fromM: Double, toM: Double): Pair<Double, Double>? {
        val kx = 111_320.0 * cos(Math.toRadians(fLat))
        var bestAlong = 0.0
        var bestOff = Double.MAX_VALUE
        var found = false
        for (i in 0 until lat.size - 1) {
            if (cum[i] < fromM || cum[i] > toM) continue
            val ax = (lon[i] - fLon) * kx; val ay = (lat[i] - fLat) * 111_320.0
            val bx = (lon[i + 1] - fLon) * kx; val by = (lat[i + 1] - fLat) * 111_320.0
            val vx = bx - ax; val vy = by - ay
            val len2 = vx * vx + vy * vy
            val t = if (len2 > 0) (-(ax * vx + ay * vy) / len2).coerceIn(0.0, 1.0) else 0.0
            val px = ax + t * vx; val py = ay + t * vy
            val off = sqrt(px * px + py * py)
            if (off < bestOff) {
                bestOff = off
                bestAlong = cum[i] + t * (cum[i + 1] - cum[i])
                found = true
            }
        }
        return if (found) bestAlong to bestOff else null
    }
}
