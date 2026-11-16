package com.pharrlabs.voice.settings

/** Which colors the settings screen uses: follow the phone, or force light or dark. */
enum class ThemeMode(val label: String) {
    System("System"), Light("Light"), Dark("Dark");

    fun isDark(systemNight: Boolean): Boolean = when (this) {
        System -> systemNight
        Light -> false
        Dark -> true
    }
}

/** Every color the settings screen draws with, so text always has a defined background to be readable on. */
data class Palette(
    val bg: Int,
    val surface: Int,
    val ink: Int,
    val muted: Int,
    val accent: Int,
    val accentSoft: Int,
    val onAccent: Int,
    val outline: Int,
    val chipBg: Int,
)

object Palettes {
    val light = Palette(
        bg = 0xFFFFFFFF.toInt(), surface = 0xFFF7F2FA.toInt(), ink = 0xFF1D1B20.toInt(), muted = 0xFF625B71.toInt(),
        accent = 0xFF6750A4.toInt(), accentSoft = 0xFFEADDFF.toInt(), onAccent = 0xFFFFFFFF.toInt(),
        outline = 0xFFCAC4D0.toInt(), chipBg = 0xFFFFFFFF.toInt(),
    )

    val dark = Palette(
        bg = 0xFF141218.toInt(), surface = 0xFF2B2930.toInt(), ink = 0xFFE6E0E9.toInt(), muted = 0xFFCAC4D0.toInt(),
        accent = 0xFFD0BCFF.toInt(), accentSoft = 0xFF4F378B.toInt(), onAccent = 0xFF381E72.toInt(),
        outline = 0xFF79747E.toInt(), chipBg = 0xFF1D1B20.toInt(),
    )

    fun of(mode: ThemeMode, systemNight: Boolean): Palette = if (mode.isDark(systemNight)) dark else light
}
