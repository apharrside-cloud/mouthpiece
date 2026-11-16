package com.pharrlabs.voice.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashPolicyTest {
    @Test fun showsOnTheVeryFirstLaunch() = assertTrue(SplashPolicy.shouldShow(seen = false))

    @Test fun neverShowsAgainAfterwards() = assertFalse(SplashPolicy.shouldShow(seen = true))

    @Test fun staysLongEnoughToSeeButNotLongEnoughToAnnoy() {
        assertTrue(SplashPolicy.SHOW_MS in 1200L..2500L)
        assertTrue(SplashPolicy.FADE_MS in 150L..600L)
    }

    @Test fun backupsDoNotCarrySplashState() = assertFalse("splash_seen" in Prefs.BACKUP_KEYS)

    @Test fun bubbleColorIsBackedUp() = assertTrue("bubble_color" in Prefs.BACKUP_KEYS)
}
