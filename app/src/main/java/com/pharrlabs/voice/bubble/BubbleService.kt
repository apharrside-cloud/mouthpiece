package com.pharrlabs.voice.bubble

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.pharrlabs.voice.BuildConfig
import com.pharrlabs.voice.asr.ModelManager
import com.pharrlabs.voice.asr.ModelMissing
import com.pharrlabs.voice.asr.StreamingSession
import com.pharrlabs.voice.audio.SilenceTrimmer
import com.pharrlabs.voice.asr.WhisperCppEngine
import com.pharrlabs.voice.audio.Recorder
import com.pharrlabs.voice.clean.Cleaner
import com.pharrlabs.voice.clean.VoiceEdit
import com.pharrlabs.voice.inject.PieceEdit
import com.pharrlabs.voice.clean.PeriodMode
import com.pharrlabs.voice.field.FieldClassifier
import com.pharrlabs.voice.field.FieldInfo
import com.pharrlabs.voice.field.FieldFormat
import com.pharrlabs.voice.field.FieldKind
import com.pharrlabs.voice.inject.InjectResult
import com.pharrlabs.voice.history.HistoryEntry
import com.pharrlabs.voice.inject.InsertRecord
import com.pharrlabs.voice.inject.Injector
import com.pharrlabs.voice.inject.Undo
import com.pharrlabs.voice.learn.Correction
import com.pharrlabs.voice.learn.LearnWatcher
import com.pharrlabs.voice.settings.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shows a mic bubble over editable fields. Tap to start dictating, tap again to stop; the speech is
 * transcribed on-device, cleaned up and typed into the field that was focused when recording began.
 */
class BubbleService : AccessibilityService() {
    private val tag = "PharrVoice"

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val machine = BubbleStateMachine()

    private lateinit var wm: WindowManager
    private lateinit var recorder: Recorder
    private lateinit var injector: Injector
    private lateinit var prefs: Prefs
    private var engine: WhisperCppEngine? = null
    private var engineModel: String? = null

    private var bubble: BubbleView? = null
    private var focusedNode: AccessibilityNodeInfo? = null
    private var focusedPkg: String? = null
    private var pipelineJob: Job? = null
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private var imePackages: Set<String> = emptySet()
    private lateinit var chip: ChipOverlay
    private lateinit var hideTarget: HideTargetOverlay
    private lateinit var learner: LearnWatcher
    private val checkLearn = Runnable { checkForCorrection() }
    private var debugReceiver: android.content.BroadcastReceiver? = null

    private var pendingNode: AccessibilityNodeInfo? = null
    private var pendingPkg: String = ""
    private val evaluate = Runnable { evaluateFocus() }
    private val reanchor = Runnable { validateAndAnchor() }
    private fun fieldInfo(node: AccessibilityNodeInfo) =
        FieldInfo(node.viewIdResourceName, node.hintText?.toString(), node.contentDescription?.toString(), node.inputType, node.packageName?.toString().orEmpty())

    private fun fieldKind(node: AccessibilityNodeInfo?): FieldKind =
        if (node == null) FieldKind.Prose else FieldClassifier.classify(fieldInfo(node))

    private fun fieldFormat(node: AccessibilityNodeInfo?): FieldFormat =
        if (node == null) FieldFormat.Text else FieldClassifier.format(fieldInfo(node))

