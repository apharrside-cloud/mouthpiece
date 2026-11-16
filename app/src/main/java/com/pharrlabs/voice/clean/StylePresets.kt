package com.pharrlabs.voice.clean

/** Built-in style per app, used until the user picks one. */
object StylePresets {
    private val presets = mapOf(
        "com.google.android.apps.messaging" to Style.Casual,
        "com.whatsapp" to Style.Casual,
        "org.telegram.messenger" to Style.Casual,
        "com.facebook.orca" to Style.Casual,
        "com.discord" to Style.Casual,
        "com.Slack" to Style.Casual,
        "com.google.android.gm" to Style.Formal,
        "com.microsoft.office.outlook" to Style.Formal,
        "com.microsoft.teams" to Style.Standard,
        "com.termux" to Style.Literal,
    )

    fun forPackage(pkg: String): Style? = presets[pkg]
}
