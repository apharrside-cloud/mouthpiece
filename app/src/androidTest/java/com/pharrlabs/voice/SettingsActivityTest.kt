package com.pharrlabs.voice

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pharrlabs.voice.asr.ModelManager
import com.pharrlabs.voice.settings.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    @Test fun modelStatusAndUpdateActionsAreClear() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        val prefs = Prefs(context)
        val previousModel = prefs.modelName
        val previousCheck = prefs.updateCheck
        val previousFound = prefs.updateFound
        val previousSkipped = prefs.updateSkipped
        val manager = ModelManager(context.getExternalFilesDir(null)!!)
        val tiny = manager.modelFile("tiny.en-q5_1")
        val created = !tiny.exists()
        // Only file presence/size is needed for this UI test. Never replace an existing downloaded model.
        if (created) RandomAccessFile(tiny, "rw").use { it.setLength(ModelManager.DEFAULT_SPECS.getValue("tiny.en-q5_1").sizeBytes) }
        prefs.modelName = "medium.en-q5_0"
        prefs.updateCheck = false
        prefs.updateFound = "99.0.0"
        prefs.updateSkipped = null
        val activity = instrumentation.startActivitySync(
            Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val all = views(activity.window.decorView)
                fun text(value: String) = all.filterIsInstance<TextView>().first { it.text.toString() == value }
                assertEquals("tiny.en-q5_1", prefs.modelName)
                assertTrue(text("✓ In use · Downloaded").isShown)
                assertNotNull(text("Not downloaded"))
                assertNotNull(text("Download & use"))
                val check = text("Check for updates")
                val download = text("Download update")
                assertTrue(text("Report a problem").isClickable)
                assertTrue(text("Suggest a feature").isClickable)
                assertSame(check.parent, download.parent)
                assertEquals(check.top, download.top)
                assertTrue(download.left >= check.right)
                assertTrue(all.none { it is TextView && it.text.toString() == "Update available" })
                val menu = all.filterIsInstance<ImageButton>().single { it.contentDescription == "Open page menu" }
                menu.performClick()
            }
            instrumentation.waitForIdleSync()
            automation.waitForIdle(500, 5000)
            val root = checkNotNull(automation.rootInActiveWindow) { "Page menu has no accessible window" }
            for (page in listOf("Home", "Dictation", "Bubble", "Words", "Apps", "History", "What’s new")) {
                val tile = root.findAccessibilityNodeInfosByText(page).first { it.text?.toString() == page }
                assertTrue("Menu contains a clickable $page tile", tile.isClickable)
                assertFalse("Page tiles do not use radio buttons", tile.isCheckable)
                assertEquals("Only the current page is highlighted", page == "Home", tile.isSelected)
            }
            var target = root.findAccessibilityNodeInfosByText("Dictation").first()
            while (!target.isClickable) target = checkNotNull(target.parent)
            assertTrue(target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "How you start" })
                views(activity.window.decorView).filterIsInstance<ImageButton>()
                    .single { it.contentDescription == "Open page menu" }.performClick()
            }
            instrumentation.waitForIdleSync()
            automation.waitForIdle(500, 5000)
            val changedMenu = checkNotNull(automation.rootInActiveWindow)
            assertTrue(changedMenu.findAccessibilityNodeInfosByText("Dictation").first().isSelected)
            assertFalse(changedMenu.findAccessibilityNodeInfosByText("Home").first().isSelected)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                views(activity.window.decorView).filterIsInstance<ImageButton>()
                    .single { it.contentDescription == "Open page menu" }.performClick()
            }
            instrumentation.waitForIdleSync()
            automation.waitForIdle(500, 5000)
            val words = checkNotNull(automation.rootInActiveWindow).findAccessibilityNodeInfosByText("Words").first()
            assertTrue(words.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val fields = views(activity.window.decorView).filterIsInstance<android.widget.EditText>()
                val spoken = fields.single { it.hint.toString() == "When I say…" }
                val written = fields.single { it.hint.toString() == "Write…" }
                assertSame(spoken.parent, written.parent)
                assertEquals(spoken.top, written.top)
                assertTrue("Spoken text is left of its replacement", spoken.right <= written.left)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            prefs.modelName = previousModel
            prefs.updateCheck = previousCheck
            prefs.updateFound = previousFound
            prefs.updateSkipped = previousSkipped
            if (created) tiny.delete()
        }
    }
}