    /** The input-focused node of the foreground app window, or null. rootInActiveWindow can be null mid-transition. */
    private fun findInputFocus(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.isFocused || it.isActive }
            .firstNotNullOfOrNull { it.root }
        return root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    /** Finds an already-focused input (returning to an app fires no new focus event) and evaluates it. */
    private val probe = Runnable {
        if (bubble != null) return@Runnable
        val node = findInputFocus() ?: return@Runnable
        pendingNode = node
        pendingPkg = node.packageName?.toString().orEmpty()
        evaluateFocus()
    }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        recorder = Recorder(this)
        injector = Injector(this)
        prefs = Prefs(this)
        chip = ChipOverlay(this, wm)
        hideTarget = HideTargetOverlay(this, wm)
        learner = LearnWatcher(isKnown = { c -> prefs.dictionary().get()[c.from] == c.to })
        imePackages = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }.toSet()
        Log.i(tag, "service connected; ime=$imePackages")
        if (BuildConfig.DEBUG) registerDebugReceiver()
    }

    /**
     * DEBUG BUILDS ONLY: `adb shell am broadcast -a com.pharrlabs.voice.DEBUG_DICTATE --es wav jfk.wav`
     * runs a WAV from the app's external files dir through the real pipeline (transcribe, clean,
     * inject) into the focused field, so end-to-end behaviour can be verified without speaking.
     * `--es text "..."` skips transcription and runs Cleaner + Injector on the given raw text.
     */
    private fun registerDebugReceiver() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                if (bubble == null && !intent.hasExtra("pref") && !intent.hasExtra("sstream")) { Log.w(tag, "debug dictate: no bubble/focused field"); return }
                // `--es pref period_mode=Never` sets a SharedPreferences string/boolean/int for feature checks.
                intent.getStringExtra("pref")?.let { kv ->
                    val (k, v) = kv.split("=", limit = 2)
                    val sp = getSharedPreferences("pharr_voice", MODE_PRIVATE).edit()
                    when {
                        v == "true" || v == "false" -> sp.putBoolean(k, v.toBoolean())
                        v.toIntOrNull() != null -> sp.putInt(k, v.toInt())
                        else -> sp.putString(k, v)
                    }
                    sp.commit()
                    Log.i(tag, "debug pref $k=$v")
                    return
                }
                val wav = intent.getStringExtra("wav")
                // `--es play jfk.wav`: play a WAV out of the speaker so the real mic path (tap the bubble
                // to record, tap again to stop) can be exercised without a human voice.
                intent.getStringExtra("play")?.let { name ->
                    Thread {
                        val pcm = readWav(java.io.File(getExternalFilesDir(null), name))
                        val track = android.media.AudioTrack.Builder()
                            .setAudioAttributes(android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                            .setAudioFormat(android.media.AudioFormat.Builder()
                                .setSampleRate(16000).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                            .setBufferSizeInBytes(pcm.size * 2).build()
                        track.write(pcm, 0, pcm.size); track.play()
                        Thread.sleep(pcm.size * 1000L / 16000 + 500); track.release()
                    }.start()
                    return
                }
                // `--es sstream jfk33.wav`: replays a WAV into a StreamingSession at 4x real time and logs how long
                // the final tail took versus transcribing the whole clip in one go.
                intent.getStringExtra("sstream")?.let { name ->
                    scope.launch(Dispatchers.Default) {
                        val pcm = readWav(java.io.File(getExternalFilesDir(null), name))
                        val sess = StreamingSession(getEngine())
                        var t = 16000
                        while (t < pcm.size) { sess.poll(pcm.copyOf(t)); delay(250); t += 16000 }
                        val t0 = System.currentTimeMillis()
                        val text = sess.finish(pcm)
                        Log.i(tag, "sstream tail=${System.currentTimeMillis() - t0}ms text='$text'")
                        val t1 = System.currentTimeMillis()
                        val whole = getEngine().transcribe(SilenceTrimmer.trim(pcm))
                        Log.i(tag, "sstream whole=${System.currentTimeMillis() - t1}ms text='$whole'")
                    }
                    return
                }
                val rawText = intent.getStringExtra("text")
                if (machine.state != BubbleState.Idle) { Log.w(tag, "debug dictate: busy"); return }
                machine.onTap(); machine.onTap() // Idle -> Listening -> Processing
                bubble?.setState(machine.state)
                if (rawText != null) {
                    runPipeline(source = { ShortArray(16000) }, rawOverride = rawText)
                } else {
                    runPipeline(source = { readWav(java.io.File(getExternalFilesDir(null), wav ?: "jfk.wav")) })
                }
            }
        }
        val filter = android.content.IntentFilter("com.pharrlabs.voice.DEBUG_DICTATE")
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        debugReceiver = receiver
    }

    private fun readWav(f: java.io.File): ShortArray {
        val bytes = f.readBytes()
        val bb = java.nio.ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        return ShortArray((bytes.size - 44) / 2) { bb.short }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (BuildConfig.DEBUG && event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            Log.v(tag, "event ${AccessibilityEvent.eventTypeToString(event.eventType)} pkg=${event.packageName} cls=${event.className}")
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val node = event.source ?: return
                // Typing/injecting fires selection events from containers; only a real focus change
                // may hide the bubble, so selection events matter only for editable fields.
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED && (!node.isEditable || !node.isFocused)) return
                pendingNode = node
                pendingPkg = event.packageName?.toString().orEmpty()
                main.removeCallbacks(evaluate)
                main.postDelayed(evaluate, 150) // debounce rapid focus switching
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (prefs.autoLearn && event.packageName?.toString() == focusedPkg) {
                    main.removeCallbacks(checkLearn)
                    main.postDelayed(checkLearn, 1500) // wait for the user to finish the edit
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                // Keyboards, our own overlay and system UI chrome do not mean the user left the field.
                if (pkg in imePackages || pkg == packageName || pkg == "com.android.systemui") return
                // Autofill/suggestion popups (e.g. com.google.android.ext.services) are separate windows
                // but not the user leaving the field: only the ACTIVE window's package decides.
                val active = rootInActiveWindow?.packageName?.toString()
                if (bubble != null && active != null && active != focusedPkg &&
                    active !in imePackages && active != packageName && active != "com.android.systemui"
                ) hideBubble("active window changed to $active")
                if (bubble == null) { main.removeCallbacks(probe); main.postDelayed(probe, 400) }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (bubble != null) {
                    main.removeCallbacks(reanchor)
                    main.postDelayed(reanchor, 120)
                } else if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                    main.removeCallbacks(probe); main.postDelayed(probe, 400)
                }
            }
        }
    }

    override fun onInterrupt() = hideBubble("interrupt")

    override fun onDestroy() {
        if (instance === this) instance = null
        debugReceiver?.let { runCatching { unregisterReceiver(it) } }
        hideBubble("destroy")
        main.removeCallbacksAndMessages(null)
        engine?.close()
        engine = null
        scope.cancel()
        super.onDestroy()
    }

    // ---- focus tracking -------------------------------------------------------------------

    private fun evaluateFocus() {
        val node = pendingNode ?: return
        pendingNode = null
        val show = FieldFilter.shouldShow(node.isEditable, node.isPassword, pendingPkg, packageName, node.hintText?.toString()) &&
            pendingPkg !in prefs.hiddenApps && prefs.bubbleActive()
        if (BuildConfig.DEBUG) Log.v(tag, "node cls=${node.className} id=${node.viewIdResourceName} focused=${node.isFocused} " +
            "text='${node.text}' hint='${node.hintText}' showingHint=${node.isShowingHintText} sel=${node.textSelectionStart}..${node.textSelectionEnd} desc='${node.contentDescription}'")
        Log.i(tag, "focus pkg=$pendingPkg editable=${node.isEditable} password=${node.isPassword} show=$show")
        if (show) showFor(node, pendingPkg) else hideBubble("ineligible field")
    }

    private fun showFor(node: AccessibilityNodeInfo, pkg: String) {
        val previous = focusedNode
        if (previous != null && previous != node && machine.state != BubbleState.Idle) {
            // Focus moved to a different field mid-dictation: drop it rather than type in the wrong place.
            cancelDictation()
        }
        focusedNode = node
        focusedPkg = pkg
        if (bubble == null) {
            bubble = BubbleView(this, gestureFactory = { newGesture() }, onEvent = ::onGesture)
                .also { it.style = prefs.bubbleShape; it.symbol = prefs.bubbleIcon; it.tint = prefs.bubbleTint; it.idleAlpha = prefs.idleOpacityPct / 100f; it.setState(machine.state) }
            hideTarget.attach() // before the bubble, so the bubble draws on top of it
            wm.addView(bubble, layoutParams(0, 0))
            lastX = Int.MIN_VALUE; lastY = Int.MIN_VALUE
        }
        anchor()
    }

    /** Re-anchors, hiding only if the field is still gone on a second look (the keyboard opening flaps it briefly). */
    private fun validateAndAnchor(retry: Boolean = true) {
        val node = focusedNode ?: return
        if (node.refresh() && node.isVisibleToUser) {
            anchor()
        } else if (retry) {
            main.removeCallbacks(reanchor)
            main.postDelayed({ validateAndAnchor(retry = false) }, 300)
        } else {
            hideBubble("field gone")
        }
    }

    private fun layoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        val size = (prefs.bubbleSizeDp * resources.displayMetrics.density).toInt()
        return WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Coordinates are absolute screen pixels: do not shift the window below the status bar.
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            this.x = x
            this.y = y
        }
    }

    /** Puts the bubble at the top-right of the field, clamped to the screen and above the keyboard. */
    private fun bubbleSizePx() = (prefs.bubbleSizeDp * resources.displayMetrics.density).toInt()

    private fun screenBox(): Screen {
        val d = resources.displayMetrics.density
        val screen = wm.currentWindowMetrics.bounds
        val statusBar = resources.getIdentifier("status_bar_height", "dimen", "android")
            .let { if (it > 0) resources.getDimensionPixelSize(it) else (24 * d).toInt() }
        val keyboardTop = windows
            .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { w -> Rect().also { w.getBoundsInScreen(it) }.top }
            ?: screen.bottom
        return Screen(screen.width(), statusBar, keyboardTop, bottom = screen.bottom)
    }

    /** The focused text field's bounds, so the Undo chip can stay off the text. */
    private fun fieldArea(): Area? {
        val node = focusedNode ?: return null
        node.refresh()
        val r = Rect().also { node.getBoundsInScreen(it) }
        return Area(r.left, r.top, r.right, r.bottom)
    }

    /** Where the bubble belongs for the focused field, with the drag offset applied (or not). */
    private fun placement(withOffset: Boolean): Pos? {
        val node = focusedNode ?: return null
        node.refresh()
        val field = Rect().also { node.getBoundsInScreen(it) }
        val margin = (6 * resources.displayMetrics.density).toInt()
        return BubblePlacement.place(
            FieldBox(field.left, field.top, field.right), screenBox(), bubbleSizePx(), margin, prefs.bubbleLeft,
            if (withOffset) prefs.bubbleOffsetX else 0, if (withOffset) prefs.bubbleOffsetY else 0, prefs.bubbleCorner,
        )
    }

    private fun anchor() {
        val view = bubble ?: return
        if (dragging) return
        val p = placement(withOffset = true) ?: return
        if (p.x == lastX && p.y == lastY) return // typing fires many content events; skip no-op relayouts
        lastX = p.x; lastY = p.y
        wm.updateViewLayout(view, layoutParams(p.x, p.y))
    }

    // ---- dragging ---------------------------------------------------------------------------

    private var dragging = false
    private var dragStart = Pos(0, 0)

    private fun onDrag(event: GestureEvent) {
        val view = bubble ?: return
        when (event) {
            GestureEvent.DragStart -> {
                if (machine.state != BubbleState.Idle) return
                dragging = true
                dragStart = Pos(lastX, lastY)
                chip.hide()
                hideTarget.show(screenBox())
            }
            is GestureEvent.DragMove -> if (dragging) {
                val p = BubblePlacement.clamp(
                    Pos(dragStart.x + event.dx.toInt(), dragStart.y + event.dy.toInt()), screenBox(), bubbleSizePx(), prefs.bubbleCorner,
                )
                lastX = p.x; lastY = p.y
                wm.updateViewLayout(view, layoutParams(p.x, p.y))
                hideTarget.area?.let { hideTarget.highlight(BubbleVisibility.overTarget(p, bubbleSizePx(), it)) }
            }
            GestureEvent.DragEnd -> if (dragging) {
                dragging = false
                val target = hideTarget.area
                hideTarget.hide()
                if (target != null && BubbleVisibility.overTarget(Pos(lastX, lastY), bubbleSizePx(), target)) {
                    hideForTenMinutes()
                    return
                }
                val base = placement(withOffset = false) ?: return
                val off = BubblePlacement.offsetFor(Pos(lastX, lastY), base)
                prefs.bubbleOffsetX = off.x
                prefs.bubbleOffsetY = off.y
                Log.i(tag, "bubble moved, offset=${off.x},${off.y}")
            }
            else -> Unit
        }
    }

    /** Re-checks whether the bubble may show, after the tile or a timed hide changed that. */
    private fun onVisibilityChanged() {
        if (!prefs.bubbleActive()) hideBubble("switched off") else main.post(probe)
    }

    private fun hideForTenMinutes() {
        val until = BubbleVisibility.snoozeUntil(System.currentTimeMillis())
        prefs.bubbleHiddenUntil = until
        Toast.makeText(this, "Bubble hidden for 10 minutes. To bring it back sooner, open Mouthpiece and tap Show now, or use the Quick Settings tile.", Toast.LENGTH_LONG).show()
        hideBubble("dragged to the bottom")
        main.removeCallbacks(unsnooze)
        main.postDelayed(unsnooze, BubbleVisibility.SNOOZE_MS + 500)
    }

    private val unsnooze = Runnable { onVisibilityChanged() }

    private fun hideBubble(reason: String) {
        if (bubble == null && focusedNode == null) return
        Log.i(tag, "hide bubble: $reason")
        main.removeCallbacks(evaluate)
        main.removeCallbacks(reanchor)
        main.removeCallbacks(probe)
        cancelDictation()
        chip.hide()
        learner.stop()
        hideTarget.detach()
        bubble?.let { v -> runCatching { wm.removeView(v) } }
        bubble = null
        focusedNode = null
        focusedPkg = null
    }

    /** Abandons any recording/transcription; late results are dropped by the state machine. */
    private fun cancelDictation() {
        pipelineJob?.cancel()
        pipelineJob = null
        holdMode = false
        main.removeCallbacks(autoStopPoll)
        stopStreaming()
        chip.hide()
        val wasActive = machine.state != BubbleState.Idle
        machine.reset()
        if (wasActive) scope.launch { recorder.cancel() } // release the mic only if we may hold it
        bubble?.setState(machine.state)
    }

    // ---- dictation pipeline ---------------------------------------------------------------

    private var holdMode = false
    private var session: StreamingSession? = null
    private var streamJob: Job? = null
    @Volatile private var streamActive = false
    @Volatile private var streamFailed = false

    /** While recording, transcribe each finished phrase in the background so only the last one is left at stop. */
    private fun startStreaming() {
        if (!prefs.streaming) return
        val s = StreamingSession(getEngine(), onText = { text -> if (prefs.liveCaption) main.post { showCaption(text) } })
        session = s
        streamActive = true
        streamFailed = false
        streamJob = scope.launch(Dispatchers.Default) {
            try {
                var ticks = 0
                while (isActive && streamActive) {
                    delay(100) // short steps so stopping never waits on a long sleep
                    if (++ticks % 6 == 0 && streamActive) {
                        s.poll(recorder.copyAll())
                        // Draft of what is being said right now, so the caption keeps up between pauses.
                        if (streamActive && prefs.liveCaption) s.preview(recorder.copyAll())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(tag, "streaming aborted, will transcribe in one go", t)
                streamFailed = true
            }
        }
    }

    private fun showCaption(text: String) {
        if (machine.state == BubbleState.Idle || lastX == Int.MIN_VALUE) return
        val size = (prefs.bubbleSizeDp * resources.displayMetrics.density).toInt()
        chip.show(text, emptyList(), lastX, lastY, size, timeoutMs = 0, screen = screenBox(), field = fieldArea(), liveCaption = true)
    }

    private fun stopStreaming() {
        streamActive = false
        streamJob?.cancel()
        streamJob = null
        session = null
    }

    private val autoStopPoll = object : Runnable {
        override fun run() {
            if (machine.state != BubbleState.Listening || holdMode) return
            if (recorder.silenceReached(prefs.autoStopMs)) {
                Log.i(tag, "auto-stop after ${prefs.autoStopMs} ms of silence")
                onBubbleTap()
            } else main.postDelayed(this, 150)
        }
    }

    /** Feeds the live mic level to the bubble so its pulse follows the voice. */
    private val levelPoll = object : Runnable {
        override fun run() {
            if (machine.state != BubbleState.Listening) return
            bubble?.setLevel((recorder.level / 2500.0).toFloat())
            main.postDelayed(this, 80)
        }
    }

    private fun newGesture(): BubbleGesture {
        val d = resources.displayMetrics.density
        val mode = prefs.interaction
        return BubbleGesture(
            slopPx = 8 * d,
            holdEnabled = mode != Prefs.Interaction.TapOnly,
            tapEnabled = mode != Prefs.Interaction.HoldOnly,
        )
    }

    private fun onGesture(event: GestureEvent) {
        when (event) {
            GestureEvent.Tap -> onBubbleTap()
            GestureEvent.HoldStart -> if (machine.state == BubbleState.Idle) {
                holdMode = true
                onBubbleTap() // Idle -> Listening
            }
            GestureEvent.HoldEnd -> if (holdMode) {
                holdMode = false
                if (machine.state == BubbleState.Listening) onBubbleTap() // -> Processing
            }
            GestureEvent.DragStart, is GestureEvent.DragMove, GestureEvent.DragEnd -> onDrag(event)
        }
    }

    private fun onBubbleTap() {
        when (machine.onTap()) {
            BubbleAction.StartRecording -> {
                chip.hide()
                bubble?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                recorder.start()
                    .onSuccess { startStreaming(); main.post(levelPoll); if (!holdMode && prefs.autoStopMs > 0) main.postDelayed(autoStopPoll, 400) }
                    .onFailure { holdMode = false; fail(it.message ?: "microphone unavailable") }
            }
            BubbleAction.StopAndTranscribe -> {
                bubble?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                val s = session
                session = null
                runPipeline(streamed = s)
            }
            BubbleAction.Nothing, BubbleAction.ShowError -> Unit
        }
        bubble?.setState(machine.state)
    }

    private fun runPipeline(
        source: suspend () -> ShortArray = { recorder.stop() },
        rawOverride: String? = null,
        streamed: StreamingSession? = null,
    ) {
        val target = focusedNode
        pipelineJob = scope.launch {
            val pcm = if (streamed != null) {
                streamActive = false
                streamJob?.join() // let an in-flight phrase finish; it is already part of the session
                streamJob = null
                recorder.stop(trim = false)
            } else source()
            if (BuildConfig.DEBUG) {
                val peak = pcm.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
                val rms = if (pcm.isEmpty()) 0.0 else kotlin.math.sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
                Log.i(tag, "pcm samples=${pcm.size} sec=${pcm.size / 16000.0} peak=$peak rms=${rms.toInt()}")
            }
            machine.onRecorded(if (streamed != null) SilenceTrimmer.trim(pcm).isEmpty() else pcm.isEmpty())
            bubble?.setState(machine.state)
            if (machine.state != BubbleState.Processing) { chip.hide(); return@launch } // empty recording or reset

            val raw = try {
                when {
                    rawOverride != null -> rawOverride
                    streamed != null && !streamFailed -> streamed.finish(pcm)
                    streamed != null -> getEngine().transcribe(SilenceTrimmer.trim(pcm))
                    else -> getEngine().transcribe(pcm)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ModelMissing) {
                fail("Speech model missing. Open Mouthpiece to install it.")
                return@launch
            } catch (t: Throwable) {
                Log.e(tag, "transcription failed", t)
                fail("Transcription failed: ${t.message}")
                return@launch
            }
            if (machine.state != BubbleState.Processing) return@launch

            // The field that has input focus NOW wins over the one the bubble first anchored to
            // (apps like Gmail move focus between subject and body without a clean focus event).
            val live = findInputFocus()?.takeIf { it.isEditable && !it.isPassword && it.packageName == focusedPkg }
            val dest = live ?: target
            val kind = fieldKind(dest)
            if (prefs.voiceEdit) {
                val cmd = VoiceEdit.standalone(raw)
                if (cmd != null && applyVoiceEdit(cmd, dest)) {
                    chip.hide()
                    machine.onInjected()
                    bubble?.setState(machine.state)
                    return@launch
                }
            }
            val appPeriod = prefs.appRules().ruleFor(focusedPkg.orEmpty()).period
            val period = if (appPeriod != PeriodMode.Auto) appPeriod else prefs.periodMode
            val text = Cleaner.clean(raw, prefs.dictionary().get(), singleLine = kind == FieldKind.Short, period = period, smartEdit = prefs.smartEdit, style = prefs.styleFor(focusedPkg.orEmpty()), formatNumbers = prefs.formatNumbers, voiceEdit = prefs.voiceEdit, format = fieldFormat(dest))
            Log.i(tag, "raw='$raw' cleaned='$text' kind=$kind period=$period style=${prefs.styleFor(focusedPkg.orEmpty())}")
            chip.hide()
            if (text.isEmpty()) {
                machine.onInjected()
                bubble?.setState(machine.state)
                return@launch
            }
            if (dest == null || !dest.refresh()) {
                fail("Field lost")
                return@launch
            }
            val result = injector.inject(dest, text)
            Log.i(tag, "inject result=$result")
            if (result == InjectResult.ClipboardOnly) {
                Toast.makeText(this@BubbleService, "Couldn't type here. Text copied, paste it manually.", Toast.LENGTH_LONG).show()
            }
            machine.onInjected()
            bubble?.setState(machine.state)
            injector.lastInsert?.let {
                recentInsert = RecentInsert(focusedPkg.orEmpty(), it.piece, System.currentTimeMillis())
                if (prefs.undoChip) offerUndo(it)
            }
            if (prefs.historyEnabled) {
                prefs.history().add(HistoryEntry(System.currentTimeMillis(), focusedPkg.orEmpty(), raw, text, result.name))
            }
            if (prefs.autoLearn) main.postDelayed({
                val n = findInputFocus() ?: return@postDelayed
                learner.start(baseline = n.text?.toString().orEmpty(), inserted = text)
            }, 700)
        }
    }

    /** The last text a dictation put into a field, so a following "capitalize that" knows what "that" is. */
    private class RecentInsert(val pkg: String, val piece: String, val at: Long)
    private var recentInsert: RecentInsert? = null

    /**
     * Runs a spoken edit command on the previous dictation. Returns false when the utterance should be typed as
     * ordinary text instead (a "replace" whose target is not in the last dictation).
     */
    private fun applyVoiceEdit(cmd: VoiceEdit.Command, dest: AccessibilityNodeInfo?): Boolean {
        val recent = recentInsert?.takeIf { it.pkg == focusedPkg && System.currentTimeMillis() - it.at < 5 * 60_000L }
        if (recent == null || !VoiceEdit.applies(cmd, recent.piece)) {
            if (cmd is VoiceEdit.Command.Replace) return false
            Toast.makeText(this, "Nothing to change yet", Toast.LENGTH_SHORT).show()
            return true
        }
        val node = dest?.takeIf { it.refresh() }
        val current = node?.text?.toString()
        val newPiece = VoiceEdit.applyTo(cmd, recent.piece)
        val edited = if (current != null) PieceEdit.replace(current, recent.piece, newPiece) else null
        if (node == null || edited == null) {
            Toast.makeText(this, "Text changed — nothing to edit", Toast.LENGTH_SHORT).show()
            return true
        }
        learner.stop()
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, edited.text)
        })
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, android.os.Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, edited.selStart)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, edited.selEnd)
        })
        recentInsert = RecentInsert(recent.pkg, newPiece, System.currentTimeMillis())
        Log.i(tag, "voice edit $cmd applied")
        return true
    }

    /** Undo / Retry chip for a few seconds after a dictation. */
    private fun offerUndo(rec: InsertRecord) {
        if (lastX == Int.MIN_VALUE) return
        val size = (prefs.bubbleSizeDp * resources.displayMetrics.density).toInt()
        chip.show(
            message = null,
            actions = listOf(
                ChipAction("Undo") { if (undoInsert(rec)) Log.i(tag, "undo ok") },
                ChipAction("Retry") { if (undoInsert(rec) && machine.state == BubbleState.Idle) onBubbleTap() },
            ),
            anchorX = lastX, anchorY = lastY, bubbleSizePx = size, timeoutMs = 6_000,
            screen = screenBox(), field = fieldArea(),
        )
    }

    /** Puts the field back the way it was before the dictation; false (with a toast) if it changed too much. */
    private fun undoInsert(rec: InsertRecord): Boolean {
        val node = findInputFocus()?.takeIf { it.isEditable && it.packageName == focusedPkg }
        val current = node?.text?.toString()
        val reverted = if (node != null && current != null) Undo.revert(rec, current) else null
        if (node == null || reverted == null) {
            Toast.makeText(this, "Text changed — nothing to undo", Toast.LENGTH_SHORT).show()
            return false
        }
        learner.stop()
        val setArgs = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reverted.text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, android.os.Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, reverted.selStart)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, reverted.selEnd)
        })
        return ok
    }

    /** The user edited the field after a dictation: if it looks like a vocabulary fix, offer to remember it. */
    private fun checkForCorrection() {
        val current = findInputFocus()?.text?.toString() ?: return
        val c = learner.onTextChanged(current) ?: return
        Log.i(tag, "learn offer ${c.from} -> ${c.to}")
        offerLearn(c)
    }

    private fun offerLearn(c: Correction) {
        val size = (prefs.bubbleSizeDp * resources.displayMetrics.density).toInt()
        if (lastX == Int.MIN_VALUE) return
        chip.show(
            message = "Remember “${c.from}” → “${c.to}”?",
            actions = listOf(ChipAction("Yes") {
                prefs.dictionary().put(c.from, c.to)
                Log.i(tag, "learned ${c.from} -> ${c.to}")
                Toast.makeText(this, "Learned: ${c.from} → ${c.to}", Toast.LENGTH_SHORT).show()
            }, ChipAction("No") {}),
            anchorX = lastX, anchorY = lastY, bubbleSizePx = size, timeoutMs = 10_000,
            screen = screenBox(), field = fieldArea(),
        )
    }

    private fun getEngine(): WhisperCppEngine {
        val model = prefs.modelName
        engine?.let { if (engineModel == model) return it else it.close() }
        return WhisperCppEngine(ModelManager(getExternalFilesDir(null)!!).modelFile(model))
            .also { engine = it; engineModel = model }
    }

    private fun fail(reason: String) {
        if (machine.onFailure(reason) != BubbleAction.ShowError) return
        Log.w(tag, "error: $reason")
        chip.hide()
        bubble?.setState(machine.state)
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
        main.postDelayed({
            if (machine.state is BubbleState.Error) {
                machine.onTap() // clears Error -> Idle
                bubble?.setState(machine.state)
            }
        }, 4000)
    }

    companion object {
        @Volatile private var instance: BubbleService? = null

        /** Called by the Quick Settings tile after it changes the on/off state. */
        fun refresh() { instance?.let { svc -> svc.main.post { svc.onVisibilityChanged() } } }
    }
}
