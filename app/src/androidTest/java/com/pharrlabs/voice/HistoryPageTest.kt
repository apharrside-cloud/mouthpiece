package com.pharrlabs.voice

import android.app.Activity
import android.app.job.JobScheduler
import android.content.ClipboardManager
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pharrlabs.voice.history.HistoryCleanupService
import com.pharrlabs.voice.history.HistoryEntry
import com.pharrlabs.voice.history.HistoryStore
import com.pharrlabs.voice.settings.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryPageTest {
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    @Test fun historySummarizesThirtyDaysAndProvidesCompactRecoverableText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        val sp = context.getSharedPreferences("pharr_voice", 0)
        val keys = listOf("history", "history_enabled", "update_check", "splash_seen", "model")
        val saved = sp.all.filterKeys { it in keys }
        val prefs = Prefs(context)
        var activity: Activity? = null
        try {
            prefs.updateCheck = false
            prefs.splashSeen = true
            prefs.historyEnabled = true
            val store = prefs.history()
            store.clear()
            val now = System.currentTimeMillis()
            val longText = "A longer dictation with all of its words still available. ".repeat(12).trim()
            store.add(HistoryEntry(now - 1000, context.packageName, longText, longText, "SetText", 60_000, 1200))
            store.add(HistoryEntry(now - 2000, context.packageName, "One two three four.", "One two three four.", "ClipboardOnly", 4000, 250))
            (1..19).forEach { n -> store.add(HistoryEntry(now - 3000 - n, context.packageName, "", "Saved dictation $n.", "Pasted")) }
            store.add(HistoryEntry(now - 24 * 60 * 60 * 1000, context.packageName, "", "Older saved words.", "Pasted"))
            // A legacy entry beyond the retention window must be removed, including from stored JSON.
            sp.edit().putString("history", sp.getString("history", "[]")!!.dropLast(1) +
                ",{" + "\"t\":${now - HistoryStore.RETENTION_MS - 1000},\"x\":\"Expired text\"}]").commit()
            activity = instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val screen = activity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                views(screen.window.decorView).filterIsInstance<ImageButton>().single { it.contentDescription == "Open page menu" }.performClick()
            }
            automation.waitForIdle(500, 5000)
            val historyTile = checkNotNull(automation.rootInActiveWindow).findAccessibilityNodeInfosByText("History").first { it.text == "History" }
            assertTrue(historyTile.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            fun text(value: String) = views(screen.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == value }
            instrumentation.runOnMainSync {
                assertNotNull(text("Last 30 days"))
                assertNotNull(text("Words")); assertNotNull(text("Dictations")); assertNotNull(text("Avg. words"))
                assertNotNull(text("Recording time")); assertNotNull(text("1m 4s")); assertNotNull(text("22"))
                assertNotNull(text("Today"))
                assertTrue(text(longText).lineCount <= 3)
                assertEquals(3, text(longText).maxLines)
                assertTrue(views(screen.window.decorView).filterIsInstance<TextView>().none { it.text == "Delete" })
                val copy = views(screen.window.decorView).filterIsInstance<TextView>().first { it.contentDescription == "Copy dictation" }
                assertTrue(copy.isClickable)
                text(longText).performClick()
            }
            instrumentation.waitForIdleSync()
            automation.waitForIdle(500, 5000)
            var root = checkNotNull(automation.rootInActiveWindow)
            assertTrue(root.findAccessibilityNodeInfosByText("Recording: 1m 0s").isNotEmpty())
            assertTrue(root.findAccessibilityNodeInfosByText("Processing: 1.2s").isNotEmpty())
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                text("Show 2 more").performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertNotNull(text("Yesterday"))
                assertNotNull(text("Older saved words."))
                views(screen.window.decorView).filterIsInstance<Switch>().single().isChecked = false
            }
            instrumentation.waitForIdleSync()
            assertFalse(prefs.historyEnabled)
            assertEquals(22, store.all().size)
            assertFalse(sp.getString("history", "")!!.contains("Expired text"))
            val job = checkNotNull(context.getSystemService(JobScheduler::class.java).getPendingJob(HistoryCleanupService.JOB_ID))
            assertTrue(job.isPersisted); assertEquals(24L * 60 * 60 * 1000, job.intervalMillis)
            // Optional local visual QA fixture, never enabled during ordinary test runs.
            if (InstrumentationRegistry.getArguments().getString("historyScreenshot") == "true") {
                instrumentation.runOnMainSync {
                    views(screen.window.decorView).filterIsInstance<Switch>().single().isChecked = true
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    views(screen.window.decorView).filterIsInstance<android.widget.ScrollView>().first().scrollTo(0, 0)
                }
                automation.waitForIdle(500, 5000)
                automation.takeScreenshot().let { bitmap ->
                    java.io.File(context.getExternalFilesDir(null), "history-preview.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
                instrumentation.runOnMainSync { views(screen.window.decorView).filterIsInstance<Switch>().single().isChecked = false }
                instrumentation.waitForIdleSync()
            }
            instrumentation.runOnMainSync {
                views(screen.window.decorView).filterIsInstance<TextView>().first { it.contentDescription == "Copy dictation" }.performClick()
                assertEquals(longText, context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
                text("Clear").performClick()
            }
            instrumentation.waitForIdleSync()
            automation.waitForIdle(500, 5000)
            root = checkNotNull(automation.rootInActiveWindow)
            assertTrue(root.findAccessibilityNodeInfosByText("Clear history?").isNotEmpty())
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            assertEquals("Cancelling Clear preserves history", 22, store.all().size)
            instrumentation.runOnMainSync { text("Clear").performClick() }
            automation.waitForIdle(500, 5000)
            val clear = checkNotNull(automation.rootInActiveWindow).findAccessibilityNodeInfosByText("Clear history").first { it.isClickable }
            assertTrue(clear.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertNotNull(text("History is paused")) }
            assertTrue(store.all().isEmpty())
        } finally {
            activity?.let { screen -> instrumentation.runOnMainSync { screen.finish() } }
            val edit = sp.edit()
            keys.forEach { edit.remove(it) }
            saved.forEach { (key, value) ->
                when (value) {
                    is Boolean -> edit.putBoolean(key, value)
                    is String -> edit.putString(key, value)
                }
            }
            edit.commit()
        }
    }
}
