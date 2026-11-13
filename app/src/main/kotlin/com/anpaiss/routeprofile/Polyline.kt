package com.anpaiss.routeprofile

/**
 * Google encoded polylines as the Karoo sends them (karoo-ext 1.1.9, same decoding as BetterETA):
 *  - routePolyline: (lat, lon) pairs, precision 5
 *  - routeElevationPolyline: (distance from the start, elevation) pairs in metres, precision 1
 * A truncated string returns what was decoded so far; a blank one an empty list.
 */
object Polyline {

    fun latLng(encoded: String): List<Pair<Double, Double>> = pairs(encoded, 1e5)

    fun distanceElevation(encoded: String): List<Pair<Double, Double>> = pairs(encoded, 10.0)

    private fun pairs(encoded: String, scale: Double): List<Pair<Double, Double>> {
        if (encoded.isBlank()) return emptyList()
        val out = ArrayList<Pair<Double, Double>>()
        val idx = intArrayOf(0)
        var a = 0
        var b = 0
        try {
            while (idx[0] < encoded.length) {
                a += next(encoded, idx)
                if (idx[0] >= encoded.length) break
                b += next(encoded, idx)
                out.add(a / scale to b / scale)
            }
        } catch (_: Exception) { /* truncated */ }
        return out
    }

    private fun next(s: String, idx: IntArray): Int {
        var result = 0
        var shift = 0
        var c: Int
        do {
            c = s[idx[0]++].code - 63
            result = result or ((c and 0x1f) shl shift)
            shift += 5
        } while (c >= 0x20 && idx[0] < s.length)
        return if (result and 1 != 0) (result shr 1).inv() else result shr 1
    }

    /** Encoder, for the tests and the sample route. */
    fun encode(points: List<Pair<Double, Double>>, scale: Double): String {
        val sb = StringBuilder()
        var pa = 0
        var pb = 0
        fun put(v: Int) {
            var x = if (v < 0) (v shl 1).inv() else v shl 1
            while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f)) + 63).toChar()); x = x shr 5 }
            sb.append((x + 63).toChar())
        }
        for ((a, b) in points) {
            val ia = Math.round(a * scale).toInt()
            val ib = Math.round(b * scale).toInt()
            put(ia - pa); put(ib - pb)
            pa = ia; pb = ib
        }
        return sb.toString()
    }
}
