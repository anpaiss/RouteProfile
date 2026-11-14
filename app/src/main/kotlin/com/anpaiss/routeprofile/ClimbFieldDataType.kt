package com.anpaiss.routeprofile

import android.content.Context
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One climb number as a single field, drawn like the Karoo's own fields ([NativeField]).
 * The stream publishes the raw value (metres or %), -1 when there is none, so BigFields can
 * show it in large digits.
 */
class ClimbFieldDataType(
    extension: String,
    typeId: String,
    private val title: String,
    private val state: StateFlow<ProfileState>,
    private val value: (ClimbFields) -> Double?,
    private val format: (Double) -> String,
) : DataTypeImpl(extension, typeId) {

    companion object {
        fun all(extension: String, state: StateFlow<ProfileState>) = listOf(
            ClimbFieldDataType(extension, "to-climb", "TO CLIMB", state, { it.toStartM }, ::distance),
            ClimbFieldDataType(extension, "to-top", "TO TOP", state, { it.toTopM }, ::distance),
            ClimbFieldDataType(extension, "up-to-top", "UP TO TOP", state, { it.upToTopM }, { "${it.roundToInt()} m" }),
            ClimbFieldDataType(extension, "grade-to-top", "GRADE TO TOP", state, { it.gradeToTopPct }, { "${it.roundToInt()} %" }),
        )

        fun distance(m: Double): String =
            if (m < 1000.0) "${(m / 10.0).roundToInt() * 10} m" else String.format(Locale.US, "%.1f km", m / 1000.0)

        private val SAMPLE = ClimbFields(toStartM = 1_200.0, toTopM = 3_700.0, upToTopM = 245.0, gradeToTopPct = 8.0)
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            state.map { value(it.fields) ?: -1.0 }.distinctUntilChanged().collect { v ->
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to v))))
            }
        }
        emitter.setCancellable { scope.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            val source = if (config.preview) flowOf(SAMPLE) else state.map { it.fields }
            source.map { f -> value(f)?.let(format) ?: "--" }.distinctUntilChanged().collect { text ->
                try { emitter.updateView(NativeField.views(context, config, title, text)) } catch (_: Exception) { }
            }
        }
        emitter.setCancellable { scope.cancel() }
    }
}
