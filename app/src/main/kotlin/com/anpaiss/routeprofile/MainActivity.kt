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
            "Tap the field: whole route / the climb (or the next 10 km when no climb is near); with the ride paused, the list of the climbs.", 15f))

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
        col.addView(text("On: when a climb starts within 2 km, and while you are on it, the field shows that climb across its whole width, then the whole route again. " +
            "A tap always switches; the automatic choice takes over again at the next climb.", 13f))

        col.addView(text("Zoom on these climbs", 15f, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
        col.addView(text("A climb's category goes by how much it climbs.", 13f))
        val catSwitches = ClimbCategory.entries.map { cat ->
            Switch(this).apply {
                text = cat.description; textSize = 16f
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

        col.addView(text("Climbs popup", 15f, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
        col.addView(text("With the ride paused, a tap on the field lists the route's climbs, coloured by category, the next one highlighted. " +
            "It closes with CLOSE or when the ride records again.", 13f))
        col.addView(android.widget.Button(this).apply {
            text = "TEST POPUP: CLIMBS"
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, ClimbsActivity::class.java).putExtra(ClimbsActivity.EXTRA_SAMPLE, true))
            }
        })

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
        preview("Auto zoom, approaching a climb: the whole climb fitted to the cell", ProfileState.sample(Zoom.CLIMB), 478, 126)
        preview("On the climb: what is left to the top", ProfileState.sample(Zoom.CLIMB, atM = 17_000.0), 478, 126)
        preview("A tap with no climb near: next 10 km", ProfileState.sample(Zoom.AHEAD), 478, 126)
        preview("Downhill: the instant grade in blue, with its sign", ProfileState.sample(Zoom.WHOLE, atM = 26_000.0).copy(gradePct = -8.0), 478, 126)
        preview("Taller cell (478×212)", ProfileState.sample(Zoom.WHOLE), 478, 212)
        preview("Half cell (238×126)", ProfileState.sample(Zoom.WHOLE), 238, 126)
        preview("No route", ProfileState(), 478, 126)

        setContentView(ScrollView(this).apply { addView(col, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)); setBackgroundColor(0xFFF4F2EE.toInt()) })
    }
}
