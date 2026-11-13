package com.anpaiss.routeprofile

/**
 * The list of the climbs popup (tap on the field while not recording): the route's climbs in
 * route order, which ones are done, the next one and how far it is, and what is still ahead.
 */
data class ClimbRow(
    val climb: ClimbSpan,
    val done: Boolean,
    /** The first climb not done yet: the one highlighted. */
    val next: Boolean,
    /** Metres to its start for the next climb, 0 when the rider is already on it; null for the others. */
    val inM: Double?,
)

data class ClimbList(val rows: List<ClimbRow>) {
    val total: Int get() = rows.size
    val ahead: Int get() = rows.count { !it.done }
    val aheadAscentM: Double get() = rows.filter { !it.done }.sumOf { it.climb.ascentM }

    companion object {
        /** Before the first position everything is ahead and the first climb is the next one. */
        fun of(climbs: List<ClimbSpan>, posM: Double?): ClimbList {
            val sorted = climbs.filter { it.lengthM > 0 }.sortedBy { it.startM }
            var nextGiven = false
            val rows = sorted.map { c ->
                val done = posM != null && posM >= c.endM
                val next = !done && !nextGiven
                if (next) nextGiven = true
                ClimbRow(c, done, next, if (next) ((c.startM - (posM ?: 0.0)).coerceAtLeast(0.0)) else null)
            }
            return ClimbList(rows)
        }
    }
}
