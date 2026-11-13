package com.anpaiss.routeprofile

import android.content.Context
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The "Route profile" field. The view is one bitmap of the cell ([ProfileRenderer]); the stream
 * publishes the distance still to go, so the field has a value if someone puts it as a number.
 * In the profile editor it draws the sample route; a tap is wired only outside the editor.
 */
class ProfileDataType(
    extension: String,
    private val state: StateFlow<ProfileState>,
) : DataTypeImpl(extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "route-profile"
        /** The rider moves ~10 m/s at most on an e-MTB: a redraw every 2 s is plenty and cheap. */
        private const val REDRAW_MS = 2_000L
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            state.map { it.remainingM }.distinctUntilChanged().collect { v ->
                emitter.onNext(if (v == null) StreamState.NotAvailable
                    else StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to v))))
            }
        }
        emitter.setCancellable { scope.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val (w, h) = config.viewSize
        val density = context.resources.displayMetrics.density
        val tap = if (config.preview) null else TapReceiver.pendingIntent(context)
        scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            val source = if (config.preview) flowOf(ProfileState.sample()) else state
            var last: ProfileState? = null
            source.distinctUntilChanged().collect { s ->
                if (s == last) return@collect
                last = s
                try {
                    val views = RemoteViews(context.packageName, R.layout.view_profile)
                    views.setImageViewBitmap(R.id.iv, ProfileRenderer.render(s, w, h, density))
                    tap?.let { views.setOnClickPendingIntent(R.id.root, it) }
                    emitter.updateView(views)
                } catch (_: Exception) { /* never take the field down with a drawing error */ }
                delay(REDRAW_MS)
            }
        }
        emitter.setCancellable { scope.cancel() }
    }
}
