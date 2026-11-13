package com.anpaiss.routeprofile

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * The app page: what the field does, the auto-zoom switch and the FIELD PREVIEW drawn by the
 * real renderer at the Karoo 3 cell sizes (full-width bottom row of the map page, a taller
 * cell, and a half cell).
 */
class MainActivity : Activity() {

    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }
        fun text(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; setTextColor(0xFF1A1A1A.toInt()); if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        }
        col.addView(text("RouteProfile", 26f, bold = true))
        col.addView(text("The whole route's elevation, coloured by gradient, with where you are and the distance and climb still to go. " +
            "Put \"Route profile\" in the bottom row of the map page, full width (add it as the third field, or alone). " +
            "Tap the field: whole route / next 10 km.", 15f))

        val sw = Switch(this).apply {
            text = "Auto zoom on climbs"; textSize = 17f
            isChecked = Prefs.autoZoom(this@MainActivity)
            setPadding(0, dp(12), 0, dp(4))
            setOnCheckedChangeListener { _, on ->
                Prefs.setAutoZoom(this@MainActivity, on)
                RouteProfileExtension.instance?.settingsChanged()
            }
        }
        col.addView(sw)
        col.addView(text("On: the next 10 km when a climb starts within 2 km and while you are on it, then the whole route again. " +
            "A tap always switches; the automatic choice takes over again at the next climb.", 13f))

        col.addView(text("Zoom on these climbs", 15f, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
        col.addView(text("Category from length (m) × average grade (%): Cat 4 from 8,000, Cat 3 from 16,000, " +
            "Cat 2 from 32,000, Cat 1 from 64,000, HC from 80,000. Below 8,000: uncategorized (an overpass, a short ramp).", 13f))
        val catSwitches = ClimbCategory.entries.map { cat ->
            Switch(this).apply {
                val hint = if (cat == ClimbCategory.UNCATEGORIZED) "score below 8,000" else "score from %,d".format(cat.minScore.toInt())
                text = "${cat.label}  ($hint)"; textSize = 16f
                isChecked = Prefs.zoomOn(this@MainActivity, cat)
                isEnabled = sw.isChecked
                setPadding(dp(12), dp(6), 0, dp(6))
                setOnCheckedChangeListener { _, on ->
                    Prefs.setZoomOn(this@MainActivity, cat, on)
                    RouteProfileExtension.instance?.settingsChanged()
                }
            }.also { col.addView(it) }
        }
        sw.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoZoom(this@MainActivity, on)
            catSwitches.forEach { it.isEnabled = on }
            RouteProfileExtension.instance?.settingsChanged()
        }

        col.addView(text("FIELD PREVIEW", 17f, bold = true).apply { setPadding(0, dp(18), 0, dp(4)) })
        col.addView(text("Sample route, rider at km 14.5. Sizes of the Karoo 3 cells.", 13f))
        fun preview(label: String, s: ProfileState, w: Int, h: Int) {
            col.addView(text(label, 13f))
            // the real cell size, scaled to the column (the Karoo screen is 480 px, the column a bit less)
            val colW = resources.displayMetrics.widthPixels - dp(32)
            val shownW = if (w >= 400) colW else w * colW / 478
            col.addView(ImageView(this).apply {
                setImageBitmap(ProfileRenderer.render(s, w, h, d))
                scaleType = ImageView.ScaleType.FIT_XY
            }, LinearLayout.LayoutParams(shownW, shownW * h / w).apply { bottomMargin = dp(8) })
        }
        preview("Map page, bottom row (478×126): whole route", ProfileState.sample(Zoom.WHOLE), 478, 126)
        preview("Same, after a tap: next 10 km", ProfileState.sample(Zoom.AHEAD), 478, 126)
        preview("Taller cell (478×212)", ProfileState.sample(Zoom.WHOLE), 478, 212)
        preview("Half cell (238×126)", ProfileState.sample(Zoom.WHOLE), 238, 126)
        preview("No route", ProfileState(), 478, 126)

        setContentView(ScrollView(this).apply { addView(col, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)); setBackgroundColor(0xFFF4F2EE.toInt()) })
    }
}
