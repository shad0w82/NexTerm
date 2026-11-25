package dev.nexterm.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.nexterm.MainActivity
import dev.nexterm.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Foreground service (type specialUse) whose only job is keeping the process alive while a
 * session is connected, so switching apps doesn't drop the serial link. With Auto-reconnect it also stays
 * while the session waits for its adapter to come back. Stops itself as soon as neither holds.
 */
class SessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground promptly after startForegroundService(), even if we stop right away.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(SessionManager.active.value?.title, SessionManager.active.value?.awaitingAdapter?.value == true),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (intent?.action == ACTION_DISCONNECT) {
            Log.i(TAG, "disconnect requested from notification")
            SessionManager.disconnect()
            SessionManager.stopWaiting()
        }
        if (!started) {
            started = true
            scope.launch {
                SessionManager.active
                    .flatMapLatest { s ->
                        if (s == null) flowOf(null)
                        else combine(s.connected, s.awaitingAdapter) { connected, waiting -> if (connected || waiting) s to waiting else null }
                    }
                    .collect { live ->
                        if (live == null) {
                            Log.i(TAG, "no connected session and nothing to wait for; stopping service")
                            ServiceCompat.stopForeground(this@SessionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            val (s, waiting) = live
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(s.title, waiting))
                        }
                    }
            }
        }
        return START_NOT_STICKY // a USB session can't be resurrected after process death anyway
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(title: String?, waiting: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Active serial session", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a serial connection is open or waiting for its adapter"
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_TERMINAL)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, SessionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(if (waiting) "Waiting for the adapter" else "Serial session active")
            .setContentText(if (waiting) "The session resumes when the adapter is plugged back in" else title ?: "Connecting…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, if (waiting) "Stop waiting" else "Disconnect", disconnect)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "NexTerm.Service"
        private const val CHANNEL_ID = "session"
        private const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT = "dev.nexterm.action.DISCONNECT"

        @Suppress("unused")
        fun stop(context: Context) = context.stopService(Intent(context, SessionService::class.java))
    }
}
