package com.pharrlabs.voice.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ThemeTest {
    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = lum(a).let { x -> lum(b).let { y -> maxOf(x, y) to minOf(x, y) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, p: Palette) {
        fun need(label: String, fg: Int, bg: Int) = assertTrue("$name $label = ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5)
        need("ink on page", p.ink, p.bg)
        need("ink on card", p.ink, p.surface)
        need("muted on card", p.muted, p.surface)
        need("muted on page", p.muted, p.bg)
        need("ink on chip", p.ink, p.chipBg)
        need("accent on selected", p.accent, p.accentSoft)
        need("accent on chip", p.accent, p.chipBg)
        need("onAccent on accent", p.onAccent, p.accent)
    }

    @Test fun lightPaletteIsReadable() = check("light", Palettes.light)

    @Test fun darkPaletteIsReadable() = check("dark", Palettes.dark)

    @Test fun darkPageIsActuallyDark() = assertTrue(lum(Palettes.dark.bg) < 0.05)

    @Test fun lightPageIsActuallyLight() = assertTrue(lum(Palettes.light.bg) > 0.8)

    @Test fun systemModeFollowsDevice() {
        assertTrue(ThemeMode.System.isDark(systemNight = true))
        assertFalse(ThemeMode.System.isDark(systemNight = false))
    }

    @Test fun explicitModesIgnoreDevice() {
        assertFalse(ThemeMode.Light.isDark(systemNight = true))
        assertTrue(ThemeMode.Dark.isDark(systemNight = false))
    }

    @Test fun paletteForModePicksMatchingColors() {
        assertEquals(Palettes.dark, Palettes.of(ThemeMode.Dark, systemNight = false))
        assertEquals(Palettes.light, Palettes.of(ThemeMode.System, systemNight = false))
    }

    private fun hue(c: Int): Float {
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        if (d == 0f) return 0f
        val h = when (mx) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f }
        return (h * 60f + 360f) % 360f
    }

    @Test fun lightAccentIsBrandOrange() = assertTrue("hue ${hue(Palettes.light.accent)}", hue(Palettes.light.accent) in 15f..35f)

    @Test fun darkAccentIsBrandOrange() = assertTrue("hue ${hue(Palettes.dark.accent)}", hue(Palettes.dark.accent) in 15f..35f)

    @Test fun cardsAreWarmNotLavender() {
        for (p in listOf(Palettes.light, Palettes.dark)) assertTrue("hue ${hue(p.surface)}", hue(p.surface) in 10f..45f)
    }
}
