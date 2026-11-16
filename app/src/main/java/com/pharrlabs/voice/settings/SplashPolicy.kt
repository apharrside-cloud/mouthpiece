package com.pharrlabs.voice.settings

/** The short welcome splash: shown once, on the first launch of an install. */
object SplashPolicy {
    const val SHOW_MS = 1800L
    const val FADE_MS = 350L

    fun shouldShow(seen: Boolean): Boolean = !seen
}
