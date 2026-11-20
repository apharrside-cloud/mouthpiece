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
import android.widget.ImageButton
import android.widget.PopupWindow
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
import com.pharrlabs.voice.feedback.Feedback
import com.pharrlabs.voice.feedback.FeedbackKind
import com.pharrlabs.voice.feedback.FeedbackSender
import com.pharrlabs.voice.feedback.SendResult
import com.pharrlabs.voice.update.UpdateCheck
import com.pharrlabs.voice.update.UpdateFetcher
import com.pharrlabs.voice.update.UpdateInfo
import com.pharrlabs.voice.update.UpdateJobService
import com.pharrlabs.voice.update.UpdateNotifier
import com.pharrlabs.voice.update.ReleaseNotes
import com.pharrlabs.voice.bubble.BubbleStyle
import com.pharrlabs.voice.bubble.BubbleView
import com.pharrlabs.voice.asr.ModelManager
import com.pharrlabs.voice.asr.ModelSelection
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
 * Mouthpiece settings: a page menu over card-style sections.
 * Re-renders on resume so the setup checklist reflects what the user just did in system settings.
 */
class SettingsActivity : Activity() {
    private enum class Tab(val label: String) { Home("Home"), Dictation("Dictation"), Bubble("Bubble"), Words("Words"), Apps("Apps"), History("History"), WhatsNew("What’s new") }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: Prefs
    private lateinit var models: ModelManager
    private lateinit var menuButton: ImageButton
    private lateinit var pageLabel: TextView
    private lateinit var header: LinearLayout
    private var pageMenu: PopupWindow? = null
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView

    private var tab = Tab.Home
    private var downloadJob: Job? = null
    private var downloadStatus: String? = null
    private var downloadingModel: String? = null
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

