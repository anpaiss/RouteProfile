package com.anpaiss.routeprofile

import android.content.Context

/** Settings of the app page. */
object Prefs {
    private const val FILE = "routeprofile"
    private const val AUTO_ZOOM = "auto_zoom"
    private const val AUTO_SPAN_M = "auto_span_m"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun autoZoom(c: Context): Boolean = sp(c).getBoolean(AUTO_ZOOM, true)

    fun setAutoZoom(c: Context, on: Boolean) { sp(c).edit().putBoolean(AUTO_ZOOM, on).apply() }

    /** What the auto zoom shows on a climb: 0 = the whole climb (default), else the next 10, 5 or 2 km (at most to the top). */
    fun autoSpanM(c: Context): Double = sp(c).getFloat(AUTO_SPAN_M, 0f).toDouble()

    fun setAutoSpanM(c: Context, m: Double) { sp(c).edit().putFloat(AUTO_SPAN_M, m.toFloat()).apply() }

    /** Uncategorized climbs (an overpass, a short ramp) off by default, all the others on. */
    fun zoomOn(c: Context, cat: ClimbCategory): Boolean =
        sp(c).getBoolean("zoom_${cat.name}", cat != ClimbCategory.UNCATEGORIZED)

    fun setZoomOn(c: Context, cat: ClimbCategory, on: Boolean) { sp(c).edit().putBoolean("zoom_${cat.name}", on).apply() }

    fun zoomCategories(c: Context): Set<ClimbCategory> = ClimbCategory.entries.filter { zoomOn(c, it) }.toSet()
}
