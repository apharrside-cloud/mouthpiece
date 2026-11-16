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
        bg = 0xFFFFFAF5.toInt(), surface = 0xFFFFF0E3.toInt(), ink = 0xFF1D1B20.toInt(), muted = 0xFF6B5546.toInt(),
        accent = 0xFFA84300.toInt(), accentSoft = 0xFFFFDDBF.toInt(), onAccent = 0xFFFFFFFF.toInt(),
        outline = 0xFFE0CBB8.toInt(), chipBg = 0xFFFFFFFF.toInt(),
    )

    val dark = Palette(
        bg = 0xFF17120E.toInt(), surface = 0xFF2A2019.toInt(), ink = 0xFFF3E9E0.toInt(), muted = 0xFFD2C3B6.toInt(),
        accent = 0xFFFFA45C.toInt(), accentSoft = 0xFF5A3515.toInt(), onAccent = 0xFF2B1608.toInt(),
        outline = 0xFF8A7565.toInt(), chipBg = 0xFF1F1812.toInt(),
    )

    fun of(mode: ThemeMode, systemNight: Boolean): Palette = if (mode.isDark(systemNight)) dark else light
}
