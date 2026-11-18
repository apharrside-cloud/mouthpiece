package com.pharrlabs.voice.bubble

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pharrlabs.voice.SettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveCaptionTest {
    @Test fun captionShowsFiveLinesAndFollowsLatestWordsWithoutDiscardingEarlierText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        var chip: ChipOverlay? = null
        lateinit var row: ViewGroup
        val transcript = (1..20).joinToString("\n") { "Dictated line $it" }
        fun caption() = row.getChildAt(0) as TextView
        try {
            instrumentation.runOnMainSync {
                val actual = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                // Attach the real overlay layout to the test activity without accessibility permissions.
                val wm = object : WindowManager by actual {
                    override fun getCurrentWindowMetrics() = actual.currentWindowMetrics

                    override fun addView(view: View, params: ViewGroup.LayoutParams) {
                        (params as WindowManager.LayoutParams).apply {
                            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                            token = activity.window.decorView.windowToken
                        }
                        row = view as ViewGroup
                        actual.addView(view, params)
                    }
                }
                chip = ChipOverlay(activity, wm)
                chip!!.show(transcript, emptyList(), 120, 500, 48, 0, liveCaption = true)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val text = caption()
                assertEquals(transcript, text.text.toString())
                assertEquals(5, text.minLines)
                assertEquals(5, text.maxLines)
                val visibleHeight = text.height - text.compoundPaddingTop - text.compoundPaddingBottom
                assertTrue(visibleHeight >= text.lineHeight * 5 && visibleHeight < text.lineHeight * 6)
                assertTrue("Long transcript must scroll to its end", text.scrollY > 0)
                assertEquals(text.layout.height - visibleHeight, text.scrollY)
                text.scrollTo(0, 0)
                assertEquals("Earlier words remain available", 0, text.scrollY)
                chip!!.show("A short caption", emptyList(), 120, 500, 48, 0, liveCaption = true)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals("A short caption", caption().text.toString())
                assertEquals(0, caption().scrollY)
                chip!!.show("Inserted", listOf(ChipAction("Undo") {}), 120, 500, 48, 0)
                assertNotEquals("Action chips retain their normal height", 5, caption().minLines)
            }
        } finally {
            instrumentation.runOnMainSync {
                chip?.hide()
                activity.finish()
            }
        }
    }
}
