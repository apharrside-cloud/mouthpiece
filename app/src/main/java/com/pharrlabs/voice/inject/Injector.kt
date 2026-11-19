package com.pharrlabs.voice.inject

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.accessibility.AccessibilityNodeInfo

enum class InjectResult { SetText, Pasted, ClipboardOnly }

/**
 * Puts dictated text into a focused editable node. Order of preference: ACTION_SET_TEXT with the
 * merged text (keeps the existing content and puts the cursor after the insertion), then a
 * clipboard paste, then leaving the text on the clipboard for the user to paste manually.
 */
class Injector(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    /** What the last successful ACTION_SET_TEXT insert did, so it can be undone. Null after a paste fallback. */
    var lastInsert: InsertRecord? = null
        private set

    fun inject(node: AccessibilityNodeInfo, text: String): InjectResult {
        lastInsert = null
        node.refresh()
        // Some apps (Telegram "Message", Gmail "Compose email") expose their placeholder as real text and never set the hint flag.
        val hint = Placeholders.isPlaceholder(node.text?.toString(), node.hintText?.toString(), node.isShowingHintText)
        val existing = if (hint) "" else node.text?.toString().orEmpty()
        val selStart = if (hint) 0 else node.textSelectionStart
        val selEnd = if (hint) 0 else node.textSelectionEnd
        val merged = TextMerger.merge(existing, selStart, selEnd, text)

        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged.text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) {
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, merged.cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, merged.cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
            lastInsert = Undo.record(existing, selStart, selEnd, text)
            return InjectResult.SetText
        }

        // Paste inserts at the cursor / over the selection, so paste only the inserted piece.
        val (from, _) = TextMerger.range(existing, selStart, selEnd)
        val pasteText = merged.text.substring(from, merged.cursor)
        return pasteFallback(node, pasteText)
    }

    private fun pasteFallback(node: AccessibilityNodeInfo, pasteText: String): InjectResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val previous = runCatching { cm.primaryClip }.getOrNull()
        val clip = ClipData.newPlainText("dictation", pasteText).apply {
            description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true) // hide from the clipboard preview
            }
        }
        cm.setPrimaryClip(clip)
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            return InjectResult.ClipboardOnly // leave the text on the clipboard for a manual paste
        }
        main.postDelayed({
            runCatching {
                if (previous != null) cm.setPrimaryClip(previous) else cm.clearPrimaryClip()
            }
        }, 500)
        return InjectResult.Pasted
    }
}
