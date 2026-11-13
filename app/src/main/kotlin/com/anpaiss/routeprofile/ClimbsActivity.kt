package com.anpaiss.routeprofile

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.hammerhead.karooext.models.RideState
import java.util.Locale

/**
 * The climbs popup: a tap on the field while the ride is paused. The route's climbs in order,
 * coloured by category, the next one highlighted, those done dimmed with a ✓; it closes with
 * CLOSE or by itself when the ride records again. With [EXTRA_SAMPLE] it shows the sample route
 * (TEST POPUP in the app).
 */
class ClimbsActivity : Activity() {

    companion object {
        const val EXTRA_SAMPLE = "sample"

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, ClimbsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Background and text of a row by category: the harder, the hotter (grey, green, yellow, orange, red, purple). */
        fun colours(c: ClimbCategory): Pair<Int, Int> = when (c) {
            ClimbCategory.UNCATEGORIZED -> 0xFF5F6368.toInt() to Color.WHITE
            ClimbCategory.CAT4 -> 0xFF43A047.toInt() to Color.WHITE
            ClimbCategory.CAT3 -> 0xFFFDD835.toInt() to Color.BLACK
            ClimbCategory.CAT2 -> 0xFFFB8C00.toInt() to Color.BLACK
            ClimbCategory.CAT1 -> 0xFFE53935.toInt() to Color.WHITE
            ClimbCategory.HC -> 0xFF8E24AA.toInt() to Color.WHITE
        }

        fun rowText(c: ClimbSpan): String = String.format(Locale.US, "km %.1f  %s  %.1f km  +%d m  %d %%",
            c.startM / 1000.0, if (c.category == ClimbCategory.UNCATEGORIZED) "Unc." else c.category.label,
            c.lengthM / 1000.0, Math.round(c.ascentM), Math.round(c.gradePct.takeIf { it > 0 } ?: (c.ascentM / c.lengthM * 100.0)))
    }

    private val main = Handler(Looper.getMainLooper())
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()
    private val condensed by lazy { Typeface.create("ibm-plex-sans-condensed", Typeface.NORMAL) }
    private val condensedBold by lazy { Typeface.create(condensed, Typeface.BOLD) }

    /** Closes the popup as soon as the ride records again. */
    private val watch = object : Runnable {
        override fun run() {
            if (RouteProfileExtension.instance?.rideState?.value is RideState.Recording) { finish(); return }
            main.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sample = intent.getBooleanExtra(EXTRA_SAMPLE, false)
        val state = if (sample) ProfileState.sample() else RouteProfileExtension.instance?.state?.value ?: ProfileState()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.BLACK)
        }
        fun text(s: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = sp; setTextColor(color); typeface = if (bold) condensedBold else condensed
        }

        val list = ClimbList.of(state.climbs, state.posM)
        col.addView(text("CLIMBS ON THE ROUTE", 22f, bold = true))
        when {
            state.profile == null -> col.addView(text("No route", 20f, 0xFFB0B0B0.toInt()).apply { setPadding(0, dp(16), 0, dp(16)) })
            list.total == 0 -> col.addView(text("No climbs on this route", 20f, 0xFFB0B0B0.toInt()).apply { setPadding(0, dp(16), 0, dp(16)) })
            else -> {
                col.addView(text("${list.ahead} of ${list.total} ahead", 17f, 0xFFB0B0B0.toInt()).apply { setPadding(0, 0, 0, dp(8)) })
                val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                for (r in list.rows) {
                    val (bg, fg) = colours(r.climb.category)
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(10), dp(6), dp(10), dp(6))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(8).toFloat(); setColor(bg)
                            if (r.next) setStroke(dp(3), Color.WHITE)
                        }
                        alpha = if (r.done) 0.4f else 1f
                    }
                    row.addView(text(rowText(r.climb), 19f, fg, bold = r.next).apply {
                        if (r.done) {
                            // the Karoo's fonts have no ✓: a small check-mark icon before the text
                            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
                            compoundDrawablePadding = dp(6)
                            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(fg)
                        }
                    })
                    if (r.next) row.addView(text(r.inM?.let { if (it < 1.0) "you are on it" else String.format(Locale.US, "in %.1f km", it / 1000.0) } ?: "",
                        17f, fg, bold = true))
                    rows.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
                }
                col.addView(ScrollView(this).apply { addView(rows) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
                col.addView(text(String.format(Locale.US, "Ahead: %d climb%s, +%d m", list.ahead, if (list.ahead == 1) "" else "s", Math.round(list.aheadAscentM)), 19f, bold = true)
                    .apply { setPadding(0, dp(8), 0, dp(4)) })
            }
        }
        col.addView(Button(this).apply {
            text = "CLOSE"; textSize = 18f
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6); gravity = Gravity.BOTTOM })
        setContentView(col)
        if (!sample) main.postDelayed(watch, 1_000)
    }

    override fun onDestroy() {
        main.removeCallbacks(watch)
        super.onDestroy()
    }
}
