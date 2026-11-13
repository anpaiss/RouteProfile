package com.anpaiss.routeprofile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A tap on the field (RemoteViews.setOnClickPendingIntent, as in KarooSwitches and boschRange):
 * whole route, climb, next 10 / 5 / 2 km.
 */
class TapReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.anpaiss.routeprofile.FIELD_TAP"

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, TapReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) RouteProfileExtension.instance?.onTap()
    }
}
