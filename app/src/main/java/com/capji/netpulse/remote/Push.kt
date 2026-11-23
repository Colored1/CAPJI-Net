package com.capji.netpulse.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.capji.netpulse.MainActivity
import com.capji.netpulse.R
import com.capji.netpulse.data.net.Diag
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

object Push {
    const val CHANNEL = "capji_push"
    const val TOPIC = "all_users"

    fun init(context: Context) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT))
        }
        subscribe(context)
    }

    /** Every user joins "all_users" so one click in the Firebase console reaches everybody. */
    fun subscribe(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) return
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
                .addOnCompleteListener { Diag.add("FCM topic $TOPIC: ${if (it.isSuccessful) "subscribed" else "failed"}") }
        } catch (t: Throwable) {
            Diag.add("FCM subscribe failed: ${t.message}")
        }
    }

    /** Shows a notification with the CAPJI icon. Tapping opens the app (MainActivity routes url / tab / deep link). */
    fun show(context: Context, title: String, body: String, data: Map<String, String>) {
        runCatching {
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data.forEach { (k, v) -> putExtra(k, v) }
            }
            val pi = PendingIntent.getActivity(
                context, (System.nanoTime() and 0x7fffffff).toInt(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_capji)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.capji_mark))
                .setColor(0xFF58E6D9.toInt())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            context.getSystemService(NotificationManager::class.java).notify((System.nanoTime() and 0x7fffffff).toInt(), n)
        }.onFailure { Diag.add("notification failed: ${it.message}") }
    }
}

class CapjiFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Diag.add("FCM token refreshed")
        Push.subscribe(applicationContext)
    }

    /** Called for foreground messages and for data-only messages in the background. */
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return
        Push.show(applicationContext, title, body, message.data)
    }
}
