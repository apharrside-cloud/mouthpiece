package com.pharrlabs.voice.update

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pharrlabs.voice.settings.Prefs
import com.pharrlabs.voice.SettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateNotifierTest {
    @Test fun backgroundScheduleAndUpdateAlertRespectUserSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        val sp = context.getSharedPreferences("pharr_voice", 0)
        val keys = listOf("update_check", "update_notifications", "update_found", "update_found_size", "update_last_check", "update_skipped", "update_notified_version")
        val saved = sp.all.filterKeys { it in keys }
        val prefs = Prefs(context)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        try {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cmd statusbar collapse")).use { it.readBytes() }
            prefs.updateCheck = false
            instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            prefs.updateCheck = true
            prefs.updateNotifications = true
            prefs.updateNotifiedVersion = null
            prefs.updateSkipped = null
            UpdateJobService.schedule(context)
            val job = checkNotNull(scheduler.getPendingJob(UpdateJobService.JOB_ID))
            assertEquals(UpdateCheck.INTERVAL_MS, job.intervalMillis)
            assertTrue(job.isPeriodic)
            assertTrue(job.isPersisted)
            assertEquals(JobInfo.NETWORK_TYPE_ANY, job.networkType)
            scheduler.cancel(UpdateJobService.JOB_ID)

            val update = UpdateInfo("99.0.0", "7", "")
            UpdateNotifier.record(context, update, notify = true)
            fun alert() = manager.activeNotifications.firstOrNull { it.id == 500 }
            var notification = checkNotNull(alert()).notification
            assertEquals("Mouthpiece 99.0.0 is available", notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("99.0.0", prefs.updateNotifiedVersion)

            UpdateNotifier.cancel(context)
            UpdateNotifier.record(context, update, notify = true)
            assertNull("Dismissing an alert does not cause repeat alerts", alert())

            val next = UpdateInfo("99.0.1", "7", "")
            UpdateNotifier.record(context, next, notify = true)
            notification = checkNotNull(alert()).notification
            notification.contentIntent.send()
            instrumentation.waitForIdleSync()
            automation.waitForIdle(500, 5000)
            val root = checkNotNull(automation.rootInActiveWindow)
            assertTrue("Notification opens release details", root.findAccessibilityNodeInfosByText("Latest release notes").isNotEmpty())

            prefs.updateSkipped = next.version
            UpdateNotifier.record(context, next, notify = true)
            assertNull("Skipped releases do not stay in notifications", alert())

            prefs.updateNotifications = false
            UpdateNotifier.record(context, UpdateInfo("99.0.2", "7", ""), notify = true)
            assertNull("Opting out prevents notifications", alert())

            prefs.updateCheck = false
            UpdateJobService.schedule(context)
            assertNull(scheduler.getPendingJob(UpdateJobService.JOB_ID))
        } finally {
            UpdateNotifier.cancel(context)
            sp.edit().apply {
                keys.forEach { remove(it) }
                saved.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        is Long -> putLong(key, value)
                    }
                }
            }.commit()
            UpdateJobService.schedule(context)
        }
    }
}
