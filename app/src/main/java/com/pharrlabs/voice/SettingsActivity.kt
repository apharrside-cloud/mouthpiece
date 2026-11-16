package com.pharrlabs.voice

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.pharrlabs.voice.bubble.BubbleIcon
import com.pharrlabs.voice.bubble.BubbleTint
import com.pharrlabs.voice.bubble.BubbleService
import com.pharrlabs.voice.bubble.BubbleVisibility
import com.pharrlabs.voice.update.UpdateCheck
import com.pharrlabs.voice.update.UpdateFetcher
import com.pharrlabs.voice.update.UpdateInfo
import com.pharrlabs.voice.bubble.BubbleStyle
import com.pharrlabs.voice.bubble.BubbleView
import com.pharrlabs.voice.asr.ModelManager
import com.pharrlabs.voice.clean.PeriodMode
import com.pharrlabs.voice.clean.Style
import com.pharrlabs.voice.settings.AppRule
import com.pharrlabs.voice.settings.OnboardingChecks
import com.pharrlabs.voice.settings.Palette
import com.pharrlabs.voice.settings.Palettes
import com.pharrlabs.voice.settings.SplashPolicy
import com.pharrlabs.voice.settings.ThemeMode
import com.pharrlabs.voice.ui.FlowLayout
import com.pharrlabs.voice.settings.OnboardingChecks.Step
import com.pharrlabs.voice.settings.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Mouthpiece settings: a tab bar (Home, Dictation, Bubble, Words, Apps, History) over card-style sections.
 * Re-renders on resume so the setup checklist reflects what the user just did in system settings.
 */
class SettingsActivity : Activity() {
    private enum class Tab(val label: String) { Home("Home"), Dictation("Dictation"), Bubble("Bubble"), Words("Words"), Apps("Apps"), History("History") }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: Prefs
    private lateinit var models: ModelManager
    private lateinit var tabBar: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView

    private var tab = Tab.Home
    private var downloadJob: Job? = null
    private var downloadStatus: String? = null
    private var draftSpoken = ""
    private var draftWritten = ""
    private var draftPackage = ""

    private var pal: Palette = Palettes.light
    private val accent get() = pal.accent
    private val accentSoft get() = pal.accentSoft
    private val surface get() = pal.surface
    private val ink get() = pal.ink
    private val muted get() = pal.muted
    private lateinit var page: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var tabScroll: HorizontalScrollView

