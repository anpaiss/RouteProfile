package com.anpaiss.routeprofile

/**
 * The route's climbs found on its own elevation, used until the Karoo sends its own: it does during the
 * ride (BetterETA's samples, rides of 18 and 24/10/2026), but not before the start nor indoors without
 * GPS, so without this the popup and the climb fields would be empty there.
 *
 * Checked on the seven lagorai.bike routes (14/11/2026): Menador 13.7-21.3 km +726 m (the climb Andrea
 * describes from km 14 to 22), Barricata 0-11 km +955 m, Manghen in three pieces split by its false
 * flats, Marter 0.6-15 km +1240 m.
 *
 * 1. A climb runs from the lowest point before it to its top, and ends when the road drops more than
 *    [DROP_M] or [DROP_SHARE] of the gain so far below the top.
 * 2. Inside it, a [FLAT_SPLIT_M] stretch under [FLAT_GRADE] splits it, and flat ends are trimmed
 *    (a [COARSE_TRIM_M] then a [TRIM_M] window under [FLAT_GRADE]).
 * 3. What is left counts from [MIN_GAIN_M], [MIN_LENGTH_M] and [MIN_GRADE].
 */
object ClimbDetector {
    const val DROP_M = 25.0
    const val DROP_SHARE = 0.2
    const val FLAT_GRADE = 2.0
    const val TRIM_M = 150.0
    const val COARSE_TRIM_M = 500.0
    const val FLAT_SPLIT_M = 1_500.0
    const val MIN_GAIN_M = 40.0
    const val MIN_LENGTH_M = 300.0
    const val MIN_GRADE = 3.0

    /** [ele]: the smoothed elevation every [stepM] metres. */
    fun find(ele: DoubleArray, stepM: Double): List<ClimbSpan> {
        val out = ArrayList<ClimbSpan>()
        for ((a, b) in raw(ele)) for ((x, y) in refine(ele, stepM, a, b)) {
            val gain = ele[y] - ele[x]
            val len = (y - x) * stepM
            if (gain >= MIN_GAIN_M && len >= MIN_LENGTH_M && gain / len * 100.0 >= MIN_GRADE)
                out.add(ClimbSpan(x * stepM, len, gain / len * 100.0, gain))
        }
        return out
    }

    /** Step 1: lowest point to top, ended by a real drop. Indices into [e]. */
    private fun raw(e: DoubleArray): List<Pair<Int, Int>> {
        val res = ArrayList<Pair<Int, Int>>()
        var lo = 0
        var top = 0
        for (i in 1 until e.size) {
            if (e[i] > e[top]) top = i
            val gain = e[top] - e[lo]
            if (top > lo && e[top] - e[i] > maxOf(DROP_M, DROP_SHARE * gain)) {
                res.add(lo to top); lo = i; top = i
            } else if (e[i] < e[lo] && (top <= lo || gain < MIN_GAIN_M)) {
                lo = i; top = i
            }
        }
        if (top > lo) res.add(lo to top)
        return res
    }

    /** Step 2: split at long false flats, trim flat ends. */
    private fun refine(e: DoubleArray, stepM: Double, a: Int, b: Int): List<Pair<Int, Int>> {
        val w = (TRIM_M / stepM).toInt()
        val fs = (FLAT_SPLIT_M / stepM).toInt()
        fun grade(i: Int, j: Int) = (e[j] - e[i]) / ((j - i) * stepM) * 100.0
        val pieces = ArrayList<Pair<Int, Int>>()
        var i = a
        var start = a
        while (i + fs <= b) {
            if (grade(i, i + fs) < FLAT_GRADE) {
                var j = i + fs
                while (j < b && grade(j - fs, j) < FLAT_GRADE) j++
                pieces.add(start to i); start = j - fs; i = j
            } else i++
        }
        pieces.add(start to b)
        return pieces.mapNotNull { (x0, y0) ->
            var x = x0; var y = y0
            // first a coarse trim (a false flat), then a fine one (where the climb really starts and ends)
            for (ww in intArrayOf((COARSE_TRIM_M / stepM).toInt(), w)) {
                while (x + ww < y && grade(x, x + ww) < FLAT_GRADE) x++
                while (y - ww > x && grade(y - ww, y) < FLAT_GRADE) y--
            }
            if (y > x) x to y else null
        }
    }
}
