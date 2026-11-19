package com.pharrlabs.voice.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.pharrlabs.voice.BuildConfig
import com.pharrlabs.voice.R
import com.pharrlabs.voice.SettingsActivity
import com.pharrlabs.voice.settings.Prefs

/** A quiet, opt-in alert, once per available version. Tapping opens the update details in Mouthpiece. */
object UpdateNotifier {
    private const val CHANNEL = "app_updates"
    private const val NOTIFICATION_ID = 500

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New Mouthpiece releases"
            }
        )
    }

    fun allowed(context: Context): Boolean {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

    @Synchronized
    fun record(context: Context, info: UpdateInfo, notify: Boolean) {
        val prefs = Prefs(context)
        prefs.updateFound = info.version
        prefs.updateFoundSizeMb = info.sizeMb
        prefs.updateLastCheck = System.currentTimeMillis()
        if (!UpdateCheck.available(info, BuildConfig.VERSION_NAME, prefs.updateSkipped)) {
            cancel(context)
            return
        }
        if (!notify || !prefs.updateCheck || !prefs.updateNotifications || !allowed(context) ||
            !UpdateCheck.shouldNotify(info, BuildConfig.VERSION_NAME, prefs.updateSkipped, prefs.updateNotifiedVersion)) return
        val intent = Intent(context, SettingsActivity::class.java).apply {
            action = SettingsActivity.ACTION_SHOW_UPDATES
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle("Mouthpiece ${info.version} is available")
            .setContentText("Tap to view the update and download it.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            return // Permission may have been revoked between checking it and posting.
        }
        prefs.updateNotifiedVersion = info.version
    }
}