    /** Picks light or dark colors (own setting, or the phone's), and paints the page and system bars to match. */
    private fun applyTheme() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        pal = Palettes.of(prefs.themeMode, night)
        page.setBackgroundColor(pal.bg)
        window.decorView.setBackgroundColor(pal.bg)
        titleView.setTextColor(pal.ink)
        val lightBars = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (pal.bg == Palettes.light.bg) lightBars else 0, lightBars)
    }

    /** Text boxes must set their own colors: the app theme is dark, so the default text is white. */
    private fun EditText.themed(): EditText = apply {
        setTextColor(pal.ink); setHintTextColor(pal.muted)
        backgroundTintList = android.content.res.ColorStateList.valueOf(pal.outline)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        models = ModelManager(getExternalFilesDir(null)!!)
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleView = TextView(this).apply {
            text = "Mouthpiece"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD; setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        val logo = ImageView(this).apply {
            setImageDrawable(getDrawable(R.mipmap.ic_launcher))
            scaleType = ImageView.ScaleType.FIT_CENTER
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) = o.setRoundRect(0, 0, v.width, v.height, dp(10).toFloat())
            }
            clipToOutline = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), 0, dp(20), 0)
            addView(logo, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        titleView.setPadding(dp(12), dp(16), 0, dp(8))
        header.addView(titleView)
        page.addView(header)
        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), 0, dp(12), dp(8)) }
        tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabBar) }
        page.addView(tabScroll)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(32)) }
        scroll = ScrollView(this).apply { addView(content) }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        scroll.clipToPadding = false
        val root = android.widget.FrameLayout(this)
        root.addView(page, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        if (SplashPolicy.shouldShow(prefs.splashSeen)) { prefs.splashSeen = true; showSplash(root) }
        // Edge-to-edge: keep content clear of the status bar, the gesture bar and the keyboard.
        page.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        applyTheme()
    }

    /** One-time welcome: the Pharr Labs pilot on brand orange, fading away by itself or on tap. */
    private fun showSplash(root: android.widget.FrameLayout) {
        val dark = 0xFF1D1B20.toInt()
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xFFFC7E21.toInt()); isClickable = true
            addView(ImageView(context).apply { setImageResource(R.drawable.splash_mascot); scaleType = ImageView.ScaleType.FIT_CENTER },
                LinearLayout.LayoutParams(dp(260), dp(260)))
            addView(TextView(context).apply {
                text = "Mouthpiece"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f); typeface = Typeface.DEFAULT_BOLD
                setTextColor(dark); gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0)
            })
            addView(TextView(context).apply {
                text = "by Pharr Labs"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(dark); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
            })
        }
        root.addView(splash, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        var gone = false
        val dismiss = {
            if (!gone) {
                gone = true
                splash.animate().alpha(0f).setDuration(SplashPolicy.FADE_MS).withEndAction { root.removeView(splash) }.start()
            }
        }
        splash.setOnClickListener { dismiss() }
        splash.postDelayed({ dismiss() }, SplashPolicy.SHOW_MS)
    }

    override fun onResume() { super.onResume(); render(); maybeCheckForUpdate(force = false) }

    private var updateStatus: String? = null

    /** Reads the site's version file at most twice a day (or on demand) and refreshes the banner. */
    private fun maybeCheckForUpdate(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && !(prefs.updateCheck && UpdateCheck.due(prefs.updateLastCheck, now))) return
        scope.launch {
            val info = UpdateFetcher.latest()
            if (info == null) {
                updateStatus = if (force) "Could not reach the download site." else null
            } else {
                prefs.updateLastCheck = System.currentTimeMillis()
                prefs.updateFound = info.version
                prefs.updateFoundSizeMb = info.sizeMb
                updateStatus = if (UpdateCheck.isNewer(info.version, BuildConfig.VERSION_NAME)) null else "You have the latest version."
            }
            render()
        }
    }

    private fun foundUpdate(): UpdateInfo? = prefs.updateFound?.let { UpdateInfo(it, prefs.updateFoundSizeMb, "") }
        ?.takeIf { UpdateCheck.available(it, BuildConfig.VERSION_NAME, prefs.updateSkipped) }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = render()

    // ---- state ---------------------------------------------------------------------------

    private fun micGranted() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    private fun batteryExempt() = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    // ---- rendering -----------------------------------------------------------------------

    private fun render() {
        val y = scroll.scrollY
        applyTheme()
        renderTabs()
        content.removeAllViews()
        when (tab) {
            Tab.Home -> home()
            Tab.Dictation -> dictation()
            Tab.Bubble -> bubble()
            Tab.Words -> words()
            Tab.Apps -> apps()
            Tab.History -> history()
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun renderTabs() {
        tabBar.removeAllViews()
        Tab.values().forEach { t ->
            tabBar.addView(TextView(this).apply {
                text = t.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                val on = t == tab
                setTextColor(if (on) accent else muted)
                typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = pill(if (on) accentSoft else Color.TRANSPARENT)
                setOnClickListener { tab = t; render() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
            })
        }
        tabBar.post { tabBar.getChildAt(tab.ordinal)?.let { tabScroll.smoothScrollTo((it.left - dp(24)).coerceAtLeast(0), 0) } }
    }

    private fun home() {
        bubbleStatusCard()
        foundUpdate()?.let { u ->
            card("Update available") {
                text("Mouthpiece ${u.version} is ready${if (u.sizeMb.isNotBlank()) " (${u.sizeMb} MB)" else ""}. You have ${BuildConfig.VERSION_NAME}. Your settings and dictionary stay as they are.")
                row {
                    button("Download") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateFetcher.SITE))) }
                    button("Skip this version") { prefs.updateSkipped = u.version; render() }
                }
            }
        }
        val missing = OnboardingChecks.missing(micGranted(), serviceEnabled(), models.isInstalled(prefs.modelName), batteryExempt())
        if (missing.isEmpty()) {
            card("Ready") {
                text("Tap a text field in any app, then tap the orange mic bubble, or press and hold it, speak, and let go. Speech never leaves this phone.")
            }
        } else {
            card("Finish setup") { missing.forEach { stepRow(this, it) } }
        }

        card("Try it here") {
            text("Tap this box, then use the bubble to see how dictation behaves.")
            addView(EditText(context).apply {
                hint = com.pharrlabs.voice.bubble.FieldFilter.TRY_HINT; minLines = 3; gravity = Gravity.TOP
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }.themed())
        }

        card("Speech model") {
            text("Bigger models make fewer mistakes but take longer after you stop talking and need more memory. Each downloads once and runs on your phone. ${com.pharrlabs.voice.asr.ModelInfo.RUNTIME}")
            Prefs.MODELS.forEach { (name, label) ->
                val installed = models.isInstalled(name)
                choice("$label${if (installed) "" else " (not downloaded)"}", prefs.modelName == name) {
                    prefs.modelName = name; downloadStatus = null; render()
                }
                com.pharrlabs.voice.asr.ModelInfo.of(name)?.let { info ->
                    addView(styledText(info.line()).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); alpha = 0.7f; setPadding(dp(34), 0, 0, dp(4)) })
                }
                // The model in use can't be removed; switch to another one first.
                if (installed && prefs.modelName != name) {
                    row {
                        button("Remove download") {
                            val mb = models.remove(name) / 1_000_000
                            toast("Removed, freed $mb MB")
                            render()
                        }
                    }
                }
            }
        }

        card("Updates") {
            text("Mouthpiece ${BuildConfig.VERSION_NAME}")
            switchRow("Check for updates", "Opening this screen looks at the download site's version file about twice a day. Nothing about you or your phone is sent.", prefs.updateCheck) { prefs.updateCheck = it }
            row { button("Check now") { updateStatus = "Checking…"; render(); maybeCheckForUpdate(force = true) } }
            updateStatus?.let { text(it) }
        }

        card("Appearance") {
            ThemeMode.values().forEach { m -> choice(m.label, prefs.themeMode == m) { prefs.themeMode = m; render() } }
        }

        card("Backup") {
            text("Save your dictionary, app rules and preferences to a file, or restore them on a new phone. History is not included.")
            row {
                button("Export") {
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "pharr-voice-backup.json")
                    }, REQ_EXPORT)
                }
                button("Import") {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                    }, REQ_IMPORT)
                }
            }
        }
    }

    private fun dictation() {
        card("How you start") {
            Prefs.Interaction.values().forEach { m -> choice(m.label, prefs.interaction == m) { prefs.interaction = m; render() } }
            text("Tap: keeps listening until you tap the bubble again. Hold: records only while your finger is down.")
        }
        card("Stop on silence (tap mode)") {
            val options = listOf(0 to "Off", 2000 to "2 s", 3000 to "3 s", 5000 to "5 s", 8000 to "8 s")
            chips { options.forEach { (ms, label) -> chipButton(label, prefs.autoStopMs == ms) { prefs.autoStopMs = ms; render() } } }
            text(if (prefs.autoStopMs == 0) "Off: tap again to finish, so a long pause never cuts you off." else "A tap-started dictation ends after ${prefs.autoStopMs / 1000} s of silence once you have spoken.")
        }
        card("Writing") {
            switchRow("Smart edit", "\u201Cscratch that\u201D, \u201Cno wait\u201D and \u201CI mean\u201D fix what you just said.", prefs.smartEdit) { prefs.smartEdit = it }
            switchRow("Edit by voice", "\u201Ccapitalize that\u201D, \u201Call caps\u201D, \u201Clowercase that\u201D and \u201Creplace noon with one\u201D change what you just dictated.", prefs.voiceEdit) { prefs.voiceEdit = it }
            switchRow("Numbers, times and web addresses", "“twenty five dollars” becomes $25, “three thirty pm” becomes 3:30 PM, “dot com” becomes .com.", prefs.formatNumbers) { prefs.formatNumbers = it }
            switchRow("Learn from my corrections", "Offer to remember a word when you fix it.", prefs.autoLearn) { prefs.autoLearn = it }
            label("Closing period")
            chips { PeriodMode.values().forEach { m -> chipButton(m.name, prefs.periodMode == m) { prefs.periodMode = m; render() } } }
            label("Default style")
            chips { Style.values().forEach { s -> chipButton(s.label, prefs.defaultStyle == s) { prefs.defaultStyle = s; render() } } }
        }
        card("While you talk") {
            switchRow("Transcribe as you go", "Finished phrases are processed while you keep talking, so there is less waiting at the end.", prefs.streaming) { prefs.streaming = it }
            switchRow("Live caption", "Show the words recognised so far next to the bubble.", prefs.liveCaption) { prefs.liveCaption = it }
            switchRow("Show Undo / Retry after dictating", "A small chip next to the bubble for a few seconds. Turn it off to never see it.", prefs.undoChip) { prefs.undoChip = it }
        }
    }

    private var lookPreview: BubbleView? = null

    /** Makes the preview bubble match the saved size and idle opacity, live while a slider moves. */
    private fun applyPreviewLook() {
        val v = lookPreview ?: return
        v.idleAlpha = prefs.idleOpacityPct / 100f
        v.layoutParams?.let { lp ->
            val px = dp(prefs.bubbleSizeDp)
            if (lp.width != px || lp.height != px) { lp.width = px; lp.height = px; v.layoutParams = lp }
        }
    }

    /** Says plainly when the bubble is switched off or hidden, with a button to bring it back right away. */
    private fun bubbleStatusCard() {
        val note = BubbleVisibility.notice(prefs.bubbleOn, prefs.bubbleHiddenUntil, System.currentTimeMillis()) ?: return
        card("Bubble not showing") {
            text("$note. It will not appear in any app until you bring it back.")
            row {
                button("Show now") {
                    prefs.bubbleOn = true; prefs.bubbleHiddenUntil = 0L
                    BubbleService.refresh(); render()
                }
            }
        }
    }

    private fun bubble() {
        bubbleStatusCard()
        card("Look") {
            row {
                gravity = Gravity.TOP
                val preview = BubbleView(this@SettingsActivity, gestureFactory = { error("preview") }, onEvent = {}).apply {
                    interactive = false
                    style = prefs.bubbleShape
                    symbol = prefs.bubbleIcon
                    tint = prefs.bubbleTint
                }
                lookPreview = preview
                applyPreviewLook()
                // A fixed slot as big as the largest bubble, so the card does not jump while the size slider moves.
                val slot = android.widget.FrameLayout(context)
                slot.addView(preview, android.widget.FrameLayout.LayoutParams(dp(prefs.bubbleSizeDp), dp(prefs.bubbleSizeDp), Gravity.CENTER))
                addView(slot, LinearLayout.LayoutParams(dp(84), dp(84)).apply { topMargin = dp(10); marginEnd = dp(12) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    label("Shape")
                    chips { BubbleStyle.values().forEach { st -> chipButton(st.label, prefs.bubbleShape == st) { prefs.bubbleShape = st; render() } } }
                    label("Icon")
                    chips { BubbleIcon.values().forEach { ic -> chipButton(ic.label, prefs.bubbleIcon == ic) { prefs.bubbleIcon = ic; render() } } }
                    label("Color")
                    chips { BubbleTint.values().forEach { t -> swatch(t, prefs.bubbleTint == t) { prefs.bubbleTint = t; render() } } }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        card("Size") {
            val readout = styledText("${prefs.bubbleSizeDp} dp").also { addView(it) }
            seek(max = 40, progress = prefs.bubbleSizeDp - 40) {
                prefs.bubbleSizeDp = 40 + it
                readout.text = "${prefs.bubbleSizeDp} dp"
                applyPreviewLook()
            }
        }
        card("Idle opacity") {
            val readout = styledText("${prefs.idleOpacityPct}%").also { addView(it) }
            seek(max = 75, progress = prefs.idleOpacityPct - 25) {
                prefs.idleOpacityPct = 25 + it
                readout.text = "${prefs.idleOpacityPct}%"
                applyPreviewLook()
            }
        }
        card("Position") {
            choice("Right of the field", !prefs.bubbleLeft) { prefs.bubbleLeft = false; prefs.bubbleOffsetX = 0; prefs.bubbleOffsetY = 0; render() }
            choice("Left of the field", prefs.bubbleLeft) { prefs.bubbleLeft = true; prefs.bubbleOffsetX = 0; prefs.bubbleOffsetY = 0; render() }
            text("You can also drag the bubble anywhere; it remembers where you put it.")
            if (prefs.bubbleOffsetX != 0 || prefs.bubbleOffsetY != 0) {
                button("Reset dragged position") { prefs.bubbleOffsetX = 0; prefs.bubbleOffsetY = 0; render() }
            }
        }
    }

    private fun words() {
        val dictionary = prefs.dictionary()
        card("Dictionary") {
            text("When you say the left side, it is written as the right side.")
            dictionary.get().toSortedMap().forEach { (spoken, written) ->
                row {
                    addView(TextView(context).apply {
                        text = "$spoken  \u2192  $written"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTextColor(ink)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    button("Remove") { dictionary.remove(spoken); render() }
                }
            }
        }
        card("Add a word") {
            val spokenField = EditText(this@SettingsActivity).apply {
                hint = "When I say…"; setText(draftSpoken); inputType = InputType.TYPE_CLASS_TEXT
                setOnFocusChangeListener { _, _ -> draftSpoken = text.toString() }
            }.themed()
            val writtenField = EditText(this@SettingsActivity).apply {
                hint = "Write…"; setText(draftWritten); inputType = InputType.TYPE_CLASS_TEXT
                setOnFocusChangeListener { _, _ -> draftWritten = text.toString() }
            }.themed()
            addView(spokenField); addView(writtenField)
            button("Add to dictionary") {
                if (dictionary.put(spokenField.text.toString(), writtenField.text.toString())) {
                    draftSpoken = ""; draftWritten = ""; render()
                } else toast("Both fields are required")
            }
        }
    }

    private fun apps() {
        val rules = prefs.appRules()
        val hidden = prefs.hiddenApps
        val known = (rules.all().keys + hidden).toSortedSet()
        card("Per-app rules") {
            text("Choose how each app gets its text. Gmail, Messages and a few others already have built-in styles.")
            if (known.isEmpty()) text("No custom apps yet.")
            known.forEach { pkg ->
                val rule = rules.ruleFor(pkg)
                label(appName(pkg))
                chips {
                    chipButton("Period: ${rule.period.name}", rule.period != PeriodMode.Auto) {
                        val next = PeriodMode.values()[(rule.period.ordinal + 1) % PeriodMode.values().size]
                        rules.set(pkg, rule.copy(period = next)); render()
                    }
                    chipButton("Style: ${rule.style?.label ?: prefs.styleFor(pkg).label}", rule.style != null) {
                        val order = listOf<Style?>(null) + Style.values().toList()
                        rules.set(pkg, rule.copy(style = order[(order.indexOf(rule.style) + 1) % order.size])); render()
                    }
                    chipButton(if (pkg in hidden) "Bubble: hidden" else "Bubble: shown", pkg in hidden) {
                        prefs.hiddenApps = if (pkg in hidden) hidden - pkg else hidden + pkg; render()
                    }
                }
            }
        }
        card("Add an app") {
            val recent = prefs.history().all().map { it.pkg }.filter { it.isNotBlank() && it !in known }.distinct().take(6)
            if (recent.isNotEmpty()) {
                text("Recently dictated into (tap to add with Period: Never):")
                chips { recent.forEach { pkg -> chipButton(appName(pkg), false) { prefs.appRules().set(pkg, AppRule(period = PeriodMode.Never)); render() } } }
            }
            val field = EditText(this@SettingsActivity).apply {
                hint = "Package name, e.g. com.google.android.gm"; setText(draftPackage)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setOnFocusChangeListener { _, _ -> draftPackage = text.toString() }
            }.themed()
            addView(field)
            button("Add") {
                val pkg = field.text.toString().trim()
                if (pkg.contains('.')) { prefs.appRules().set(pkg, AppRule(period = PeriodMode.Never)); draftPackage = ""; render() }
                else toast("Enter a package name")
            }
        }
    }

    private fun history() {
        val h = prefs.history()
        card("History") {
            switchRow("Keep a log of recent dictations", "Stays on this phone. Handy when an app swallows the text.", prefs.historyEnabled) { prefs.historyEnabled = it }
        }
        val entries = h.all()
        if (entries.isEmpty()) {
            card("Nothing yet") { text("Your last 20 dictations will appear here.") }
        } else {
            entries.forEach { e ->
                card("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.timeMs))} \u00B7 ${appName(e.pkg)}") {
                    text(e.text)
                    row {
                        button("Copy") {
                            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Mouthpiece", e.text))
                            toast("Copied")
                        }
                        button("Delete") { h.remove(e.timeMs); render() }
                    }
                }
            }
            card("Clear") { button("Clear history") { h.clear(); render() } }
        }
    }

    private fun stepRow(box: LinearLayout, step: Step) {
        when (step) {
            Step.GrantMic -> {
                box.text("Microphone access is needed to hear you.")
                box.button("Grant microphone permission") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
            }
            Step.AllowRestrictedSettings -> {
                box.text("Android blocks accessibility for apps installed outside the Play Store. First tap Mouthpiece once in Accessibility settings (it will say \"Restricted setting\"; tap OK). Then open App info, tap the \u22EE menu (top right) and choose \"Allow restricted settings\". The menu only appears after the first step.")
                box.button("Open Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                box.button("Open App info") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            }
            Step.EnableService -> {
                box.text("Turn on Mouthpiece under Installed apps so the bubble can appear over text fields.")
                box.button("Open Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            Step.DownloadModel -> {
                box.text("Download the speech model once (internet needed only for this). It runs fully offline afterwards.")
                val running = downloadJob?.isActive == true
                box.button(if (running) "Downloading…" else "Download model") { if (!running) startDownload() }
                downloadStatus?.let { box.text(it) }
            }
            Step.BatteryExemption -> {
                box.text("Recommended: exempt from battery optimization so Android does not stop the bubble in the background.")
                box.button("Allow background use") { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            }
        }
    }

    private fun startDownload() {
        val name = prefs.modelName
        downloadStatus = "Starting…"
        downloadJob = scope.launch {
            val result = models.download(name) { p ->
                runOnUiThread { downloadStatus = "Downloading… ${(p * 100).toInt()}%"; if (!isDestroyed) render() }
            }
            downloadJob = null
            downloadStatus = result.fold(
                onSuccess = { null },
                onFailure = { "Download failed: ${it.message}. Check your connection and try again." },
            )
            if (!isDestroyed) render()
        }
        render()
    }

    // ---- backup ----------------------------------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_EXPORT -> toast(runCatching {
                contentResolver.openOutputStream(uri)!!.use { it.write(prefs.exportBackup().toByteArray()) }
                "Backup saved"
            }.getOrElse { "Export failed: ${it.message}" })
            REQ_IMPORT -> toast(runCatching {
                val json = contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
                prefs.importBackup(json)?.let { "Restored $it settings" } ?: "That file is not a Mouthpiece backup"
            }.getOrElse { "Import failed: ${it.message}" })
        }
        render()
    }

    // ---- view helpers --------------------------------------------------------------------

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun appName(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.ifBlank { "Unknown app" })

    private fun pill(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat() }

    private fun styledText(t: String) = TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(muted); setPadding(0, dp(4), 0, dp(4))
    }

    /**
     * A pill button drawn as a padded TextView that wraps its own height. A framework Button with a fixed
     * height and its own insets/min-size rules clipped the label's bottom edge; this cannot.
     */
    private fun styledButton(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(pal.onAccent)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        minHeight = dp(48)
        minWidth = dp(88)
        setPadding(dp(22), dp(13), dp(22), dp(13))
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), pill(accent), null,
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8); bottomMargin = dp(6); marginEnd = dp(8) }
    }

    /** A rounded card with a title; [body] adds children to it. */
    private fun card(title: String, body: LinearLayout.() -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(surface); cornerRadius = dp(20).toFloat() }
            setPadding(dp(18), dp(14), dp(18), dp(14))
            addView(TextView(context).apply {
                text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); setTextColor(ink); typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(4))
            })
        }
        content.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        c.body()
    }

    private fun LinearLayout.text(t: String) = addView(styledText(t))

    private fun LinearLayout.label(t: String) = addView(TextView(context).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(ink); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(10), 0, dp(2))
    })

    private fun LinearLayout.button(t: String, onClick: () -> Unit) = addView(styledButton(t, onClick))

    private fun LinearLayout.row(body: LinearLayout.() -> Unit) {
        val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addView(r)
        r.body()
    }

    /** Option chips that wrap onto extra lines, so every choice is visible without sideways scrolling. */
    private fun LinearLayout.chips(body: FlowLayout.() -> Unit) {
        val f = FlowLayout(context, hGapPx = dp(8), vGapPx = dp(8))
        f.setPadding(0, dp(6), 0, dp(6))
        addView(f)
        f.body()
    }

    /** A round color dot for picking the bubble color; the chosen one gets a ring in the text color. */
    private fun FlowLayout.swatch(t: BubbleTint, on: Boolean, onClick: () -> Unit) = addView(object : View(context) {
        // FlowLayout offers "at most the full row", which a plain View would take literally; force a fixed round dot.
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(36), dp(36))
    }.apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(t.argb)
            setStroke(if (on) dp(3) else dp(1), if (on) ink else pal.outline)
        }
        contentDescription = t.label + if (on) ", selected" else ""
        setOnClickListener { onClick() }
    })

    private fun FlowLayout.chipButton(t: String, on: Boolean, onClick: () -> Unit) = addView(TextView(context).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(if (on) accent else ink)
        background = GradientDrawable().apply {
            setColor(if (on) accentSoft else pal.chipBg); cornerRadius = dp(18).toFloat()
            setStroke(dp(1), if (on) accent else pal.outline)
        }
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { onClick() }
        minHeight = dp(40); gravity = Gravity.CENTER_VERTICAL
    })

    private fun LinearLayout.choice(t: String, selected: Boolean, onClick: () -> Unit) = addView(TextView(context).apply {
        text = "${if (selected) "\u25CF" else "\u25CB"}  $t"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(if (selected) accent else ink); setPadding(0, dp(10), 0, dp(10)); setOnClickListener { onClick() }
    })

    private fun LinearLayout.switchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
        val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        r.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTextColor(ink) })
            addView(TextView(context).apply { text = sub; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(muted) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(Switch(context).apply {
            isChecked = on
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = android.content.res.ColorStateList(states, intArrayOf(pal.accent, pal.outline))
            trackTintList = android.content.res.ColorStateList(states, intArrayOf(pal.accentSoft, pal.surface))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        })
        addView(r)
    }

    private fun LinearLayout.seek(max: Int, progress: Int, onChange: (Int) -> Unit) = addView(SeekBar(context).apply {
        this.max = max; this.progress = progress.coerceIn(0, max)
        progressTintList = android.content.res.ColorStateList.valueOf(pal.accent)
        thumbTintList = android.content.res.ColorStateList.valueOf(pal.accent)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
            override fun onStartTrackingTouch(s: SeekBar) = Unit
            override fun onStopTrackingTouch(s: SeekBar) = render()
        })
    })

    private companion object {
        const val REQ_EXPORT = 101
        const val REQ_IMPORT = 102
    }
}
