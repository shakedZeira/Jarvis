package com.jarvis.remote.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jarvis.remote.MainActivity
import com.jarvis.remote.R

object NotificationHelper {

    const val CHANNEL_WORK = "jarvis_work"
    const val CHANNEL_ERROR = "jarvis_error"
    const val CHANNEL_PERMISSION = "jarvis_permission"
    const val CHANNEL_FOREGROUND = "jarvis_foreground"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val work = NotificationChannel(
            CHANNEL_WORK,
            "Work finished",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when a model finishes working"
            enableVibration(true)
        }

        val error = NotificationChannel(
            CHANNEL_ERROR,
            "Session errors",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies about session errors"
        }

        val permission = NotificationChannel(
            CHANNEL_PERMISSION,
            "Needs approval",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Asks for permission when opencode needs approval"
            enableVibration(true)
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
        }

        val foreground = NotificationChannel(
            CHANNEL_FOREGROUND,
            "Foreground service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the Jarvis session monitor running"
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(work, error, permission, foreground))
    }

    fun notify(context: Context, channel: String, id: Int, title: String, message: String) {
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun foregroundNotification(context: Context, status: String = "Connected"): Notification =
        NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Jarvis connected — monitoring …")
            .setContentText(status)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}