    /** Picks light or dark colors (own setting, or the phone's), and paints the page and system bars to match. */
    private fun applyTheme() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        pal = Palettes.of(prefs.themeMode, night)
        page.setBackgroundColor(pal.bg)
        window.decorView.setBackgroundColor(pal.bg)
        titleView.setTextColor(pal.ink)
        pageLabel.setTextColor(pal.muted)
        menuButton.imageTintList = android.content.res.ColorStateList.valueOf(pal.ink)
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
        Thread { models.removeRetired() }.start()
        tab = savedInstanceState?.getString("page")?.let { name -> Tab.values().find { it.name == name } } ?: Tab.Home
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
        header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(16), dp(8))
        }
        menuButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_menu)
            contentDescription = "Open page menu"
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22000000), null, null,
            )
            setOnClickListener { showPageMenu() }
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(logo, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(8) })
        titleView.setPadding(0, 0, 0, 0)
        pageLabel = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(titleView)
            addView(pageLabel)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(header)
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

    override fun onResume() {
        super.onResume()
        selectAvailableModel()
        if (intent.action == ACTION_SELECT_MODEL) {
            tab = Tab.Home
            scroll.scrollTo(0, 0)
            intent.action = null
        }
        if (intent.action == ACTION_SHOW_UPDATES) {
            tab = Tab.WhatsNew
            scroll.scrollTo(0, 0)
            intent.action = null
        }
        UpdateJobService.schedule(this)
        render()
        maybeCheckForUpdate(force = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SELECT_MODEL) { tab = Tab.Home; scroll.scrollTo(0, 0); render() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", tab.name)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        pageMenu?.dismiss()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        pageMenu?.dismiss()
        render()
    }

    private fun selectAvailableModel() {
        ModelSelection.resolve(prefs.modelName, Prefs.MODELS.map { it.first }.filter(models::isInstalled))?.let { prefs.modelName = it }
    }

    private var updateStatus: String? = null
    private var updateJob: Job? = null

    /** Reads the site's version file at most twice a day (or on demand) and refreshes the update controls. */
    private fun maybeCheckForUpdate(force: Boolean) {
        val now = System.currentTimeMillis()
        if (updateJob?.isActive == true) return
        if (!force && !(prefs.updateCheck && UpdateCheck.due(prefs.updateLastCheck, now))) return
        if (force) { updateStatus = "Checking…"; render() }
        updateJob = scope.launch {
            val info = UpdateFetcher.latest()
            if (info != null) {
                UpdateNotifier.record(this@SettingsActivity, info, notify = !force)
            }
            if (force) {
                // Asking explicitly means "tell me what's out there": bring back a version skipped earlier and say the result in place.
                if (info != null && UpdateCheck.isNewer(info.version, BuildConfig.VERSION_NAME)) prefs.updateSkipped = null
                updateStatus = UpdateCheck.manualMessage(info, BuildConfig.VERSION_NAME)
                val newer = info != null && UpdateCheck.isNewer(info.version, BuildConfig.VERSION_NAME)
                toast(if (info == null) "Couldn't reach the download site" else if (newer) "Update available: ${info.version}" else "You're up to date")
            }
            updateJob = null
            render()
        }
    }

    private fun foundUpdate(): UpdateInfo? = prefs.updateFound?.let { UpdateInfo(it, prefs.updateFoundSizeMb, "") }
        ?.takeIf { UpdateCheck.available(it, BuildConfig.VERSION_NAME, prefs.updateSkipped) }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) {
            prefs.updateNotifications = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            if (prefs.updateNotifications) notifyAboutKnownUpdate()
        }
        render()
    }

    private fun notifyAboutKnownUpdate() {
        foundUpdate()?.let { UpdateNotifier.record(this, it, notify = true) }
    }

    private fun enableUpdateNotifications(enabled: Boolean) {
        if (!enabled) {
            prefs.updateNotifications = false
            UpdateNotifier.cancel(this)
        } else if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        } else {
            prefs.updateNotifications = true
            notifyAboutKnownUpdate()
        }
        render()
    }

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
        pageLabel.text = tab.label
        content.removeAllViews()
        when (tab) {
            Tab.Home -> home()
            Tab.Dictation -> dictation()
            Tab.Bubble -> bubble()
            Tab.Words -> words()
            Tab.Apps -> apps()
            Tab.History -> history()
            Tab.WhatsNew -> whatsNew()
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun showPageMenu() {
        pageMenu?.let { it.dismiss(); return }
        val tiles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val menu = PopupWindow(this).apply {
            contentView = ScrollView(this@SettingsActivity).apply { addView(tiles) }
            width = page.width - page.paddingLeft - page.paddingRight - dp(32)
            val location = IntArray(2).also(header::getLocationOnScreen)
            val availableHeight = window.decorView.height - page.paddingBottom - location[1] - header.height - dp(8)
            height = minOf(dp(Tab.values().size * 56 + 12), availableHeight.coerceAtLeast(dp(48)))
            isFocusable = true
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(pal.bg)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), pal.outline)
            })
            setOnDismissListener {
                pageMenu = null
                menuButton.setImageResource(R.drawable.ic_menu)
                menuButton.contentDescription = "Open page menu"
            }
        }
        Tab.values().forEach { t ->
            val current = t == tab
            tiles.addView(TextView(this).apply {
                text = t.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(ink)
                typeface = if (current) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(52)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(accentSoft),
                    GradientDrawable().apply {
                        setColor(if (current) accentSoft else surface)
                        cornerRadius = dp(10).toFloat()
                    }, null,
                )
                isSelected = current
                isFocusable = true
                setOnClickListener {
                    menu.dismiss()
                    tab = t
                    scroll.scrollTo(0, 0)
                    render()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (t != Tab.Home) topMargin = dp(4)
            })
        }
        pageMenu = menu
        menuButton.setImageResource(R.drawable.ic_close)
        menuButton.contentDescription = "Close page menu"
        menu.showAsDropDown(header, dp(16), 0, Gravity.START)
    }

    private fun home() {
        bubbleStatusCard()
        val missing = OnboardingChecks.missing(micGranted(), serviceEnabled(), models.isInstalled(prefs.modelName), batteryExempt())
        if (missing.isEmpty()) {
            card("Ready") {
                text("Tap a text field in any app, then tap the orange mic bubble, or press and hold it, speak, and let go. Speech never leaves this phone.")
            }
        } else {
            card("Finish setup") { missing.forEach { stepRow(this, it) } }
        }

        speechModels()

        card("Try it here") {
            text("Tap this box, then use the bubble to see how dictation behaves.")
            addView(EditText(context).apply {
                hint = com.pharrlabs.voice.bubble.FieldFilter.TRY_HINT; minLines = 3; gravity = Gravity.TOP
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }.themed())
        }

        card("Updates") {
            text("Mouthpiece ${BuildConfig.VERSION_NAME}")
            switchRow("Automatic update checks", "Check about every 12 hours in the background and when settings opens. Android may delay checks to save battery. Nothing about you or your phone is sent.", prefs.updateCheck) {
                prefs.updateCheck = it
                UpdateJobService.schedule(this@SettingsActivity)
            }
            switchRow("Update notifications", "Let this phone alert you when a new version is available. Turn on automatic checks to receive background alerts.", prefs.updateNotifications) { enableUpdateNotifications(it) }
            if (!UpdateNotifier.allowed(this@SettingsActivity)) {
                text("Android notification permission is needed for update alerts.")
                addView(compactAction("Notification settings") {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                })
            }
            val update = foundUpdate()
            row {
                addView(compactAction(if (updateStatus == "Checking…") "Checking…" else "Check for updates") { maybeCheckForUpdate(force = true) },
                    LinearLayout.LayoutParams(if (update == null) ViewGroup.LayoutParams.WRAP_CONTENT else 0, ViewGroup.LayoutParams.WRAP_CONTENT, if (update == null) 0f else 1f))
                update?.let { u ->
                    addView(compactAction("Download update", primary = true) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateFetcher.SITE))) },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
                }
            }
            val msg = updateStatus ?: update?.let { UpdateCheck.manualMessage(it, BuildConfig.VERSION_NAME) }
            if (msg != null) {
                addView(TextView(context).apply {
                    text = msg; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(ink)
                    background = GradientDrawable().apply { setColor(accentSoft); cornerRadius = dp(14).toFloat() }
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                })
            }
            update?.let { u ->
                addView(compactAction("Skip this version") {
                    prefs.updateSkipped = u.version
                    UpdateNotifier.cancel(this@SettingsActivity)
                    updateStatus = null
                    render()
                })
            }
        }

        card("Feedback") {
            text("Found a bug or want a feature? Tell us. Only what you type here, plus the app version and your phone model, is sent.")
            row { button(FeedbackKind.PROBLEM.title) { showFeedback(FeedbackKind.PROBLEM) } }
            row { button(FeedbackKind.IDEA.title) { showFeedback(FeedbackKind.IDEA) } }
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

    private fun whatsNew() {
        card("What’s new") {
            text("Installed version: ${BuildConfig.VERSION_NAME}")
            foundUpdate()?.let { update ->
                text("Mouthpiece ${update.version} is available.")
                addView(compactAction("Download update", primary = true) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateFetcher.SITE)))
                })
            }
            addView(compactAction("Latest release notes") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseNotes.LATEST_URL)))
            })
        }
        ReleaseNotes.releases.forEach { release ->
            card("Mouthpiece ${release.version}") {
                release.changes.forEach { change -> text("• $change") }
            }
        }
    }

    private fun speechModels() {
        card("Speech models") {
            val active = prefs.modelName.takeIf(models::isInstalled)
            text(active?.let { name -> "In use: ${Prefs.MODELS.first { it.first == name }.second}" }
                ?: "No model ready. Download a speech model below to start dictating.")
            text("Listed from fastest to most accurate. Parakeet is the best balance of speed and accuracy. Medium and Large turbo can take several times longer than your speech to transcribe. Downloads stay on this phone and work offline.")
            Prefs.MODELS.forEach { (name, modelLabel) ->
                val installed = models.isInstalled(name)
                val inUse = name == active
                val downloading = name == downloadingModel
                val recommended = name == Prefs.RECOMMENDED_MODEL
                val option = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(8))
                    background = GradientDrawable().apply {
                        // In use: full accent tint. Recommended but not in use: a lighter tint and thicker outline so it stands out.
                        setColor(when { inUse -> accentSoft; recommended -> blend(surface, accentSoft, 0.45f); else -> surface })
                        cornerRadius = dp(12).toFloat()
                        setStroke(if (recommended) dp(2) else dp(1), if (inUse || recommended) accent else pal.outline)
                    }
                }
                addView(option, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
                if (recommended) {
                    option.addView(TextView(context).apply {
                        text = "★ Recommended"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); typeface = Typeface.DEFAULT_BOLD
                        setTextColor(pal.onAccent); setPadding(dp(8), dp(2), dp(8), dp(2)); setSingleLine(true)
                        background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(10).toFloat() }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                option.label(modelLabel)
                option.addView(styledText(when {
                    downloading -> downloadStatus ?: "Downloading…"
                    inUse -> "✓ In use · Downloaded"
                    installed -> "Downloaded · Ready to use"
                    else -> "Not downloaded"
                }).apply { if (inUse) { setTextColor(accent); typeface = Typeface.DEFAULT_BOLD } })
                if (name in Prefs.SLOW_MODELS) option.addView(styledText("Slow on a phone: a minute of speech can take several minutes to transcribe.").apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) })
                com.pharrlabs.voice.asr.ModelInfo.of(name)?.let { info ->
                    option.addView(styledText("${info.family} · ${info.params} parameters").apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) })
                }
                if (!inUse) {
                    option.chips {
                        if (installed) {
                            addView(compactAction("Use model", primary = true) { prefs.modelName = name; render() })
                            addView(compactAction("Remove") {
                                val mb = models.remove(name) / 1_000_000
                                toast("Download removed, freed $mb MB")
                                render()
                            }.apply { contentDescription = "Remove download: $modelLabel" })
                        } else {
                            addView(compactAction(if (downloading) "Downloading…" else "Download & use", primary = true) { startDownload(name) }
                                .apply { isEnabled = downloadJob?.isActive != true; alpha = if (isEnabled) 1f else 0.5f })
                        }
                    }
                }
            }
            if (downloadingModel == null) downloadStatus?.let { text(it) }
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
            choice("Right of the field", !prefs.bubbleCorner && !prefs.bubbleLeft) { setPosition(left = false, corner = false) }
            choice("Left of the field", !prefs.bubbleCorner && prefs.bubbleLeft) { setPosition(left = true, corner = false) }
            choice("Bottom-left corner (by the navigation bar)", prefs.bubbleCorner) { setPosition(left = prefs.bubbleLeft, corner = true) }
            text("You can also drag the bubble anywhere; it remembers where you put it.")
            if (prefs.bubbleOffsetX != 0 || prefs.bubbleOffsetY != 0) {
                button("Reset dragged position") { prefs.bubbleOffsetX = 0; prefs.bubbleOffsetY = 0; render() }
            }
        }
    }

    private fun setPosition(left: Boolean, corner: Boolean) {
        prefs.bubbleLeft = left; prefs.bubbleCorner = corner
        prefs.bubbleOffsetX = 0; prefs.bubbleOffsetY = 0
        render()
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
            row {
                gravity = Gravity.TOP
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                addView(spokenField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(writtenField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
            }
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
                box.text("A speech model is required. Choose “Download & use” in Speech models below. Once downloaded, it works offline.")
            }
            Step.BatteryExemption -> {
                box.text("Recommended: exempt from battery optimization so Android does not stop the bubble in the background.")
                box.button("Allow background use") { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            }
        }
    }

    private fun startDownload(name: String) {
        if (downloadJob?.isActive == true) return
        downloadingModel = name
        downloadStatus = "Starting…"
        downloadJob = scope.launch {
            val result = models.download(name) { p ->
                runOnUiThread { downloadStatus = "Downloading… ${(p * 100).toInt()}%"; if (!isDestroyed) render() }
            }
            downloadJob = null
            downloadingModel = null
            downloadStatus = result.fold(
                onSuccess = { prefs.modelName = name; "Model downloaded and selected. Ready to dictate." },
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

    /** A small form that posts a problem report or feature idea to the download site (no account needed). */
    private fun showFeedback(kind: FeedbackKind) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            background = GradientDrawable().apply { setColor(surface); cornerRadius = dp(24).toFloat() }
        }
        box.addView(TextView(this).apply {
            text = kind.title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(ink); typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(styledText("Only what you type here, plus the app version and your phone model, is sent."))
        fun field(hintText: String, multi: Boolean) = EditText(this).apply {
            hint = hintText; setHintTextColor(muted); setTextColor(ink); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            inputType = InputType.TYPE_CLASS_TEXT or (if (multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else 0)
            if (multi) { minLines = 4; gravity = Gravity.TOP or Gravity.START }
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        }
        val message = field(kind.hint, true)
        val contact = field("Email or phone (optional, only if you want a reply)", false)
        box.addView(message); box.addView(contact)
        val dialog = android.app.AlertDialog.Builder(this).setView(box).create()
        var sending = false
        val send = styledButton("Send") {}
        send.setOnClickListener {
            if (sending) return@setOnClickListener
            val json = Feedback.payload(
                kind, message.text.toString(), contact.text.toString(), BuildConfig.VERSION_NAME,
                android.os.Build.VERSION.RELEASE, android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL,
            )
            if (json == null) { toast(Feedback.message(SendResult.EMPTY)); return@setOnClickListener }
            sending = true; send.text = "Sending..."
            scope.launch {
                val r = FeedbackSender.send(json)
                sending = false; send.text = "Send"
                toast(Feedback.message(r))
                if (r == SendResult.SENT) dialog.dismiss()
            }
        }
        val cancel = TextView(this).apply {
            text = "Cancel"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(accent)
            minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, dp(16), 0)
            setOnClickListener { dialog.dismiss() }
        }
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(cancel); addView(send)
        })
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }


    private fun appName(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.ifBlank { "Unknown app" })

    private fun pill(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat() }

    private fun styledText(t: String) = TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(muted); setPadding(0, dp(4), 0, dp(4))
    }

    /** Small visual footprint with a full-height touch target, wrapping alongside related actions. */
    private fun compactAction(t: String, primary: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(if (primary) pal.onAccent else accent)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(pal.accentSoft),
            pill(if (primary) accent else Color.TRANSPARENT), null,
        )
        isFocusable = true
        setOnClickListener { onClick() }
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

    private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
    )

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

    companion object {
        const val ACTION_SELECT_MODEL = "com.pharrlabs.voice.SELECT_MODEL"
        const val ACTION_SHOW_UPDATES = "com.pharrlabs.voice.SHOW_UPDATES"
        private const val REQ_NOTIFICATIONS = 103
        private const val REQ_EXPORT = 101
        private const val REQ_IMPORT = 102
    }
}
